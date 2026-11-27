package com.nibblenerds.unitedminecraft.client;

/**
 * How {@link MapBeaconController}'s chime changes as the player nears what it points at: pitch and
 * repeat rate both rise, Geiger-counter style, from {@link #NEAR_DISTANCE_BLOCKS} out down to the
 * arrival radius, and a slow idle pulse takes over once arrived. The arrival radius differs by what
 * is being pointed at - a map's target is only ever approximate, a compass's is exact - so every
 * function here takes it. Kept apart from the controller so it can be tested without the game.
 */
final class BeaconTiming {
	/** At or beyond this far from the target, the chime is at its slowest and lowest. */
	static final double NEAR_DISTANCE_BLOCKS = 50.0;
	static final int INTERVAL_FAR_TICKS = 40;
	static final int INTERVAL_NEAR_TICKS = 10;
	/** Once arrived, a slow idle pulse rather than silence - the final on-foot search still benefits from a nudge. */
	static final int INTERVAL_ARRIVED_TICKS = 100;
	static final float PITCH_FAR = 0.8f;
	static final float PITCH_NEAR = 1.8f;

	private BeaconTiming() {
	}

	/** 0 at/beyond {@link #NEAR_DISTANCE_BLOCKS}, 1 at/inside {@code arrivalRadius}, linear between. */
	static double proximityFactor(double distance, double arrivalRadius) {
		if (distance <= arrivalRadius) {
			return 1.0;
		}
		if (distance >= NEAR_DISTANCE_BLOCKS) {
			return 0.0;
		}
		return (NEAR_DISTANCE_BLOCKS - distance) / (NEAR_DISTANCE_BLOCKS - arrivalRadius);
	}

	static float pitchFor(double distance, double arrivalRadius) {
		return (float) (PITCH_FAR + (PITCH_NEAR - PITCH_FAR) * proximityFactor(distance, arrivalRadius));
	}

	static int chimeIntervalTicksFor(double distance, double arrivalRadius, boolean arrived) {
		if (arrived) {
			return INTERVAL_ARRIVED_TICKS;
		}
		return (int) Math.round(INTERVAL_FAR_TICKS
				+ (INTERVAL_NEAR_TICKS - INTERVAL_FAR_TICKS) * proximityFactor(distance, arrivalRadius));
	}
}
