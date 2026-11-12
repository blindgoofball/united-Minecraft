package com.nibblenerds.unitedminecraft.client.travel;

import com.nibblenerds.unitedminecraft.client.travel.ElytraLandingController;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ElytraLandingRangeTest {
	@Test
	void theHeightNeededIsEnoughToGlideThereAndNotAnyLess() {
		for (double distance = 0; distance <= 500; distance += 25) {
			double needed = ElytraLandingController.heightNeeded(distance);
			assertTrue(ElytraLandingController.inGlideRange(needed + 0.01, distance), "just enough at " + distance);
			assertFalse(ElytraLandingController.inGlideRange(needed - 0.5, distance), "just short at " + distance);
		}
	}

	@Test
	void aFarTargetNeedsMoreHeightThanANearOne() {
		assertTrue(ElytraLandingController.heightNeeded(300) > ElytraLandingController.heightNeeded(60));
	}

	private static final int TOP = 64;

	@Test
	void openGroundHasARunway() {
		assertTrue(ElytraLandingController.hasRunway((x, z) -> TOP, 0, TOP, 0));
	}

	@Test
	void aPatchAgainstAWallHasARunwayOnlyIfTheWallLeavesAWayIn() {
		// A long, tall wall two blocks north of the patch: coming in or sliding out across it is
		// out, but a line along it, a few blocks clear, still works.
		ElytraLandingController.HeightAt wall = (x, z) -> z == -2 ? TOP + 6 : TOP;
		assertTrue(ElytraLandingController.hasRunway(wall, 0, TOP, 0), "along the wall is still open");
	}

	@Test
	void aPatchInACourtyardHasNoRunway() {
		// Walls all round, a few blocks out: nowhere to come in from or slide off to.
		ElytraLandingController.HeightAt courtyard = (x, z) -> Math.max(Math.abs(x), Math.abs(z)) >= 4 ? TOP + 6 : TOP;
		assertFalse(ElytraLandingController.hasRunway(courtyard, 0, TOP, 0));
	}

	@Test
	void aPatchWithAWallJustBehindItAndOpenGroundInFrontHasNoRunwayOnTheBlockedSide() {
		// A long wall right beside the patch, open to the south only: a glide from the south would
		// slide into the wall, and one along it has to pass within a block of it.
		ElytraLandingController.HeightAt pocket = (x, z) -> (z <= -2 || x <= -2 || x >= 2) ? TOP + 6 : TOP;
		assertFalse(ElytraLandingController.hasRunway(pocket, 0, TOP, 0));
	}

	@Test
	void aRoofHasNoRunwayWhereTheSlideWouldGoOverItsEdge() {
		// A small flat roof, ground well below: sliding on from the middle of it runs off the edge
		// whichever way you come in.
		ElytraLandingController.HeightAt roof = (x, z) -> Math.max(Math.abs(x), Math.abs(z)) <= 3 ? TOP : TOP - 6;
		assertFalse(ElytraLandingController.hasRunway(roof, 0, TOP, 0));
	}

	@Test
	void aSpotOnTheRoofIsNotNearerThanTheStepInFrontOfTheDoor() {
		// Directly over the door, four blocks up, against two blocks off to the side at its own height.
		assertTrue(ElytraLandingController.placementCost(0, 4) > ElytraLandingController.placementCost(2, 0));
		assertTrue(ElytraLandingController.placementCost(3, 1) == 3);
	}

	@Test
	void groundThatRisesGentlyFarOutDoesNotCountAsAWall() {
		assertTrue(ElytraLandingController.hasRunway((x, z) -> TOP + Math.min(3, Math.abs(x) / 6), 0, TOP, 0));
	}
}
