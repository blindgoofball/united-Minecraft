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
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent, player-placed waypoints: U opens a name prompt and places one at your
 * current position, then the Scanner's Markers category cycles through every marker in
 * your current dimension - distance doesn't gate which ones show up there, unlike every
 * other category, since the whole point is reaching something you already know is far
 * away. Targeting one just reuses the Scanner's usual "aim at it" / "Shift = walk there".
 *
 * <p>Saved to disk per-world, entirely client-side - nothing here needs the server's
 * cooperation or even a compatible mod on the other end. A world is identified by its
 * singleplayer save folder name, or by a multiplayer server's saved address (falling back
 * to a shared bucket for direct-connects that were never added to the server list, since
 * there's no reliable identity to key off of there). Reloaded automatically whenever the
 * detected world changes, including switching worlds without restarting the game.
 */
public final class MapMarkerController {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/markers");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MARKER_LIST_TYPE = new TypeToken<List<MapMarker>>() {
	}.getType();

	private static List<MapMarker> markers = new ArrayList<>();
	private static String loadedWorldId;

	private MapMarkerController() {
	}

	public record MapMarker(String name, String dimension, int x, int y, int z) {
		BlockPos pos() {
			return new BlockPos(x, y, z);
		}
	}

	public static void tick(Minecraft client) {
		String worldId = worldId(client);
		if (!Objects.equals(worldId, loadedWorldId)) {
			loadedWorldId = worldId;
			markers = load(worldId);
		}
	}

	public static void reset() {
		markers = new ArrayList<>();
		loadedWorldId = null;
	}

	/** All markers placed in {@code dimension}, oldest first - deliberately not distance-sorted or range-filtered. */
	public static List<MapMarker> inDimension(ResourceKey<Level> dimension) {
		String key = dimension.identifier().toString();
		List<MapMarker> result = new ArrayList<>();
		for (MapMarker marker : markers) {
			if (marker.dimension().equals(key)) {
				result.add(marker);
			}
		}
		return result;
	}

	public static MapMarker findAt(ResourceKey<Level> dimension, BlockPos pos) {
		String key = dimension.identifier().toString();
		for (MapMarker marker : markers) {
			if (marker.dimension().equals(key) && marker.pos().equals(pos)) {
				return marker;
			}
		}
		return null;
	}

	public static void openNameScreen(Minecraft client, LocalPlayer player) {
		BlockPos pos = player.blockPosition();
		String dimension = player.level().dimension().identifier().toString();
		client.gui.setScreen(new MarkerNameScreen(name -> addMarker(client, name, dimension, pos)));
	}

	private static void addMarker(Minecraft client, String name, String dimension, BlockPos pos) {
		String finalName = name == null || name.isBlank() ? "Marker " + (markers.size() + 1) : name.trim();
		markers.add(new MapMarker(finalName, dimension, pos.getX(), pos.getY(), pos.getZ()));
		save();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.marker_placed", finalName));
	}

	public static void remove(Minecraft client, MapMarker marker) {
		if (markers.remove(marker)) {
			save();
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.marker_removed", marker.name()));
		}
	}

	private static String worldId(Minecraft client) {
		if (client.isLocalServer() && client.getSingleplayerServer() != null) {
			return "sp_" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).getFileName();
		}
		ServerData server = client.getCurrentServer();
		return "mp_" + (server != null ? server.ip : "direct_connect");
	}

	private static Path fileFor(String worldId) {
		String safeName = worldId.replaceAll("[^a-zA-Z0-9_-]", "_");
		return FabricLoader.getInstance().getConfigDir()
				.resolve("united_minecraft").resolve("markers").resolve(safeName + ".json");
	}

	private static List<MapMarker> load(String worldId) {
		Path file = fileFor(worldId);
		if (!Files.exists(file)) {
			return new ArrayList<>();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			List<MapMarker> loaded = GSON.fromJson(reader, MARKER_LIST_TYPE);
			return loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to load map markers from {}", file, e);
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
				GSON.toJson(markers, MARKER_LIST_TYPE, writer);
			}
		} catch (IOException e) {
			// Best-effort - losing the ability to persist shouldn't crash the game, and there's
			// nowhere better than the log to report a disk-write failure to.
			LOGGER.warn("Failed to save map markers to {}", file, e);
		}
	}
}
