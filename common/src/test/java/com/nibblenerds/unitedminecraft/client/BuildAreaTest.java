package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class BuildAreaTest {
	@Test
	void bothCornersAreInsideInEitherOrder() {
		BuildArea area = new BuildArea(new BlockPos(10, 64, -5), new BlockPos(2, 70, 4));
		assertTrue(area.contains(new BlockPos(10, 64, -5), true));
		assertTrue(area.contains(new BlockPos(2, 70, 4), true));
		assertTrue(area.contains(new BlockPos(6, 67, 0), true));
	}

	@Test
	void outsideOnEitherHorizontalAxisIsRejected() {
		BuildArea area = new BuildArea(new BlockPos(0, 64, 0), new BlockPos(5, 64, 5));
		assertFalse(area.contains(new BlockPos(6, 64, 3), false));
		assertFalse(area.contains(new BlockPos(-1, 64, 3), false));
		assertFalse(area.contains(new BlockPos(3, 64, 6), false));
		assertFalse(area.contains(new BlockPos(3, 64, -1), false));
	}

	@Test
	void heightOnlyLimitsWhenLocked() {
		BuildArea area = new BuildArea(new BlockPos(0, 64, 0), new BlockPos(5, 68, 5));
		BlockPos high = new BlockPos(2, 100, 2);
		assertTrue(area.contains(high, false));
		assertFalse(area.contains(high, true));
		assertFalse(area.contains(new BlockPos(2, 63, 2), true));
	}

	@Test
	void sizesCountBothCorners() {
		BuildArea area = new BuildArea(new BlockPos(10, 70, 4), new BlockPos(2, 64, -5));
		assertEquals(9, area.width());
		assertEquals(10, area.depth());
		assertEquals(7, area.height());
	}
}
