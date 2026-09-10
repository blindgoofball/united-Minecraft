package com.nibblenerds.unitedminecraft.client;

import java.lang.reflect.Type;
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
	private static Map<DimPos, String> namedBlocksByPos = new HashMap<>();

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
		return namedBlocksByPos.get(new DimPos(dimension.identifier().toString(), pos));
	}

	private static void rebuildIndex() {
		Map<DimPos, String> index = new HashMap<>();
		for (NamedBlock named : STORE.entries()) {
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
		STORE.entries().removeIf(named -> named.dimension().equals(key) && named.pos().equals(pos));
		if (name == null || name.isBlank()) {
			STORE.save();
			rebuildIndex();
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.named_block_cleared"));
			return;
		}
		String finalName = name.trim();
		STORE.entries().add(new NamedBlock(finalName, key, pos.getX(), pos.getY(), pos.getZ()));
		STORE.save();
		rebuildIndex();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.named_block_set", finalName));
	}
}
