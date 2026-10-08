package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

class AutoCrosshairNarrationTest {
	private static final BlockPos A = new BlockPos(0, 0, 0);
	private static final BlockPos B = new BlockPos(1, 0, 0);

	@Test
	void aDifferentBlockTypeAlwaysNarrates() {
		assertTrue(AutoCrosshairNarrationController.shouldNarrate(false, A, B, false));
		assertTrue(AutoCrosshairNarrationController.shouldNarrate(false, A, B, true));
	}

	@Test
	void anotherBlockOfTheSameTypeIsSilentByDefault() {
		assertFalse(AutoCrosshairNarrationController.shouldNarrate(true, B, A, false));
	}

	@Test
	void anotherBlockOfTheSameTypeNarratesWhenRepeatIsOn() {
		assertTrue(AutoCrosshairNarrationController.shouldNarrate(true, B, A, true));
	}

	@Test
	void theSameBlockNeverRepeats() {
		assertFalse(AutoCrosshairNarrationController.shouldNarrate(true, A, A, true));
	}
}
