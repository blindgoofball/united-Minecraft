package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Proactively warns blind and low-vision players when they start mining a block with an
 * incorrect tool or insufficient tool tier (e.g., mining diamond ore with a stone pickaxe
 * or stone with bare hands), preventing wasted mining time and destroyed block drops.
 *
 * <p>Edge-triggered on the start of mining a block or switching held items while mining,
 * so it narrates only once per mining attempt rather than spamming every tick.
 */
public final class ToolHarvestAwarenessController {
	private static BlockPos lastWarnedPos;
	private static ItemStack lastHeldItem = ItemStack.EMPTY;
	private static boolean wasMining;

	private ToolHarvestAwarenessController() {
	}

	public static void register() {
		// AccessibilityTickHandler calls tick() directly
	}

	public static void reset() {
		lastWarnedPos = null;
		lastHeldItem = ItemStack.EMPTY;
		wasMining = false;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!UnitedMinecraftConfig.get().toolHarvestWarningEnabled) {
			reset();
			return;
		}

		if (client.level == null || client.level != player.level() || player.isSpectator() || player.isCreative()) {
			reset();
			return;
		}

		boolean isMiningKey = client.options.keyAttack.isDown();
		HitResult hit = client.hitResult;

		if (!isMiningKey || !(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			wasMining = false;
			lastWarnedPos = null;
			return;
		}

		BlockPos pos = blockHit.getBlockPos();
		Level level = player.level();
		BlockState state = level.getBlockState(pos);
		ItemStack held = player.getMainHandItem();

		if (!wasMining || !pos.equals(lastWarnedPos) || !ItemStack.matches(held, lastHeldItem)) {
			wasMining = true;
			lastWarnedPos = pos;
			lastHeldItem = held.copy();

			if (state.requiresCorrectToolForDrops() && !player.hasCorrectToolForDrops(state)) {
				Component blockName = state.getBlock().getName();
				Component message = getRequirementMessage(state, blockName);
				client.getNarrator().saySystemNow(message);
			}
		}
	}

	private static Component getRequirementMessage(BlockState state, Component blockName) {
		if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
			return Component.translatable("united_minecraft.narrate.tool_mismatch_diamond", blockName);
		}
		if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
			return Component.translatable("united_minecraft.narrate.tool_mismatch_iron", blockName);
		}
		if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
			return Component.translatable("united_minecraft.narrate.tool_mismatch_stone", blockName);
		}
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return Component.translatable("united_minecraft.narrate.tool_mismatch_pickaxe", blockName);
		}
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return Component.translatable("united_minecraft.narrate.tool_mismatch_axe", blockName);
		}
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return Component.translatable("united_minecraft.narrate.tool_mismatch_shovel", blockName);
		}
		if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
			return Component.translatable("united_minecraft.narrate.tool_mismatch_hoe", blockName);
		}
		return Component.translatable("united_minecraft.narrate.tool_mismatch_generic", blockName);
	}
}
