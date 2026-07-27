package com.nibblenerds.unitedminecraft.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared "is this a valuable ore, and can you actually see it" logic for both the mining
 * radar and the scanner's Ores category - deliberately not full x-ray: a block only counts
 * once it borders air or a fluid on at least one face, i.e. it's been physically exposed by
 * mining (or was already exposed naturally, in a cave), the same way a sighted player would
 * only notice it once there's a gap to see it through. Ore still fully entombed in solid
 * rock on every side is invisible to both features, same as it would be to anyone else.
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

	static boolean isExposed(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos neighborPos = pos.relative(direction);
			BlockState neighborState = level.getBlockState(neighborPos);
			if (neighborState.isAir() || !neighborState.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
