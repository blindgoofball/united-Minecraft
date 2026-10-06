package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link ExplorationRange#effective} - the render-distance cap on biome and structure ranges. */
class ExplorationRangeTest {
	@Test
	void settingStandsWhenRenderDistanceIsFarEnough() {
		assertEquals(128.0, ExplorationRange.effective(128.0, 12));
		assertEquals(96.0, ExplorationRange.effective(96.0, 32));
	}

	@Test
	void lowRenderDistanceCapsTheSetting() {
		assertEquals(96.0, ExplorationRange.effective(128.0, 6));
		assertEquals(128.0, ExplorationRange.effective(128.0, 8));
		assertEquals(32.0, ExplorationRange.effective(96.0, 2));
	}

	@Test
	void defaultsSitWithinTheMaximum() {
		assertEquals(128.0, ExplorationRange.MAX);
		assertEquals(true, ExplorationRange.DEFAULT_SCANNER <= ExplorationRange.MAX);
		assertEquals(true, ExplorationRange.DEFAULT_VOICES <= ExplorationRange.DEFAULT_SCANNER);
	}
}
