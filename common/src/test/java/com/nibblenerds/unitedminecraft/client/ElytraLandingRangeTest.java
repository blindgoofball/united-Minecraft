package com.nibblenerds.unitedminecraft.client;

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
}
