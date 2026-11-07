package com.nibblenerds.unitedminecraft.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.PowderSnowBlock;
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
public final class ClientPathfinding {
	private static final int SEARCH_MARGIN = 16;
	// Flying approach spots (see flightApproaches): the rider's eye this many blocks from the target in
	// each direction, at least APPROACH_MIN_DISTANCE out, and the box corner counts as there within a couple of blocks.
	private static final int APPROACH_RADIUS = 4;
	private static final int APPROACH_MIN_DISTANCE = 3;
	private static final int FLIGHT_APPROACH_REACH_RANGE = 2;

	private ClientPathfinding() {
	}

	static Path computePath(LocalPlayer player, BlockPos target, float maxPathLength, int reachRange) {
		return search(player, target, maxPathLength, reachRange, false, false);
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
	static Path computePath(LocalPlayer player, BlockPos target, float maxPathLength, int reachRange, boolean allowSwimming,
			boolean openDoors) {
		if (allowSwimming && !AutoWalkController.movesInThreeDimensions(AutoWalkController.bodyOf(player))) {
			Path swimming = search(player, target, maxPathLength, reachRange, true, openDoors);
			if (swimming != null && swimming.canReach()) {
				return swimming;
			}
		}
		return search(player, target, maxPathLength, reachRange, false, openDoors);
	}

	private static Path search(LocalPlayer player, BlockPos target, float maxPathLength, int reachRange, boolean swim,
			boolean openDoors) {
		Level level = player.level();
		// ignoreChecks=true - this ghost is never actually spawned into the world, just used as
		// a parameter bag (bounding box, step height, fall tolerance) for the pathfinder below,
		// so it shouldn't be subject to spawn-eligibility rules like "not allowed on Peaceful".
		// Ridden, the thing that has to fit through gaps and climb steps is the mount, not the player
		// sitting on it: stand in a never-spawned copy of the mount's own type, at the mount's feet.
		Entity body = AutoWalkController.bodyOf(player);
		Entity ghostEntity = body == player
				? EntityTypes.ZOMBIE.create(level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, true))
				: body.getType().create(level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, true));
		if (!(ghostEntity instanceof Mob ghost)) {
			if (ghostEntity != null) {
				ghostEntity.discard();
			}
			return null;
		}
		ghost.snapTo(body.getX(), body.getY(), body.getZ(), player.getYRot(), 0.0f);
		if (body instanceof AbstractHorse horse) {
			// The pathfinder climbs as high as the mover's step height says it can; for a horse that is
			// the tallest rise its jump lands on, so it routes up ledges and over fences it can jump.
			AttributeInstance stepHeight = ghost.getAttribute(Attributes.STEP_HEIGHT);
			if (stepHeight != null) {
				stepHeight.setBaseValue(MountStats.maxRise(
						horse.getAttributeValue(Attributes.JUMP_STRENGTH), horse.getAttributeValue(Attributes.GRAVITY)));
			}
		}

		BlockPos playerPos = body.blockPosition();
		BlockPos regionStart = new BlockPos(
				Math.min(playerPos.getX(), target.getX()) - SEARCH_MARGIN,
				Math.min(playerPos.getY(), target.getY()) - SEARCH_MARGIN,
				Math.min(playerPos.getZ(), target.getZ()) - SEARCH_MARGIN);
		BlockPos regionEnd = new BlockPos(
				Math.max(playerPos.getX(), target.getX()) + SEARCH_MARGIN,
				Math.max(playerPos.getY(), target.getY()) + SEARCH_MARGIN,
				Math.max(playerPos.getZ(), target.getZ()) + SEARCH_MARGIN);
		PathNavigationRegion region = new PathNavigationRegion(level, regionStart, regionEnd);

		// A ridden happy ghast flies: route it through open air with vanilla's flying evaluator.
		NodeEvaluator evaluator = body instanceof HappyGhast
				? new FlyNodeEvaluator()
				// A nautilus stays under the water, as a fish does.
				: body instanceof AbstractNautilus
						? new SwimNodeEvaluator(false)
						: new AutoWalkNodeEvaluator(swim, PowderSnowBlock.canEntityWalkOnPowderSnow(player), openDoors);
		PathFinder finder = new PathFinder(evaluator, 4096);
		Set<BlockPos> targets = Set.of(target);
		if (AutoWalkController.movesInThreeDimensions(body)) {
			Set<BlockPos> approaches = flightApproaches(level, player, body, target);
			if (!approaches.isEmpty()) {
				targets = approaches;
				reachRange = FLIGHT_APPROACH_REACH_RANGE;
			}
		}
		Path path = finder.findPath(region, ghost, targets, maxPathLength, reachRange, 1.0f);
		ghost.discard();
		return path;
	}

	/**
	 * Where a flying mount should aim for, as path nodes (the corner of the mount's box), when the target
	 * is a block it needs to be able to see or use. Counting "near enough" by distance alone would let a
	 * ghast stop on the far side of a wall from a door; instead the search is given only spots a few
	 * blocks out from which the rider's eye has a clear line to the target, so the route goes round to
	 * the side the target can actually be reached from. Empty when there is no such spot, and the search
	 * falls back to plain distance.
	 */
	private static Set<BlockPos> flightApproaches(Level level, LocalPlayer player, Entity ghast, BlockPos target) {
		Vec3 targetCentre = Vec3.atCenterOf(target);
		double eyeAbove = player.getEyeY() - ghast.getY();
		// A node is the corner of the box; the mount's centre sits half a (rounded-up) width in from it.
		int cornerOffset = (int) (ghast.getBbWidth() + 1.0F) / 2;
		Set<BlockPos> nodes = new HashSet<>();
		for (int dx = -APPROACH_RADIUS; dx <= APPROACH_RADIUS; dx++) {
			for (int dy = -APPROACH_RADIUS; dy <= APPROACH_RADIUS; dy++) {
				for (int dz = -APPROACH_RADIUS; dz <= APPROACH_RADIUS; dz++) {
					if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) < APPROACH_MIN_DISTANCE) {
						continue;
					}
					BlockPos eye = target.offset(dx, dy, dz);
					if (!level.getBlockState(eye).getCollisionShape(level, eye).isEmpty()) {
						continue;
					}
					BlockHitResult hit = level.clip(new ClipContext(
							Vec3.atCenterOf(eye), targetCentre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
					if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target)) {
						nodes.add(new BlockPos(eye.getX() - cornerOffset, (int) Math.floor(eye.getY() + 0.5 - eyeAbove),
								eye.getZ() - cornerOffset));
					}
				}
			}
		}
		return nodes;
	}
}
