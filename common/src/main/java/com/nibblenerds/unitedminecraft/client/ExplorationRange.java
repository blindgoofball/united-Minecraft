package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;

/**
 * How far out biomes and structures are reported: the Scanner's Biomes and Structures category
 * and the Structure Voices announcements each have a setting for it, and both are held to the
 * most a sighted player could make anything out - never more than {@link #MAX} blocks, and never
 * further than the player's own render distance, since past that the world isn't drawn at all.
 *
 * <p>The render distance used is the effective one (the lower of the client's setting and the
 * server's view distance), which is what actually decides how many chunks are loaded around the
 * player.
 */
final class ExplorationRange {
	/** The furthest either setting can go - matches {@link com.nibblenerds.unitedminecraft.structure.StructureScanner#MAX_RANGE}. */
	static final double MAX = 128.0;
	static final double MIN = 16.0;
	static final double DEFAULT_SCANNER = 128.0;
	static final double DEFAULT_VOICES = 96.0;
	private static final int BLOCKS_PER_CHUNK = 16;

	private ExplorationRange() {
	}

	/** The Scanner category's reach right now: its setting, held to the render distance. */
	static double scanner() {
		return effective(UnitedMinecraftConfig.get().exploreScannerRange, renderDistanceChunks());
	}

	/** How far out structure voices announce right now: their setting, held to the render distance. */
	static double voices() {
		return effective(UnitedMinecraftConfig.get().structureVoiceRange, renderDistanceChunks());
	}

	/** {@code setting} limited to {@code renderChunks} chunks' worth of blocks. */
	static double effective(double setting, int renderChunks) {
		return Math.min(setting, (double) renderChunks * BLOCKS_PER_CHUNK);
	}

	private static int renderDistanceChunks() {
		return Minecraft.getInstance().options.getEffectiveRenderDistance();
	}
}
