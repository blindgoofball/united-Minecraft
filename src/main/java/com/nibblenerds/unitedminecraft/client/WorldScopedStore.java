package com.nibblenerds.unitedminecraft.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import com.nibblenerds.unitedminecraft.platform.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import org.slf4j.Logger;

/**
 * A JSON list persisted per world, entirely client-side - the shared backing for {@link
 * MapMarkerController}'s markers and {@link NamedBlockController}'s block names, which were
 * previously line-for-line duplicates of each other's persistence apart from the element type
 * and the subdirectory name.
 *
 * <p>A world is identified by its singleplayer save folder name, or by a multiplayer server's
 * saved address (falling back to a shared bucket for direct-connects that were never added to
 * the server list, since there's no reliable identity to key off of there). {@link #tick}
 * reloads automatically whenever the detected world changes, including switching worlds without
 * restarting the game.
 *
 * <p>Not thread-safe; only ever used from the client thread.
 */
final class WorldScopedStore<T> {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final String subdirectory;
	private final Type listType;
	private final Logger logger;

	private List<T> entries = new ArrayList<>();
	private String loadedWorldId;

	// Caches worldId() against the server/singleplayer-server object identity it was computed
	// from - tick() calls worldId() every client tick, but that identity only actually changes
	// once per world join/leave, so recomputing the Path#normalize()/getFileName() (or the
	// ServerData lookup) 20 times a second for an answer that hasn't changed is pure waste.
	private Object lastWorldIdSource;
	private String cachedWorldId;

	WorldScopedStore(String subdirectory, Type listType, Logger logger) {
		this.subdirectory = subdirectory;
		this.listType = listType;
		this.logger = logger;
	}

	/**
	 * Reloads from disk if the detected world has changed since the last call. Returns true only
	 * when a reload actually happened, so the caller can rebuild whatever index it keeps
	 * alongside {@link #entries()}.
	 */
	boolean tick(Minecraft client) {
		String worldId = worldId(client);
		if (Objects.equals(worldId, loadedWorldId)) {
			return false;
		}
		loadedWorldId = worldId;
		entries = load(worldId);
		return true;
	}

	void reset() {
		entries = new ArrayList<>();
		loadedWorldId = null;
		lastWorldIdSource = null;
		cachedWorldId = null;
	}

	/** The live, mutable entry list - callers add/remove directly, then call {@link #save()}. */
	List<T> entries() {
		return entries;
	}

	void save() {
		if (loadedWorldId == null) {
			return;
		}
		Path file = fileFor(loadedWorldId);
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(entries, listType, writer);
			}
		} catch (IOException e) {
			// Best-effort - losing the ability to persist shouldn't crash the game, and there's
			// nowhere better than the log to report a disk-write failure to.
			logger.warn("Failed to save {} to {}", subdirectory, file, e);
		}
	}

	private String worldId(Minecraft client) {
		Object source = client.isLocalServer() ? client.getSingleplayerServer() : client.getCurrentServer();
		if (cachedWorldId != null && source == lastWorldIdSource) {
			return cachedWorldId;
		}
		lastWorldIdSource = source;
		cachedWorldId = computeWorldId(client);
		return cachedWorldId;
	}

	private static String computeWorldId(Minecraft client) {
		if (client.isLocalServer() && client.getSingleplayerServer() != null) {
			// LevelResource.ROOT's id is literally "." - getWorldPath resolves to
			// ".../saves/<world>/.", and Path#resolve doesn't normalize away that trailing
			// segment, so getFileName() would return "." for every world without this.
			// normalize() first so it actually returns the save folder name.
			Path worldPath = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize();
			return "sp_" + worldPath.getFileName();
		}
		ServerData server = client.getCurrentServer();
		return "mp_" + (server != null ? server.ip : "direct_connect");
	}

	private Path directory() {
		return Platform.get().configDir().resolve("united_minecraft").resolve(subdirectory);
	}

	/**
	 * The file backing {@code worldId}: the sanitized id (so the filename stays recognizable when
	 * browsing the config folder) plus a hash of the <em>raw</em> id.
	 *
	 * <p>The hash is what actually keys the file. Sanitizing alone collapses every character
	 * outside {@code [a-zA-Z0-9_-]} to an underscore, so save folders {@code My World} and {@code
	 * My_World} - or servers {@code 1.2.3.4:25565} and {@code 1-2-3-4-25565} - all produced the
	 * same filename and silently shared one another's data, with whichever world was joined
	 * second overwriting the first's on its next save. {@link String#hashCode()} is specified
	 * exactly by the JDK, so the same world always resolves to the same file across machines and
	 * Java versions.
	 */
	private Path fileFor(String worldId) {
		return directory().resolve(sanitize(worldId) + "-" + Integer.toUnsignedString(worldId.hashCode(), 16) + ".json");
	}

	/** Where {@code worldId}'s data lived before {@link #fileFor} started disambiguating by hash - see {@link #load}. */
	private Path legacyFileFor(String worldId) {
		return directory().resolve(sanitize(worldId) + ".json");
	}

	private static String sanitize(String worldId) {
		return worldId.replaceAll("[^a-zA-Z0-9_-]", "_");
	}

	/**
	 * Reads {@code worldId}'s entries, migrating from the pre-hash filename ({@link
	 * #legacyFileFor}) the first time a world saved by an older version is loaded, so existing
	 * markers and block names survive the change.
	 *
	 * <p>The legacy file is deleted once it has been rewritten to the new path. In the rare case
	 * where two worlds' sanitized names genuinely collided, that one shared file held both
	 * worlds' entries merged together - which was the bug - and it migrates to whichever of them
	 * is loaded first, leaving the other to start clean. There's no way to un-merge it after the
	 * fact, and carrying the merged blob into both worlds would just perpetuate it.
	 */
	private List<T> load(String worldId) {
		Path file = fileFor(worldId);
		if (Files.exists(file)) {
			return read(file);
		}
		Path legacy = legacyFileFor(worldId);
		if (!Files.exists(legacy)) {
			return new ArrayList<>();
		}
		List<T> migrated = read(legacy);
		loadedWorldId = worldId;
		entries = migrated;
		save();
		if (Files.exists(file)) {
			try {
				Files.delete(legacy);
				logger.info("Migrated {} for {} from {} to {}", subdirectory, worldId, legacy, file);
			} catch (IOException e) {
				// The new file is already written, so the data is safe either way - the stale
				// legacy copy just lingers. Not worth failing the load over.
				logger.warn("Migrated {} for {} but could not remove {}", subdirectory, worldId, legacy, e);
			}
		}
		return migrated;
	}

	private List<T> read(Path file) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			List<T> loaded = GSON.fromJson(reader, listType);
			return loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
		} catch (IOException | JsonParseException e) {
			logger.warn("Failed to load {} from {}", subdirectory, file, e);
			return new ArrayList<>();
		}
	}
}
