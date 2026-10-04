package com.nibblenerds.unitedminecraft.client;

import java.util.HashMap;
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

	private static ElytraAutopilot autopilot;
	private static Spot target;
	private static ElytraAutopilot.Phase announcedPhase;
	private static int ticksFlown;
	private static int hazardTicks;
	// Column surface heights already looked up this tick - the autopilot asks for the same columns
	// over and over while predicting, and every one of them is a heightmap read.
	private static final Map<Long, Double> SURFACE_CACHE = new HashMap<>();

	private ElytraLandingController() {
	}

	/** The nearest landing spot: where to stand ({@code feet}) and how far away it is horizontally. */
	private record Spot(BlockPos feet, double distance) {
	}

	public static boolean isActive() {
		return autopilot != null;
	}

	public static void reset() {
		autopilot = null;
		target = null;
		announcedPhase = null;
		ticksFlown = 0;
		hazardTicks = 0;
		SURFACE_CACHE.clear();
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
		Level level = player.level();
		ElytraAutopilot pilot = new ElytraAutopilot(spot.feet().getX() + 0.5, spot.feet().getY(), spot.feet().getZ() + 0.5,
				(x, z) -> surfaceHeight(level, x, z, spot.feet().getY()));
		SURFACE_CACHE.clear();
		ElytraAutopilot.Command first = pilot.step(stateOf(player));
		if (first.phase() == ElytraAutopilot.Phase.ORBIT && player.getY() - spot.feet().getY() < MIN_START_HEIGHT_TO_CIRCLE) {
			// Not lined up for the spot and too low to circle down to it: better to say so than to
			// start something that can only end short of it.
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_too_low"));
			return;
		}

		autopilot = pilot;
		target = spot;
		announcedPhase = null;
		ticksFlown = 0;
		hazardTicks = 0;
		Vec3 from = player.position();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_started",
				(int) Math.round(spot.distance()), CameraUtil.compassDirectionTo(from, Vec3.atBottomCenterOf(spot.feet()))));
		apply(player, first);
	}

	public static void cancel(Minecraft client, LocalPlayer player) {
		if (!isActive()) {
			return;
		}
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
			reset();
			// A touchdown is just the result of the landing, so it waits its turn; stopping the
			// glide anywhere but the ground means falling, and is said at once.
			if (landed) {
				client.getNarrator().saySystemQueued(Component.translatable("united_minecraft.narrate.elytra_landing_touchdown"));
			} else {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_ended"));
			}
			return;
		}

		SURFACE_CACHE.clear();
		ElytraAutopilot.Command command = autopilot.step(stateOf(player));

		hazardTicks = command.hazard() ? hazardTicks + 1 : 0;
		if (hazardTicks >= HAZARD_TICKS_TO_ABORT) {
			reset();
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_hazard"));
			return;
		}

		if (command.phase() != announcedPhase) {
			announcedPhase = command.phase();
			client.getNarrator().saySystemNow(Component.translatable(announcedPhase == ElytraAutopilot.Phase.ORBIT
					? "united_minecraft.narrate.elytra_landing_circling" : "united_minecraft.narrate.elytra_landing_final"));
		}
		ticksFlown++;
		if (ticksFlown % STATUS_INTERVAL_TICKS == 0) {
			Vec3 to = Vec3.atBottomCenterOf(target.feet());
			double horizontal = Math.hypot(to.x() - player.getX(), to.z() - player.getZ());
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_landing_status",
					(int) Math.round(horizontal), (int) Math.round(player.getY() - to.y())));
		}
		apply(player, command);
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
