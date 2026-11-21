package com.nibblenerds.unitedminecraft.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.nibblenerds.unitedminecraft.platform.Platform;

/**
 * The saved worlds Minecraft 26.3 left for a server address. That version filed a multiplayer
 * world's markers and block names under the address plus the world seed the server sent, and
 * 26.4 no longer sends a seed, so those files cannot be found by id any more. They can still be
 * found by name: a file's name is its id with the characters outside {@code [a-zA-Z0-9_-]} turned
 * into underscores, plus a hash of the raw id, so for a known address the hash proves a file is
 * this address's and gives back the id it was saved under.
 *
 * <p>What cannot be recovered is which world each set was - the seed is gone - which is why a
 * player is asked when an address has more than one. See {@link WorldMigration}.
 */
final class LegacyWorldSets {
	/** The folders {@link WorldScopedStore}s keep their files in, one per kind of saved data. */
	static final List<String> SUBDIRECTORIES = List.of("markers", "named_blocks", "saved_searches", "scanner_filters");

	private static final Pattern HEX = Pattern.compile("[0-9a-f]{1,16}");
	private static final int NAMES_SHOWN = 3;

	private LegacyWorldSets() {
	}

	/**
	 * One old world's saved data.
	 *
	 * @param rawId the id the files were saved under, e.g. {@code mp_play.example.com_1a2b3c}
	 * @param markerCount how many markers it holds
	 * @param markerNames up to a few of their names, to jog the player's memory
	 * @param lastUsed when any of its files last changed, in epoch milliseconds
	 */
	record LegacySet(String rawId, int markerCount, List<String> markerNames, long lastUsed) {
	}

	/** Every old set for {@code address}, most recently used first. */
	static List<LegacySet> find(String address) {
		String prefix = WorldScopedStore.sanitize("mp_" + address) + "_";
		Map<String, Long> lastUsed = new LinkedHashMap<>();
		for (String subdirectory : SUBDIRECTORIES) {
			Path directory = directory(subdirectory);
			if (!Files.isDirectory(directory)) {
				continue;
			}
			try (Stream<Path> files = Files.list(directory)) {
				for (Path file : (Iterable<Path>) files::iterator) {
					String rawId = rawIdOf(file.getFileName().toString(), address, prefix);
					if (rawId != null) {
						lastUsed.merge(rawId, modified(file), Math::max);
					}
				}
			} catch (IOException e) {
				// An unreadable folder only means fewer sets are offered; nothing is lost.
			}
		}
		List<LegacySet> sets = new ArrayList<>();
		for (Map.Entry<String, Long> entry : lastUsed.entrySet()) {
			List<String> names = markerNames(entry.getKey());
			sets.add(new LegacySet(entry.getKey(), names.size(),
					names.subList(0, Math.min(NAMES_SHOWN, names.size())), entry.getValue()));
		}
		sets.sort(Comparator.comparingLong(LegacySet::lastUsed).reversed());
		return sets;
	}

	/**
	 * The id {@code fileName} was saved under if it is one of {@code address}'s old sets, else null.
	 * The name is rebuilt from the id and compared, so a file from a different address that happens
	 * to clean up to the same text is not taken.
	 */
	private static String rawIdOf(String fileName, String address, String prefix) {
		if (!fileName.endsWith(".json")) {
			return null;
		}
		String base = fileName.substring(0, fileName.length() - ".json".length());
		int dash = base.lastIndexOf('-');
		if (dash < 0 || !base.startsWith(prefix)) {
			return null;
		}
		String hex = base.substring(prefix.length(), dash);
		if (!HEX.matcher(hex).matches()) {
			return null;
		}
		String rawId = "mp_" + address + "_" + hex;
		return WorldScopedStore.fileNameFor(rawId).equals(fileName) ? rawId : null;
	}

	/** Moves {@code set}'s files to {@code worldId}, leaving any file the world already has alone. */
	static void claim(LegacySet set, String worldId) {
		for (String subdirectory : SUBDIRECTORIES) {
			Path source = directory(subdirectory).resolve(WorldScopedStore.fileNameFor(set.rawId()));
			Path target = directory(subdirectory).resolve(WorldScopedStore.fileNameFor(worldId));
			if (Files.exists(source) && !Files.exists(target)) {
				try {
					Files.move(source, target);
				} catch (IOException e) {
					// Left where it was, so it is offered again next time rather than lost.
				}
			}
		}
	}

	/** Records that {@code worldId} has no old data, so it is not asked about again. */
	static void startFresh(String worldId) {
		for (String subdirectory : SUBDIRECTORIES) {
			Path target = directory(subdirectory).resolve(WorldScopedStore.fileNameFor(worldId));
			if (!Files.exists(target)) {
				try {
					SafeFiles.writeAtomically(target, writer -> writer.write("[]"));
				} catch (IOException e) {
					// Asked again next time, which is harmless.
				}
			}
		}
	}

	/** Whether any store has a file for {@code worldId}. */
	static boolean hasData(String worldId) {
		for (String subdirectory : SUBDIRECTORIES) {
			if (Files.exists(directory(subdirectory).resolve(WorldScopedStore.fileNameFor(worldId)))) {
				return true;
			}
		}
		return false;
	}

	private static List<String> markerNames(String rawId) {
		Path file = directory("markers").resolve(WorldScopedStore.fileNameFor(rawId));
		List<String> names = new ArrayList<>();
		if (!Files.exists(file)) {
			return names;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (root.isJsonArray()) {
				for (JsonElement element : root.getAsJsonArray()) {
					if (element.isJsonObject() && element.getAsJsonObject().has("name")) {
						names.add(element.getAsJsonObject().get("name").getAsString());
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			// A file that will not read is still a set; it just shows no names.
		}
		return names;
	}

	private static long modified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}

	private static Path directory(String subdirectory) {
		return Platform.get().configDir().resolve("united_minecraft").resolve(subdirectory);
	}
}
