package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a sampled picture of the world around the player into plain text for a language model to
 * describe. Pure: {@link WorldSnapshotBuilder} does the reading of the live world and hands the
 * measurements here, so all of this can be tested without a running game.
 *
 * <p>The split is deliberate. Code only measures and labels - what exists, where, how big, how
 * high - and never decides what it means (a ridge, a waterfall, a village). That interpretation
 * is the model's job, so it can recognise things nobody wrote a detector for.
 *
 * <p>The text is model input, not something narrated to the player, so it is English and not
 * run through translation keys.
 *
 * <p>Bearings are compass bearings (0 = north, 90 = east); heights are relative to the player's
 * feet.
 */
final class WorldSnapshotText {
	/** What the ground surface is made of, as far as a description cares. */
	enum Surface {
		GRASS("grass"), DIRT("dirt"), STONE("stone"), SAND("sand"), SNOW("snow"), ICE("ice"),
		WATER("water"), LAVA("lava"), BUILT("built blocks"), OTHER("other");

		final String label;

		Surface(String label) {
			this.label = label;
		}
	}

	/**
	 * One column of ground. {@code canopyHeight} is how far tree leaves/logs rise above the ground
	 * there (0 for none); {@code waterDepth} how deep water is when {@code surface} is WATER;
	 * {@code detail} the block name for OTHER surfaces, otherwise null.
	 */
	record Sample(int dx, int dz, int relY, Surface surface, int canopyHeight, int waterDepth, String detail) {
	}

	/** A connected body of falling (not still) water or lava - a waterfall or lavafall, or part of one. */
	record Falling(boolean lava, int dx, int dz, int minRelY, int maxRelY, int cells) {
	}

	/** One ray cast from the eyes. {@code distance} is negative if it hit nothing within range. */
	record Ray(int compass, int elevation, double distance, String material) {
	}

	record Facts(String dimension, int facingBearing, String timeOfDay, String weather, int lightLevel,
			boolean enclosed, Map<String, Integer> biomeCounts) {
	}

	record Input(Facts facts, List<Sample> samples, int step, int range, List<Falling> falling,
			List<Ray> rays, int rayRange) {
	}

