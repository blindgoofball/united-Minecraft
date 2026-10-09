package com.nibblenerds.unitedminecraft.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * The node evaluator behind {@link ClientPathfinding}: vanilla's {@link WalkNodeEvaluator} with
 * two corrections for client-side Auto-Walk.
 *
 * <p><b>Open trapdoors.</b> {@code WalkNodeEvaluator#getPathTypeFromState} classifies any
 * trapdoor as {@link PathType#TRAPDOOR} purely from its block tag, without ever checking whether
 * it's actually open. So the cell directly above an <em>open</em> trapdoor - which is really just
 * a hole, exactly like standing over any other missing floor block - gets classified {@link
 * PathType#ON_TOP_OF_TRAPDOOR} at zero movement cost, indistinguishable from solid ground. Every
 * vanilla mob shares this same blind spot (it's baked into the node evaluator, not a per-species
 * trait), so it isn't fixed by swapping which mob type backs the pathfinding ghost {@link
 * ClientPathfinding} builds - only fixing the node evaluator itself does.
 *
 * <p>Reclassifies only that one specific case - an open trapdoor immediately below an
 * otherwise-open cell - as {@link PathType#OPEN}, the exact same result vanilla's own logic
 * already produces whenever it finds a below-cell that's genuinely empty (see the {@code
 * case OPEN, WATER, LAVA, WALKABLE -> PathType.OPEN} branch of {@code getPathTypeStatic}). The
 * pathfinder then treats an open trapdoor as the hole it actually is. A closed trapdoor is
 * untouched and still classifies (and walks) exactly as vanilla always has.
 *
 * <p><b>Ladders and vines.</b> Vanilla mobs never climb: a ladder has no collision, so to
 * {@code WalkNodeEvaluator} a ladder shaft is an empty drop, and the cells in it are
 * {@link PathType#OPEN} that the pathfinder falls straight through. Here a climbable cell (see
 * {@link #isClimbable}) is instead standable ground ({@link PathType#WALKABLE}) whose floor is
 * its own feet level, and a node in one gets an extra edge to the climbable cell directly above
 * and below it. Everything else falls out of vanilla's existing logic: a ladder cell is entered
 * from an adjacent floor like any other cell, stepped off onto a ledge with the ordinary
 * one-block step-up, and entered from the top by the ordinary short fall onto the first
 * non-open cell below. {@link AutoWalkController} is what actually drives the climb.
 */
final class AutoWalkNodeEvaluator extends WalkNodeEvaluator {
	/**
	 * With {@code swim}, water is crossed at the surface (vanilla's "can float" mode, the one boats'
	 * passengers and swimming mobs use) instead of by walking along the bottom, which is what
	 * vanilla's land pathfinding does by default and which is no use to a player who can't
	 * actually walk the bed of deep water. {@link ClientPathfinding} only uses such a route when it
	 * reaches the target, so the player never swims somewhere they can't get out of.
	 */
	AutoWalkNodeEvaluator(boolean swim) {
		setCanFloat(swim);
	}

	/** Whether a block state is something Auto-Walk can climb. Scaffolding is left out: it's solid from the side and is climbed differently. */
	static boolean isClimbable(BlockState state) {
		return state.is(BlockTags.CLIMBABLE)
				&& !state.is(Blocks.SCAFFOLDING)
				&& !state.getFluidState().is(FluidTags.LAVA);
	}

	@Override
	public Node getStart() {
		// Standing in a ladder, vanilla's start-finding assumes a mob that isn't on the ground is
		// falling and walks down to whatever solid block is below - the foot of the shaft, not here.
		BlockPos feet = this.mob.blockPosition();
		if (isClimbable(this.currentContext.getBlockState(feet))) {
			return this.getStartNode(feet);
		}
		return super.getStart();
	}

	@Override
	public int getNeighbors(Node[] neighbors, Node pos) {
		int count = super.getNeighbors(neighbors, pos);
		if (!isClimbable(this.currentContext.getBlockState(new BlockPos(pos.x, pos.y, pos.z)))) {
			return count;
		}
		for (int dy = -1; dy <= 1; dy += 2) {
			int y = pos.y + dy;
			if (!isClimbable(this.currentContext.getBlockState(new BlockPos(pos.x, y, pos.z)))) {
				continue;
			}
			// The player's whole body has to fit, not just the feet cell (e.g. a block above the
			// next ladder cell).
			PathType type = this.getCachedPathType(pos.x, y, pos.z);
			if (this.mob.getPathfindingMalus(type) < 0.0F) {
				continue;
			}
			Node node = this.getNode(pos.x, y, pos.z);
			if (!node.closed) {
				node.type = type;
				neighbors[count++] = node;
			}
		}
		return count;
	}

	@Override
	protected double getFloorLevel(BlockPos pos) {
		if (isClimbable(this.currentContext.getBlockState(pos))) {
			return pos.getY();
		}
		return super.getFloorLevel(pos);
	}

	@Override
	public PathType getPathType(PathfindingContext context, int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		if (isClimbable(context.getBlockState(pos))) {
			return PathType.WALKABLE;
		}
		if (y >= context.level().getMinY() + 1
				&& getPathTypeFromState(context.level(), pos) == PathType.OPEN
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
