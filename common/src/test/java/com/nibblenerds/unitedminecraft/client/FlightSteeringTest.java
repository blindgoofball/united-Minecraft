package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlightSteeringTest {
	@Test
	void yawFacesSouthWestEastAndNorth() {
		assertEquals(0.0f, FlightSteering.yaw(0, 5), 1e-4);
		assertEquals(90.0f, FlightSteering.yaw(-5, 0), 1e-4);
		assertEquals(-90.0f, FlightSteering.yaw(5, 0), 1e-4);
		assertEquals(180.0f, Math.abs(FlightSteering.yaw(0, -5)), 1e-4);
	}

	@Test
	void pitchLooksDownForAPointBelowAndUpForOneAbove() {
		assertEquals(0.0f, FlightSteering.pitch(10, 0), 1e-4);
		assertEquals(45.0f, FlightSteering.pitch(10, -10), 1e-4);
		assertEquals(-45.0f, FlightSteering.pitch(10, 10), 1e-4);
	}

	@Test
	void pitchLooksStraightUpOrDownOverThePoint() {
		assertEquals(-90.0f, FlightSteering.pitch(0, 4), 1e-4);
		assertEquals(90.0f, FlightSteering.pitch(0, -4), 1e-4);
	}

	@Test
	void pushesOnlyOnceRoughlyFacingThePoint() {
		assertTrue(FlightSteering.shouldPush(10, 20));
		assertTrue(FlightSteering.shouldPush(-30, 20));
		assertFalse(FlightSteering.shouldPush(60, 20));
		assertFalse(FlightSteering.shouldPush(-170, 20));
	}

	@Test
	void pushesWhenDirectlyAboveOrBelowWhateverItsHeading() {
		assertTrue(FlightSteering.shouldPush(170, 0.1));
	}
}
