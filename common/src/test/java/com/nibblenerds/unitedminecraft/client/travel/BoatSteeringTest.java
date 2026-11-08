package com.nibblenerds.unitedminecraft.client.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

class BoatSteeringTest {
	/** The vanilla boat's turning, tick for tick: drag on the turn speed, then the nudge, then the move. */
	private static final class SimBoat {
		double yaw;
		double turnSpeed;
		double lastMove;

		SimBoat(double yaw) {
			this.yaw = yaw;
		}

		void tick(int steer) {
			turnSpeed = turnSpeed * BoatSteering.WATER_DRAG + steer;
			yaw += turnSpeed;
			lastMove = turnSpeed;
		}
	}

	/** Steers a boat toward {@code target} the way BoatController does; returns the ticks until it settled, or -1. */
	private static int steerTo(SimBoat boat, double target) {
		for (int tick = 1; tick <= 400; tick++) {
			int steer = BoatSteering.decide(boat.yaw, boat.lastMove, target);
			if (steer == 0 && BoatSteering.settled(boat.yaw, boat.lastMove, target)) {
				return tick;
			}
			boat.tick(steer);
		}
		return -1;
	}

	@Test
	void turnsToTheTargetFromRest() {
		for (double start = -180; start < 180; start += 7) {
			for (double offset = -180; offset <= 180; offset += 15) {
				SimBoat boat = new SimBoat(start);
				double target = start + offset;
				assertTrue(steerTo(boat, target) > 0, "never settled from " + start + " to " + target);
				double miss = Math.abs(Mth.wrapDegrees(target - boat.yaw));
				assertTrue(miss <= BoatSteering.RESOLUTION_DEGREES / 2.0 + 0.5, "missed " + target + " by " + miss + " from " + start);
			}
		}
	}

	@Test
	void turnsToTheTargetWhileAlreadySwinging() {
		for (double speed = -8; speed <= 8; speed += 2) {
			for (double offset = -120; offset <= 120; offset += 20) {
				SimBoat boat = new SimBoat(30);
				boat.turnSpeed = speed;
				boat.lastMove = speed;
				double target = 30 + offset;
				assertTrue(steerTo(boat, target) > 0, "never settled at speed " + speed + " to " + target);
				assertTrue(Math.abs(Mth.wrapDegrees(target - boat.yaw)) <= 5.5, "missed " + target + " at speed " + speed);
			}
		}
	}

	@Test
	void goesTheShortWayRound() {
		SimBoat boat = new SimBoat(170);
		steerTo(boat, 190);
		assertTrue(boat.yaw > 170 && boat.yaw < 200, "swung " + boat.yaw);
	}

	@Test
	void leavesTheWheelAloneOnTarget() {
		assertEquals(0, BoatSteering.decide(90, 0, 90));
		assertEquals(0, BoatSteering.decide(90, 0, 93));
	}

	@Test
	void turnsTheRightWayFromRest() {
		assertEquals(1, BoatSteering.decide(0, 0, 60));
		assertEquals(-1, BoatSteering.decide(0, 0, -60));
	}

	@Test
	void easesOffBeforeTheTarget() {
		// Already turning fast enough to coast the rest of the way: no more nudging.
		assertEquals(0, BoatSteering.decide(0, 5, 50));
		// Turning too fast for the distance left: nudge the other way to slow it.
		assertEquals(-1, BoatSteering.decide(0, 8, 30));
	}

	@Test
	void doesNotSettleWhileStillSwinging() {
		assertTrue(!BoatSteering.settled(90, 3, 90));
		assertTrue(BoatSteering.settled(90, 0.05, 92));
		assertTrue(!BoatSteering.settled(90, 0.05, 110));
	}
}
