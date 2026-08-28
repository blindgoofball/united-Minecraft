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
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Shared "compute a walking path with vanilla's own mob pathfinding" helper, used by both
 * {@link AutoWalkController} (the scanner's "walk to it") and {@link MovementAssistController}
 * (checking whether an obstacle can be routed around). See {@link AutoWalkController}'s class
 * doc for why this needs a throwaway, never-spawned {@link Mob}.
 */
final class ClientPathfinding {
	private static final int SEARCH_MARGIN = 16;

	private ClientPathfinding() {
	}

	static Path computePath(LocalPlayer player, BlockPos target, float maxPathLength, int reachRange) {
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

		PathFinder finder = new PathFinder(new WalkNodeEvaluator(), 4096);
		Path path = finder.findPath(region, ghost, Set.of(target), maxPathLength, reachRange, 1.0f);
		ghost.discard();
		return path;
	}
}
