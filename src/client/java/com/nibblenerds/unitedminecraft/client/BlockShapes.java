package com.nibblenerds.unitedminecraft.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

	/**
	 * A point centred on {@code shape}'s own face in direction {@code face} - unlike {@link
	 * #centreOf}, which sits in the middle of the shape's full bounds on all three axes, this
	 * one is pinned to the boundary plane on {@code face}'s axis (still centred within the
	 * shape's footprint on the other two, so a thin or off-centre shape - a fence post, a wall -
	 * still lands on real geometry rather than empty air past its edge).
	 *
	 * <p>{@link BuildModeController#bucketAimTarget} needs this distinction specifically: aiming
	 * at a neighbor's overall centre instead of the exact shared face can make the real vanilla
	 * raycast enter that neighbor's shape through a different face than the one actually bordering
	 * the cursor (most likely for a full cube approached from an angle, or any shape where the
	 * line from the player's eye to the geometric centre doesn't happen to cross the intended
	 * face first) - reporting a hit direction that points the resulting placement at a completely
	 * different cell than the cursor. Pinning the target to the correct face's own plane removes
	 * that ambiguity: the ray can only ever cross into the shape through that face.
	 */
	static Vec3 centreOfFace(VoxelShape shape, BlockPos pos, Direction face) {
		if (shape.isEmpty()) {
			return Vec3.atCenterOf(pos);
		}
		AABB bounds = shape.bounds();
		double x = (bounds.minX + bounds.maxX) / 2.0;
		double y = (bounds.minY + bounds.maxY) / 2.0;
		double z = (bounds.minZ + bounds.maxZ) / 2.0;
		boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
		switch (face.getAxis()) {
			case X -> x = positive ? bounds.maxX : bounds.minX;
			case Y -> y = positive ? bounds.maxY : bounds.minY;
			case Z -> z = positive ? bounds.maxZ : bounds.minZ;
		}
		return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
	}
}
