package com.nibblenerds.unitedminecraft.client.travel;

import com.nibblenerds.unitedminecraft.client.AngleAssistController;
import com.nibblenerds.unitedminecraft.client.CameraUtil;
import com.nibblenerds.unitedminecraft.client.WaterExitController;
import com.nibblenerds.unitedminecraft.client.autowalk.AutoWalkController;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import com.nibblenerds.unitedminecraft.client.keybind.ClientKeyBindings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Elytra landing help, on the same keys as {@link WaterExitController}: while gliding, Y reports the
 * nearest safe landing spot you can reach ({@link #narrate}) and Shift+Y flies you onto it ({@link
 * #start}), the flying counterpart of "which way out" and "swim there".
 *
 * <p>A spot qualifies when it is:
 * <ul>
 * <li><b>Within glide range.</b> An elytra covers a limited distance per block of height lost, so
 * a spot further away than {@link #GLIDE_RATIO} times the height above it (less {@link
 * #FLARE_HEIGHT} kept back for levelling off) simply can't be reached without fireworks.</li>
 * <li><b>Flat and solid.</b> A 3x3 patch at one height, every block of it a full, sturdy top face
 * that isn't leaves, magma or a campfire, with open air above - so touching down and sliding
 * doesn't tip into a pit or onto something harmful. Water and lava never count.</li>
 * <li><b>Reachable.</b> A straight line from the player to just above the spot is clear of
 * terrain, trees and builds.</li>
 * </ul>
 *
 * <p>The flying itself is {@link ElytraAutopilot}: this class only feeds it the player's state each
 * tick and applies the look it asks for, owning the camera while it runs (like Auto-Walk does) until
 * the player touches down, cancels with the stop key, or it hands back control because terrain is
 * in the way.
 */
public final class ElytraLandingController {
	// Blocks travelled forward per block of height lost. Real elytra glide is better than this
	// (about 10 in level flight), but a landing needs room to turn onto the spot and to level
	// off, so range is judged conservatively rather than at the best case.
	static final double GLIDE_RATIO = 6.0;
	// Height kept back above the spot for levelling off before touching down.
	static final double FLARE_HEIGHT = 3.0;
	private static final int SEARCH_RADIUS = 96;
	private static final double APPROACH_AIM_HEIGHT = 2.0;
	// Below this height above the spot there is no room left to circle down, so a spot the autopilot
	// can't line up on straight away is declined rather than attempted.
	static final double MIN_START_HEIGHT_TO_CIRCLE = 30.0;
	private static final int STATUS_INTERVAL_TICKS = 100;
	// Consecutive ticks of a predicted collision before control is handed back - a single tick of
	// noise in the prediction shouldn't throw the player out of an otherwise fine landing.
	static final int HAZARD_TICKS_TO_ABORT = 5;
	// How far from a chosen destination a landing spot is looked for.
	// Wide enough to get out from against a building to open ground; the walk from there is short.
	private static final int NEAR_TARGET_RADIUS = 10;
	// Room a landing spot needs: blocks of clear approach, and of clear ground to slide on afterwards,
	// found along one of this many evenly spaced directions.
	// Blocks above or below the destination that cost nothing, then the cost of each block beyond.
	static final int HEIGHT_SLACK = 1;
	static final double HEIGHT_PENALTY = 1.5;
	static final double MAX_HEIGHT_PENALTY = 5.0;
	// Past this many blocks of height difference the ground is not somewhere to walk from at all (a
	// farm in the sky, a tower), and each further block costs this much.
	static final int FAR_HEIGHT = 10;
	static final double FAR_HEIGHT_PENALTY = 0.25;
	// Cost of a spot without room to come in and slide out: a roof small enough to have none is still
	// the better place to land than ground a long way off.
	static final double NO_RUNWAY_PENALTY = 4.0;
	// Clear height a spot under something needs, for the player to stand in.
	private static final int COVERED_HEADROOM = 2;
	static final int RUNWAY = 16;
	static final int RUNOUT = 4;
	private static final int RUNWAY_DIRECTIONS = 16;
	// Landing this close to the destination counts as having got there.
	private static final double ARRIVED_DISTANCE = 3.0;

	private static ElytraAutopilot autopilot;
	private static Spot target;
	private static ElytraAutopilot.Phase announcedPhase;
	private static int ticksFlown;
	private static int hazardTicks;
	// Set when the flight is to somewhere the player chose (a Scanner target) rather than the nearest
	// safe spot: where they wanted to end up, what to call it, and what to do once they have.
	private static BlockPos destination;
	private static Component destinationName;
	private static boolean destinationExact;
	private static Runnable destinationArrival;
	// Column surface heights already looked up this tick - the autopilot asks for the same columns
	// over and over while predicting, and every one of them is a heightmap read.
	private static final Map<Long, Double> SURFACE_CACHE = new HashMap<>();
	// For columns with something over the flight, the ground it would meet instead: {surface, highest
	// height it holds for}. Terrain doesn't change under a flight, so these are kept for a while.
	private static final Map<Long, double[]> BELOW_CACHE = new HashMap<>();
	private static final int BELOW_CACHE_TICKS = 20;
	// How far under the destination ground is looked for when it has something over it.
	private static final int COVERED_DEPTH = 8;
	// How far below the spot the flight is given up as missed.
	private static final double MISSED_BELOW = 3.0;
	// How far below the search limit ground is taken to be where none was found - a void.
	private static final int NO_GROUND_DEPTH = 64;
	// Blocks either way from the spot whose ground goes in a flight's log.
	private static final int RECORDED_RADIUS = 24;

	private ElytraLandingController() {
	}

	/** The nearest landing spot: where to stand ({@code feet}) and how far away it is horizontally. */
	private record Spot(BlockPos feet, double distance) {
	}

	public static boolean isActive() {
		return autopilot != null;
	}

	public static void reset() {
		ElytraFlightRecorder.end("reset", "control released");
		autopilot = null;
		target = null;
		announcedPhase = null;
		ticksFlown = 0;
		hazardTicks = 0;
		destination = null;
		destinationName = null;
		destinationExact = false;
		destinationArrival = null;
		SURFACE_CACHE.clear();
		BELOW_CACHE.clear();
	}

	/** Reports distance, direction and height to the nearest landing spot within glide range, or that there is none. */
	public static void narrate(Minecraft client, LocalPlayer player) {
		Spot spot = findSpot(player);
		if (spot == null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_none"));
			return;
		}
		Vec3 from = player.position();
		Vec3 to = Vec3.atBottomCenterOf(spot.feet());
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_direction",
				(int) Math.round(spot.distance()), CameraUtil.compassDirectionTo(from, to),
				(int) Math.round(from.y() - to.y())));
	}

	/** Takes over the glide and flies the player onto the nearest landing spot. */
	public static void start(Minecraft client, LocalPlayer player) {
		if (!player.isFallFlying()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_not_flying"));
			return;
		}
		if (isActive()) {
			reset();
		}
		if (AutoWalkController.isActive()) {
			AutoWalkController.cancel(client, player);
		}

		Spot spot = findSpot(player);
		if (spot == null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_none"));
			return;
		}
		Vec3 from = player.position();
		engage(client, player, spot, Component.translatable("united_minecraft.narrate.elytra_landing_started",
				(int) Math.round(spot.distance()), CameraUtil.compassDirectionTo(from, Vec3.atBottomCenterOf(spot.feet()))));
	}

	/**
	 * Starts the autopilot on {@code spot}, announcing {@code started} once it has taken over. False, after
	 * saying so, when the autopilot is not lined up for the spot and too low to circle down to it: better to
	 * say that than to start something that can only end short of it.
	 */
	private static boolean engage(Minecraft client, LocalPlayer player, Spot spot, Component started) {
		Level level = player.level();
		ElytraAutopilot pilot = new ElytraAutopilot(spot.feet().getX() + 0.5, spot.feet().getY(), spot.feet().getZ() + 0.5,
				new LevelProbe(level, spot.feet().getY() - COVERED_DEPTH, spot.feet().getY()));
		SURFACE_CACHE.clear();
		BELOW_CACHE.clear();
		ElytraAutopilot.Command first = pilot.step(stateOf(player));
		if (first.phase() == ElytraAutopilot.Phase.ORBIT && player.getY() - spot.feet().getY() < MIN_START_HEIGHT_TO_CIRCLE) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_too_low"));
			return false;
		}

		autopilot = pilot;
		target = spot;
		announcedPhase = null;
		ticksFlown = 0;
		hazardTicks = 0;
		client.getNarrator().saySystemNow(started);
		AngleAssistController.disable(client);
		startRecording(player, spot, pilot, level, first);
		apply(player, first);
		return true;
	}

	/**
	 * Flies to somewhere the player chose, such as a Scanner target, while gliding. The target itself is
	 * rarely somewhere to land (a tree, an ore in a cave, a mob, the water), so this lands on the nearest
	 * safe patch to it within {@link #NEAR_TARGET_RADIUS} blocks, and says how far off it ended up once
	 * down. Says why instead when there is no such patch, the player is too low to reach one, or terrain
	 * is in the way. Never uses fireworks.
	 *
	 * @param onArrival what to do once the player is standing at the target - run straight away if the
	 *        landing is already there, or after the auto-walk that follows it, if that setting is on
	 * @param exact whether the target is a cell to stand in rather than a block to stand beside, as for
	 *        {@link AutoWalkController#startExact}
	 */
	public static void startToward(Minecraft client, LocalPlayer player, BlockPos wanted, Component name, Runnable onArrival, boolean exact) {
		if (isActive()) {
			reset();
		}
		if (AutoWalkController.isActive()) {
			AutoWalkController.cancel(client, player);
		}
		Approach approach = findSpotNear(player, wanted);
		if (approach.spot() == null) {
			client.getNarrator().saySystemNow(approach.failure(name));
			return;
		}
		Spot spot = approach.spot();
		// What matters is how close the landing will be to the target, not how far off the spot is from here.
		Vec3 targetCentre = Vec3.atBottomCenterOf(wanted);
		Vec3 spotCentre = Vec3.atBottomCenterOf(spot.feet());
		int offset = (int) Math.round(Math.hypot(spotCentre.x() - targetCentre.x(), spotCentre.z() - targetCentre.z()));
		Component started = offset <= ARRIVED_DISTANCE
				? Component.translatable("united_minecraft.narrate.elytra_target_started_at", name)
				: Component.translatable("united_minecraft.narrate.elytra_target_started", name, offset,
						CameraUtil.compassDirectionTo(targetCentre, spotCentre));
		if (!engage(client, player, spot, started)) {
			return;
		}
		destination = wanted;
		destinationName = name;
		destinationExact = exact;
		destinationArrival = onArrival;
		ElytraFlightRecorder.row(String.format(java.util.Locale.ROOT, "# destination,%d,%d,%d", wanted.getX(), wanted.getY(), wanted.getZ()));
	}

	/** Says how far from the destination the flight ended, and walks the rest of the way if the player has asked for that. */
	private static void finishAtDestination(Minecraft client, LocalPlayer player, BlockPos wanted, Component name,
			boolean exact, Runnable onArrival) {
		Vec3 to = Vec3.atBottomCenterOf(wanted);
		int distance = (int) Math.round(Math.hypot(to.x() - player.getX(), to.z() - player.getZ()));
		if (distance <= ARRIVED_DISTANCE) {
			client.getNarrator().saySystemQueued(Component.translatable("united_minecraft.narrate.elytra_target_landed_at", name));
			if (onArrival != null) {
				onArrival.run();
			}
			return;
		}
		Component direction = CameraUtil.compassDirectionTo(player.position(), to);
		if (UnitedMinecraftConfig.get().elytraAutoWalkAfterLanding) {
			AutoWalkController.startAnnounced(client, player, wanted, name, onArrival, exact,
					Component.translatable("united_minecraft.narrate.elytra_target_landed_walking", distance, direction, name));
		} else {
			client.getNarrator().saySystemQueued(
					Component.translatable("united_minecraft.narrate.elytra_target_landed", distance, direction, name));
		}
	}

	/** How high above a spot {@code distance} blocks away (horizontally) the player needs to be to glide to it. */
	static double heightNeeded(double distance) {
		return distance / GLIDE_RATIO + FLARE_HEIGHT;
	}

	/** The flyable landing spot near a destination, or why there is none. */
	private record Approach(Spot spot, boolean anySafe, boolean blocked, double extraHeight) {
		Component failure(Component name) {
			if (!anySafe) {
				return Component.translatable("united_minecraft.narrate.elytra_target_no_landing", name);
			}
			if (blocked) {
				return Component.translatable("united_minecraft.narrate.elytra_target_blocked", name);
			}
			return Component.translatable("united_minecraft.narrate.elytra_target_too_low", name, (int) Math.ceil(extraHeight));
		}
	}

	/**
	 * The flyable landing spot nearest to {@code wanted}: searched outward from it, so the first hits
	 * are the nearest, with the same flat-and-safe, within-glide-range and clear-line rules as {@link
	 * #findSpot}. When nothing qualifies, says which of those it was that failed.
	 */
	private static Approach findSpotNear(LocalPlayer player, BlockPos wanted) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		// The best spot by placementCost, plus a penalty for one without room to fly in and slide out.
		Spot best = null;
		double bestCost = Double.MAX_VALUE;
		boolean anySafe = false;
		boolean blocked = false;
		double extraHeight = Double.MAX_VALUE;
		HeightAt heights = (hx, hz) -> level.hasChunkAt(new BlockPos(hx, 0, hz))
				? level.getHeight(Heightmap.Types.MOTION_BLOCKING, hx, hz) : Integer.MIN_VALUE;
		LevelProbe probe = new LevelProbe(level, wanted.getY() - COVERED_DEPTH, wanted.getY());
		BELOW_CACHE.clear();

		for (int r = 0; r <= NEAR_TARGET_RADIUS; r++) {
			if (best != null && r > bestCost + 1) {
				break;
			}
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = wanted.getX() + dx;
					int z = wanted.getZ() + dz;
					if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
						continue;
					}
					int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
					// Two places to land in a column: on top of it, and - where something is built over
					// the destination, like a platform higher up - on the ground under that, beside the
					// target. The column's surface is only the first of them.
					for (int layer = 0; layer < 2; layer++) {
						boolean covered = layer == 1;
						int top = surface;
						if (covered) {
							top = (int) Math.floor(probe.heightBelow(x + 0.5, wanted.getY() + 1.5, z + 0.5));
							if (top <= wanted.getY() - COVERED_DEPTH || top + COVERED_HEADROOM > surface) {
								continue;
							}
						}
						if (covered ? !isFlatSafeFloor(level, x, top, z) : !isFlatSafePatch(level, x, top, z)) {
							continue;
						}
						anySafe = true;
						double height = player.getY() - top;
						double distance = Math.hypot(x + 0.5 - player.getX(), z + 0.5 - player.getZ());
						if (!inGlideRange(height, distance)) {
							extraHeight = Math.min(extraHeight, heightNeeded(distance) - height);
							continue;
						}
						// Under something there is no straight line down to it; the autopilot finds the way in
						// from the side, and says so if there isn't one.
						Vec3 aim = new Vec3(x + 0.5, top + APPROACH_AIM_HEIGHT, z + 0.5);
						if (!covered && level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
							blocked = true;
							continue;
						}
						double cost = placementCost(Math.hypot(dx, dz), top - wanted.getY());
						int floorTop = top;
						HeightAt around = covered ? (hx, hz) -> level.hasChunkAt(new BlockPos(hx, 0, hz))
								? (int) Math.floor(probe.heightBelow(hx + 0.5, floorTop + 2.0, hz + 0.5)) : Integer.MIN_VALUE
								: heights;
						// Looked up last, being the dear part, and only for a spot that could still win.
						if (cost < bestCost && (cost += hasRunway(around, x, top, z) ? 0.0 : NO_RUNWAY_PENALTY) < bestCost) {
							best = new Spot(new BlockPos(x, top, z), distance);
							bestCost = cost;
						}
					}
				}
			}
		}
		return new Approach(best, anySafe, blocked, extraHeight);
	}

	/**
	 * How good a spot is to land on for a destination: its distance from it, plus a penalty for each block
	 * it is above or below it beyond {@link #HEIGHT_SLACK}. Otherwise the roof over a door is as good a
	 * place as the step in front of it - nearer, even - and the flight ends clipping the roof's edge
	 * from below. The penalty tops out at {@link #MAX_HEIGHT_PENALTY}, so ground only wins over a roof that is
	 * right over the destination when it is within about that many blocks of it: a roof is the place to
	 * land on a tower or a farm, and walking further than that to avoid one is not worth it. Lower is better;
	 * never less than the distance.
	 */
	static double placementCost(double distance, int heightDifference) {
		int difference = Math.abs(heightDifference);
		return distance + Math.min(MAX_HEIGHT_PENALTY, Math.max(0, difference - HEIGHT_SLACK) * HEIGHT_PENALTY)
				+ Math.max(0, difference - FAR_HEIGHT) * FAR_HEIGHT_PENALTY;
	}

	/** The height of the highest motion-blocking block's top in a column - {@code Integer.MIN_VALUE} where nothing is loaded. */
	interface HeightAt {
		int height(int x, int z);
	}

	/**
	 * Whether a spot has room to be landed on: some direction along which a glider can come in over
	 * clear ground for {@link #RUNWAY} blocks (allowing the rise a glide of {@link #GLIDE_RATIO} clears)
	 * and carry on sliding for {@link #RUNOUT} blocks after touching down, a block either side of that
	 * line included. A flat patch up against the wall of a building has none, however close it is to
	 * the door: the only ways in are through the wall.
	 */
	static boolean hasRunway(HeightAt heights, int x, int top, int z) {
		for (int i = 0; i < RUNWAY_DIRECTIONS; i++) {
			double angle = 2 * Math.PI * i / RUNWAY_DIRECTIONS;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			if (lineIsClear(heights, x, top, z, dx, dz, RUNWAY, true) && lineIsClear(heights, x, top, z, -dx, -dz, RUNOUT, false)) {
				return true;
			}
		}
		return false;
	}

	private static boolean lineIsClear(HeightAt heights, int x, int top, int z, double dx, double dz, int length, boolean rising) {
		for (int k = 1; k <= length; k++) {
			// A glide in comes down from above, so ground can be higher the further out it is.
			int allowed = top + 1 + (rising ? (int) (k / GLIDE_RATIO) : 0);
			for (int side = -1; side <= 1; side++) {
				int cx = (int) Math.floor(x + 0.5 + dx * k - dz * side);
				int cz = (int) Math.floor(z + 0.5 + dz * k + dx * side);
				int h = heights.height(cx, cz);
				if (h > allowed) {
					return false;
				}
				// Sliding on past the spot, an edge is as bad as a wall; ground not loaded is no edge.
				if (!rising && h != Integer.MIN_VALUE && h < top - 1) {
					return false;
				}
			}
		}
		return true;
	}

	public static void cancel(Minecraft client, LocalPlayer player) {
		if (!isActive()) {
			return;
		}
		ElytraFlightRecorder.end("cancelled", endState(player));
		reset();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_cancelled"));
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!isActive()) {
			return;
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_STOP_LOCK)) {
			cancel(client, player);
			return;
		}
		if (!player.isFallFlying()) {
			boolean landed = player.onGround();
			ElytraFlightRecorder.end(landed ? "touchdown" : "stopped-gliding", endState(player));
			BlockPos wanted = destination;
			Component wantedName = destinationName;
			boolean wantedExact = destinationExact;
			Runnable wantedArrival = destinationArrival;
			reset();
			// A touchdown is just the result of the landing, so it waits its turn; stopping the
			// glide anywhere but the ground means falling, and is said at once.
			if (landed && wanted != null) {
				finishAtDestination(client, player, wanted, wantedName, wantedExact, wantedArrival);
			} else if (landed) {
				client.getNarrator().saySystemQueued(Component.translatable("united_minecraft.narrate.elytra_landing_touchdown"));
			} else {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_ended"));
			}
			return;
		}

		if (player.onGround()) {
			// Down, but the glide has not ended: left alone it carries on across the ground for several
			// ticks at nearly the speed it came in, into whatever is there - a wall at the speed of the
			// glide, or off the edge of a platform. Stop it where it touched down.
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(0.0, Math.min(v.y(), 0.0), 0.0);
			return;
		}
		if (player.getY() < target.feet().getY() - MISSED_BELOW) {
			// Below the spot: it can't be landed on now, and flying on only makes the fall longer.
			ElytraFlightRecorder.end("missed", endState(player));
			reset();
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_missed"));
			return;
		}
		SURFACE_CACHE.clear();
		if (ticksFlown % BELOW_CACHE_TICKS == 0) {
			BELOW_CACHE.clear();
		}
		ElytraAutopilot.Command command = autopilot.step(stateOf(player));

		hazardTicks = command.hazard() ? hazardTicks + 1 : 0;
		if (hazardTicks >= HAZARD_TICKS_TO_ABORT) {
			ElytraFlightRecorder.end("hazard", endState(player));
			reset();
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_hazard"));
			return;
		}

		if (command.phase() != announcedPhase) {
			announcedPhase = command.phase();
			// Queued, so the first one doesn't cut off the message that announced the flight.
			client.getNarrator().saySystemQueued(Component.translatable(announcedPhase == ElytraAutopilot.Phase.ORBIT
					? "united_minecraft.narrate.elytra_landing_circling" : "united_minecraft.narrate.elytra_landing_final"));
		}
		ticksFlown++;
		recordTick(player, command);
		if (ticksFlown % STATUS_INTERVAL_TICKS == 0) {
			Vec3 to = Vec3.atBottomCenterOf(target.feet());
			double horizontal = Math.hypot(to.x() - player.getX(), to.z() - player.getZ());
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_status",
					(int) Math.round(horizontal), (int) Math.round(player.getY() - to.y())));
		}
		apply(player, command);
	}

	/** Opens the flight's log: what it is headed for, where it starts, and the ground around the spot as the autopilot sees it. */
	private static void startRecording(LocalPlayer player, Spot spot, ElytraAutopilot pilot, Level level, ElytraAutopilot.Command first) {
		BlockPos feet = spot.feet();
		List<String> lines = new ArrayList<>();
		lines.add("# elytra landing flight log");
		lines.add(String.format(Locale.ROOT, "# spot,%d,%d,%d", feet.getX(), feet.getY(), feet.getZ()));
		lines.add(destination == null ? "# destination,none" : String.format(Locale.ROOT, "# destination,%d,%d,%d",
				destination.getX(), destination.getY(), destination.getZ()));
		lines.add("# dimension," + level.dimension());
		lines.add(String.format(Locale.ROOT, "# start,%.2f,%.2f,%.2f,v=%.3f,%.3f,%.3f,yaw=%.1f,pitch=%.1f",
				player.getX(), player.getY(), player.getZ(), player.getDeltaMovement().x(), player.getDeltaMovement().y(),
				player.getDeltaMovement().z(), player.getYRot(), player.getXRot()));
		lines.add("# first-phase," + first.phase() + ",predicted-miss=" + String.format(Locale.ROOT, "%.2f", first.predictedMiss()));
		lines.add("# G-columns,x,z,surface,below (ground a flight just over the spot meets)");
		LevelProbe probe = new LevelProbe(level, feet.getY() - COVERED_DEPTH, feet.getY());
		for (int dx = -RECORDED_RADIUS; dx <= RECORDED_RADIUS; dx++) {
			for (int dz = -RECORDED_RADIUS; dz <= RECORDED_RADIUS; dz++) {
				int x = feet.getX() + dx;
				int z = feet.getZ() + dz;
				lines.add(String.format(Locale.ROOT, "G,%d,%d,%.0f,%.0f", x, z,
						probe.height(x + 0.5, z + 0.5), probe.heightBelow(x + 0.5, feet.getY() + 2.0, z + 0.5)));
			}
		}
		lines.add("# T-columns,tick,x,y,z,vx,vy,vz,yaw,pitch,cmdYaw,cmdPitch,phase,predictedMiss,hazard,onGround");
		ElytraFlightRecorder.begin(lines);
	}

	private static void recordTick(LocalPlayer player, ElytraAutopilot.Command command) {
		if (!ElytraFlightRecorder.isRecording()) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		ElytraFlightRecorder.row(String.format(Locale.ROOT, "T,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%.1f,%.1f,%.1f,%s,%.2f,%b,%b",
				ticksFlown, player.getX(), player.getY(), player.getZ(), v.x(), v.y(), v.z(), player.getYRot(), player.getXRot(),
				command.yaw(), command.pitch(), command.phase(), command.predictedMiss(), command.hazard(), player.onGround()));
	}

	/** Where the player is and how far from the spot, for the last line of a flight's log. */
	private static String endState(LocalPlayer player) {
		Vec3 v = player.getDeltaMovement();
		String away = target == null ? "" : String.format(Locale.ROOT, ",spot-offset=%.2f,height-over-spot=%.2f",
				Math.hypot(target.feet().getX() + 0.5 - player.getX(), target.feet().getZ() + 0.5 - player.getZ()),
				player.getY() - target.feet().getY());
		return String.format(Locale.ROOT, "pos=%.2f;%.2f;%.2f,v=%.3f;%.3f;%.3f,onGround=%b%s",
				player.getX(), player.getY(), player.getZ(), v.x(), v.y(), v.z(), player.onGround(), away);
	}

	private static ElytraAutopilot.State stateOf(LocalPlayer player) {
		Vec3 velocity = player.getDeltaMovement();
		return new ElytraAutopilot.State(player.getX(), player.getY(), player.getZ(),
				velocity.x(), velocity.y(), velocity.z(), player.getYRot(), player.getXRot());
	}

	private static void apply(LocalPlayer player, ElytraAutopilot.Command command) {
		player.setYRot(command.yaw());
		player.setXRot(command.pitch());
		player.setYHeadRot(command.yaw());
	}

	/** The top surface of the column at {@code (x, z)}, or {@code fallback} if that part of the world isn't loaded. */
	private static double surfaceHeight(Level level, double x, double z, double fallback) {
		int blockX = (int) Math.floor(x);
		int blockZ = (int) Math.floor(z);
		long key = (((long) blockX) << 32) ^ (blockZ & 0xffffffffL);
		Double cached = SURFACE_CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		double height = level.hasChunkAt(new BlockPos(blockX, 0, blockZ))
				? level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ)
				: fallback;
		SURFACE_CACHE.put(key, height);
		return height;
	}

	/**
	 * The ground as the autopilot sees it. Its surface in a column is the first thing a flight from above
	 * would meet, but one flying under something - a platform over the landing, say - meets what is
	 * below it, and only counts what is no deeper than {@code lowest}.
	 */
	private static final class LevelProbe implements ElytraAutopilot.GroundProbe {
		private final Level level;
		private final int lowest;
		private final double fallback;

		LevelProbe(Level level, int lowest, double fallback) {
			this.level = level;
			this.lowest = lowest;
			this.fallback = fallback;
		}

		@Override
		public double height(double x, double z) {
			return surfaceHeight(level, x, z, fallback);
		}

		@Override
		public double heightBelow(double x, double y, double z) {
			double top = height(x, z);
			if (y >= top) {
				return top;
			}
			int blockX = (int) Math.floor(x);
			int blockZ = (int) Math.floor(z);
			long key = (((long) blockX) << 32) ^ (blockZ & 0xffffffffL);
			double[] known = BELOW_CACHE.get(key);
			if (known != null && y >= known[0] && y <= known[1]) {
				return known[0];
			}
			// Down from the flight until something solid (or liquid): its top is the ground. Starting inside
			// something solid finds that straight away, with its top above the flight - which is a hit.
			int blockY = (int) Math.floor(y);
			// Nothing solid down to the limit is no ground at all, not ground at the limit: a flight below
			// the surface of a platform it is passing under must not be taken to have landed under it.
			double surface = lowest - NO_GROUND_DEPTH;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int by = blockY; by >= lowest; by--) {
				pos.set(blockX, by, blockZ);
				BlockState state = level.getBlockState(pos);
				if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) {
					surface = by + 1;
					break;
				}
			}
			BELOW_CACHE.put(key, new double[] {surface, blockY + 0.999});
			return surface;
		}
	}

	private static Spot findSpot(LocalPlayer player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		int px = player.blockPosition().getX();
		int pz = player.blockPosition().getZ();

		Spot best = null;
		// Search outward in square rings, so the first hits found are the nearest ones and the
		// search can stop as soon as no ring left could beat what it already has.
		for (int r = 0; r <= SEARCH_RADIUS; r++) {
			if (best != null && r > best.distance() + 1) {
				break;
			}
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					Spot candidate = candidateAt(level, player, eye, px + dx, pz + dz);
					if (candidate != null && (best == null || candidate.distance() < best.distance())) {
						best = candidate;
					}
				}
			}
		}
		return best;
	}

	/** Whether a spot {@code height} below and {@code distance} away (horizontally) is close enough to glide to. */
	static boolean inGlideRange(double height, double distance) {
		return height > FLARE_HEIGHT && distance <= (height - FLARE_HEIGHT) * GLIDE_RATIO;
	}

	private static Spot candidateAt(Level level, LocalPlayer player, Vec3 eye, int x, int z) {
		if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
			return null;
		}
		// The first block with nothing solid (or liquid) in it above this column - so a tree canopy,
		// a roof or the surface of a lake is what gets found, and then rejected below, rather than
		// the ground hidden underneath.
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		double height = player.getY() - top;
		double distance = Math.hypot(x + 0.5 - player.getX(), z + 0.5 - player.getZ());
		if (!inGlideRange(height, distance)) {
			return null;
		}
		if (!isFlatSafePatch(level, x, top, z)) {
			return null;
		}
		Vec3 aim = new Vec3(x + 0.5, top + APPROACH_AIM_HEIGHT, z + 0.5);
		if (level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
			return null;
		}
		return new Spot(new BlockPos(x, top, z), distance);
	}

	/**
	 * Like {@link #isFlatSafePatch}, for ground with something built over it, so the heightmap says nothing about
	 * it: a 3x3 patch whose every block is a safe place to land on, with room to stand over all of it.
	 */
	private static boolean isFlatSafeFloor(Level level, int x, int top, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos ground = new BlockPos(x + dx, top - 1, z + dz);
				BlockState state = level.getBlockState(ground);
				if (!state.isFaceSturdy(level, ground, Direction.UP)
						|| state.is(BlockTags.LEAVES)
						|| state.is(BlockTags.CAMPFIRES)
						|| state.is(Blocks.MAGMA_BLOCK)) {
					return false;
				}
				for (int up = 0; up < COVERED_HEADROOM; up++) {
					BlockPos space = ground.above(1 + up);
					BlockState inside = level.getBlockState(space);
					if (!inside.getCollisionShape(level, space).isEmpty() || !inside.getFluidState().isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** A 3x3 patch centred on the column, all at {@code top}, every block a safe place to land on. */
	private static boolean isFlatSafePatch(Level level, int x, int top, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz) != top) {
					return false;
				}
				BlockPos ground = new BlockPos(x + dx, top - 1, z + dz);
				BlockState state = level.getBlockState(ground);
				if (!state.isFaceSturdy(level, ground, Direction.UP)
						|| state.is(BlockTags.LEAVES)
						|| state.is(BlockTags.CAMPFIRES)
						|| state.is(Blocks.MAGMA_BLOCK)) {
					return false;
				}
			}
		}
		return true;
	}
}
