package com.nibblenerds.unitedminecraft.client;

/**
 * Where a block column sits within its 16x16 chunk, as the chunk's own coordinates plus how many
 * blocks separate the column from each chunk border. A distance of 0 means the column is the
 * outermost one on that side, i.e. standing right against the border.
 *
 * <p>Kept free of Minecraft types so the math (notably for negative coordinates) can be unit tested.
 */
record ChunkPosition(int chunkX, int chunkZ, int west, int east, int north, int south) {
	static ChunkPosition of(int blockX, int blockZ) {
		int localX = blockX & 15;
		int localZ = blockZ & 15;
		return new ChunkPosition(blockX >> 4, blockZ >> 4, localX, 15 - localX, localZ, 15 - localZ);
	}
}
