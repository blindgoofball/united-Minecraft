package com.nibblenerds.unitedminecraft.client;

import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Shared "is this a valuable ore, and can you actually see it" logic for both the mining
 * radar and the scanner's Ores category - deliberately not full x-ray: a face bordering air
 * or a fluid isn't enough on its own, since that air might be a sealed-off cave pocket on the
 * far side of an unmined wall - it also has to be a face the player could actually see through
 * a real raycast, the same way vanilla's own block-interaction raycast works. Ore that's
 * either fully entombed or only exposed somewhere out of sight is invisible to both features,
 * same as it would be to anyone else.
 */
final class OreDetection {
	private OreDetection() {
	}

	static boolean isValuableOre(BlockState state) {
		return state.is(BlockItemTags.COAL_ORES.block())
				|| state.is(BlockTags.IRON_ORES)
				|| state.is(BlockTags.COPPER_ORES)
				|| state.is(BlockTags.GOLD_ORES)
				|| state.is(BlockItemTags.REDSTONE_ORES.block())
				|| state.is(BlockItemTags.LAPIS_ORES.block())
				|| state.is(BlockItemTags.DIAMOND_ORES.block())
				|| state.is(BlockItemTags.EMERALD_ORES.block())
				|| state.is(Blocks.NETHER_GOLD_ORE)
				|| state.is(Blocks.NETHER_QUARTZ_ORE)
				|| state.is(Blocks.ANCIENT_DEBRIS);
	}

	static boolean isExposed(Level level, BlockPos pos, Vec3 eye) {
		return isExposed(level, level::getBlockState, pos, eye);
	}

	/**
	 * Same as {@link #isExposed(Level, BlockPos, Vec3)}, but reading neighbor block states
	 * through {@code neighborLookup} instead of {@link Level#getBlockState} directly - a scanner
	 * scan can call this thousands of times in one burst (six neighbors each), and {@link
	 * FastBlockAccess} answers those without re-resolving the owning chunk from scratch on every
	 * single one the way {@code Level#getBlockState} does. {@code level} itself is still needed
	 * for the real raycast below, which has no such fast path.
	 */
	static boolean isExposed(Level level, Function<BlockPos, BlockState> neighborLookup, BlockPos pos, Vec3 eye) {
		Vec3 center = Vec3.atCenterOf(pos);
		FluidState fluidHere = neighborLookup.apply(pos).getFluidState();
		for (Direction direction : Direction.values()) {
			BlockPos neighborPos = pos.relative(direction);
			BlockState neighborState = neighborLookup.apply(neighborPos);
			// A torch, rail, slab, snow layer, or any other non-full-cube neighbor doesn't
			// actually seal the face the way a real solid block does - only a genuinely solid,
			// fully-opaque neighbor rules a face out here. isSolidRender() is the same check
			// vanilla's own renderer uses to decide whether an adjacent face needs culling, so
			// it's already exactly "does this neighbor visually block what's behind it". The
			// real raycast below (hasLineOfSight) still has the final say either way - this is
			// only a fast pre-filter, not the actual visibility test.
			if (neighborState.isSolidRender() && neighborState.getFluidState().isEmpty()) {
				continue;
			}
			// A neighbor filled with the exact same fluid isn't a real boundary either - it's
			// the interior of the same body, not a face anything could ever be seen through.
			// This only ever applies when pos itself is a fluid (ore is never one), so it can't
			// change ore visibility - but without it, a block buried in the middle of a large
			// lake or underground aquifer needed a real raycast in every direction that bordered
			// more of that same lake, just to conclude (correctly) that none of it was visible.
			if (!fluidHere.isEmpty() && neighborState.getFluidState().getType() == fluidHere.getType()) {
				continue;
			}
			Vec3 facePoint = center.add(
					direction.getStepX() * 0.5, direction.getStepY() * 0.5, direction.getStepZ() * 0.5);
			if (hasLineOfSight(level, eye, facePoint, pos)) {
				return true;
			}
		}
		return false;
	}

	/** True if nothing solid sits between {@code from} and {@code to} before reaching {@code target} itself. */
	private static boolean hasLineOfSight(Level level, Vec3 from, Vec3 to, BlockPos target) {
		BlockHitResult hit = level.clip(new ClipContext(
				from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
	}
}
