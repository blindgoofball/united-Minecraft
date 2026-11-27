package com.nibblenerds.unitedminecraft.client.autowalk;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * Drives a ridden horse's jump for {@link AutoWalkController}. A horse jumps the way a rider does by
 * hand: hold the jump key to charge (full power after about ten ticks) and let go to launch, which
 * throws the horse upward by its jump strength and a little forward. Anything up to a block high it
 * simply steps onto, so only a rise taller than that needs this.
 */
final class MountJumper {
	// A horse climbs a block-high step on its own; only a rise beyond this is a jump.
	private static final double STEP_UP = 1.05;
	// Start charging once this close to the waypoint (horizontally): a charge takes about ten ticks.
	private static final double START_DISTANCE = 2.6;
	// Stop walking and wait out the charge this close to the waypoint - a wide horse is touching the
	// wall by about this distance from the centre of the cell above it.
	private static final double HOLD_DISTANCE = 1.6;
	// Past full charge the power fades again, so let go regardless after this many ticks.
	private static final int MAX_CHARGE_TICKS = 14;
	private static final float FULL_CHARGE = 0.95f;

	private static final int LAUNCH_WAIT_TICKS = 4;

	private static int chargeTicks;
	private static int launchWait;

	private MountJumper() {
	}

	static void reset() {
		chargeTicks = 0;
		launchWait = 0;
	}

	/** Whether the next waypoint is higher than the mount can simply step up to. */
	static boolean needsJump(double mountFeetY, int waypointY) {
		return waypointY - mountFeetY > STEP_UP;
	}

	/** Sets this tick's input for a ridden horse heading for a waypoint {@code distance} blocks away. */
	static void drive(LocalPlayer player, AbstractHorse horse, boolean jumpNeeded, double distance, RouteInput input) {
		if (launchWait > 0) {
			// The launch happens a tick or two after the key is let go. Pressing again in that gap would
			// start a second charge that fires on landing.
			launchWait--;
			input.setMounted(true, false);
			return;
		}
		if (!horse.onGround()) {
			// Airborne - a jump in flight, or a fall. Keep pushing forward, and do not hold the key.
			chargeTicks = 0;
			input.setMounted(true, false);
			return;
		}
		if (!jumpNeeded || player.jumpableVehicle() == null || horse.getJumpCooldown() > 0
				|| (chargeTicks == 0 && distance > START_DISTANCE)) {
			chargeTicks = 0;
			input.setMounted(true, false);
			return;
		}
		boolean charged = player.getJumpRidingScale() >= FULL_CHARGE;
		// Full power lasts a single tick and fades after it, so let go the moment it is reached.
		if (chargeTicks > 0 && (charged || chargeTicks >= MAX_CHARGE_TICKS)) {
			// Letting go launches it; forward keeps it moving onto the ledge.
			chargeTicks = 0;
			launchWait = LAUNCH_WAIT_TICKS;
			input.setMounted(true, false);
			return;
		}
		chargeTicks++;
		input.setMounted(distance > HOLD_DISTANCE, true);
	}
}
