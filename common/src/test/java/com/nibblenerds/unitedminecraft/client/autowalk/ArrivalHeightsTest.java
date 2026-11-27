package com.nibblenerds.unitedminecraft.client.autowalk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link ArrivalHeights} - Auto-Walk not counting a drop or a floor above the target as arrived. */
class ArrivalHeightsTest {
	@Test
	void standingOnALedgeAboveADropWaypointHasNotReachedIt() {
		assertTrue(ArrivalHeights.stillAboveWaypoint(70.0, 66));
	}

	@Test
	void havingFallenToWithinABlockOfTheWaypointHasReachedIt() {
		assertFalse(ArrivalHeights.stillAboveWaypoint(66.9, 66));
		assertFalse(ArrivalHeights.stillAboveWaypoint(66.0, 66));
	}

	@Test
	void aWaypointLevelWithOrAboveThePlayerIsNeverStillAbove() {
		assertFalse(ArrivalHeights.stillAboveWaypoint(64.0, 64));
		assertFalse(ArrivalHeights.stillAboveWaypoint(64.0, 65));
		assertFalse(ArrivalHeights.stillAboveWaypoint(64.0, 70));
	}

	@Test
	void aChestOnTheFloorBelowIsNotLevelWithSomeoneStandingUpstairs() {
		assertFalse(ArrivalHeights.levelWithTarget(68.0, 64));
	}

	@Test
	void standingNextToATargetAtItsOwnHeightIsLevel() {
		assertTrue(ArrivalHeights.levelWithTarget(64.0, 64));
	}

	@Test
	void aTargetUpToACoupleOfBlocksAwayInHeightStillCounts() {
		assertTrue(ArrivalHeights.levelWithTarget(64.0, 65));
		assertTrue(ArrivalHeights.levelWithTarget(64.0, 66));
		assertTrue(ArrivalHeights.levelWithTarget(66.0, 64));
		assertFalse(ArrivalHeights.levelWithTarget(64.0, 67));
	}
}
