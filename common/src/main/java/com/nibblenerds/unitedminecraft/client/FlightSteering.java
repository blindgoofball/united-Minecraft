package com.nibblenerds.unitedminecraft.client;

/**
 * The aiming maths for flying a ridden happy ghast to a point, free of Minecraft types so it can be
 * tested without the game. A ghast moves along its rider's pitch and turns only slowly toward the
 * rider's yaw, so steering is aiming the view at the point and pushing forward when lined up.
 */
final class FlightSteering {
	/** Closer than this horizontally there is no meaningful heading to turn to. */
	static final double MIN_HEADING_DISTANCE = 0.5;
	// Pushing forward while facing further than this off the point just carries the ghast past it.
	private static final double MAX_PUSH_ERROR_DEGREES = 30.0;

	private FlightSteering() {
	}

	/** Yaw (0 = south, increasing toward west) that faces the horizontal offset. */
	static float yaw(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Pitch (positive looks down) that faces a point {@code dy} above and {@code horizontal} away. */
	static float pitch(double horizontal, double dy) {
		return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
	}

	/** Whether to push forward, given how far the ghast's own yaw is off the heading. */
	static boolean shouldPush(double yawErrorDegrees, double horizontal) {
		return horizontal <= MIN_HEADING_DISTANCE || Math.abs(yawErrorDegrees) <= MAX_PUSH_ERROR_DEGREES;
	}
}
