package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * "How do I get back to where I came from": caves are uneven enough that there's rarely a
 * single known point to path to (what {@link AutoWalkController} and {@link WaterExitController}
 * both need), so this instead continuously records the player's own actual positions as a
 * breadcrumb trail and, on request, walks that recorded route back in reverse - the same
 * waypoint-following mechanism those two controllers already use, just fed a route that was
 * recorded instead of computed. Because it's a literal reverse of a route the player already
 * walked, it's <em>almost</em> always trivially reachable by construction - but not quite
 * guaranteed: consecutive trail points are only ever recorded a minimum distance apart (see
 * {@link #TRAIL_MIN_SPACING}) and connected by a straight walk between them, not a
 * re-simulation of the original route, so a curve the outbound walk took around a corner or
 * pillar within one of those gaps can end up clipped by the straight line back. {@link #tick}
 * watches for exactly that - no real progress toward the current waypoint for a while despite
 * actively walking - and stops instead of pushing against it silently forever.
 *
 * <p>That same reachable-by-construction property doubles as the answer to "do I need to build
 * up here": any point where retracing would require climbing steeper than an ordinary step is
 * exactly the point where the outbound trip dropped down more than a step, i.e. exactly where
 * the player needs to place a block to climb back out. See {@link #tick} for that check too.
 */
public final class TrailController {
	// 3D distance, so a vertical drop gets recorded the same as horizontal wandering.
	private static final double TRAIL_MIN_SPACING = 1.5;
	private static final double TRAIL_MIN_SPACING_SQR = TRAIL_MIN_SPACING * TRAIL_MIN_SPACING;
	// Several thousand blocks of path at TRAIL_MIN_SPACING - generous for a single cave dive.
	private static final int MAX_TRAIL_POINTS = 3000;
	private static final double NODE_ARRIVAL_DISTANCE_SQR = 0.7 * 0.7;
	private static final double VERTICAL_DIRECTION_THRESHOLD = 3.0;
	// Steeper than this isn't a normal step-up - it's exactly the "you fell here, so you need
	// to place a block to climb back out" case this whole feature exists to catch.
	private static final double MAX_CLIMBABLE_STEP = 1.0;
	// Consecutive trail points are connected by a straight walk, not a re-simulation of the
	// original route - almost always fine, but not guaranteed: the outbound walk could have
	// curved around a corner or pillar within a single recorded gap (points are only ever
	// TRAIL_MIN_SPACING apart) in a way this straight line clips. Rather than trying to detect
	// that geometrically, #tick instead notices the practical symptom - no real progress toward
	// the current waypoint for this many ticks despite actively walking - and stops instead of
	// pushing against it silently forever.
	private static final int STUCK_TICKS_THRESHOLD = 40;
	private static final double STUCK_PROGRESS_EPSILON = 0.05;

	private static final List<Vec3> trail = new ArrayList<>();

	private static List<Vec3> route;
	private static int routeIndex;
	private static ClientInput previousInput;
	private static double bestDistanceToNext;
	private static int stuckTicks;

	private TrailController() {
	}

	public static boolean isActive() {
		return route != null;
	}

	public static void reset() {
		trail.clear();
		route = null;
		routeIndex = 0;
		previousInput = null;
		bestDistanceToNext = Double.MAX_VALUE;
		stuckTicks = 0;
	}

	/**
	 * Records the player's position into the trail, or - if they're manually retracing steps
	 * without this feature's help - un-records it instead.
	 *
	 * <p>Recording happens (a) once the player has moved {@link #TRAIL_MIN_SPACING} in any
	 * direction from the last recorded point, or (b) as soon as they've changed elevation by a
	 * full {@link #MAX_CLIMBABLE_STEP}, even if that alone isn't far in 3D distance - a spiral or
	 * tightly-turning staircase can otherwise cover more than one block of height between
	 * horizontally-close checkpoints, which {@link #tick}'s climb check would then misread as a
	 * fall that needs a placed block to climb back out of, when it was really just ordinary
	 * stairs.
	 *
	 * <p>Before recording forward, though: if the player is now closer to the point *before* the
	 * last recorded one than to the last one itself, they've backtracked past it under their own
	 * steam - drop it rather than adding yet another point, so a later retrace only ever covers
	 * the ground still actually ahead of them, instead of replaying a walk-back they already did
	 * manually.
	 */
	public static void recordTick(Minecraft client, LocalPlayer player) {
		Vec3 pos = player.position();
		if (trail.isEmpty()) {
			trail.add(pos);
			return;
		}

		while (trail.size() >= 2) {
			Vec3 last = trail.get(trail.size() - 1);
			Vec3 prev = trail.get(trail.size() - 2);
			if (pos.distanceToSqr(prev) <= pos.distanceToSqr(last)) {
				trail.remove(trail.size() - 1);
			} else {
				break;
			}
		}

		Vec3 last = trail.get(trail.size() - 1);
		boolean farEnough = pos.distanceToSqr(last) >= TRAIL_MIN_SPACING_SQR;
		boolean steppedElevation = Math.abs(pos.y() - last.y()) >= MAX_CLIMBABLE_STEP;
		if (!farEnough && !steppedElevation) {
			return;
		}
		trail.add(pos);
		if (trail.size() > MAX_TRAIL_POINTS) {
			trail.remove(0);
		}
	}

	/** Clears the trail and starts recording fresh from here - marks a deliberate "return to this point". */
	public static void markStart(Minecraft client, LocalPlayer player) {
		trail.clear();
		trail.add(player.position());
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.trail_marked"));
	}

	/** Reports distance and direction back to the start of the recorded trail, without moving the player. */
	public static void narrate(Minecraft client, LocalPlayer player) {
		List<Vec3> path = buildRoute(player);
		if (path == null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.trail_none"));
			return;
		}
		if (path.isEmpty()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.trail_at_start"));
			return;
		}

		Vec3 from = player.position();
		Vec3 to = path.get(path.size() - 1);
		int distance = (int) Math.round(from.distanceTo(to));
		Component direction = CameraUtil.compassDirectionTo(from, to);
		double dy = to.y() - from.y();
		if (dy > VERTICAL_DIRECTION_THRESHOLD) {
			direction = direction.copy().append(Component.literal(", ")).append(Component.translatable("united_minecraft.direction.above"));
		} else if (dy < -VERTICAL_DIRECTION_THRESHOLD) {
			direction = direction.copy().append(Component.literal(", ")).append(Component.translatable("united_minecraft.direction.below"));
		}
		client.getNarrator().saySystemNow(
				Component.translatable("united_minecraft.narrate.trail_direction", distance, direction));
	}

	/** Walks the player back along the recorded trail, one waypoint at a time, until it reaches the start. */
	public static void start(Minecraft client, LocalPlayer player) {
		if (isActive()) {
			cancel(client, player);
		}
		if (AutoWalkController.isActive()) {
			AutoWalkController.cancel(client, player);
		}
		if (WaterExitController.isActive()) {
			WaterExitController.cancel(client, player);
		}

		List<Vec3> path = buildRoute(player);
		if (path == null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.trail_none"));
			return;
		}
		if (path.isEmpty()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.trail_at_start"));
			return;
		}

		route = path;
		routeIndex = 0;
		bestDistanceToNext = Double.MAX_VALUE;
		stuckTicks = 0;
		previousInput = player.input;
		player.input = new TrailInput();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.trail_started"));
	}

	public static void cancel(Minecraft client, LocalPlayer player) {
		if (!isActive()) {
			return;
		}
		finish(client, player, "united_minecraft.narrate.trail_cancelled");
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!isActive()) {
			return;
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_STOP_LOCK)) {
			cancel(client, player);
			return;
		}

		Vec3 next = route.get(routeIndex);
		if (player.position().distanceToSqr(next) < NODE_ARRIVAL_DISTANCE_SQR) {
			routeIndex++;
			bestDistanceToNext = Double.MAX_VALUE;
			stuckTicks = 0;
			if (routeIndex >= route.size()) {
				finish(client, player, "united_minecraft.narrate.trail_arrived");
			}
			return;
		}

		// A climb steeper than a normal step means this leg of the trail was a drop on the way
		// in - retracing it needs a placed block, not more walking. Route and trail are both
		// left intact so a fresh Shift+Z after building resumes right where this left off.
		if (next.y() - player.getY() > MAX_CLIMBABLE_STEP) {
			finish(client, player, "united_minecraft.narrate.trail_blocked");
			return;
		}

		// See STUCK_TICKS_THRESHOLD's doc - this is what actually catches a straight-line leg
		// that clips something the original walk curved around, since nothing above this point
		// checks for real obstructions at all.
		double distance = player.position().distanceTo(next);
		if (distance < bestDistanceToNext - STUCK_PROGRESS_EPSILON) {
			bestDistanceToNext = distance;
			stuckTicks = 0;
		} else if (++stuckTicks > STUCK_TICKS_THRESHOLD) {
			finish(client, player, "united_minecraft.narrate.trail_stuck");
			return;
		}

		CameraUtil.aimAt(player, next);
		boolean rise = next.y() > player.getY() + 0.1;
		((TrailInput) player.input).setWalking(rise);
	}

	private static void finish(Minecraft client, LocalPlayer player, String messageKey) {
		player.input = previousInput;
		route = null;
		routeIndex = 0;
		previousInput = null;
		client.getNarrator().saySystemNow(Component.translatable(messageKey));
	}

	/**
	 * Builds the walk-back route from the trail point nearest the player's current position back
	 * to the oldest recorded point - null if nothing has been recorded, empty if the player is
	 * already essentially at the start.
	 */
	private static List<Vec3> buildRoute(LocalPlayer player) {
		if (trail.isEmpty()) {
			return null;
		}

		Vec3 pos = player.position();
		int nearest = 0;
		double bestDistSqr = Double.MAX_VALUE;
		for (int i = 0; i < trail.size(); i++) {
			double distSqr = trail.get(i).distanceToSqr(pos);
			if (distSqr < bestDistSqr) {
				bestDistSqr = distSqr;
				nearest = i;
			}
		}

		List<Vec3> path = new ArrayList<>();
		for (int i = nearest - 1; i >= 0; i--) {
			path.add(trail.get(i));
		}
		return path;
	}

	/** Reports "forward" (and "jump" when the next waypoint sits above the player) as held. */
	private static final class TrailInput extends ClientInput {
		void setWalking(boolean jump) {
			this.keyPresses = new Input(true, false, false, false, jump, false, false);
			this.moveVector = new Vec2(0.0f, 1.0f);
		}
	}
}