	private static final String[] DIRECTIONS = {
			"north", "north-northeast", "northeast", "east-northeast", "east", "east-southeast", "southeast",
			"south-southeast", "south", "south-southwest", "southwest", "west-southwest", "west", "west-northwest",
			"northwest", "north-northwest"};
	private static final String[] OCTANTS = {
			"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
	private static final int[] RADII = {16, 32, 48, 64, 96, 128, 160, 192};
	private static final int FOREST_CANOPY = 3;
	private static final int MAX_FEATURES_PER_KIND = 4;

	private WorldSnapshotText() {
	}

	/** Spacing of the ground samples: fine enough to see small features up close, coarser for a long view. */
	static int gridStep(int range) {
		return range <= 128 ? 4 : 8;
	}

	/** Compass bearing in degrees [0, 360) of an offset, where +x is east and +z is south. */
	static double bearing(double dx, double dz) {
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		return degrees < 0 ? degrees + 360.0 : degrees;
	}

	static String direction(double bearing) {
		int index = Math.floorMod((int) Math.round(bearing / 22.5), 16);
		return DIRECTIONS[index];
	}

	private static String octant(int bearing) {
		return OCTANTS[Math.floorMod(Math.round(bearing / 45.0f), 8)];
	}

	static String signed(int value) {
		return value > 0 ? "+" + value : Integer.toString(value);
	}

	private static String where(double dx, double dz) {
		return Math.round(Math.hypot(dx, dz)) + " blocks " + direction(bearing(dx, dz));
	}

	static String render(Input in) {
		StringBuilder out = new StringBuilder();
		out.append("You are describing the scene below for a blind Minecraft player, who cannot see it. Set the mood ")
				.append("from the time of day, weather and light, then move from what is close to the horizon, placing features by ")
				.append("compass direction. Speak to the player as \"you\", in the present tense.\n\n")
				.append("The measurements are for your understanding only; turn them into imagery rather than repeating them. ")
				.append("Convey distance with phrases like \"just ahead\" or \"far in the distance\", and size with words like ")
				.append("\"a small pond\" or \"a vast sea\". Use a number only when it really helps, such as \"a drop of about ten ")
				.append("blocks\", and never list coordinates or heights.\n\n")
				.append("Describe only what the data supports; do not invent landmarks or events. Do not greet the player or ")
				.append("comment on yourself. Write two paragraphs of about four sentences each: the first for your immediate ")
				.append("surroundings, the second for the wider landscape out to the horizon. Give the significant features a ")
				.append("little detail, but skip minor ones, and use no headings or bullet points.\n\n");
		out.append("Notes: bearings are compass directions (north is -z, east is +x). Heights like +12 or -3 are ")
				.append("blocks above or below the player's feet. Distances are in blocks.\n\n");

		appendFacts(out, in);
		if (!in.facts().enclosed()) {
			appendTerrain(out, in);
			appendFeatures(out, in);
		} else {
			out.append("== Terrain ==\nThe player is indoors or underground, so the land above and around is left out.\n\n");
		}
		appendFalling(out, in);
		appendSurroundings(out, in);
		return out.toString();
	}

	private static void appendFacts(StringBuilder out, Input in) {
		Facts f = in.facts();
		out.append("== Player ==\n");
		out.append("Dimension: ").append(f.dimension()).append('\n');
		out.append("Facing: ").append(direction(f.facingBearing())).append(" (").append(f.facingBearing()).append(" degrees)\n");
		out.append("Time: ").append(f.timeOfDay()).append(", weather: ").append(f.weather()).append('\n');
		out.append("Light level here: ").append(f.lightLevel()).append(" of 15\n");
		out.append("Setting: ").append(f.enclosed() ? "enclosed (underground, indoors or under a ceiling)" : "outdoors").append('\n');
		if (!f.biomeCounts().isEmpty()) {
			int total = f.biomeCounts().values().stream().mapToInt(Integer::intValue).sum();
			List<Map.Entry<String, Integer>> sorted = new ArrayList<>(f.biomeCounts().entrySet());
			sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
			out.append("Biomes in view range: ");
			int shown = 0;
			for (Map.Entry<String, Integer> e : sorted) {
				if (shown++ > 0) {
					out.append(", ");
				}
				out.append(e.getKey().replace('_', ' ')).append(' ').append(Math.round(100.0 * e.getValue() / total)).append('%');
				if (shown == 6) {
					break;
				}
			}
			out.append('\n');
		}
		if (!f.enclosed()) {
			out.append("Terrain is known out to ").append(in.range()).append(" blocks (only chunks the game has loaded).\n");
		}
		out.append('\n');
	}

	private static long key(int i, int j) {
		return ((long) i << 32) ^ (j & 0xffffffffL);
	}

	private static Map<Long, Sample> grid(Input in) {
		Map<Long, Sample> grid = new HashMap<>();
		for (Sample s : in.samples()) {
			grid.put(key(Math.floorDiv(s.dx(), in.step()), Math.floorDiv(s.dz(), in.step())), s);
		}
		return grid;
	}

	private static String describe(Sample s) {
		String base = s.surface() == Surface.OTHER && s.detail() != null ? s.detail().replace('_', ' ') : s.surface().label;
		if (s.surface() == Surface.WATER && s.waterDepth() > 0) {
			base += " " + s.waterDepth() + " deep";
		}
		if (s.canopyHeight() >= FOREST_CANOPY) {
			base += "+trees";
		}
		return base + " " + signed(s.relY());
	}

	private static final int[] BAND_EDGES = {0, 48, 112, Integer.MAX_VALUE};

	/** The surface a sample counts as in a composition: trees where there is a canopy, else what it is made of. */
	private static String cover(Sample s) {
		if (s.canopyHeight() >= FOREST_CANOPY) {
			return "trees";
		}
		return s.surface() == Surface.OTHER && s.detail() != null ? s.detail().replace('_', ' ') : s.surface().label;
	}

	/** Percentages of the most common covers, e.g. "ice 70%, water 30%". */
	private static String composition(List<Sample> samples) {
		Map<String, Integer> counts = new TreeMap<>();
		for (Sample s : samples) {
			counts.merge(cover(s), 1, Integer::sum);
		}
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
		sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
		StringBuilder text = new StringBuilder();
		int shown = 0;
		for (Map.Entry<String, Integer> e : sorted) {
			long percent = Math.round(100.0 * e.getValue() / samples.size());
			if (percent < 10 && shown > 0 || shown == 4) {
				break;
			}
			if (shown++ > 0) {
				text.append(", ");
			}
			text.append(e.getKey()).append(' ').append(Math.max(percent, 1)).append('%');
		}
		return text.toString();
	}

	private static String heights(List<Sample> samples) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		long sum = 0;
		for (Sample s : samples) {
			min = Math.min(min, s.relY());
			max = Math.max(max, s.relY());
			sum += s.relY();
		}
		String range = min == max ? signed(min) : signed(min) + " to " + signed(max);
		return "ground " + range + " (average " + signed((int) Math.round((double) sum / samples.size())) + ")";
	}

