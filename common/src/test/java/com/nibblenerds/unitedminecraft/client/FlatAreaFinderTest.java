package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FlatAreaFinderTest {
	/** A w by d mask with every cell open. */
	private static boolean[] open(int w, int d) {
		boolean[] cells = new boolean[w * d];
		Arrays.fill(cells, true);
		return cells;
	}

	/** Opens the rectangle starting at (x0, z0) in a mask w cells wide. */
	private static void fill(boolean[] cells, int w, int x0, int z0, int width, int depth) {
		for (int z = z0; z < z0 + depth; z++) {
			for (int x = x0; x < x0 + width; x++) {
				cells[z * w + x] = true;
			}
		}
	}

	private static Map<Integer, boolean[]> floor(int y, boolean[] mask) {
		Map<Integer, boolean[]> floors = new LinkedHashMap<>();
		floors.put(y, mask);
		return floors;
	}

	@Test
	void wholeGridIsOneArea() {
		List<FlatAreaFinder.Area> areas = FlatAreaFinder.find(floor(64, open(10, 8)), 10, 8, 5, 5, 10);
		assertEquals(List.of(new FlatAreaFinder.Area(0, 0, 10, 8, 64)), areas);
	}

	@Test
	void tooSmallFindsNothing() {
		assertTrue(FlatAreaFinder.find(floor(64, open(4, 4)), 4, 4, 5, 5, 10).isEmpty());
	}

	@Test
	void eitherOrientationQualifies() {
		assertEquals(1, FlatAreaFinder.find(floor(64, open(3, 9)), 3, 9, 9, 3, 10).size());
		assertEquals(1, FlatAreaFinder.find(floor(64, open(3, 9)), 3, 9, 3, 9, 10).size());
		assertTrue(FlatAreaFinder.find(floor(64, open(3, 9)), 3, 9, 4, 4, 10).isEmpty());
	}

	@Test
	void aStepSplitsTheArea() {
		// The left half stands at 64 and the right half at 65, so neither is 6 wide.
		boolean[] low = new boolean[10 * 6];
		boolean[] high = new boolean[10 * 6];
		fill(low, 10, 0, 0, 5, 6);
		fill(high, 10, 5, 0, 5, 6);
		Map<Integer, boolean[]> floors = new LinkedHashMap<>();
		floors.put(64, low);
		floors.put(65, high);
		assertTrue(FlatAreaFinder.find(floors, 10, 6, 6, 6, 10).isEmpty());
	}

	@Test
	void aStepStillLeavesEachHalfFindable() {
		boolean[] low = new boolean[10 * 6];
		boolean[] high = new boolean[10 * 6];
		fill(low, 10, 0, 0, 5, 6);
		fill(high, 10, 5, 0, 5, 6);
		Map<Integer, boolean[]> floors = new LinkedHashMap<>();
		floors.put(64, low);
		floors.put(65, high);
		assertEquals(2, FlatAreaFinder.find(floors, 10, 6, 5, 5, 10).size());
	}

	@Test
	void holeSkippedAndBiggestRectangleChosen() {
		// One bad cell in the middle of a 9x9: the biggest rectangle 4x4 or better is a 9x4 strip.
		boolean[] cells = open(9, 9);
		cells[4 * 9 + 4] = false;
		FlatAreaFinder.Area first = FlatAreaFinder.find(floor(64, cells), 9, 9, 4, 4, 10).get(0);
		assertEquals(36, first.width() * first.depth());
	}

	@Test
	void separateFieldsAtTheSameHeightAreBothFound() {
		boolean[] cells = new boolean[21 * 5];
		fill(cells, 21, 0, 0, 5, 5);
		fill(cells, 21, 16, 0, 5, 5);
		assertEquals(2, FlatAreaFinder.find(floor(64, cells), 21, 5, 5, 5, 10).size());
	}

	@Test
	void stackedFloorsInTheSameColumnsAreSeparateAreas() {
		// A hillside over a cave floor: the same columns are standable at two heights.
		Map<Integer, boolean[]> floors = new LinkedHashMap<>();
		floors.put(64, open(6, 6));
		floors.put(80, open(6, 6));
		List<FlatAreaFinder.Area> areas = FlatAreaFinder.find(floors, 6, 6, 6, 6, 10);
		assertEquals(List.of(64, 80), areas.stream().map(FlatAreaFinder.Area::y).sorted().toList());
	}

	@Test
	void resultsAreCapped() {
		boolean[] cells = new boolean[30 * 3];
		for (int block = 0; block < 6; block++) {
			fill(cells, 30, block * 5, 0, 3, 3);
		}
		assertEquals(4, FlatAreaFinder.find(floor(64, cells), 30, 3, 3, 3, 4).size());
	}

	@Test
	void parsesSizes() {
		assertArrayEquals(new int[] {8, 8}, FlatAreaFinder.parseSize("8"));
		assertArrayEquals(new int[] {8, 12}, FlatAreaFinder.parseSize("8x12"));
		assertArrayEquals(new int[] {8, 12}, FlatAreaFinder.parseSize(" 8 by 12 "));
		assertArrayEquals(new int[] {8, 12}, FlatAreaFinder.parseSize("8, 12"));
		assertNull(FlatAreaFinder.parseSize(""));
		assertNull(FlatAreaFinder.parseSize("big"));
		assertNull(FlatAreaFinder.parseSize("0"));
		assertNull(FlatAreaFinder.parseSize("65"));
		assertNull(FlatAreaFinder.parseSize("1 2 3"));
		assertNull(FlatAreaFinder.parseSize("99999999999"));
	}
}
