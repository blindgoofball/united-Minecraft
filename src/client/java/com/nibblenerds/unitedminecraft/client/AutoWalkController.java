package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The scanner's "walk to it" automation: computes a path with vanilla's own pathfinding
 * (the same {@link PathFinder}/{@link WalkNodeEvaluator} a mob uses), then simulates
 * ordinary forward/turn/jump input each tick until the path finishes. This is the
 * client-side replacement for the old server-teleport approach - since it only ever
 * generates the same movement packets any real player already sends, it needs no server
 * cooperation at all and works on any vanilla server.
 *
 * <p>Path computation needs a {@link Mob} purely as a parameter bag (bounding box, step
 * height, fall tolerance) - it's never added to the world or ticked, just positioned at
 * the player's feet and handed to the pathfinder, which only reads those parameters.
 *
 * <p>Once a path is found, the player's own {@link LocalPlayer#input} field (normally a
 * {@code KeyboardInput} reading real keys) is swapped for {@link AutoWalkInput}, which
 * reports "forward" (and "jump" when the next waypoint is a step up) as if held - the same
 * mechanism real keyboard input uses, so movement and network sync all just work. Sprinting
 * still follows the real, actual sprint key each tick ({@code LocalPlayer} reads it straight
 * off whatever {@code Input} is currently installed, same as it would for real keyboard
 * input) - hold it down while auto-walking and you'll sprint there, exactly like walking
 * there yourself would. The original input is restored when the walk ends or is cancelled.
 */
public final class AutoWalkController {
	private static final float MAX_PATH_LENGTH = 128.0f;
	// Vanilla's PathFinder stops searching the instant it pops ANY node within Manhattan
	// distance reachRange of the target - not the closest one, just the first one it happens
	// to dequeue, which on open ground is whichever node the search frontier reaches first at
	// that exact distance. With reachRange 2, a straight-line approach to a door/chest hits
	// that condition two whole blocks out and the search stops right there, well short of the
	// tile actually adjacent to the target - canReach() then reports success for a path that
	// never got close enough for hasClearLineToTarget to agree, producing a false "didn't
	// reach" even though nothing physically blocked the last two steps. reachRange 1 only ever
	// satisfies on a node that's genuinely (orthogonally) adjacent to the target, so the walk
	// keeps going until the player could actually reach out and interact with it.
	private static final int REACH_RANGE = 1;
	private static final double NODE_ARRIVAL_DISTANCE_SQR = 0.5 * 0.5;

	// See TrailController's identical pattern (and its own doc on STUCK_TICKS_THRESHOLD) for why
	// "no real progress toward the next waypoint for a while" is the right thing to watch for -
	// a mob being pushed off its path, a block placed mid-walk, or another player's build can all
	// strand this exactly the same way a straight-line trail leg can clip an obstacle.
	private static final int STUCK_TICKS_THRESHOLD = 40;
	private static final double STUCK_PROGRESS_EPSILON = 0.05;

	private static Path currentPath;
	private static ClientInput previousInput;
	private static Component targetName;
	private static Runnable onArrival;
	private static double bestDistanceToNext = Double.MAX_VALUE;
	private static int stuckTicks;
	// Whether this stuck episode has already tried recomputing the path once - only one retry
	// per episode, so a genuinely unreachable spot still gives up instead of re-pathing forever.
	private static boolean rePathAttempted;

	private AutoWalkController() {
	}

	public static boolean isActive() {
		return currentPath != null;
	}

	public static void reset() {
		currentPath = null;
		previousInput = null;
		targetName = null;
		onArrival = null;
		bestDistanceToNext = Double.MAX_VALUE;
		stuckTicks = 0;
		rePathAttempted = false;
	}

	public static void start(Minecraft client, LocalPlayer player, BlockPos target, Component name) {
		start(client, player, target, name, null);
	}