	private static void appendTerrain(StringBuilder out, Input in) {
		List<Sample> all = new ArrayList<>();
		for (Sample s : in.samples()) {
			if (Math.hypot(s.dx(), s.dz()) >= in.step()) {
				all.add(s);
			}
		}
		if (all.isEmpty()) {
			return;
		}
		out.append("== Overview ==\n");
		out.append("Ground cover overall: ").append(composition(all)).append('\n');
		Sample high = all.get(0);
		Sample low = all.get(0);
		for (Sample s : all) {
			if (s.relY() > high.relY()) {
				high = s;
			}
			if (s.relY() < low.relY()) {
				low = s;
			}
		}
		out.append("Highest ground: ").append(signed(high.relY())).append(", ").append(where(high.dx(), high.dz()))
				.append('\n');
		out.append("Lowest ground: ").append(signed(low.relY())).append(", ").append(where(low.dx(), low.dz())).append("\n\n");

		out.append("== Land by direction and distance ==\n");
		out.append("For each of eight directions and three distance bands: what covers the ground, and its height.\n");
		for (int o = 0; o < 8; o++) {
			for (int band = 0; band < 3; band++) {
				List<Sample> cell = new ArrayList<>();
				for (Sample s : all) {
					double dist = Math.hypot(s.dx(), s.dz());
					double diff = Math.abs(bearing(s.dx(), s.dz()) - o * 45.0);
					if (dist >= BAND_EDGES[band] && dist < BAND_EDGES[band + 1] && Math.min(diff, 360.0 - diff) <= 22.5) {
						cell.add(s);
					}
				}
				if (cell.isEmpty()) {
					continue;
				}
				String bandName = band == 0 ? "0-48m" : band == 1 ? "48-112m" : "112m and beyond";
				out.append(OCTANTS[o]).append(", ").append(bandName).append(": ").append(composition(cell)).append("; ")
						.append(heights(cell)).append('\n');
			}
		}
		out.append('\n');
	}

	private enum Kind {
		LAVA("lava"), WATER("water"), BUILT("built blocks (planks, bricks, glass and the like)"),
		FOREST("trees"), SNOW("snow"), ICE("ice"), SAND("sand");

		final String label;

		Kind(String label) {
			this.label = label;
		}

		static Kind of(Sample s) {
			if (s.surface() == Surface.LAVA) {
				return LAVA;
			}
			if (s.surface() == Surface.WATER) {
				return WATER;
			}
			if (s.surface() == Surface.BUILT) {
				return BUILT;
			}
			if (s.canopyHeight() >= FOREST_CANOPY) {
				return FOREST;
			}
			return switch (s.surface()) {
				case SNOW -> SNOW;
				case ICE -> ICE;
				case SAND -> SAND;
				default -> null;
			};
		}

		int minSamples() {
			return this == WATER || this == LAVA || this == BUILT ? 2 : 4;
		}
	}

	private record Region(Kind kind, int count, int minI, int maxI, int minJ, int maxJ, double sumDx, double sumDz,
			int minRelY, int maxRelY, double nearestDist, int nearestDx, int nearestDz) {
	}

