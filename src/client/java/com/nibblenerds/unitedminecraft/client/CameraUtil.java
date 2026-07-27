package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Shared "snap the player's look direction at a point" math, used by build mode and the scanner. */
public final class CameraUtil {
	// Matches AbstractArrow's own per-tick physics (getDefaultGravity/getAirDrag) - position
	// advances by the current velocity, then velocity is scaled by drag and gravity is
	// subtracted from its vertical component, in that order, every tick.
	private static final double PROJECTILE_GRAVITY = 0.05;
	private static final double PROJECTILE_DRAG = 0.99;
	private static final int MAX_SIMULATION_TICKS = 140;
	private static final int BISECTION_STEPS = 40;

	// Ordered every 45 degrees starting at compass bearing 0 (north), matching the same
	// north=0 convention AccessibilityTickHandler's bearing narration uses.
	private static final String[] COMPASS_DIRECTION_KEYS = {
			"united_minecraft.direction.north",
			"united_minecraft.direction.northeast",
			"united_minecraft.direction.east",
			"united_minecraft.direction.southeast",
			"united_minecraft.direction.south",
			"united_minecraft.direction.southwest",
			"united_minecraft.direction.west",
			"united_minecraft.direction.northwest",
	};

	private CameraUtil() {
	}

	public static void aimAt(LocalPlayer player, Vec3 target) {
		Vec3 eye = player.getEyePosition();
		double dx = target.x() - eye.x();
		double dy = target.y() - eye.y();
		double dz = target.z() - eye.z();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		// Inverse of Entity.calculateViewVector: x = -sin(yaw)*cos(pitch), y = -sin(pitch), z = cos(yaw)*cos(pitch).
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) Math.toDegrees(Math.atan2(-dy, horizontalDistance));

		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setOldRot();
		player.setYHeadRot(yaw);
	}

	/**
	 * Like {@link #aimAt}, but solves for the launch pitch a real arrow (at {@code projectileSpeed}
	 * blocks/tick, matching {@code BowItem}'s own draw-power formula) needs to actually arc onto
	 * the target, instead of just pointing straight at it - which under-shoots anything beyond
	 * point-blank range, since gravity pulls the real arrow down along the way.
	 */
	public static void aimBallisticAt(LocalPlayer player, Vec3 target, double projectileSpeed) {
		Vec3 eye = player.getEyePosition();
		double dx = target.x() - eye.x();
		double dy = target.y() - eye.y();
		double dz = target.z() - eye.z();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = horizontalDistance > 1.0e-3
				? solveBallisticPitch(horizontalDistance, dy, projectileSpeed)
				: (float) Math.toDegrees(Math.atan2(-dy, horizontalDistance));

		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setOldRot();
		player.setYHeadRot(yaw);
	}

	/**
	 * Binary search for the launch pitch (degrees, vanilla convention: negative = up) whose
	 * simulated arc passes through {@code heightDiff} at {@code horizontalDistance}. A steeper
	 * (more negative/upward) pitch always arrives higher at that distance than a flatter one, so
	 * the simulated height is monotonic in pitch across a sane range, which is all bisection needs.
	 */
	private static float solveBallisticPitch(double horizontalDistance, double heightDiff, double speed) {
		double low = -80.0;
		double high = 80.0;
		for (int i = 0; i < BISECTION_STEPS; i++) {
			double mid = (low + high) / 2.0;
			if (heightAtDistance(mid, speed, horizontalDistance) > heightDiff) {
				low = mid;
			} else {
				high = mid;
			}
		}
		return (float) ((low + high) / 2.0);
	}

	/**
	 * Simulates a projectile launched at {@code pitchDegrees}/{@code speed} and returns its
	 * height once it's first travelled {@code horizontalDistance}, interpolated between the two
	 * surrounding ticks - or {@link Double#NEGATIVE_INFINITY} if it never covers that much
	 * ground (an overly steep angle, or too little draw speed).
	 */
	private static double heightAtDistance(double pitchDegrees, double speed, double horizontalDistance) {
		double pitch = Math.toRadians(pitchDegrees);
		double vy = -Math.sin(pitch) * speed;
		double vxz = Math.cos(pitch) * speed;
		double x = 0.0;
		double y = 0.0;
		for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
			double nextX = x + vxz;
			double nextY = y + vy;
			if (nextX >= horizontalDistance) {
				double fraction = nextX > x ? (horizontalDistance - x) / (nextX - x) : 1.0;
				return y + (nextY - y) * fraction;
			}
			x = nextX;
			y = nextY;
			vxz *= PROJECTILE_DRAG;
			vy = vy * PROJECTILE_DRAG - PROJECTILE_GRAVITY;
		}
		return Double.NEGATIVE_INFINITY;
	}

	/** The compass direction (north/southeast/etc.) of {@code to} as seen from {@code from}, ignoring height. */
	public static Component compassDirectionTo(Vec3 from, Vec3 to) {
		double dx = to.x() - from.x();
		double dz = to.z() - from.z();
		// Same yaw formula aimAt uses, then the same yaw-to-compass-bearing shift
		// AccessibilityTickHandler.narrateBearing uses (Minecraft yaw 0 = south).
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		int bearing = Math.floorMod(Math.round(yaw) + 180, 360);
		int octant = Math.floorMod(Math.round(bearing / 45.0f), 8);
		return Component.translatable(COMPASS_DIRECTION_KEYS[octant]);
	}
}
