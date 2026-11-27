package com.nibblenerds.unitedminecraft.client;

import net.minecraft.core.BlockPos;

/**
 * The box between two corner markers that Build Mode's cursor is locked to - see {@link
 * BuildModeController#placeAreaMarker}. Both corners are inside it, whichever way round they were
 * placed. Kept apart from the controller so the bounds can be unit tested without the game.
 */
record BuildArea(BlockPos a, BlockPos b) {
	/** Whether {@code pos} is inside; height only counts when {@code lockHeight} is set. */
	boolean contains(BlockPos pos, boolean lockHeight) {
		return pos.getX() >= Math.min(a.getX(), b.getX()) && pos.getX() <= Math.max(a.getX(), b.getX())
				&& pos.getZ() >= Math.min(a.getZ(), b.getZ()) && pos.getZ() <= Math.max(a.getZ(), b.getZ())
				&& (!lockHeight || pos.getY() >= Math.min(a.getY(), b.getY()) && pos.getY() <= Math.max(a.getY(), b.getY()));
	}

	/** Blocks along X, counting both corners. */
	int width() {
		return Math.abs(a.getX() - b.getX()) + 1;
	}

	/** Blocks along Z, counting both corners. */
	int depth() {
		return Math.abs(a.getZ() - b.getZ()) + 1;
	}

	/** Blocks along Y, counting both corners. */
	int height() {
		return Math.abs(a.getY() - b.getY()) + 1;
	}
}
