package com.nibblenerds.unitedminecraft.client.autowalk;

/**
 * The height rules for Auto-Walk's arrival checks. {@link AutoWalkController} used to look only at
 * the horizontal distance to a waypoint, so a drop of a few blocks - a waypoint in the same column as
 * the ledge the player is standing on - counted as reached while they were still on the ledge, and a
 * walk could end, and announce its arrival, on the floor above its target.
 */
final class ArrivalHeights {
	/**
	 * How far above a waypoint the player's feet may be and still count as having reached it. A drop
	 * has to actually be made, down to within a block of the waypoint; stepping up needs nothing, since
	 * being in the waypoint's column at all means the player is already on top of it.
	 */
	static final double MAX_ABOVE_WAYPOINT = 1.0;

	/**
	 * How far apart in height the player's feet and the target may be at the end of a walk. The path
	 * finishes next to the target, within a block of it either way, so anything much more than that is
	 * standing on a different floor - whatever lies between them horizontally.
	 */
	static final double MAX_FROM_TARGET = 2.0;

	private ArrivalHeights() {
	}

	/** Whether the player is still too far above a waypoint at {@code waypointY} to have reached it. */
	static boolean stillAboveWaypoint(double playerY, int waypointY) {
		return playerY - waypointY > MAX_ABOVE_WAYPOINT;
	}

	/** Whether the player's height is close enough to a target at {@code targetY} to have arrived at it. */
	static boolean levelWithTarget(double playerY, int targetY) {
		return Math.abs(playerY - targetY) <= MAX_FROM_TARGET;
	}
}
