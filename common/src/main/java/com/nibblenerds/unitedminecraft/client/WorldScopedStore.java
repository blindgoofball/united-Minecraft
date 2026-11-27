package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.scanner.MapMarkerController;
import com.nibblenerds.unitedminecraft.client.scanner.NamedBlockController;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

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
 * <p>A world is identified by its singleplayer save folder name, or on a server by its address
 * plus the world seed the server sent (see {@link ServerWorldSeed}), so two worlds behind one
 * address keep separate data. A server with no ServerData at all - a launch straight into a
 * server, say - falls back to a shared bucket. {@link #tick} reloads automatically whenever the
 * detected world changes, including switching worlds without restarting the game.
 *
 * <p>Not thread-safe; only ever used from the client thread.
 */
public final class WorldScopedStore<T> {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final String subdirectory;
	private final Type listType;
	private final Logger logger;

	/**
	 * Which world is loaded. {@code addressOnlyId} is what a server world was filed under before
	 * the seed became part of its id; it is null for everything else.
	 */
	private record WorldKey(String id, String addressOnlyId) {
	}

	private List<T> entries = new ArrayList<>();
	private String loadedWorldId;

	// Caches worldKey() against the server/singleplayer-server object identity and the seed it
	// was computed from - tick() calls worldKey() every client tick, but those only actually
	// change once per world join/leave, so recomputing the Path#normalize()/getFileName() (or the
	// ServerData lookup) 20 times a second for an answer that hasn't changed is pure waste.
	private Object lastWorldIdSource;
	private OptionalLong lastWorldIdSeed = OptionalLong.empty();
	private WorldKey cachedWorldKey;

	public WorldScopedStore(String subdirectory, Type listType, Logger logger) {
		this.subdirectory = subdirectory;
		this.listType = listType;
		this.logger = logger;
	}

	/**
	 * Reloads from disk if the detected world has changed since the last call. Returns true only
	 * when a reload actually happened, so the caller can rebuild whatever index it keeps
	 * alongside {@link #entries()}.
	 */
	public boolean tick(Minecraft client) {
		WorldKey key = worldKey(client);
		if (Objects.equals(key.id(), loadedWorldId)) {
			return false;
		}
		loadedWorldId = key.id();
		entries = load(key);
		return true;
	}

	public void reset() {
		entries = new ArrayList<>();
		loadedWorldId = null;
		lastWorldIdSource = null;
		lastWorldIdSeed = OptionalLong.empty();
		cachedWorldKey = null;
	}

	/**
	 * Deletes what was saved for the singleplayer save folder {@code levelId}, so a new world later
	 * created under the same name starts empty instead of inheriting the old one's data.
	 */
	public void forgetSingleplayerWorld(String levelId) {
		String worldId = "sp_" + levelId;
		for (Path file : List.of(fileFor(worldId), legacyFileFor(worldId))) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException e) {
				// The world is already gone; a leftover file only means stale data may reappear.
				logger.warn("Failed to delete {} for deleted world {}", file, levelId, e);
			}
		}
	}

	/** The live, mutable entry list - callers add/remove directly, then call {@link #save()}. */
	public List<T> entries() {
		return entries;
	}

	public void save() {
		if (loadedWorldId == null) {
			return;
		}
		Path file = fileFor(loadedWorldId);
		try {
			SafeFiles.writeAtomically(file, writer -> GSON.toJson(entries, listType, writer));
		} catch (IOException e) {
			// Best-effort - losing the ability to persist shouldn't crash the game, and there's
			// nowhere better than the log to report a disk-write failure to.
			logger.warn("Failed to save {} to {}", subdirectory, file, e);
		}
	}

	private WorldKey worldKey(Minecraft client) {
		Object source = client.isLocalServer() ? client.getSingleplayerServer() : client.getCurrentServer();
		OptionalLong seed = ServerWorldSeed.current();
		if (cachedWorldKey != null && source == lastWorldIdSource && seed.equals(lastWorldIdSeed)) {
			return cachedWorldKey;
		}
		lastWorldIdSource = source;
		lastWorldIdSeed = seed;
		cachedWorldKey = computeWorldKey(client, seed);
		return cachedWorldKey;
	}

	/**
	 * A server world's id: the address, plus the seed the server sent when there is one. Before
	 * the first login or respawn packet there is no seed, and the id is the bare address.
	 */
	static String serverWorldId(String address, OptionalLong seed) {
		String base = "mp_" + address;
		return seed.isPresent() ? base + "_" + Long.toHexString(seed.getAsLong()) : base;
	}

	private static WorldKey computeWorldKey(Minecraft client, OptionalLong seed) {
		if (client.isLocalServer() && client.getSingleplayerServer() != null) {
			// LevelResource.ROOT's id is literally "." - getWorldPath resolves to
			// ".../saves/<world>/.", and Path#resolve doesn't normalize away that trailing
			// segment, so getFileName() would return "." for every world without this.
			// normalize() first so it actually returns the save folder name.
			Path worldPath = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize();
			return new WorldKey("sp_" + worldPath.getFileName(), null);
		}
		ServerData server = client.getCurrentServer();
		String address = server != null ? server.ip : "direct_connect";
		return new WorldKey(serverWorldId(address, seed), serverWorldId(address, OptionalLong.empty()));
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
	private List<T> load(WorldKey key) {
		String worldId = key.id();
		Path file = fileFor(worldId);
		if (Files.exists(file)) {
			return read(file);
		}
		Path legacy = findLegacyFile(key);
		if (legacy == null) {
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

	/**
	 * The older file a world with no file of its own yet should adopt, or null. A server world
	 * adopts what was filed under its bare address, which could have been any of that address's
	 * worlds; as with a name collision there is no telling which, so the first one joined takes
	 * it and the rest start clean.
	 */
	private Path findLegacyFile(WorldKey key) {
		String older = key.addressOnlyId() != null && !key.addressOnlyId().equals(key.id()) ? key.addressOnlyId() : null;
		List<Path> candidates = older != null
				? List.of(fileFor(older), legacyFileFor(older))
				: List.of(legacyFileFor(key.id()));
		for (Path candidate : candidates) {
			if (Files.exists(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private List<T> read(Path file) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			List<T> loaded = GSON.fromJson(reader, listType);
			return loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
		} catch (IOException | JsonParseException e) {
			logger.warn("Failed to load {} from {}", subdirectory, file, e);
			// Starting this world empty is unavoidable, but the next marker placed would otherwise
			// save straight over the only copy of everything that was in the file.
			SafeFiles.preserveUnreadable(file, logger);
			return new ArrayList<>();
		}
	}
}
