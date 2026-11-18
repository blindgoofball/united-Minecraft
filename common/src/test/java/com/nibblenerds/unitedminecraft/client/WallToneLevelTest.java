package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WallToneLevelTest {
	@Test
	void normalIsLoudAgainstAWallAndSilentWhenOpen() {
		assertEquals(0.0f, WallToneController.level(Double.POSITIVE_INFINITY, 8, false), 1e-6);
		assertEquals(0.0f, WallToneController.level(8, 8, false), 1e-6);
		assertEquals(1.0f, WallToneController.level(0, 8, false), 1e-6);
	}

	@Test
	void invertedIsSilentAgainstAWallAndLoudWhenOpen() {
		assertEquals(1.0f, WallToneController.level(Double.POSITIVE_INFINITY, 8, true), 1e-6);
		assertEquals(1.0f, WallToneController.level(8, 8, true), 1e-6);
		assertEquals(0.0f, WallToneController.level(0, 8, true), 1e-6);
	}

	@Test
	void invertedGrowsAsTheWallRecedes() {
		assertTrue(WallToneController.level(6, 8, true) > WallToneController.level(2, 8, true));
	}
}
