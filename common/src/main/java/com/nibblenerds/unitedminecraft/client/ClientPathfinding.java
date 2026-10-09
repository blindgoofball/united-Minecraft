package com.nibblenerds.unitedminecraft.client;

import java.util.Set;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * Shared "compute a walking path with vanilla's own mob pathfinding" helper, used by both
 * {@link AutoWalkController} (the scanner's "walk to it") and {@link MovementAssistController}
 * (checking whether an obstacle can be routed around). See {@link AutoWalkController}'s class
 * doc for why this needs a throwaway, never-spawned {@link Mob}, and {@link
 * AutoWalkNodeEvaluator}'s own doc for why the node evaluator isn't vanilla's plain {@code
 * WalkNodeEvaluator}.
 */
final class ClientPathfinding {
	private static final int SEARCH_MARGIN = 16;

	private ClientPathfinding() {
	}

	static Path computePath(LocalPlayer player, BlockPos target, float maxPathLength, int reachRange) {
		return search(player, target, maxPathLength, reachRange, false);
	}

	/**
	 * Same as {@link #computePath(LocalPlayer, BlockPos, float, int)}, but with {@code
	 * allowSwimming} the route may cross water at the surface - only when that route actually
	 * reaches the target. Vanilla hands back a best-effort partial path when the target can't be
	 * reached, and for a swimming route that partial path would end in the middle of the water,
	 * stranding the player there; so a swimming route that falls short is discarded in favour of
	 * the ordinary one. Water is expensive to the pathfinder, so land is still preferred whenever
	 * there is a way round.
	 */
	static Path computePath(LocalPlayer player, BlockPos target, float maxPathLength, int reachRange, boolean allowSwimming) {
		if (allowSwimming) {
			Path swimming = search(player, target, maxPathLength, reachRange, true);
			if (swimming != null && swimming.canReach()) {
				return swimming;
			}
		}
		return search(player, target, maxPathLength, reachRange, false);
	}

	private static Path search(LocalPlayer player, BlockPos target, float maxPathLength, int reachRange, boolean swim) {
		Level level = player.level();
		// ignoreChecks=true - this ghost is never actually spawned into the world, just used as
		// a parameter bag (bounding box, step height, fall tolerance) for the pathfinder below,
		// so it shouldn't be subject to spawn-eligibility rules like "not allowed on Peaceful".
		Mob ghost = EntityTypes.ZOMBIE.create(level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, true));
		if (ghost == null) {
			return null;
		}
		ghost.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);

		BlockPos playerPos = player.blockPosition();
		BlockPos regionStart = new BlockPos(
				Math.min(playerPos.getX(), target.getX()) - SEARCH_MARGIN,
				Math.min(playerPos.getY(), target.getY()) - SEARCH_MARGIN,
				Math.min(playerPos.getZ(), target.getZ()) - SEARCH_MARGIN);
		BlockPos regionEnd = new BlockPos(
				Math.max(playerPos.getX(), target.getX()) + SEARCH_MARGIN,
				Math.max(playerPos.getY(), target.getY()) + SEARCH_MARGIN,
				Math.max(playerPos.getZ(), target.getZ()) + SEARCH_MARGIN);
		PathNavigationRegion region = new PathNavigationRegion(level, regionStart, regionEnd);

		PathFinder finder = new PathFinder(new AutoWalkNodeEvaluator(swim), 4096);
		Path path = finder.findPath(region, ghost, Set.of(target), maxPathLength, reachRange, 1.0f);
		ghost.discard();
		return path;
	}
}
