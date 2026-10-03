package com.nibblenerds.unitedminecraft.client;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.reflect.TypeToken;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent, player-assigned names for individual blocks (a specific door, a specific
 * chest) - the Scanner's Name Item key opens a prompt to set or clear one, and once named,
 * that name replaces the block's ordinary derived name everywhere the Scanner narrates it,
 * the same way a Map Marker's name does - see {@link ScannerController#itemName}, which
 * applies it for every block-based category. Saved to disk per-world via {@link
 * WorldScopedStore}, shared with {@link MapMarkerController}.
 */
public final class NamedBlockController {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/named_blocks");
	private static final Type NAMED_BLOCK_LIST_TYPE = new TypeToken<List<NamedBlock>>() {
	}.getType();

	private static final WorldScopedStore<NamedBlock> STORE =
			new WorldScopedStore<>("named_blocks", NAMED_BLOCK_LIST_TYPE, LOGGER);

	// Mirrors the store's entries, keyed for O(1) lookup - findAt is called once per narrated
	// Scanner item, so a linear scan (plus a fresh BlockPos allocation per comparison) added up.
	private static Map<DimPos, NamedBlock> namedBlocksByPos = new HashMap<>();

	private NamedBlockController() {
	}

	// marker: also listed in the Scanner's Markers category. Absent from worlds saved before this
	// existed, where Gson fills in false.
	private record NamedBlock(String name, String dimension, int x, int y, int z, boolean marker) {
		BlockPos pos() {
			return new BlockPos(x, y, z);
		}
	}

	/** A named block flagged to appear under Markers. */
	public record MarkedBlock(String name, BlockPos pos) {
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
		namedBlocksByPos = new HashMap<>();
	}

	/** The custom name assigned to the block at {@code pos} in {@code dimension}, or null if it has none. */
	public static String findAt(ResourceKey<Level> dimension, BlockPos pos) {
		NamedBlock named = namedBlocksByPos.get(new DimPos(dimension.identifier().toString(), pos));
		return named == null ? null : named.name();
	}

	/** Whether the named block at {@code pos} is flagged to appear under Markers. */
	public static boolean isMarker(ResourceKey<Level> dimension, BlockPos pos) {
		NamedBlock named = namedBlocksByPos.get(new DimPos(dimension.identifier().toString(), pos));
		return named != null && named.marker();
	}

	/** Every block in {@code dimension} flagged to appear under Markers, in no particular order. */
	public static List<MarkedBlock> markedBlocksIn(ResourceKey<Level> dimension) {
		String key = dimension.identifier().toString();
		List<MarkedBlock> result = new ArrayList<>();
		for (NamedBlock named : STORE.entries()) {
			if (named.marker() && named.dimension().equals(key)) {
				result.add(new MarkedBlock(named.name(), named.pos()));
			}
		}
		return result;
	}

	/** Takes a block back out of Markers but keeps its name - the Delete key in the Markers category. */
	public static void unmark(Minecraft client, ResourceKey<Level> dimension, BlockPos pos) {
		String key = dimension.identifier().toString();
		List<NamedBlock> entries = STORE.entries();
		for (int i = 0; i < entries.size(); i++) {
			NamedBlock named = entries.get(i);
			if (named.marker() && named.dimension().equals(key) && named.pos().equals(pos)) {
				entries.set(i, new NamedBlock(named.name(), named.dimension(), named.x(), named.y(), named.z(), false));
				STORE.save();
				rebuildIndex();
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.marker_removed", named.name()));
				return;
			}
		}
	}

	private static void rebuildIndex() {
		Map<DimPos, NamedBlock> index = new HashMap<>();
		for (NamedBlock named : STORE.entries()) {
			index.put(new DimPos(named.dimension(), named.pos()), named);
		}
		namedBlocksByPos = index;
	}

	/** {@code afterNamed} runs once the name is actually saved (not on cancel) - lets callers refresh anything depending on it. */
	public static void openNameScreen(Minecraft client, ResourceKey<Level> dimension, BlockPos pos, String currentName, Runnable afterNamed) {
		boolean currentlyMarker = isMarker(dimension, pos);
		String initialValue = currentName == null ? "" : currentName;
		client.gui.setScreen(new MarkerNameScreen(
				Component.translatable("united_minecraft.named_block_screen.title"),
				Component.translatable("united_minecraft.narrate.named_block_prompt"),
				Component.translatable("united_minecraft.narrate.named_block_cancelled"),
				Component.translatable("united_minecraft.named_block_screen.name"),
				initialValue,
				Component.translatable("united_minecraft.named_block_screen.marker"),
				currentlyMarker,
				(name, marker) -> {
					setName(client, dimension, pos, name, marker);
					afterNamed.run();
				}));
	}

	private static void setName(Minecraft client, ResourceKey<Level> dimension, BlockPos pos, String name, boolean marker) {
		String key = dimension.identifier().toString();
		STORE.entries().removeIf(named -> named.dimension().equals(key) && named.pos().equals(pos));
		if (name == null || name.isBlank()) {
			STORE.save();
			rebuildIndex();
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.named_block_cleared"));
			return;
		}
		String finalName = name.trim();
		STORE.entries().add(new NamedBlock(finalName, key, pos.getX(), pos.getY(), pos.getZ(), marker));
		STORE.save();
		rebuildIndex();
		client.getNarrator().saySystemNow(Component.translatable(
				marker ? "united_minecraft.narrate.named_block_set_marker" : "united_minecraft.narrate.named_block_set", finalName));
	}
}
