package com.nibblenerds.unitedminecraft.client;

/**
 * Watches for "no real progress toward the current waypoint for a while, despite actively
 * walking" - the practical symptom every route-following mode needs to notice so it stops
 * pushing against an obstruction instead of holding forward forever.
 *
 * <p>Shared by {@link AutoWalkController} (a mob shoving the player off the path, a block placed
 * mid-walk, terrain changing), {@link TrailController} (a straight-line leg clipping something
 * the original walk curved around - see its class doc), and {@link WaterExitController} (a
 * current pushing the player back, or the route being invalidated mid-swim). Distance is passed
 * in rather than computed here, since each mode measures toward its own kind of waypoint:
 * horizontal-only for ground walking, full 3D while swimming.
 */
final class StuckDetector {
	// Long enough to ride out normal micro-stutters (bumping a slab, a mob briefly in the way)
	// without calling a still-progressing walk stuck.
	private static final int STUCK_TICKS_THRESHOLD = 40;
	// How much closer counts as real progress - small enough to notice a slow crawl forward,
	// large enough that floating-point jitter while genuinely stuck doesn't read as movement.
	private static final double STUCK_PROGRESS_EPSILON = 0.05;

	private double bestDistance = Double.MAX_VALUE;
	private int stuckTicks;

	/** Called on arrival at a waypoint (and on starting a route) so the next leg measures from scratch. */
	void reset() {
		bestDistance = Double.MAX_VALUE;
		stuckTicks = 0;
	}

	/**
	 * Records this tick's distance to the current waypoint and reports whether the route has
	 * been stuck long enough to give up on. Any new closest approach resets the count, so only
	 * a sustained lack of progress trips it.
	 */
	boolean isStuck(double distanceToWaypoint) {
		if (distanceToWaypoint < bestDistance - STUCK_PROGRESS_EPSILON) {
			bestDistance = distanceToWaypoint;
			stuckTicks = 0;
			return false;
		}
		return ++stuckTicks > STUCK_TICKS_THRESHOLD;
	}
}
