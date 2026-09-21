package com.nibblenerds.unitedminecraft.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Fixes a vanilla {@link WalkNodeEvaluator} quirk that's exactly as annoying for client-side
 * Auto-Walk as it is for a real zombie: {@code WalkNodeEvaluator#getPathTypeFromState}
 * classifies any trapdoor as {@link PathType#TRAPDOOR} purely from its block tag, without ever
 * checking whether it's actually open. So the cell directly above an <em>open</em> trapdoor -
 * which is really just a hole, exactly like standing over any other missing floor block - gets
 * classified {@link PathType#ON_TOP_OF_TRAPDOOR} at zero movement cost, indistinguishable from
 * solid ground. Every vanilla mob shares this same blind spot (it's baked into the node
 * evaluator, not a per-species trait), so it isn't fixed by swapping which mob type backs the
 * pathfinding ghost {@link ClientPathfinding} builds - only fixing the node evaluator itself
 * does, hence this class rather than a different {@code EntityType}.
 *
 * <p>Reclassifies only that one specific case - an open trapdoor immediately below an
 * otherwise-open cell - as {@link PathType#OPEN}, the exact same result vanilla's own logic
 * already produces whenever it finds a below-cell that's genuinely empty (see the {@code
 * case OPEN, WATER, LAVA, WALKABLE -> PathType.OPEN} branch of {@code getPathTypeStatic}). This
 * doesn't invent new pathfinding behavior, it just corrects which below-cells count as empty,
 * which in turn makes the pathfinder treat an open trapdoor as the hole it actually is - falling
 * through it if that's genuinely the only way down, or routing around it - instead of
 * confidently walking the player onto open air. A closed trapdoor is untouched and still
 * classifies (and walks) exactly as vanilla always has.
 */
final class TrapdoorAwareNodeEvaluator extends WalkNodeEvaluator {
	@Override
	public PathType getPathType(PathfindingContext context, int x, int y, int z) {
		if (y >= context.level().getMinY() + 1
				&& getPathTypeFromState(context.level(), new BlockPos(x, y, z)) == PathType.OPEN
				&& isOpenTrapdoor(context.getBlockState(new BlockPos(x, y - 1, z)))) {
			return PathType.OPEN;
		}
		return super.getPathType(context, x, y, z);
	}

	private static boolean isOpenTrapdoor(BlockState state) {
		return state.is(BlockTags.TRAPDOORS)
				&& state.hasProperty(BlockStateProperties.OPEN)
				&& state.getValue(BlockStateProperties.OPEN);
	}
}