	/** Connected patches of the same notable surface - water, trees, snow and so on - found by flood-filling the sample grid. */
	private static void appendFeatures(StringBuilder out, Input in) {
		Map<Long, Sample> grid = grid(in);
		Map<Long, Boolean> seen = new HashMap<>();
		List<Region> regions = new ArrayList<>();
		for (Map.Entry<Long, Sample> entry : grid.entrySet()) {
			if (seen.containsKey(entry.getKey())) {
				continue;
			}
			Kind kind = Kind.of(entry.getValue());
			if (kind == null) {
				continue;
			}
			Region region = flood(grid, seen, entry.getValue(), kind, in.step());
			if (region.count() >= kind.minSamples()) {
				regions.add(region);
			}
		}
		if (regions.isEmpty()) {
			return;
		}
		out.append("== Notable patches of ground ==\n");
		out.append("Connected areas of one kind of surface, largest first.\n");
		for (Kind kind : Kind.values()) {
			List<Region> ofKind = new ArrayList<>(regions.stream().filter(r -> r.kind() == kind).toList());
			ofKind.sort(Comparator.comparingInt(Region::count).reversed());
			for (int n = 0; n < Math.min(MAX_FEATURES_PER_KIND, ofKind.size()); n++) {
				Region r = ofKind.get(n);
				int area = r.count() * in.step() * in.step();
				int eastWest = (r.maxI() - r.minI() + 1) * in.step();
				int northSouth = (r.maxJ() - r.minJ() + 1) * in.step();
				out.append("- ").append(kind.label).append(": about ").append(area).append(" square blocks, spanning ")
						.append(eastWest).append(" east-west by ").append(northSouth).append(" north-south; centre ")
						.append(where(r.sumDx() / r.count(), r.sumDz() / r.count())).append(", nearest part ")
						.append(where(r.nearestDx(), r.nearestDz())).append("; height ")
						.append(signed(r.minRelY())).append(r.minRelY() == r.maxRelY() ? "" : " to " + signed(r.maxRelY()))
						.append('\n');
			}
		}
		out.append('\n');
	}

