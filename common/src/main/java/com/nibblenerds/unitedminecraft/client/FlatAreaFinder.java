package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Finds level, open rectangles in a grid of standable cells - the logic behind the Terrain
 * category's flat areas. Takes plain arrays rather than the world so it can be tested without
 * the game; {@link ScannerController} builds the grid from the live level.
 *
 * <p>A grid cell is "standable at height y" when there is solid ground under y and room above it.
 * One column can be standable at several heights (a cave floor under a hillside), so each height
 * is searched on its own: a flat area is a rectangle of cells that are all standable at the same
 * height, never a slope or a step.
 *
 * <p>Within a height, the biggest qualifying rectangle is taken first and its cells removed
 * before looking again, so one big field is one result rather than dozens of overlapping ones,
 * while a second, separate field of the same height is still found. A rectangle qualifies when it
 * is at least {@code a} by {@code b} in either orientation.
 */
final class FlatAreaFinder {
	/** A rectangle of {@code width} (x) by {@code depth} (z) cells whose near corner is cell ({@code x}, {@code z}), all standable at {@code y}. */
	record Area(int x, int z, int width, int depth, int y) {
	}

	/** The largest side the prompt accepts - the search only reaches this far out anyway. */
	static final int MAX_SIDE = 64;

	private FlatAreaFinder() {
	}

	/**
	 * Reads a size the player typed: "8" is 8 by 8, "8x12", "8 by 12" and "8, 12" are 8 by 12.
	 * Returns {width, depth}, or null if there isn't one or two whole numbers from 1 to {@link
	 * #MAX_SIDE}.
	 */
	static int[] parseSize(String text) {
		if (text == null) {
			return null;
		}
		String[] parts = text.strip().split("[^0-9]+");
		List<Integer> numbers = new ArrayList<>();
		for (String part : parts) {
			if (!part.isEmpty()) {
				if (part.length() > 3) {
					return null;
				}
				numbers.add(Integer.parseInt(part));
			}
		}
		if (numbers.isEmpty() || numbers.size() > 2) {
			return null;
		}
		int width = numbers.get(0);
		int depth = numbers.size() == 2 ? numbers.get(1) : width;
		if (width < 1 || depth < 1 || width > MAX_SIDE || depth > MAX_SIDE) {
			return null;
		}
		return new int[] {width, depth};
	}

	/**
	 * {@code floors} maps a standing height to a row-major mask ({@code gridWidth} cells per
	 * row) of the cells standable at it. The masks are consumed: found areas are cleared from
	 * them. Stops after {@code maxResults} areas, which are unordered - the caller sorts them by
	 * whatever it cares about.
	 */
	static List<Area> find(Map<Integer, boolean[]> floors, int gridWidth, int gridDepth, int a, int b, int maxResults) {
		List<Area> results = new ArrayList<>();
		if (a < 1 || b < 1 || gridWidth < 1 || gridDepth < 1) {
			return results;
		}
		for (Map.Entry<Integer, boolean[]> floor : floors.entrySet()) {
			boolean[] open = floor.getValue();
			while (results.size() < maxResults) {
				Area best = largest(open, gridWidth, gridDepth, a, b, floor.getKey());
				if (best == null) {
					break;
				}
				results.add(best);
				for (int z = best.z(); z < best.z() + best.depth(); z++) {
					Arrays.fill(open, z * gridWidth + best.x(), z * gridWidth + best.x() + best.width(), false);
				}
			}
		}
		return results;
	}

	/**
	 * The largest-area open rectangle that is at least a by b (or b by a), or null if none is.
	 * Row by row it keeps a histogram of how many open cells sit directly above each column and
	 * walks the stack of bars, which visits every maximal rectangle - any qualifying rectangle
	 * is inside one of those, and qualifying is preserved by growing, so none is missed.
	 */
	private static Area largest(boolean[] open, int w, int d, int a, int b, int y) {
		Area best = null;
		int[] bars = new int[w];
		int[] stack = new int[w + 1];
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				bars[x] = open[z * w + x] ? bars[x] + 1 : 0;
			}
			int top = 0;
			for (int x = 0; x <= w; x++) {
				int bar = x == w ? 0 : bars[x];
				while (top > 0 && bars[stack[top - 1]] >= bar) {
					int height = bars[stack[--top]];
					int left = top == 0 ? 0 : stack[top - 1] + 1;
					int width = x - left;
					if (qualifies(width, height, a, b)
							&& (best == null || width * height > best.width() * best.depth())) {
						best = new Area(left, z - height + 1, width, height, y);
					}
				}
				stack[top++] = x;
			}
		}
		return best;
	}

	private static boolean qualifies(int width, int depth, int a, int b) {
		return (width >= a && depth >= b) || (width >= b && depth >= a);
	}
}
