package com.nibblenerds.unitedminecraft.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Caches the last chunk this touched, for code that resolves chunks by coordinate repeatedly
 * in a short burst - {@link Level#getChunk(int, int)} re-resolves from the chunk map on every
 * single call, which adds up fast across thousands of calls in one scanner scan. The single-
 * position {@link #getBlockState} built on top of it is for scattered lookups that don't
 * already know which chunk/section they're in (e.g. {@link OreDetection#isExposed} checking a
 * block's six neighbors); {@link #chunkAt} alone is for callers like {@link
 * ScannerController#forEachBlockInRange} that already iterate chunk-by-chunk and just want to
 * share this same resolution step instead of reimplementing it. Not thread-safe; only ever used
 * from the render thread.
 */
final class FastBlockAccess {
	private final Level level;

	private int cachedChunkX = Integer.MIN_VALUE;
	private int cachedChunkZ = Integer.MIN_VALUE;
	private ChunkAccess cachedChunk;

	FastBlockAccess(Level level) {
		this.level = level;
	}

	/** The chunk at ({@code chunkX}, {@code chunkZ}) in chunk coordinates, or {@code null} if it isn't currently loaded. */
	ChunkAccess chunkAt(int chunkX, int chunkZ) {
		if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
			cachedChunk = level.hasChunk(chunkX, chunkZ) ? level.getChunk(chunkX, chunkZ) : null;
			cachedChunkX = chunkX;
			cachedChunkZ = chunkZ;
		}
		return cachedChunk;
	}

	BlockState getBlockState(BlockPos pos) {
		ChunkAccess chunk = chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
		if (chunk == null) {
			// Outside the client's loaded area - fall back to the level's own handling (vanilla
			// itself treats unloaded space as air for these purposes) rather than caching
			// nothing as though it were a real chunk.
			return level.getBlockState(pos);
		}
		LevelChunkSection[] sections = chunk.getSections();
		int sectionIndex = chunk.getSectionIndex(pos.getY());
		if (sectionIndex < 0 || sectionIndex >= sections.length) {
			// Above/below the world's build height.
			return Blocks.AIR.defaultBlockState();
		}
		return sections[sectionIndex].getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
	}
}
