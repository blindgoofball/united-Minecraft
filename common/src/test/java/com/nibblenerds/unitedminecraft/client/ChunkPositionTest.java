package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChunkPositionTest {
	@Test
	void originIsTheNorthWestCornerOfChunkZeroZero() {
		assertEquals(new ChunkPosition(0, 0, 0, 15, 0, 15), ChunkPosition.of(0, 0));
	}

	@Test
	void positiveCoordinatesCountFromTheNorthWestCorner() {
		assertEquals(new ChunkPosition(1, 2, 3, 12, 14, 1), ChunkPosition.of(19, 46));
	}

	@Test
	void negativeCoordinatesFloorIntoTheChunkBelowZero() {
		assertEquals(new ChunkPosition(-1, -1, 15, 0, 15, 0), ChunkPosition.of(-1, -1));
		assertEquals(new ChunkPosition(-1, -1, 0, 15, 0, 15), ChunkPosition.of(-16, -16));
		assertEquals(new ChunkPosition(-2, -2, 15, 0, 15, 0), ChunkPosition.of(-17, -17));
	}
}