	/**
	 * Same as {@link #start(Minecraft, LocalPlayer, BlockPos, Component)}, but runs {@code
	 * onArrival} once the walk actually finishes - not on cancellation - so a caller can re-run
	 * whatever pressing the scanner's target key directly would have done (aiming at the block,
	 * or locking onto the entity) now that the player is actually standing there. Runs after
	 * the player's real input is restored, so it's free to change rotation as if it were the
	 * player doing it themselves.
	 */
	public static void start(Minecraft client, LocalPlayer player, BlockPos target, Component name, Runnable onArrival) {
		if (isActive()) {
			cancel(client, player);
		}

		Path path = ClientPathfinding.computePath(player, target, MAX_PATH_LENGTH, REACH_RANGE);
		if (path == null || path.getNodeCount() == 0) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.autowalk_unreachable"));
			return;
		}

		currentPath = path;
		targetName = name;
		AutoWalkController.onArrival = onArrival;
		previousInput = player.input;
		player.input = new AutoWalkInput();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.autowalk_started", name));
	}

	public static void cancel(Minecraft client, LocalPlayer player) {
		if (!isActive()) {
			return;
		}
		finish(client, player, "united_minecraft.narrate.autowalk_cancelled");
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!isActive()) {
			return;
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_STOP_LOCK)) {
			cancel(client, player);
			return;
		}

		BlockPos nextPos = currentPath.getNextNodePos();
		double dx = nextPos.getX() + 0.5 - player.getX();
		double dz = nextPos.getZ() + 0.5 - player.getZ();

		if (dx * dx + dz * dz < NODE_ARRIVAL_DISTANCE_SQR) {
			currentPath.advance();
			bestDistanceToNext = Double.MAX_VALUE;
			stuckTicks = 0;
			rePathAttempted = false;
			if (currentPath.isDone()) {
				// vanilla's pathfinder gives up and hands back its best-effort partial path
				// (rather than null) both when the target is farther than MAX_PATH_LENGTH and
				// when it's blocked off entirely - canReach() is what actually distinguishes "got
				// there" from "walked as far as it could and stopped". But canReach() alone isn't
				// enough either: it's satisfied once the best walkable node found is within
				// reachRange of the target's raw coordinates, with no check that anything solid
				// sits between them - standing flush against a wall with a chest 1-2 blocks away
				// on its far side counts as "reached" by that measure even though there's no way
				// through. The line-of-sight check below catches that case.
				if (currentPath.canReach() && hasClearLineToTarget(player.level(), player, currentPath.getTarget())) {
					Runnable callback = onArrival;
					client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_CHIME.value(),
							SoundSource.MASTER, 0.7f, 1.4f, player.getRandom(), player.getX(), player.getY(), player.getZ()));
					// A caller with its own onArrival callback narrates something more specific
					// ("Facing X", a lock-on) - saying "Arrived" first would just be immediately
					// talked over. Only narrate it here when there's no callback to say anything
					// else (Build Mode's walk-to-cursor, say).
					finish(client, player, callback != null ? null : "united_minecraft.narrate.autowalk_arrived");
					if (callback != null) {
						callback.run();
					}
				} else {
					int remaining = Math.round(currentPath.getDistToTarget());
					finishIncomplete(client, player, remaining);
				}
			}
			return;
		}

		// No real progress toward the current waypoint for a while, despite actively walking -
		// something's blocking the straight line the path assumed was clear (a mob shoved the
		// player off course, a block got placed mid-walk, terrain changed). Try recomputing the
		// path once from here before giving up outright.
		double distanceToNext = Math.sqrt(dx * dx + dz * dz);
		if (distanceToNext < bestDistanceToNext - STUCK_PROGRESS_EPSILON) {
			bestDistanceToNext = distanceToNext;
			stuckTicks = 0;
		} else if (++stuckTicks > STUCK_TICKS_THRESHOLD) {
			if (!rePathAttempted && tryRepath(player)) {
				rePathAttempted = true;
				bestDistanceToNext = Double.MAX_VALUE;
				stuckTicks = 0;
			} else {
				finishStuck(client, player);
			}
			return;
		}

		// Same inverse-of-calculateViewVector formula CameraUtil.aimAt uses, but yaw only -
		// we want the player's body (and thus their walk direction) turning to face the next
		// waypoint, not their pitch changing while they walk.
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setOldRot();

		boolean needsJump = player.onGround() && nextPos.getY() > Mth.floor(player.getY() + 0.1);
		((AutoWalkInput) player.input).setWalking(needsJump, client.options.keySprint.isDown());
	}

	/** {@code messageKey} may be null to restore input and reset state without narrating anything. */
	private static void finish(Minecraft client, LocalPlayer player, String messageKey) {
		Component name = targetName;
		player.input = previousInput;
		reset();
		if (messageKey == null) {
			return;
		}
		client.getNarrator().saySystemNow(name != null
				? Component.translatable(messageKey, name)
				: Component.translatable(messageKey));
	}

	/**
	 * Ends the walk when the path never actually reached the target (see the {@code canReach()}
	 * check above) - no arrival chime and no {@code onArrival} callback, since neither belongs
	 * once the player didn't actually get there; just a plain narration of how much farther the
	 * target still is, so it's clear more walking (or a different route) is needed rather than
	 * this reading as a successful arrival.
	 */
	private static void finishIncomplete(Minecraft client, LocalPlayer player, int remainingBlocks) {
		Component name = targetName;
		player.input = previousInput;
		reset();
		playStoppedCue(client, player);
		client.getNarrator().saySystemNow(name != null
				? Component.translatable("united_minecraft.narrate.autowalk_incomplete", remainingBlocks, name)
				: Component.translatable("united_minecraft.narrate.autowalk_incomplete_unnamed", remainingBlocks));
	}

	/** Recomputes the path to the original target from the player's current position - true (and swaps {@link #currentPath}) only if one was actually found. */
	private static boolean tryRepath(LocalPlayer player) {
		BlockPos target = currentPath.getTarget();
		Path fresh = ClientPathfinding.computePath(player, target, MAX_PATH_LENGTH, REACH_RANGE);
		if (fresh == null || fresh.getNodeCount() == 0) {
			return false;
		}
		currentPath = fresh;
		return true;
	}

	/** Ends the walk after a re-path attempt also failed to make progress - same shape as {@link #finishIncomplete}. */
	private static void finishStuck(Minecraft client, LocalPlayer player) {
		int remaining = Math.round(currentPath.getDistToTarget());
		Component name = targetName;
		player.input = previousInput;
		reset();
		playStoppedCue(client, player);
		client.getNarrator().saySystemNow(name != null
				? Component.translatable("united_minecraft.narrate.autowalk_stuck", remaining, name)
				: Component.translatable("united_minecraft.narrate.autowalk_stuck_unnamed", remaining));
	}

	/** Low "stopped short" thud for both {@link #finishIncomplete} and {@link #finishStuck} - deliberately
	 * a dull, low tone rather than a musical one, so it reads as a failure distinct from the bright
	 * ascending {@link SoundEvents#NOTE_BLOCK_CHIME} arrival cue and from every other note-block cue
	 * this mod already uses elsewhere. */
	private static void playStoppedCue(Minecraft client, LocalPlayer player) {
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_BASS.value(),
				SoundSource.MASTER, 0.7f, 0.7f, player.getRandom(), player.getX(), player.getY(), player.getZ()));
	}

	/**
	 * Whether anything solid actually sits between the player and the target block -
	 * {@code canReach()} alone only checks raw distance, so this catches "reached" purely by
	 * being close enough on the wrong side of a wall. A hit on the target block itself counts as
	 * clear (that's the expected, desired hit for a solid target like a chest); a hit on
	 * anything else first means something's in the way.
	 *
	 * <p>The ray starts level with the target's own height rather than the player's eye height:
	 * casting from eye height means, at close range, a steep downward angle into the target's
	 * block column - which clips whatever's stacked above the target (a door's top half, a
	 * tree's canopy overhanging its trunk) before the ray ever reaches the target itself, wrongly
	 * reporting "blocked" while standing right next to it.
	 */
	private static boolean hasClearLineToTarget(Level level, LocalPlayer player, BlockPos target) {
		Vec3 from = new Vec3(player.getX(), target.getY() + 0.5, player.getZ());
		BlockHitResult hit = level.clip(new ClipContext(
				from, Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
	}

	/** Reports "forward" (and "jump"/"sprint" as needed) as held, exactly like real keyboard input would. */
	private static final class AutoWalkInput extends ClientInput {
		void setWalking(boolean jump, boolean sprint) {
			this.keyPresses = new Input(true, false, false, false, jump, false, sprint);
			this.moveVector = new Vec2(0.0f, 1.0f);
		}
	}
}
