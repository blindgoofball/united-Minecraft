package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class AutoWalkDoorsTest {
	private static final BlockPos DOOR = new BlockPos(10, 64, 20);

	@Test
	void countsDistancePastTheDoorAlongTheWayWalked() {
		// Walking north (towards -Z): two blocks beyond the door's centre is two blocks past.
		assertEquals(2.0, AutoWalkDoors.distancePast(10.5, 18.5, DOOR, 0.0, -1.0), 1.0e-9);
		// Still on the near side: negative.
		assertTrue(AutoWalkDoors.distancePast(10.5, 22.5, DOOR, 0.0, -1.0) < 0.0);
	}

	@Test
	void sidewaysDriftDoesNotCountAsGettingPast() {
		assertEquals(0.0, AutoWalkDoors.distancePast(13.5, 20.5, DOOR, 0.0, -1.0), 1.0e-9);
	}

	@Test
	void theDoorIsClosedOnceTheWholeBodyIsClearOfIt() {
		// Standing in the doorway is not past it; a player's width (0.6) further on, still not; 1.3 blocks, yes.
		assertTrue(AutoWalkDoors.distancePast(10.5, 20.5, DOOR, 0.0, -1.0) < AutoWalkDoors.CLOSE_BEHIND_DISTANCE);
		assertTrue(AutoWalkDoors.distancePast(10.5, 19.9, DOOR, 0.0, -1.0) < AutoWalkDoors.CLOSE_BEHIND_DISTANCE);
		assertTrue(AutoWalkDoors.distancePast(10.5, 19.1, DOOR, 0.0, -1.0) >= AutoWalkDoors.CLOSE_BEHIND_DISTANCE);
	}
}
