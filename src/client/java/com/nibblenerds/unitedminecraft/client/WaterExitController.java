package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * "How do I get out of here": vanilla's own mob pathfinding ({@link ClientPathfinding}, the
 * same one {@link AutoWalkController} relies on) is a land-walker's model - it needs solid
 * ground under a node to build a path from at all, so it has nothing to anchor to once the
 * player is floating in open water, which is exactly when this is needed most.
 *
 * <p>{@link #narrate} and {@link #start} instead flood-fill outward from the player through
 * connected water and air (never lava - this is specifically an escape-a-lake tool, not a
 * general swim-anywhere one), the same technique {@link ScannerController}'s Liquids category
 * and {@link TreeChoppingAssist}-adjacent tree clustering already use elsewhere in this mod.
 * Because the search only ever visits cells actually connected to the player's current
 * position, whichever exit it finds first is guaranteed reachable by swimming there directly -
 * unlike a naive "nearest exposed land" search, which could easily point at a ledge six blocks
 * up with a solid wall in between.
 *
 * <p>{@link #start} then swims there automatically along the exact route the flood-fill found
 * (not just a beeline for the final spot, which could just as easily ram a cave wall along the
 * way), reusing {@link AutoWalkController}'s own approach of swapping the player's real input
 * for a synthetic one - but continuously aiming (yaw <em>and</em> pitch, via {@link
 * CameraUtil#aimAt}) at each waypoint in turn, and holding jump (vanilla's own "swim up" input)
 * whenever the next one sits above the player, rather than {@link AutoWalkController}'s
 * ground-walking, step-up-on-arrival jump logic - onGround() is never true while swimming, so
 * that logic would never fire underwater at all.
 */
public final class WaterExitController {
	private static final int SEARCH_RADIUS = 32;
	private static final double NODE_ARRIVAL_DISTANCE_SQR = 0.7 * 0.7;
	private static final double VERTICAL_DIRECTION_THRESHOLD = 3.0;

	private static List<BlockPos> route;
	private static int routeIndex;
	private static ClientInput previousInput;

	private WaterExitController() {
	}

	public static boolean isActive() {
		return route != null;
	}

	public static void reset() {
		route = null;
		routeIndex = 0;
		previousInput = null;
	}

	/** Reports distance and direction to the nearest reachable way out, without moving the player. */
	public static void narrate(Minecraft client, LocalPlayer player) {
		List<BlockPos> path = findRoute(player);
		if (path == null || path.isEmpty()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.water_exit_none"));
			return;
		}

		BlockPos exit = path.get(path.size() - 1);
		Vec3 from = player.position();
		Vec3 to = Vec3.atCenterOf(exit);
		int distance = (int) Math.round(from.distanceTo(to));
		Component direction = CameraUtil.compassDirectionTo(from, to);
		double dy = to.y() - from.y();
		if (dy > VERTICAL_DIRECTION_THRESHOLD) {
			direction = direction.copy().append(Component.literal(", ")).append(Component.translatable("united_minecraft.direction.above"));
		} else if (dy < -VERTICAL_DIRECTION_THRESHOLD) {
			direction = direction.copy().append(Component.literal(", ")).append(Component.translatable("united_minecraft.direction.below"));
		}
		client.getNarrator().saySystemNow(
				Component.translatable("united_minecraft.narrate.water_exit_direction", distance, direction));
	}

	/** Swims the player along the found route, one waypoint at a time, until it reaches dry land. */
	public static void start(Minecraft client, LocalPlayer player) {
		if (isActive()) {
			cancel(client, player);
		}
		if (AutoWalkController.isActive()) {
			AutoWalkController.cancel(client, player);
		}

		List<BlockPos> path = findRoute(player);
		if (path == null || path.isEmpty()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.water_exit_none"));
			return;
		}

		route = path;
		routeIndex = 0;
		previousInput = player.input;
		player.input = new SwimInput();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.water_exit_started"));
	}

	public static void cancel(Minecraft client, LocalPlayer player) {
		if (!isActive()) {
			return;
		}
		finish(client, player, "united_minecraft.narrate.water_exit_cancelled");
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!isActive()) {
			return;
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_STOP_LOCK)) {
			cancel(client, player);
			return;
		}

		BlockPos next = route.get(routeIndex);
		Vec3 target = Vec3.atCenterOf(next);
		if (player.position().distanceToSqr(target) < NODE_ARRIVAL_DISTANCE_SQR) {
			routeIndex++;
			if (routeIndex >= route.size()) {
				finish(client, player, "united_minecraft.narrate.water_exit_arrived");
			}
			return;
		}

		CameraUtil.aimAt(player, target);
		boolean rise = next.getY() > player.getY() + 0.1;
		((SwimInput) player.input).setSwimming(rise);
	}

	private static void finish(Minecraft client, LocalPlayer player, String messageKey) {
		player.input = previousInput;
		reset();
		client.getNarrator().saySystemNow(Component.translatable(messageKey));
	}

	/**
	 * Floods outward from the player through connected, in-range water and air, returning the
	 * waypoint chain to whichever standable dry cell it reaches first - or null if nothing
	 * reachable within {@link #SEARCH_RADIUS} qualifies. BFS order (not distance) decides which
	 * exit wins, since that's what "reachable by the shortest swim" actually means; a closer
	 * exit by straight-line distance could still require a much longer swim around obstacles.
	 */
	private static List<BlockPos> findRoute(LocalPlayer player) {
		Level level = player.level();
		BlockPos start = player.blockPosition();
		int r = SEARCH_RADIUS;

		Set<BlockPos> passable = new HashSet<>();
		for (BlockPos pos : BlockPos.betweenClosed(start.offset(-r, -r, -r), start.offset(r, r, r))) {
			if (isSwimmable(level.getBlockState(pos))) {
				passable.add(pos.immutable());
			}
		}
		if (!passable.contains(start)) {
			passable.add(start.immutable());
		}

		Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		cameFrom.put(start, start);
		BlockPos exit = null;

		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			if (!current.equals(start) && isExit(level, player, current)) {
				exit = current;
				break;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos neighbor = current.offset(dx, dy, dz);
						if (passable.contains(neighbor) && !cameFrom.containsKey(neighbor)) {
							cameFrom.put(neighbor, current);
							queue.add(neighbor);
						}
					}
				}
			}
		}
		if (exit == null) {
			return null;
		}

		List<BlockPos> path = new ArrayList<>();
		BlockPos step = exit;
		while (!step.equals(start)) {
			path.add(step);
			step = cameFrom.get(step);
		}
		Collections.reverse(path);
		return path;
	}

	/** Water (never lava - this is deliberately an escape-a-lake tool, not a general swim aid) or open air. */
	private static boolean isSwimmable(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		return state.getFluidState().is(FluidTags.WATER);
	}

	/** An air cell with real, dry, solid ground underneath - somewhere the player could actually climb out and stand. */
	private static boolean isExit(Level level, LocalPlayer player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.isAir()) {
			return false;
		}
		BlockPos belowPos = pos.below();
		BlockState below = level.getBlockState(belowPos);
		if (!below.getFluidState().isEmpty() || below.getCollisionShape(level, belowPos).isEmpty()) {
			return false;
		}
		AABB box = player.getBoundingBox().move(
				pos.getX() + 0.5 - player.getX(), pos.getY() - player.getY(), pos.getZ() + 0.5 - player.getZ());
		return level.noCollision(player, box);
	}

	/** Reports "forward" (and "jump", vanilla's own swim-up input) as held, exactly like real keyboard input would. */
	private static final class SwimInput extends ClientInput {
		void setSwimming(boolean rise) {
			this.keyPresses = new Input(true, false, false, false, rise, false, false);
			this.moveVector = new Vec2(0.0f, 1.0f);
		}
	}
}
