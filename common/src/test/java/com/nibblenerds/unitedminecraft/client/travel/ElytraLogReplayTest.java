package com.nibblenerds.unitedminecraft.client.travel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A tool, not a check: replays flights recorded by {@link ElytraFlightRecorder} against the current
 * autopilot, in the simulator, over the terrain the log captured (a 49-block square around the spot;
 * beyond it the nearest edge column is assumed). Run with {@code UM_FLIGHT_LOGS} set to the
 * {@code elytra-flights} directory and {@code ./gradlew :fabric:elytraSweep --rerun --tests
 * '*ElytraLogReplayTest*' -i}, and read the {@code REPLAY} lines. Does nothing without the variable.
 */
class ElytraLogReplayTest {
	@Test
	@Tag("sweep")
	void replay() throws IOException {
		String dir = System.getenv("UM_FLIGHT_LOGS");
		if (dir == null) {
			return;
		}
		List<Path> logs;
		try (Stream<Path> s = Files.list(Path.of(dir))) {
			logs = s.filter(p -> p.getFileName().toString().startsWith("flight-")).sorted().toList();
		}
		for (Path log : logs) {
			replay(log);
		}
	}

	private static void replay(Path log) throws IOException {
		Map<Long, double[]> grid = new HashMap<>();
		int sx = 0;
		int sy = 0;
		int sz = 0;
		double[] start = null;
		String yawLine = null;
		for (String line : Files.readAllLines(log)) {
			if (line.startsWith("# spot,")) {
				String[] p = line.split(",");
				sx = Integer.parseInt(p[1]);
				sy = Integer.parseInt(p[2]);
				sz = Integer.parseInt(p[3]);
			} else if (line.startsWith("# start,")) {
				yawLine = line;
			} else if (line.startsWith("G,")) {
				String[] p = line.split(",");
				grid.put(key(Integer.parseInt(p[1]), Integer.parseInt(p[2])),
						new double[] {Double.parseDouble(p[3]), Double.parseDouble(p[4])});
			}
		}
		String[] p = yawLine.replace("v=", "").replace("yaw=", "").replace("pitch=", "").split(",");
		start = new double[] {Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
				Double.parseDouble(p[4]), Double.parseDouble(p[5]), Double.parseDouble(p[6]), Double.parseDouble(p[7]), Double.parseDouble(p[8])};
		final int fx = sx;
		final int fz = sz;
		final int radius = 24;
		ElytraAutopilot.GroundProbe probe = new ElytraAutopilot.GroundProbe() {
			private double[] column(double x, double z) {
				int bx = (int) Math.floor(x);
				int bz = (int) Math.floor(z);
				bx = Math.max(fx - radius, Math.min(fx + radius, bx));
				bz = Math.max(fz - radius, Math.min(fz + radius, bz));
				return grid.get(key(bx, bz));
			}

			@Override
			public double height(double x, double z) {
				return column(x, z)[0];
			}

			@Override
			public double heightBelow(double x, double y, double z) {
				double[] c = column(x, z);
				return y >= c[0] ? c[0] : c[1];
			}
		};
		ElytraAutopilot pilot = new ElytraAutopilot(sx + 0.5, sy, sz + 0.5, probe);
		ElytraAutopilot.State s = new ElytraAutopilot.State(start[0], start[1], start[2], start[3], start[4], start[5], (float) start[6], (float) start[7]);
		try {
			ElytraFlight.Landing l = ElytraFlight.fly(pilot, s, probe);
			System.out.printf("REPLAY %s -> end %.1f,%.1f off the spot, sink %.2f, handedBack %s, ticks %d, phases %s, ground there %.0f vs spot %d%n",
					log.getFileName(), l.x() - (sx + 0.5), l.z() - (sz + 0.5), l.sink(), l.handedBack(), l.ticks(), l.phases(),
					probe.height(l.x(), l.z()), sy);
		} catch (AssertionError e) {
			System.out.println("REPLAY " + log.getFileName() + " -> " + e.getMessage());
		}
	}

	private static long key(int x, int z) {
		return (((long) x) << 32) ^ (z & 0xffffffffL);
	}
}
