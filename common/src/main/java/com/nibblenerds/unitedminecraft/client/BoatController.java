package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/**
 * Steering a boat without seeing it, gated by {@link UnitedMinecraftConfig#boatAssistEnabled}: while
 * the player drives a boat, the camera is kept on the way the boat faces, and the keys that would turn
 * the camera turn the boat instead.
 *
 * <p>The arrow keys and the snap-turn keys set a heading, and {@link BoatSteering} holds left or right
 * until the boat has swung round to it (through {@link BoatInput}, so the boat sees ordinary steering
 * keys). Snap-turning to the west is one press, rather than a guess at how long to hold A. The
 * player's own A and D still work, and take over from a heading in progress.
 *
 * <p>Yields to anything else that owns the camera - a Scanner lock-on or Combat Mode, so the player
 * can still fight from the boat, and the auto-navigate modes - handing steering back to the keys.
 */
public final class BoatController {
	/** The most a held arrow key may run ahead of the boat, so the boat is not left spinning long after the key is let go. */
	private static final double MAX_HELD_LEAD_DEGREES = 45.0;

	private static BoatInput input;
	private static boolean hasTarget;
	private static double target;
	private static float lastYaw;
	private static boolean haveLastYaw;

	private BoatController() {
	}

	/** Whether the player is driving a boat with the camera on its heading - so the turn keys steer it. */
	static boolean isActive() {
		return input != null;
	}

	public static void reset() {
		input = null;
		hasTarget = false;
		haveLastYaw = false;
	}

	/** A screen opened: stop holding a steering key for a player who can no longer see what it does. */
	static void pause() {
		hasTarget = false;
		if (input != null) {
			input.setSteer(0);
		}
	}

	/** The heading the boat is turning to, or the one it is facing if it is not turning. */
	static double heading(LocalPlayer player) {
		if (hasTarget) {
			return target;
		}
		return player.getVehicle() instanceof AbstractBoat boat ? boat.getYRot() : player.getYRot();
	}

	/** The arrow keys turned the camera by {@code degrees} this tick: turn the boat's heading that far. */
	static void turnBy(LocalPlayer player, double degrees) {
		if (degrees == 0.0 || !(player.getVehicle() instanceof AbstractBoat boat)) {
			return;
		}
		double base = hasTarget ? target : boat.getYRot();
		// A snap turn may already have set a heading further off than that; holding a key never pulls it back.
		double limit = Math.max(MAX_HELD_LEAD_DEGREES, Math.abs(base - boat.getYRot()));
		setTarget(Mth.clamp(base + degrees, boat.getYRot() - limit, boat.getYRot() + limit));
	}

	/** A snap-turn asked for this exact heading. */
	static void turnTo(LocalPlayer player, double yaw) {
		if (player.getVehicle() instanceof AbstractBoat boat) {
			setTarget(yaw);
		}
	}

	private static void setTarget(double yaw) {
		hasTarget = true;
		target = yaw;
	}

	/**
	 * Keeps the camera on the boat and works out which way to hold the steering for the next tick.
	 * Runs after the turn keys have been read.
	 *
	 * @param otherModeOwnsRotation something else is steering the camera this tick
	 */
	public static void tick(Minecraft client, LocalPlayer player, boolean otherModeOwnsRotation) {
		boolean driving = UnitedMinecraftConfig.get().boatAssistEnabled && !player.isSpectator()
				&& player.getVehicle() instanceof AbstractBoat boat && boat.getControllingPassenger() == player;
		if (!driving) {
			release(player);
			return;
		}
		AbstractBoat boat = (AbstractBoat) player.getVehicle();

		float yaw = boat.getYRot();
		double turnSpeed = haveLastYaw ? Mth.wrapDegrees(yaw - lastYaw) : 0.0;
		lastYaw = yaw;
		haveLastYaw = true;

		if (otherModeOwnsRotation) {
			// Not ours to steer or to aim: the player's own keys drive the boat while another mode drives the camera.
			hasTarget = false;
			if (input != null) {
				input.setSteer(0);
			}
			return;
		}
		if (input == null) {
			attach(player);
		}
		if (input == null) {
			return;
		}

		if (input.playerIsSteering()) {
			hasTarget = false;
		}
		int steer = 0;
		if (hasTarget) {
			steer = BoatSteering.decide(yaw, turnSpeed, target);
			if (steer == 0 && BoatSteering.settled(yaw, turnSpeed, target)) {
				hasTarget = false;
			}
		}
		input.setSteer(steer);

		// The camera stays on the boat's heading, by the shortest way round so the view does not spin a full turn.
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()));
		player.setYHeadRot(player.getYRot());
	}

	private static void attach(LocalPlayer player) {
		if (player.input instanceof BoatInput existing) {
			input = existing;
		} else {
			input = new BoatInput(player.input);
			player.input = input;
		}
		hasTarget = false;
		haveLastYaw = false;
	}

	/** Hands the player's own input back, once they are out of the boat or the assist is off. */
	private static void release(LocalPlayer player) {
		if (player.input instanceof BoatInput wrapper) {
			player.input = wrapper.real();
		}
		reset();
	}
}
