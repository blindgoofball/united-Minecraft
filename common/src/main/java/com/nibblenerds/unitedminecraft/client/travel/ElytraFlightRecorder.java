package com.nibblenerds.unitedminecraft.client.travel;

import com.mojang.logging.LogUtils;
import com.nibblenerds.unitedminecraft.platform.Platform;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;

/**
 * A flight recorder for {@link ElytraLandingController}: while the landing assist flies, it writes
 * what happened to a plain text file under {@code united_minecraft/elytra-flights/} in the game
 * directory - where the flight started and was headed, the ground around the spot as the autopilot saw
 * it, every tick's position, velocity, look and prediction, and how the flight ended. The last {@link
 * #KEEP} flights are kept. It exists to answer "why did that landing go wrong" from the file alone,
 * since a flight can't be watched: the terrain grid is enough to replay it in the unit tests'
 * simulator.
 *
 * <p>Rows: {@code #} lines are the header; {@code G,x,z,surface,below} is one column of the terrain grid
 * ({@code surface} the column's highest block's top, {@code below} the ground a flight just above the
 * spot would meet); {@code T,...} is a tick (columns named by the {@code T-columns} header line); and
 * the last line is {@code END,outcome,...}.
 */
final class ElytraFlightRecorder {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int KEEP = 10;
	private static final int FLUSH_EVERY_ROWS = 40;
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static BufferedWriter out;
	private static int rows;

	private ElytraFlightRecorder() {
	}

	static boolean isRecording() {
		return out != null;
	}

	/** Starts a new file (closing any still open as abandoned), with {@code header} as its first lines. */
	static void begin(List<String> header) {
		end("abandoned", "a new flight started");
		try {
			Path dir = Platform.get().gameDir().resolve("united_minecraft").resolve("elytra-flights");
			Files.createDirectories(dir);
			prune(dir);
			Path file = dir.resolve("flight-" + STAMP.format(LocalDateTime.now()) + ".log");
			out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
			rows = 0;
			for (String line : header) {
				out.write(line);
				out.newLine();
			}
		} catch (IOException e) {
			LOGGER.debug("Could not start an elytra flight log", e);
			out = null;
		}
	}

	static void row(String line) {
		if (out == null) {
			return;
		}
		try {
			out.write(line);
			out.newLine();
			if (++rows % FLUSH_EVERY_ROWS == 0) {
				out.flush();
			}
		} catch (IOException e) {
			LOGGER.debug("Could not write an elytra flight log row", e);
			out = null;
		}
	}

	/** Writes the closing line and closes the file; does nothing when no flight is being recorded. */
	static void end(String outcome, String detail) {
		if (out == null) {
			return;
		}
		row("END," + outcome + "," + detail);
		try {
			out.close();
		} catch (IOException e) {
			LOGGER.debug("Could not close an elytra flight log", e);
		}
		out = null;
	}

	/** Keeps the newest few files, leaving room for the one about to be written. */
	private static void prune(Path dir) throws IOException {
		List<Path> files = new ArrayList<>();
		try (Stream<Path> listing = Files.list(dir)) {
			listing.filter(path -> path.getFileName().toString().startsWith("flight-")).forEach(files::add);
		}
		Collections.sort(files);
		for (int i = 0; i < files.size() - (KEEP - 1); i++) {
			Files.deleteIfExists(files.get(i));
		}
	}
}
