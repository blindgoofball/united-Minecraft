package com.nibblenerds.unitedminecraft.client.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatBeaconTest {
	private static final double EPS = 1.0e-6;

	@Test
	void yawErrorIsZeroWhenFacingTheTarget() {
		// Yaw 0 faces south (+Z); a target due south is dead ahead.
		assertEquals(0.0, CombatBeacon.yawErrorDegrees(0.0, 10.0, 0.0f), EPS);
		// Yaw 90 faces west (-X).
		assertEquals(0.0, CombatBeacon.yawErrorDegrees(-10.0, 0.0, 90.0f), EPS);
	}

	@Test
	void yawErrorTakesTheShortWayRound() {
		// Facing south, target due west: yaw 90, so 90 degrees one way.
		assertEquals(90.0, CombatBeacon.yawErrorDegrees(-10.0, 0.0, 0.0f), EPS);
		// Facing 170 and the target is at -170 (= 190): 20 degrees, not 340.
		double targetYaw = Math.toRadians(-170.0);
		double error = CombatBeacon.yawErrorDegrees(-Math.sin(targetYaw), Math.cos(targetYaw), 170.0f);
		assertEquals(20.0, error, 1.0e-6);
		// Directly behind is the largest error there is.
		assertEquals(180.0, Math.abs(CombatBeacon.yawErrorDegrees(0.0, -10.0, 0.0f)), EPS);
	}

	@Test
	void targetStraightAboveOrBelowCountsAsAhead() {
		assertEquals(0.0, CombatBeacon.yawErrorDegrees(0.0, 0.0, 123.0f), EPS);
	}

	@Test
	void pitchErrorSaysWhichWayToLook() {
		// Level target, looking level.
		assertEquals(0.0, CombatBeacon.pitchErrorDegrees(0.0, 10.0, 0.0f), EPS);
		// Target above while looking level: negative means look up.
		assertTrue(CombatBeacon.pitchErrorDegrees(5.0, 10.0, 0.0f) < 0.0);
		// Target below: positive means look down.
		assertTrue(CombatBeacon.pitchErrorDegrees(-5.0, 10.0, 0.0f) > 0.0);
		// Already looking down at it.
		assertEquals(0.0, CombatBeacon.pitchErrorDegrees(-10.0, 10.0, 45.0f), 1.0e-4);
	}

	@Test
	void pingsSpeedUpAsTheAimCloses() {
		int away = CombatBeacon.pingIntervalTicks(180.0);
		int side = CombatBeacon.pingIntervalTicks(90.0);
		int near = CombatBeacon.pingIntervalTicks(10.0);
		int on = CombatBeacon.pingIntervalTicks(0.0);
		assertTrue(away > side && side > near && near >= on);
		assertEquals(2, on);
		assertEquals(20, away);
		// Out-of-range input is clamped rather than extrapolated.
		assertEquals(20, CombatBeacon.pingIntervalTicks(400.0));
	}

	@Test
	void pitchFallsToLookUpAndRisesToLookDown() {
		assertEquals(1.0f, CombatBeacon.pingPitch(0.0), 1.0e-6f);
		// Negative error means look up, which sounds lower; positive means look down, which sounds higher.
		assertTrue(CombatBeacon.pingPitch(-30.0) < 1.0f);
		assertTrue(CombatBeacon.pingPitch(30.0) > 1.0f);
		assertEquals(0.5f, CombatBeacon.pingPitch(-60.0), 1.0e-6f);
		assertEquals(2.0f, CombatBeacon.pingPitch(60.0), 1.0e-6f);
		// Stays within what the sound engine allows however far off.
		assertEquals(0.5f, CombatBeacon.pingPitch(-300.0), 1.0e-6f);
		assertEquals(2.0f, CombatBeacon.pingPitch(300.0), 1.0e-6f);
	}

	@Test
	void toleranceIsWiderForBiggerOrCloserTargetsWithinLimits() {
		double far = CombatBeacon.toleranceDegrees(0.3, 30.0);
		double close = CombatBeacon.toleranceDegrees(0.3, 2.0);
		assertTrue(close > far);
		assertEquals(3.0, far, EPS);
		assertEquals(12.0, CombatBeacon.toleranceDegrees(5.0, 1.0), EPS);
	}

	@Test
	void lockHasHysteresis() {
		// Just outside the tolerance: not locked when approaching...
		assertFalse(CombatBeacon.isLocked(false, 5.0, 0.0, 4.0, 4.0));
		// ...but a lock already held survives the same error.
		assertTrue(CombatBeacon.isLocked(true, 5.0, 0.0, 4.0, 4.0));
		// And is lost once well past it.
		assertFalse(CombatBeacon.isLocked(true, 7.0, 0.0, 4.0, 4.0));
		// Both axes must be on target.
		assertFalse(CombatBeacon.isLocked(false, 1.0, 10.0, 4.0, 4.0));
		assertTrue(CombatBeacon.isLocked(false, 1.0, 1.0, 4.0, 4.0));
	}
}
