package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec2;

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
	private static final int REACH_RANGE = 2;
	private static final double NODE_ARRIVAL_DISTANCE_SQR = 0.5 * 0.5;

	private static Path currentPath;
	private static ClientInput previousInput;
	private static Component targetName;
	private static Runnable onArrival;

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
		if (ClientKeyBindings.SCANNER_STOP_LOCK.consumeClick()) {
			cancel(client, player);
			return;
		}

		BlockPos nextPos = currentPath.getNextNodePos();
		double dx = nextPos.getX() + 0.5 - player.getX();
		double dz = nextPos.getZ() + 0.5 - player.getZ();

		if (dx * dx + dz * dz < NODE_ARRIVAL_DISTANCE_SQR) {
			currentPath.advance();
			if (currentPath.isDone()) {
				Runnable callback = onArrival;
				finish(client, player, "united_minecraft.narrate.autowalk_arrived");
				if (callback != null) {
					callback.run();
				}
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

	private static void finish(Minecraft client, LocalPlayer player, String messageKey) {
		Component name = targetName;
		player.input = previousInput;
		reset();
		client.getNarrator().saySystemNow(name != null
				? Component.translatable(messageKey, name)
				: Component.translatable(messageKey));
	}

	/** Reports "forward" (and "jump"/"sprint" as needed) as held, exactly like real keyboard input would. */
	private static final class AutoWalkInput extends ClientInput {
		void setWalking(boolean jump, boolean sprint) {
			this.keyPresses = new Input(true, false, false, false, jump, false, sprint);
			this.moveVector = new Vec2(0.0f, 1.0f);
		}
	}
}
