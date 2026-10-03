package com.nibblenerds.unitedminecraft.client;

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
 * Finds the nearest place an elytra glide can actually end safely, and says where it is - the
 * flying counterpart of {@link WaterExitController}'s "which way out", on the same key. Nothing
 * here moves the player; it only answers "is there somewhere I can land, and which way".
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
 * terrain, trees and builds, since that line is the glide path.</li>
 * </ul>
 */
public final class ElytraLandingController {
	// Blocks travelled forward per block of height lost. Real elytra glide is somewhat better than
	// this, but a landing needs room to turn onto the spot and to level off, so range is judged
	// conservatively rather than at the best case.
	private static final double GLIDE_RATIO = 6.0;
	// Height kept back above the spot for levelling off before touching down.
	private static final double FLARE_HEIGHT = 3.0;
	private static final int SEARCH_RADIUS = 96;
	private static final double APPROACH_AIM_HEIGHT = 2.0;

	private ElytraLandingController() {
	}

	/** The nearest landing spot: where to stand ({@code feet}) and how far away it is horizontally. */
	private record Spot(BlockPos feet, double distance) {
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
		if (height <= FLARE_HEIGHT || distance > (height - FLARE_HEIGHT) * GLIDE_RATIO) {
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
