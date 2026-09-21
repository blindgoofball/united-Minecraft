package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Whenever the ore block you're looking at gets mined, automatically re-aims at another ore
 * within {@link #SEARCH_RADIUS} blocks that you actually have line of sight to - the same
 * {@link OreDetection#isExposed} check the Scanner's Ores category and the Mining Radar both
 * use, so this won't aim you at ore sealed behind an unmined wall just because it happens to be
 * nearby. Same idea as {@link TreeChoppingAssist} but for a vein instead of a trunk: nearest
 * visible candidate wins, and it's a one-shot re-aim rather than a real lock-on, so you're free
 * to look elsewhere afterward. Silent on success, for the same reason tree chopping is - only
 * speaks up once nothing more is in range and sight, so you know to stop mining.
 *
 * <p>Only active during normal camera control - not build mode or scanner lock-on, which
 * already own the player's rotation for their own purposes.
 */
public final class OreMiningAssist {
	private static final int SEARCH_RADIUS = 3;

	private static BlockPos trackedOrePos;

	private OreMiningAssist() {
	}

	public static void reset() {
		trackedOrePos = null;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		Level level = player.level();

		if (trackedOrePos != null) {
			double maxReach = player.blockInteractionRange() + 1.0;
			if (player.getEyePosition().distanceTo(Vec3.atCenterOf(trackedOrePos)) > maxReach) {
				trackedOrePos = null;
			} else if (!OreDetection.isValuableOre(level.getBlockState(trackedOrePos))) {
				// It just got mined since the last tick we checked.
				BlockPos next = findNearbyVisibleOre(level, trackedOrePos, player.getEyePosition());
				if (next != null) {
					CameraUtil.aimAt(player, Vec3.atCenterOf(next));
					trackedOrePos = next;
				} else {
					trackedOrePos = null;
					client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.ore_mining_done"));
				}
				return;
			} else {
				return;
			}
		}

		// Not currently tracking anything; pick up whatever ore (if any) is under the crosshair.
		HitResult hit = client.hitResult;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			if (OreDetection.isValuableOre(level.getBlockState(pos))) {
				trackedOrePos = pos.immutable();
			}
		}
	}

	/**
	 * The nearest still-standing ore within {@link #SEARCH_RADIUS} blocks of {@code brokenPos}
	 * (real straight-line distance, not a cube) that {@code eye} has genuine line of sight to.
	 */
	private static BlockPos findNearbyVisibleOre(Level level, BlockPos brokenPos, Vec3 eye) {
		BlockPos best = null;
		double bestDistSq = Double.MAX_VALUE;
		double maxDistSq = (double) SEARCH_RADIUS * SEARCH_RADIUS;
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
				for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					double distSq = (double) (dx * dx + dy * dy + dz * dz);
					if (distSq > maxDistSq || distSq >= bestDistSq) {
						continue;
					}
					BlockPos candidate = brokenPos.offset(dx, dy, dz);
					BlockState state = level.getBlockState(candidate);
					if (OreDetection.isValuableOre(state) && OreDetection.isExposed(level, candidate, eye)) {
						best = candidate;
						bestDistSq = distSq;
					}
				}
			}
		}
		return best;
	}
}
