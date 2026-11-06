package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MountStatsTest {
	private static final double GRAVITY = 0.08;

	@Test
	void aWalkingPlayersSpeedIsTheKnownWalkingPace() {
		assertEquals(4.317, MountStats.speedBlocksPerSecond(0.1), 1e-9);
	}

	@Test
	void theFastestHorseCoversAboutFourteenAndAHalfBlocksASecond() {
		assertEquals(14.57, MountStats.speedBlocksPerSecond(0.3375), 0.01);
	}

	@Test
	void aPlayersJumpIsTheKnownHeight() {
		// The one jump whose height is established for certain: a player's launch speed of 0.42 reaches
		// 1.2522 blocks. It runs the same move, then gravity, then drag order a horse's jump does.
		assertEquals(1.2522, MountStats.jumpHeight(0.42, GRAVITY), 0.0001);
	}

	@Test
	void theBestHorseJumpClearsFiveBlocksButNotSix() {
		double height = MountStats.jumpHeight(1.0, GRAVITY);
		assertTrue(height > 5.0 && height < 6.0, "got " + height);
	}

	@Test
	void theWorstHorseJumpClearsOneBlockButNotTwo() {
		double height = MountStats.jumpHeight(0.4, GRAVITY);
		assertTrue(height > 1.0 && height < 2.0, "got " + height);
	}

	@Test
	void aStrongerJumpAlwaysGoesHigher() {
		double previous = 0.0;
		for (double strength = 0.4; strength <= 1.0; strength += 0.05) {
			double height = MountStats.jumpHeight(strength, GRAVITY);
			assertTrue(height > previous, "no higher at strength " + strength);
			previous = height;
		}
	}

	@Test
	void theBestHorseLandsOnAFiveBlockRiseAndTheWorstOnlyStepsOne() {
		assertEquals(5, MountStats.maxRise(1.0, GRAVITY));
		assertEquals(1, MountStats.maxRise(0.4, GRAVITY));
	}

	@Test
	void aMountNeverPlansForLessThanAOneBlockStep() {
		assertEquals(1, MountStats.maxRise(0.1, GRAVITY));
	}

	@Test
	void lowerGravityJumpsHigher() {
		assertTrue(MountStats.jumpHeight(0.7, 0.04) > MountStats.jumpHeight(0.7, GRAVITY));
	}
}
