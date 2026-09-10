package com.nibblenerds.unitedminecraft.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared geometry helpers for reasoning about a block's real shape rather than its whole cell. */
final class BlockShapes {
	private BlockShapes() {
	}

	/**
	 * The centre of {@code shape}'s bounds in world space, or the plain cell centre when the
	 * block has no shape at all.
	 *
	 * <p>For a full block those are the same point, but a thin or off-centre one occupies only a
	 * slice of its cell, and that slice moves as the block's state changes. Both callers depend
	 * on hitting the real geometry: {@link ScannerController#interactionPoint} aims the player at
	 * something they can actually interact with (re-targeting an open door to close it used to
	 * need a manual nudge, because the cube centre landed outside the swung-open shape), and
	 * {@link BuildModeController#pointOnShape} aims a bucket's own raycast, which otherwise sails
	 * straight past a sign or fence post to whatever solid surface lies further along the line.
	 */
	static Vec3 centreOf(VoxelShape shape, BlockPos pos) {
		if (shape.isEmpty()) {
			return Vec3.atCenterOf(pos);
		}
		AABB bounds = shape.bounds();
		return new Vec3(
				pos.getX() + (bounds.minX + bounds.maxX) / 2.0,
				pos.getY() + (bounds.minY + bounds.maxY) / 2.0,
				pos.getZ() + (bounds.minZ + bounds.maxZ) / 2.0);
	}
}
