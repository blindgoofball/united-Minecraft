package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PowderSnowWarningTest {
	@Test
	void powderSnowAtBodyHeightIsAlwaysWalkedInto() {
		assertTrue(PowderSnowWarningTest.enters(true, false, false, false, false));
		assertTrue(PowderSnowWarningTest.enters(true, false, false, false, true));
		assertTrue(PowderSnowWarningTest.enters(false, true, false, true, true));
	}

	@Test
	void powderSnowGroundSinksYouWithoutLeatherBoots() {
		assertTrue(PowderSnowWarningTest.enters(false, false, true, true, false));
	}

	@Test
	void leatherBootsWalkAcrossTheTopOfIt() {
		assertFalse(PowderSnowWarningTest.enters(false, false, true, true, true));
	}

	@Test
	void ordinaryGroundIsFine() {
		assertFalse(PowderSnowWarningTest.enters(false, false, false, true, false));
	}

	@Test
	void powderSnowUnderASolidBlockIsNotAGroundHazard() {
		// The feet cell is solid here, so nothing is being stepped onto.
		assertFalse(PowderSnowWarningTest.enters(false, false, true, false, false));
	}

	private static boolean enters(boolean feet, boolean head, boolean ground, boolean feetEmpty, boolean boots) {
		return PowderSnowWarningController.entersPowderSnow(feet, head, ground, feetEmpty, boots);
	}
}
