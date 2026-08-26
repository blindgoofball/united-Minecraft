package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Whenever the log block you're looking at gets broken, automatically re-aims at an
 * adjacent log (preferring straight up, matching how you'd naturally chop up a trunk),
 * so felling a tree doesn't require re-aiming after every single block. Silent on
 * success, since narrating every single block during a fast chopping run would be
 * constant chatter; only speaks up once the chain runs out, so you know to stop swinging.
 *
 * <p>Only active during normal camera control - not build mode or scanner lock-on, which
 * already own the player's rotation for their own purposes.
 */
public final class TreeChoppingAssist {
	private static BlockPos trackedLogPos;

	private TreeChoppingAssist() {
	}

	public static void reset() {
		trackedLogPos = null;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		Level level = player.level();

		if (trackedLogPos != null) {
			double maxReach = player.blockInteractionRange() + 1.0;
			if (player.getEyePosition().distanceTo(Vec3.atCenterOf(trackedLogPos)) > maxReach) {
				trackedLogPos = null;
			} else if (!level.getBlockState(trackedLogPos).is(BlockTags.LOGS)) {
				// It just got broken since the last tick we checked.
				BlockPos next = findAdjacentLog(level, trackedLogPos);
				if (next != null) {
					CameraUtil.aimAt(player, Vec3.atCenterOf(next));
					trackedLogPos = next;
				} else {
					trackedLogPos = null;
					client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.tree_chopping_done"));
				}
				return;
			} else {
				return;
			}
		}

		// Not currently tracking anything; pick up whatever log (if any) is under the crosshair.
		HitResult hit = client.hitResult;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			if (level.getBlockState(pos).is(BlockTags.LOGS)) {
				trackedLogPos = pos.immutable();
			}
		}
	}

	private static BlockPos findAdjacentLog(Level level, BlockPos brokenPos) {
		BlockPos above = brokenPos.above();
		if (level.getBlockState(above).is(BlockTags.LOGS)) {
			return above;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					BlockPos neighbor = brokenPos.offset(dx, dy, dz);
					if (level.getBlockState(neighbor).is(BlockTags.LOGS)) {
						return neighbor;
					}
				}
			}
		}
		return null;
	}
}
