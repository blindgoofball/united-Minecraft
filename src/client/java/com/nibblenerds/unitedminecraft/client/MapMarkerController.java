package com.nibblenerds.unitedminecraft.client;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent, player-placed waypoints: U opens a name prompt and places one at your
 * current position, then the Scanner's Markers category cycles through every marker in
 * your current dimension - distance doesn't gate which ones show up there, unlike every
 * other category, since the whole point is reaching something you already know is far
 * away. Targeting one just reuses the Scanner's usual "aim at it" / "Shift = walk there".
 *
 * <p>Saved to disk per-world via {@link WorldScopedStore} (shared with {@link
 * NamedBlockController}), entirely client-side - nothing here needs the server's cooperation
 * or even a compatible mod on the other end. See that class for how a world is identified and
 * when its data is reloaded.
 */
public final class MapMarkerController {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/markers");
	private static final Type MARKER_LIST_TYPE = new TypeToken<List<MapMarker>>() {
	}.getType();

	private static final WorldScopedStore<MapMarker> STORE =
			new WorldScopedStore<>("markers", MARKER_LIST_TYPE, LOGGER);

	// Mirrors the store's entries, keyed for O(1) lookup - see NamedBlockController's identical
	// index for why a linear scan (plus a fresh BlockPos allocation per comparison) doesn't
	// scale here.
	private static Map<DimPos, MapMarker> markersByPos = new HashMap<>();

	private MapMarkerController() {
	}

	public record MapMarker(String name, String dimension, int x, int y, int z) {
		BlockPos pos() {
			return new BlockPos(x, y, z);
		}
	}

	private record DimPos(String dimension, BlockPos pos) {
	}

	public static void tick(Minecraft client) {
		if (STORE.tick(client)) {
			rebuildIndex();
		}
	}

	public static void reset() {
		STORE.reset();
		markersByPos = new HashMap<>();
	}

	/** All markers placed in {@code dimension}, oldest first - deliberately not distance-sorted or range-filtered. */
	public static List<MapMarker> inDimension(ResourceKey<Level> dimension) {
		String key = dimension.identifier().toString();
		List<MapMarker> result = new ArrayList<>();
		for (MapMarker marker : STORE.entries()) {
			if (marker.dimension().equals(key)) {
				result.add(marker);
			}
		}
		return result;
	}

	public static MapMarker findAt(ResourceKey<Level> dimension, BlockPos pos) {
		return markersByPos.get(new DimPos(dimension.identifier().toString(), pos));
	}

	private static void rebuildIndex() {
		Map<DimPos, MapMarker> index = new HashMap<>();
		for (MapMarker marker : STORE.entries()) {
			index.put(new DimPos(marker.dimension(), marker.pos()), marker);
		}
		markersByPos = index;
	}

	public static void openNameScreen(Minecraft client, LocalPlayer player) {
		BlockPos pos = player.blockPosition();
		String dimension = player.level().dimension().identifier().toString();
		client.gui.setScreen(new MarkerNameScreen(name -> addMarker(client, name, dimension, pos)));
	}

	private static void addMarker(Minecraft client, String name, String dimension, BlockPos pos) {
		// The marker's own name field is a plain String (it's saved to disk and narrated
		// verbatim elsewhere), so the default name is resolved through the translation key
		// once, right here, rather than storing a raw English literal.
		String finalName = name == null || name.isBlank()
				? Component.translatable("united_minecraft.narrate.marker_default_name", STORE.entries().size() + 1).getString()
				: name.trim();
		STORE.entries().add(new MapMarker(finalName, dimension, pos.getX(), pos.getY(), pos.getZ()));
		STORE.save();
		rebuildIndex();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.marker_placed", finalName));
	}

	public static void remove(Minecraft client, MapMarker marker) {
		if (STORE.entries().remove(marker)) {
			STORE.save();
			rebuildIndex();
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.marker_removed", marker.name()));
		}
	}
}
