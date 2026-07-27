package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/**
 * Reactive "why am I stuck" narration: silent during normal movement, and only speaks up
 * once the player has been pressing forward without actually moving for a short while
 * (long enough to rule out normal micro-stutters, e.g. bumping a slab), diagnosing whatever
 * is directly ahead - a wall too tall to walk into, a wall a jump would clear, a drop-off,
 * or water/lava - rather than leaving a blind player to guess why movement just stopped.
 *
 * <p>For a genuine wall or drop-off (not just a jumpable step), it also asks the same
 * pathfinder {@link AutoWalkController} uses whether there's a way around - a single tree
 * trunk, for instance, almost always has clear ground to either side - and reports which
 * side the route goes, rather than just reporting "stuck" when you could simply step
 * around it. Only runs during ordinary manual walking; build mode, scanner lock-on, and
 * auto-walk all drive movement/rotation themselves and are diagnosed by their own narration.
 */
public final class MovementAssistController {
	private static final double STUCK_VELOCITY_THRESHOLD_SQR = 0.03 * 0.03;
	private static final int STUCK_TICKS_BEFORE_NARRATION = 15;
	private static final int MAX_DROP_SCAN = 5;

	private static final int ROUTE_CHECK_DISTANCE = 6;
	private static final float ROUTE_MAX_PATH_LENGTH = 24.0f;
	private static final int ROUTE_REACH_RANGE = 2;
	private static final double ROUTE_DIVERGENCE_DISTANCE = 1.2;
	private static final double ROUTE_LATERAL_THRESHOLD = 0.75;

	private static int stuckTicks = 0;
	private static boolean narratedThisEpisode = false;

	private MovementAssistController() {
	}

	public static void reset() {
		stuckTicks = 0;
		narratedThisEpisode = false;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		double horizontalSpeedSqr = player.getDeltaMovement().x * player.getDeltaMovement().x
				+ player.getDeltaMovement().z * player.getDeltaMovement().z;

		if (!player.input.hasForwardImpulse() || !player.onGround() || horizontalSpeedSqr > STUCK_VELOCITY_THRESHOLD_SQR) {
			stuckTicks = 0;
			narratedThisEpisode = false;
			return;
		}

		if (++stuckTicks == STUCK_TICKS_BEFORE_NARRATION && !narratedThisEpisode) {
			narratedThisEpisode = true;
			narrateObstacle(client, player);
		}
	}

	private static void narrateObstacle(Minecraft client, LocalPlayer player) {
		Level level = player.level();
		Direction facing = player.getDirection();
		BlockPos aheadPos = player.blockPosition().relative(facing);
		BlockState aheadState = level.getBlockState(aheadPos);

		if (!aheadState.getFluidState().isEmpty()) {
			client.getNarrator().saySystemNow(Component.translatable(aheadState.getFluidState().is(FluidTags.LAVA)
					? "united_minecraft.narrate.movement_lava"
					: "united_minecraft.narrate.movement_water"));
			return;
		}

		// Real AABB collision, not just the neighboring block's own state: fences and walls
		// have a taller effective collision box than their own block space specifically to
		// stop a jump clearing them, and a single-block-state check would miss that entirely
		// (it only sees the block occupying the space actually being tested, not a
		// neighboring block's shape reaching into it).
		double aheadX = player.getX() + facing.getStepX();
		double aheadZ = player.getZ() + facing.getStepZ();
		boolean blockedAtCurrentHeight = !canOccupy(level, player, aheadX, player.getY(), aheadZ);

		if (blockedAtCurrentHeight) {
			if (canOccupy(level, player, aheadX, player.getY() + 1.0, aheadZ)) {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.movement_wall_jumpable"));
				return;
			}
		} else if (!hasDropOff(level, aheadPos)) {
			// Not blocked and no drop-off found by our simple floor scan - something else is
			// preventing movement (an odd collision shape, a diagonal corner, etc.) that isn't
			// worth chasing a route for.
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.movement_blocked_generic"));
			return;
		}

		Component route = findRouteAround(player, facing);
		if (route != null) {
			client.getNarrator().saySystemNow(route);
		} else {
			client.getNarrator().saySystemNow(Component.translatable(blockedAtCurrentHeight
					? "united_minecraft.narrate.movement_wall"
					: "united_minecraft.narrate.movement_dropoff"));
		}
	}

	/** Whether the player's own bounding box would fit at this position without colliding with anything. */
	private static boolean canOccupy(Level level, LocalPlayer player, double x, double y, double z) {
		AABB box = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());
		return level.noCollision(player, box);
	}

	private static boolean hasDropOff(Level level, BlockPos ahead) {
		BlockPos scan = ahead.below();
		for (int i = 0; i < MAX_DROP_SCAN; i++) {
			if (isObstacle(level, scan, level.getBlockState(scan))) {
				return false;
			}
			scan = scan.below();
		}
		return true;
	}

	/**
	 * Asks the pathfinder for a short route to just past the obstacle, then checks whether
	 * its first real waypoint (skipping the first ~1.2 blocks, which is still basically the
	 * starting spot) diverges left or right of straight ahead.
	 */
	private static Component findRouteAround(LocalPlayer player, Direction facing) {
		Level level = player.level();
		BlockPos beyond = player.blockPosition().relative(facing, ROUTE_CHECK_DISTANCE);
		// OCEAN_FLOOR is server-only and reads back garbage on the client -
		// MOTION_BLOCKING_NO_LEAVES is the client-safe equivalent.
		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, beyond.getX(), beyond.getZ());
		BlockPos target = new BlockPos(beyond.getX(), groundY, beyond.getZ());

		Path path = ClientPathfinding.computePath(player, target, ROUTE_MAX_PATH_LENGTH, ROUTE_REACH_RANGE);
		if (path == null || path.getNodeCount() == 0 || !path.canReach()) {
			return null;
		}

		Direction rightDir = facing.getClockWise();
		double playerX = player.getX();
		double playerZ = player.getZ();
		for (int i = 0; i < path.getNodeCount(); i++) {
			BlockPos node = path.getNodePos(i);
			double dx = node.getX() + 0.5 - playerX;
			double dz = node.getZ() + 0.5 - playerZ;
			double forwardDist = dx * facing.getStepX() + dz * facing.getStepZ();
			if (forwardDist < ROUTE_DIVERGENCE_DISTANCE) {
				continue;
			}
			double lateral = dx * rightDir.getStepX() + dz * rightDir.getStepZ();
			if (lateral > ROUTE_LATERAL_THRESHOLD) {
				return Component.translatable("united_minecraft.narrate.movement_route_right");
			}
			if (lateral < -ROUTE_LATERAL_THRESHOLD) {
				return Component.translatable("united_minecraft.narrate.movement_route_left");
			}
			return Component.translatable("united_minecraft.narrate.movement_route_ahead");
		}
		return Component.translatable("united_minecraft.narrate.movement_route_ahead");
	}

	private static boolean isObstacle(Level level, BlockPos pos, BlockState state) {
		return !state.getCollisionShape(level, pos).isEmpty();
	}
}
