package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Toggleable ambient narration of whatever block the crosshair is resting on, so looking around
 * reads blocks the same way turning to face a new direction already narrates the compass octant.
 * Edge-triggered on the block's type only, not its exact position or state, so sweeping across a
 * stretch of the same block (a stone wall, say) doesn't repeat its name for every step - only an
 * actual change to a different block narrates. Air is never narrated (just treated as "nothing
 * targeted", same as being out of range entirely), since that's the common case while simply
 * looking around and would itself become the spam this feature exists to avoid.
 *
 * <p>Shares {@link SurroundingsScanner}'s fixed-range, gamemode-reach-independent raycast so both
 * features agree on what "looking at" means.
 */
public final class AutoCrosshairNarrationController {
	private static final double RANGE_BLOCKS = 6.0;

	private static boolean enabled;
	private static Block lastBlock;

	private AutoCrosshairNarrationController() {
	}

	public static void toggle(Minecraft client) {
		enabled = !enabled;
		lastBlock = null;
		client.getNarrator().saySystemNow(Component.translatable(enabled
				? "united_minecraft.narrate.auto_crosshair_narration_on"
				: "united_minecraft.narrate.auto_crosshair_narration_off"));
	}

	public static void reset() {
		enabled = false;
		lastBlock = null;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!enabled) {
			return;
		}
		Block current = lookedAtBlock(player);
		if (current == lastBlock) {
			return;
		}
		if (current != null) {
			client.getNarrator().saySystemNow(current.getName());
		}
		lastBlock = current;
	}

	/** Null for air, a fluid-only hit, or nothing within range - all narrated the same way: not at all. */
	private static Block lookedAtBlock(LocalPlayer player) {
		Level level = player.level();
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(RANGE_BLOCKS));

		BlockHitResult hit = level.clip(
				new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		return state.isAir() ? null : state.getBlock();
	}
}
