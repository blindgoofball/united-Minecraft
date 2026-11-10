package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link BeaconTiming} - how the held map and compass chime speeds up and rises in pitch as you close in. */
class BeaconTimingTest {
	private static final double MAP_RADIUS = 16.0;
	private static final double COMPASS_RADIUS = 3.0;

	@Test
	void farAwayIsSlowAndLow() {
		assertEquals(0.0, BeaconTiming.proximityFactor(BeaconTiming.NEAR_DISTANCE_BLOCKS, MAP_RADIUS));
		assertEquals(0.0, BeaconTiming.proximityFactor(500.0, MAP_RADIUS));
		assertEquals(BeaconTiming.PITCH_FAR, BeaconTiming.pitchFor(500.0, MAP_RADIUS));
		assertEquals(BeaconTiming.INTERVAL_FAR_TICKS, BeaconTiming.chimeIntervalTicksFor(500.0, MAP_RADIUS, false));
	}

	@Test
	void insideTheArrivalRadiusIsFastAndHigh() {
		assertEquals(1.0, BeaconTiming.proximityFactor(MAP_RADIUS, MAP_RADIUS));
		assertEquals(1.0, BeaconTiming.proximityFactor(0.0, MAP_RADIUS));
		assertEquals(BeaconTiming.PITCH_NEAR, BeaconTiming.pitchFor(5.0, MAP_RADIUS));
		assertEquals(BeaconTiming.INTERVAL_NEAR_TICKS, BeaconTiming.chimeIntervalTicksFor(5.0, MAP_RADIUS, false));
	}

	@Test
	void inBetweenIsInBetween() {
		double halfway = (BeaconTiming.NEAR_DISTANCE_BLOCKS + MAP_RADIUS) / 2;
		assertEquals(0.5, BeaconTiming.proximityFactor(halfway, MAP_RADIUS), 1.0e-9);
		assertEquals(25, BeaconTiming.chimeIntervalTicksFor(halfway, MAP_RADIUS, false));
	}

	@Test
	void closerAlwaysSoundsFasterAndHigher() {
		assertTrue(BeaconTiming.pitchFor(30.0, MAP_RADIUS) > BeaconTiming.pitchFor(40.0, MAP_RADIUS));
		assertTrue(BeaconTiming.chimeIntervalTicksFor(30.0, MAP_RADIUS, false) < BeaconTiming.chimeIntervalTicksFor(40.0, MAP_RADIUS, false));
	}

	@Test
	void arrivedIsAnIdlePulseWhateverTheDistance() {
		assertEquals(BeaconTiming.INTERVAL_ARRIVED_TICKS, BeaconTiming.chimeIntervalTicksFor(2.0, MAP_RADIUS, true));
		assertEquals(BeaconTiming.INTERVAL_ARRIVED_TICKS, BeaconTiming.chimeIntervalTicksFor(30.0, MAP_RADIUS, true));
	}

	@Test
	void aCompassOnlyReachesFullPitchMuchCloserThanAMap() {
		// 10 blocks out is "arrived" for a map's rough target, but still some way off a compass's exact one.
		assertEquals(1.0, BeaconTiming.proximityFactor(10.0, MAP_RADIUS));
		assertTrue(BeaconTiming.proximityFactor(10.0, COMPASS_RADIUS) < 1.0);
		assertEquals(1.0, BeaconTiming.proximityFactor(COMPASS_RADIUS, COMPASS_RADIUS));
	}
}
