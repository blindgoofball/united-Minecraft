package com.nibblenerds.unitedminecraft.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent, player-assigned names for individual blocks (a specific door, a specific
 * chest) - the Scanner's Name Item key opens a prompt to set or clear one, and once named,
 * that name replaces the block's ordinary derived name everywhere the Scanner narrates it,
 * the same way a Map Marker's name does. Saved to disk per-world, entirely client-side -
 * see {@link MapMarkerController}, whose persistence this mirrors exactly.
 */
public final class NamedBlockController {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/named_blocks");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type NAMED_BLOCK_LIST_TYPE = new TypeToken<List<NamedBlock>>() {
	}.getType();

	private static List<NamedBlock> namedBlocks = new ArrayList<>();
	// Mirrors namedBlocks, keyed for O(1) lookup - findAt is called once per matched block while
	// scanning a Scanner category, so a linear scan (plus a fresh BlockPos allocation per
	// comparison) there added up fast for a category with many results.
	private static Map<DimPos, String> namedBlocksByPos = new HashMap<>();
	private static String loadedWorldId;

	private NamedBlockController() {
	}

	private record NamedBlock(String name, String dimension, int x, int y, int z) {
		BlockPos pos() {
			return new BlockPos(x, y, z);
		}
	}

	private record DimPos(String dimension, BlockPos pos) {
	}

	public static void tick(Minecraft client) {
		String worldId = worldId(client);
		if (!Objects.equals(worldId, loadedWorldId)) {
			loadedWorldId = worldId;
			namedBlocks = load(worldId);
			rebuildIndex();
		}
	}

	public static void reset() {
		namedBlocks = new ArrayList<>();
		namedBlocksByPos = new HashMap<>();
		loadedWorldId = null;
		lastWorldIdSource = null;
		cachedWorldId = null;
	}

	/** The custom name assigned to the block at {@code pos} in {@code dimension}, or null if it has none. */
	public static String findAt(ResourceKey<Level> dimension, BlockPos pos) {
		return namedBlocksByPos.get(new DimPos(dimension.identifier().toString(), pos));
	}

	private static void rebuildIndex() {
		Map<DimPos, String> index = new HashMap<>();
		for (NamedBlock named : namedBlocks) {
			index.put(new DimPos(named.dimension(), named.pos()), named.name());
		}
		namedBlocksByPos = index;
	}

	/** {@code afterNamed} runs once the name is actually saved (not on cancel) - lets callers refresh anything depending on it. */
	public static void openNameScreen(Minecraft client, ResourceKey<Level> dimension, BlockPos pos, String currentName, Runnable afterNamed) {
		String initialValue = currentName == null ? "" : currentName;
		client.gui.setScreen(new MarkerNameScreen(
				Component.translatable("united_minecraft.named_block_screen.title"),
				Component.translatable("united_minecraft.narrate.named_block_prompt"),
				Component.translatable("united_minecraft.narrate.named_block_cancelled"),
				Component.translatable("united_minecraft.named_block_screen.name"),
				initialValue,
				name -> {
					setName(client, dimension, pos, name);
					afterNamed.run();
				}));
	}

	private static void setName(Minecraft client, ResourceKey<Level> dimension, BlockPos pos, String name) {
		String key = dimension.identifier().toString();
		namedBlocks.removeIf(named -> named.dimension().equals(key) && named.pos().equals(pos));
		if (name == null || name.isBlank()) {
			save();
			rebuildIndex();
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.named_block_cleared"));
			return;
		}
		String finalName = name.trim();
		namedBlocks.add(new NamedBlock(finalName, key, pos.getX(), pos.getY(), pos.getZ()));
		save();
		rebuildIndex();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.named_block_set", finalName));
	}

	// Caches worldId() against the server/singleplayer-server object identity it was computed
	// from - tick() calls worldId() every client tick, but that identity only actually changes
	// once per world join/leave, so recomputing the Path#normalize()/getFileName() (or the
	// ServerData lookup) 20 times a second for an answer that hasn't changed is pure waste.
	private static Object lastWorldIdSource;
	private static String cachedWorldId;

	private static String worldId(Minecraft client) {
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
			Path worldPath = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize();
			return "sp_" + worldPath.getFileName();
		}
		ServerData server = client.getCurrentServer();
		return "mp_" + (server != null ? server.ip : "direct_connect");
	}

	private static Path fileFor(String worldId) {
		String safeName = worldId.replaceAll("[^a-zA-Z0-9_-]", "_");
		return FabricLoader.getInstance().getConfigDir()
				.resolve("united_minecraft").resolve("named_blocks").resolve(safeName + ".json");
	}

	private static List<NamedBlock> load(String worldId) {
		Path file = fileFor(worldId);
		if (!Files.exists(file)) {
			return new ArrayList<>();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			List<NamedBlock> loaded = GSON.fromJson(reader, NAMED_BLOCK_LIST_TYPE);
			return loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to load named blocks from {}", file, e);
			return new ArrayList<>();
		}
	}

	private static void save() {
		if (loadedWorldId == null) {
			return;
		}
		Path file = fileFor(loadedWorldId);
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(namedBlocks, NAMED_BLOCK_LIST_TYPE, writer);
			}
		} catch (IOException e) {
			// Best-effort - losing the ability to persist shouldn't crash the game, and there's
			// nowhere better than the log to report a disk-write failure to.
			LOGGER.warn("Failed to save named blocks to {}", file, e);
		}
	}
}
