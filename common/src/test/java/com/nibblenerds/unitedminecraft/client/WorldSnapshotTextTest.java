package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Falling;
import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Facts;
import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Input;
import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Ray;
import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Sample;
import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Surface;

class WorldSnapshotTextTest {
	private static final int STEP = 4;
	private static final int RANGE = 96;

	private static Facts facts(boolean enclosed) {
		return new Facts("overworld", 180, "morning", "clear", 15, enclosed, Map.of("plains", 3, "forest", 1));
	}

	/** Flat grass, with a snowy mountain rising to the south and a pond to the east. */
	private static List<Sample> landscape() {
		List<Sample> samples = new ArrayList<>();
		for (int dx = -RANGE; dx <= RANGE; dx += STEP) {
			for (int dz = -RANGE; dz <= RANGE; dz += STEP) {
				if (dx * dx + dz * dz > RANGE * RANGE) {
					continue;
				}
				if (dz >= 48 && Math.abs(dx) <= 24) {
					samples.add(new Sample(dx, dz, 20 + (dz - 48), Surface.SNOW, 0, 0, null));
				} else if (dx >= 24 && dx <= 40 && Math.abs(dz) <= 8) {
					samples.add(new Sample(dx, dz, -1, Surface.WATER, 0, 3, null));
				} else {
					samples.add(new Sample(dx, dz, 0, Surface.GRASS, 0, 0, null));
				}
			}
		}
		return samples;
	}

	private static Input input(boolean enclosed, List<Falling> falling, List<Ray> rays) {
		return new Input(facts(enclosed), landscape(), STEP, RANGE, falling, List.of(), rays, 32);
	}

	@Test
	void bearingIsACompassBearing() {
		assertEquals(0.0, WorldSnapshotText.bearing(0, -10), 1e-9);
		assertEquals(90.0, WorldSnapshotText.bearing(10, 0), 1e-9);
		assertEquals(180.0, WorldSnapshotText.bearing(0, 10), 1e-9);
		assertEquals(270.0, WorldSnapshotText.bearing(-10, 0), 1e-9);
	}

	@Test
	void directionNamesTheNearestOfSixteenPoints() {
		assertEquals("north", WorldSnapshotText.direction(359));
		assertEquals("northeast", WorldSnapshotText.direction(45));
		assertEquals("south-southwest", WorldSnapshotText.direction(200));
	}

	@Test
	void gridIsFinerForShortRanges() {
		assertEquals(4, WorldSnapshotText.gridStep(96));
		assertEquals(8, WorldSnapshotText.gridStep(192));
	}

	@Test
	void landIsSummarisedByDirectionAndDistance() {
		String text = WorldSnapshotText.render(input(false, List.of(), List.of()));
		assertTrue(text.contains("south, 0-48m: grass 100%; ground 0 (average 0)"), text);
		assertTrue(text.contains("south, 48-112m: snow"), text);
		assertTrue(text.contains("== Overview =="), text);
		assertTrue(text.contains("Highest ground: +"), text);
	}

	@Test
	void patchesReportTheSnowAndThePond() {
		String text = WorldSnapshotText.render(input(false, List.of(), List.of()));
		assertTrue(text.contains("- snow: about"), text);
		assertTrue(text.contains("- water: about"), text);
		assertTrue(text.contains("east"), text);
	}

	@Test
	void fallingLiquidIsListed() {
		String text = WorldSnapshotText.render(input(false, List.of(new Falling(false, 40, -30, 2, 15, 28)), List.of()));
		assertTrue(text.contains("falling water: 14 blocks tall (from +15 down to +2)"), text);
		assertTrue(text.contains("east"), text);
	}

	@Test
	void enclosedSnapshotsLeaveOutTheLandscape() {
		String text = WorldSnapshotText.render(input(true, List.of(), List.of(new Ray(0, 0, 3.0, "stone"))));
		assertFalse(text.contains("== Land by direction and distance =="), text);
		assertTrue(text.contains("underground"), text);
		assertTrue(text.contains("north: stone 3 blocks away"), text);
	}

	@Test
	void openSurroundingsAreReportedAsOpen() {
		List<Ray> rays = new ArrayList<>();
		for (int elevation : new int[] {-15, 0, 15}) {
			rays.add(new Ray(90, elevation, -1, null));
		}
		rays.add(new Ray(0, 80, -1, null));
		String text = WorldSnapshotText.render(input(false, List.of(), rays));
		assertTrue(text.contains("east: open, nothing within 32 blocks"), text);
		assertTrue(text.contains("above: open"), text);
	}
}