	private static Region flood(Map<Long, Sample> grid, Map<Long, Boolean> seen, Sample start, Kind kind, int step) {
		java.util.ArrayDeque<Sample> queue = new java.util.ArrayDeque<>();
		queue.add(start);
		seen.put(key(Math.floorDiv(start.dx(), step), Math.floorDiv(start.dz(), step)), true);
		int count = 0;
		int minI = Integer.MAX_VALUE;
		int maxI = Integer.MIN_VALUE;
		int minJ = Integer.MAX_VALUE;
		int maxJ = Integer.MIN_VALUE;
		double sumDx = 0;
		double sumDz = 0;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		double nearest = Double.MAX_VALUE;
		int nearDx = 0;
		int nearDz = 0;
		int[][] neighbours = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			Sample s = queue.poll();
			int i = Math.floorDiv(s.dx(), step);
			int j = Math.floorDiv(s.dz(), step);
			count++;
			minI = Math.min(minI, i);
			maxI = Math.max(maxI, i);
			minJ = Math.min(minJ, j);
			maxJ = Math.max(maxJ, j);
			sumDx += s.dx();
			sumDz += s.dz();
			minY = Math.min(minY, s.relY());
			maxY = Math.max(maxY, s.relY());
			double dist = Math.hypot(s.dx(), s.dz());
			if (dist < nearest) {
				nearest = dist;
				nearDx = s.dx();
				nearDz = s.dz();
			}
			for (int[] d : neighbours) {
				long k = key(i + d[0], j + d[1]);
				Sample next = grid.get(k);
				if (next != null && !seen.containsKey(k) && Kind.of(next) == kind) {
					seen.put(k, true);
					queue.add(next);
				}
			}
		}
		return new Region(kind, count, minI, maxI, minJ, maxJ, sumDx, sumDz, minY, maxY, nearest, nearDx, nearDz);
	}

	private static void appendFalling(StringBuilder out, Input in) {
		if (in.falling().isEmpty()) {
			return;
		}
		out.append("== Falling liquid ==\n");
		out.append("Water or lava that is flowing down (waterfalls, lavafalls).\n");
		List<Falling> sorted = new ArrayList<>(in.falling());
		sorted.sort(Comparator.comparingDouble(f -> Math.hypot(f.dx(), f.dz())));
		for (Falling f : sorted.stream().limit(8).toList()) {
			out.append("- falling ").append(f.lava() ? "lava" : "water").append(": ")
					.append(f.maxRelY() - f.minRelY() + 1).append(" blocks tall (from ").append(signed(f.maxRelY()))
					.append(" down to ").append(signed(f.minRelY())).append("), about ").append(f.cells())
					.append(" blocks of liquid, ").append(where(f.dx(), f.dz())).append('\n');
		}
		out.append('\n');
	}

	private static String material(String raw) {
		return raw.replace('_', ' ');
	}

	/** What is close around the eyes: walls, ceiling and floor, from rays cast in every direction. */
	private static void appendSurroundings(StringBuilder out, Input in) {
		if (in.rays().isEmpty()) {
			return;
		}
		out.append("== Immediate surroundings (within ").append(in.rayRange()).append(" blocks) ==\n");
		for (int sector = 0; sector < 8; sector++) {
			int centre = sector * 45;
			List<Ray> side = new ArrayList<>();
			for (Ray ray : in.rays()) {
				int diff = Math.abs(Math.floorMod(ray.compass() - centre + 180, 360) - 180);
				if (diff <= 22 && Math.abs(ray.elevation()) <= 15) {
					side.add(ray);
				}
			}
			if (side.isEmpty()) {
				continue;
			}
			out.append(OCTANTS[sector]).append(": ").append(describeHits(side, in.rayRange())).append('\n');
		}
		out.append("above: ").append(describeVertical(in, true)).append('\n');
		out.append("below: ").append(describeVertical(in, false)).append('\n');
		out.append('\n');
	}

	private static String describeHits(List<Ray> rays, int rayRange) {
		List<Double> hits = new ArrayList<>();
		Map<String, Integer> materials = new TreeMap<>();
		int misses = 0;
		for (Ray ray : rays) {
			if (ray.distance() < 0) {
				misses++;
			} else {
				hits.add(ray.distance());
				materials.merge(ray.material(), 1, Integer::sum);
			}
		}
		if (hits.isEmpty()) {
			return "open, nothing within " + rayRange + " blocks";
		}
		hits.sort(Double::compare);
		String top = materials.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
		String text = material(top) + " " + Math.round(hits.get(0)) + (hits.size() > 1 && Math.round(hits.get(hits.size() - 1)) != Math.round(hits.get(0))
				? " to " + Math.round(hits.get(hits.size() - 1)) : "") + " blocks away";
		if (misses > 0) {
			text += ", with gaps that stay open beyond " + rayRange + " blocks";
		}
		return text;
	}

	private static String describeVertical(Input in, boolean up) {
		List<Double> clearances = new ArrayList<>();
		Map<String, Integer> materials = new TreeMap<>();
		int misses = 0;
		int total = 0;
		for (Ray ray : in.rays()) {
			boolean match = up ? ray.elevation() >= 60 : ray.elevation() <= -60;
			if (!match) {
				continue;
			}
			total++;
			if (ray.distance() < 0) {
				misses++;
			} else {
				clearances.add(ray.distance() * Math.sin(Math.toRadians(Math.abs(ray.elevation()))));
				materials.merge(ray.material(), 1, Integer::sum);
			}
		}
		if (total == 0) {
			return "unknown";
		}
		if (clearances.isEmpty()) {
			return up ? "open, nothing within " + in.rayRange() + " blocks" : "a drop of more than " + in.rayRange() + " blocks";
		}
		clearances.sort(Double::compare);
		double median = clearances.get(clearances.size() / 2);
		String top = materials.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
		if (up && misses * 2 > total) {
			return "mostly open; some " + material(top) + " " + Math.round(clearances.get(0)) + " blocks up in places";
		}
		String text = material(top) + " about " + Math.round(median) + " blocks " + (up ? "overhead" : "down");
		if (misses * 2 > total) {
			text += " (mostly open)";
		}
		return text;
	}
}
