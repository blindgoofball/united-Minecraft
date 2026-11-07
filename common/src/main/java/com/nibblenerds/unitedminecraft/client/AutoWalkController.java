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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * follows the real, actual sprint key each tick ({@code LocalPlayer} reads it straight off
 * whatever {@code Input} is currently installed, same as it would for real keyboard input) -
 * hold it down while auto-walking and you'll sprint there, exactly like walking there
 * yourself would - or, with {@link UnitedMinecraftConfig#autoWalkAutoSprint} on, sprints the
 * whole way regardless of whether the key is held, so it doesn't need holding down for the
 * length of the walk. The original input is restored when the walk ends or is cancelled.
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
	// A walkable target cell (a ladder/vine/scaffolding/etc. to climb, or an empty cell a Build
	// Mode cursor is pointing at) isn't an obstacle to stop beside - it's ground the player can
	// actually stand on, same as any other open floor. Reusing REACH_RANGE 1 for one of these
	// would let the pathfinder settle for *any* horizontally adjacent cell around the target -
	// including one on a completely different side of whatever solid block it's next to, close
	// enough by raw distance but not actually where the player meant to end up (a wall in
	// between, or, for a ladder specifically, simply the wrong side of it to climb). See {@link
	// #startExact}.
	private static final int EXACT_REACH_RANGE = 0;
	// A mount is wider than the player, and the pathfinder tests its whole hitbox at every node, so it
	// cannot stand in a doorway or right beside a wall the way a player can: the nearest spot it fits is
	// often a couple of blocks out. Searching for an adjacent node would never succeed and would run
	// the search to its node limit, so a ridden mount counts as arrived from this far (still well inside
	// the player's reach, and the line-of-sight check on arrival still applies).
	private static final int MOUNTED_REACH_RANGE = 3;
	// A happy ghast is four blocks across and cannot nose up to a wall, so it counts as arrived from further out.
	private static final int FLYING_REACH_RANGE = 5;
	// How near the next waypoint, in three dimensions, counts as having reached it - generous, since
	// the mount is large and slow to turn.
	private static final double FLY_ARRIVAL_DISTANCE = 1.5;
	private static final double NODE_ARRIVAL_DISTANCE_SQR = 0.5 * 0.5;
	// How close to a ladder cell's level counts as having reached it, going up / down. Climbing
	// up overshoots a little each tick, sliding down is slower than that.
	private static final double CLIMB_ARRIVAL_UP = 0.1;
	private static final double CLIMB_ARRIVAL_DOWN = 0.3;
	private static final double CLIMB_CENTRE_DISTANCE_SQR = 0.2 * 0.2;

	private static Path currentPath;
	private static ClientInput previousInput;
	private static Component targetName;
	private static Runnable onArrival;
	private static int reachRange = REACH_RANGE;
	private static final StuckDetector STUCK = new StuckDetector();
	// Whether this stuck episode has already tried recomputing the path once - only one retry
	// per episode, so a genuinely unreachable spot still gives up instead of re-pathing forever.
	private static boolean rePathAttempted;
	// The block the walk was asked to reach. The path's own target can differ from it: a flying route
	// aims for a spot with a view of the target instead.
	private static BlockPos routeTarget;

	private AutoWalkController() {
	}

	public static boolean isActive() {
		return currentPath != null;
	}

	/**
	 * The entity that actually moves along the route: the mount while the player steers one that Auto-Walk
	 * can drive, otherwise the player. A ridden mount carries the player at saddle height, so positions,
	 * ground contact and hitbox all have to come from it rather than from the player.
	 */
	static Entity bodyOf(LocalPlayer player) {
		Entity vehicle = player.getVehicle();
		if ((vehicle instanceof AbstractHorse || vehicle instanceof HappyGhast)
				&& ((net.minecraft.world.entity.LivingEntity) vehicle).getControllingPassenger() == player) {
			return vehicle;
		}
		return player;
	}

	public static void reset() {
		currentPath = null;
		routeTarget = null;
		previousInput = null;
		targetName = null;
		onArrival = null;
		reachRange = REACH_RANGE;
		STUCK.reset();
		rePathAttempted = false;
		MountJumper.reset();
		AutoWalkDoors.reset();
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
		start(client, player, target, name, onArrival, REACH_RANGE);
	}

	/**
	 * Same as {@link #start(Minecraft, LocalPlayer, BlockPos, Component, Runnable)}, for a target
	 * the player needs to end up standing exactly on/in rather than merely near - a ladder, vine,
	 * scaffolding, or anything else in the Climbable scanner category (which the player needs to
	 * walk into, not stand beside, to actually climb), or an empty cell a Build Mode cursor is
	 * pointing at (so "walk to the cursor" actually arrives there, instead of at an unpredictable
	 * nearby tile). {@code target} must itself be a cell the player can occupy - solid ground the
	 * player is meant to stand beside instead still wants the ordinary {@link #start}, since a
	 * solid block obviously can't be walked into. See {@link #EXACT_REACH_RANGE}'s own doc for why
	 * the ordinary reach range can otherwise land the player somewhere unintended entirely, e.g.
	 * the wrong side of a ladder to climb it.
	 */
	public static void startExact(Minecraft client, LocalPlayer player, BlockPos target, Component name, Runnable onArrival) {
		start(client, player, target, name, onArrival, EXACT_REACH_RANGE);
	}

	/**
	 * Same as {@link #start} or {@link #startExact}, but says {@code message} when the walk begins in
	 * place of the usual "Walking to ..." - for a caller that has something of its own to say first, which
	 * the usual message would cut off.
	 */
	static void startAnnounced(Minecraft client, LocalPlayer player, BlockPos target, Component name, Runnable onArrival,
			boolean exact, Component message) {
		start(client, player, target, name, onArrival, exact ? EXACT_REACH_RANGE : REACH_RANGE, message);
	}

	private static void start(Minecraft client, LocalPlayer player, BlockPos target, Component name, Runnable onArrival, int reachRange) {
		start(client, player, target, name, onArrival, reachRange, null);
	}

	private static void start(Minecraft client, LocalPlayer player, BlockPos target, Component name, Runnable onArrival,
			int reachRange, Component message) {
		if (player.isFallFlying()) {
			// There is no walking through the air: while gliding, "go there" means fly there.
			ElytraLandingController.startToward(client, player, target, name, onArrival, reachRange == EXACT_REACH_RANGE);
			return;
		}
		if (isActive()) {
			cancel(client, player);
		}
		Entity mount = bodyOf(player);
		if (mount != player) {
			reachRange = Math.max(reachRange, mount instanceof HappyGhast ? FLYING_REACH_RANGE : MOUNTED_REACH_RANGE);
		}

		Path path = ClientPathfinding.computePath(player, target, MAX_PATH_LENGTH, reachRange,
				UnitedMinecraftConfig.get().autoWalkSwim, UnitedMinecraftConfig.get().autoWalkOpenDoors);
		if (path == null || path.getNodeCount() == 0) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.autowalk_unreachable"));
			return;
		}

		currentPath = path;
		routeTarget = target;
		targetName = name;
		AutoWalkController.onArrival = onArrival;
		AutoWalkController.reachRange = reachRange;
		previousInput = player.input;
		player.input = new RouteInput();
		client.getNarrator().saySystemNow(message != null ? message : Component.translatable("united_minecraft.narrate.autowalk_started", name));
		AngleAssistController.disable(client);
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

		Entity body = bodyOf(player);
		if (body instanceof HappyGhast ghast) {
			tickFlying(client, player, ghast);
			return;
		}
		BlockPos nextPos = currentPath.getNextNodePos();
		double dx = nextPos.getX() + 0.5 - body.getX();
		double dz = nextPos.getZ() + 0.5 - body.getZ();
		// Moving between two climbable cells (see AutoWalkNodeEvaluator): the leg is vertical, so
		// "arrived" has to mean the right height too, not just the right column.
		Level level = player.level();
		boolean climbLeg = AutoWalkNodeEvaluator.isClimbable(level.getBlockState(nextPos))
				&& AutoWalkNodeEvaluator.isClimbable(level.getBlockState(body.blockPosition()));
		boolean climbingUp = climbLeg && nextPos.getY() > body.getY();
		double dy = climbLeg ? nextPos.getY() - body.getY() : 0.0;
		boolean atHeight = !climbLeg || (climbingUp ? dy <= CLIMB_ARRIVAL_UP : dy >= -CLIMB_ARRIVAL_DOWN);
		// A drop is a waypoint in the same column as the ledge the player stands on: they are
		// horizontally "there" before they have fallen, and counting that would end the walk (or skip
		// on to the next waypoint) while they are still up on the ledge.
		if (!climbLeg && ArrivalHeights.stillAboveWaypoint(body.getY(), nextPos.getY())) {
			atHeight = false;
		}

		if (dx * dx + dz * dz < NODE_ARRIVAL_DISTANCE_SQR && atHeight) {
			currentPath.advance();
			STUCK.reset();
			rePathAttempted = false;
			if (currentPath.isDone()) {
				completePath(client, player, body, true);
			}
			return;
		}

		if (UnitedMinecraftConfig.get().autoWalkOpenDoors) {
			AutoWalkDoors.closeBehind(client, player, AutoWalkDoors.CLOSE_BEHIND_DISTANCE);
			AutoWalkDoors.Gate gate = AutoWalkDoors.approach(client, player, currentPath);
			if (gate == AutoWalkDoors.Gate.FAILED) {
				finishStuck(client, player);
				return;
			}
			if (gate == AutoWalkDoors.Gate.WAIT) {
				// Standing at a door while it swings open is not the lack of progress the check below is for.
				STUCK.reset();
				((RouteInput) player.input).setIdle();
				return;
			}
		}

		// No real progress toward the current waypoint for a while, despite actively walking -
		// something's blocking the straight line the path assumed was clear (a mob shoved the
		// player off course, a block got placed mid-walk, terrain changed). Try recomputing the
		// path once from here before giving up outright.
		if (STUCK.isStuck(Math.sqrt(dx * dx + dz * dz) + Math.abs(dy))) {
			if (!rePathAttempted && tryRepath(player)) {
				rePathAttempted = true;
				STUCK.reset();
			} else {
				finishStuck(client, player);
			}
			return;
		}

		if (climbLeg) {
			// Close to the middle of the cell, stop steering: with no horizontal offset left the
			// yaw is meaningless, and pushing forward into the wall would climb when we want to
			// descend.
			boolean offCentre = dx * dx + dz * dz > CLIMB_CENTRE_DISTANCE_SQR;
			if (offCentre) {
				float climbYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				player.setYRot(climbYaw);
				player.setYHeadRot(climbYaw);
				player.setOldRot();
			}
			((RouteInput) player.input).setClimbing(climbingUp, offCentre);
			return;
		}

		// Same inverse-of-calculateViewVector formula CameraUtil.aimAt uses, but yaw only -
		// we want the player's body (and thus their walk direction) turning to face the next
		// waypoint, not their pitch changing while they walk.
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setOldRot();

		// Jumping also climbs, which is how the player gets off the top of a ladder onto a ledge.
		// Jump is also vanilla's swim-up input: held the whole way across, it keeps the player
		// afloat at the surface (and gets them up onto the bank at the far side).
		if (body instanceof AbstractHorse horse) {
			// A ridden horse steps up a block by itself; anything higher is a charged jump (MountJumper),
			// and it ignores the sprint key.
			MountJumper.drive(player, horse, MountJumper.needsJump(horse.getY(), nextPos.getY()),
					Math.sqrt(dx * dx + dz * dz), (RouteInput) player.input);
			return;
		}
		boolean swimming = player.isInWater() && UnitedMinecraftConfig.get().autoWalkSwim;
		boolean needsJump = swimming || (player.onGround() || player.onClimbable()) && nextPos.getY() > Mth.floor(player.getY() + 0.1);
		boolean sprint = client.options.keySprint.isDown() || UnitedMinecraftConfig.get().autoWalkAutoSprint;
		((RouteInput) player.input).setWalking(needsJump, sprint);
	}

	/**
	 * The last waypoint has been passed: arrived, or came up short. {@code checkLevel} is false when
	 * flying, where being level with the target means nothing.
	 */
	private static void completePath(Minecraft client, LocalPlayer player, Entity body, boolean checkLevel) {
		// vanilla's pathfinder gives up and hands back its best-effort partial path
		// (rather than null) both when the target is farther than MAX_PATH_LENGTH and
		// when it's blocked off entirely - canReach() is what actually distinguishes "got
		// there" from "walked as far as it could and stopped". But canReach() alone isn't
		// enough either: it's satisfied once the best walkable node found is within
		// reachRange of the target's raw coordinates, with no check that anything solid
		// sits between them - standing flush against a wall with a chest 1-2 blocks away
		// on its far side counts as "reached" by that measure even though there's no way
		// through. The line-of-sight check below catches that case.
		// A flying route may end at a spot picked for its view of the target rather than at the target
		// itself (see ClientPathfinding), so judge it against the target the walk was asked for, and from
		// the rider's eye, which is what matters up on a mount.
		boolean clear = checkLevel
				? hasClearLineToTarget(player.level(), player, routeTarget)
				: hasClearLineFromEye(player.level(), player, routeTarget);
		if (currentPath.canReach()
				&& (!checkLevel || ArrivalHeights.levelWithTarget(body.getY(), routeTarget.getY()))
				&& clear) {
			Runnable callback = onArrival;
			client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_CHIME.value(),
					SoundSource.MASTER, CueVolume.scale(0.7f), 1.4f, player.getRandom(), player.getX(), player.getY(), player.getZ()));
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

	/**
	 * One tick of flying a ridden happy ghast along the path. It moves along the rider's pitch as well as
	 * yaw, so the rider's view is aimed straight at the waypoint in three dimensions and forward is
	 * pushed only once the ghast, which turns slowly toward the rider's yaw, is roughly facing it.
	 */
	private static void tickFlying(Minecraft client, LocalPlayer player, HappyGhast ghast) {
		Vec3 to = currentPath.getNextEntityPos(ghast).subtract(ghast.position());
		double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
		double distance = to.length();
		if (distance < FLY_ARRIVAL_DISTANCE) {
			currentPath.advance();
			STUCK.reset();
			rePathAttempted = false;
			if (currentPath.isDone()) {
				completePath(client, player, ghast, false);
			}
			return;
		}
		if (STUCK.isStuck(distance)) {
			if (!rePathAttempted && tryRepath(player)) {
				rePathAttempted = true;
				STUCK.reset();
			} else {
				finishStuck(client, player);
			}
			return;
		}
		// Straight above or below there is no heading to turn to; keep the current one.
		float yaw = horizontal > FlightSteering.MIN_HEADING_DISTANCE ? FlightSteering.yaw(to.x, to.z) : ghast.getYRot();
		float pitch = FlightSteering.pitch(horizontal, to.y);
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setXRot(pitch);
		player.setOldRot();
		boolean forward = FlightSteering.shouldPush(Mth.wrapDegrees(yaw - ghast.getYRot()), horizontal);
		((RouteInput) player.input).setMounted(forward, false);
	}

	/** {@code messageKey} may be null to restore input and reset state without narrating anything. */
	private static void finish(Minecraft client, LocalPlayer player, String messageKey) {
		AutoWalkDoors.finish(client, player);
		Component name = targetName;
		player.input = previousInput;
		reset();
		if (messageKey == null) {
			return;
		}
		Component message = name != null
				? Component.translatable(messageKey, name)
				: Component.translatable(messageKey);
		// Cancelling answers a keypress, so it speaks at once; an arrival is the result of a walk
		// started earlier and waits for whatever is being said.
		if (messageKey.equals("united_minecraft.narrate.autowalk_cancelled")) {
			client.getNarrator().saySystemNow(message);
		} else {
			client.getNarrator().saySystemQueued(message);
		}
	}

	/**
	 * Ends the walk when the path never actually reached the target (see the {@code canReach()}
	 * check above) - no arrival chime and no {@code onArrival} callback, since neither belongs
	 * once the player didn't actually get there; just a plain narration of how much farther the
	 * target still is, so it's clear more walking (or a different route) is needed rather than
	 * this reading as a successful arrival.
	 */
	private static void finishIncomplete(Minecraft client, LocalPlayer player, int remainingBlocks) {
		AutoWalkDoors.finish(client, player);
		Component name = targetName;
		player.input = previousInput;
		reset();
		playStoppedCue(client, player);
		client.getNarrator().saySystemQueued(name != null
				? Component.translatable("united_minecraft.narrate.autowalk_incomplete", remainingBlocks, name)
				: Component.translatable("united_minecraft.narrate.autowalk_incomplete_unnamed", remainingBlocks));
	}

	/** Recomputes the path to the original target from the player's current position - true (and swaps {@link #currentPath}) only if one was actually found. */
	private static boolean tryRepath(LocalPlayer player) {
		BlockPos target = routeTarget;
		Path fresh = ClientPathfinding.computePath(player, target, MAX_PATH_LENGTH, reachRange,
				UnitedMinecraftConfig.get().autoWalkSwim, UnitedMinecraftConfig.get().autoWalkOpenDoors);
		if (fresh == null || fresh.getNodeCount() == 0) {
			return false;
		}
		currentPath = fresh;
		return true;
	}

	/** Ends the walk after a re-path attempt also failed to make progress - same shape as {@link #finishIncomplete}. */
	private static void finishStuck(Minecraft client, LocalPlayer player) {
		AutoWalkDoors.finish(client, player);
		int remaining = Math.round(currentPath.getDistToTarget());
		Component name = targetName;
		player.input = previousInput;
		reset();
		playStoppedCue(client, player);
		client.getNarrator().saySystemQueued(name != null
				? Component.translatable("united_minecraft.narrate.autowalk_stuck", remaining, name)
				: Component.translatable("united_minecraft.narrate.autowalk_stuck_unnamed", remaining));
	}

	/** Low "stopped short" thud for both {@link #finishIncomplete} and {@link #finishStuck} - deliberately
	 * a dull, low tone rather than a musical one, so it reads as a failure distinct from the bright
	 * ascending {@link SoundEvents#NOTE_BLOCK_CHIME} arrival cue and from every other note-block cue
	 * this mod already uses elsewhere. */
	private static void playStoppedCue(Minecraft client, LocalPlayer player) {
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_BASS.value(),
				SoundSource.MASTER, CueVolume.scale(0.7f), 0.7f, player.getRandom(), player.getX(), player.getY(), player.getZ()));
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
	private static boolean hasClearLineFromEye(Level level, LocalPlayer player, BlockPos target) {
		BlockHitResult hit = level.clip(new ClipContext(
				player.getEyePosition(), Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
	}

	private static boolean hasClearLineToTarget(Level level, LocalPlayer player, BlockPos target) {
		Vec3 from = new Vec3(player.getX(), target.getY() + 0.5, player.getZ());
		BlockHitResult hit = level.clip(new ClipContext(
				from, Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
	}
}
