package com.nibblenerds.unitedminecraft.client.scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

/**
 * Shared utility for extracting and narrating sign text across crosshair narration,
 * surrounding scans, and sign editor interactions.
 */
public final class SignNarrationHelper {
	private SignNarrationHelper() {
	}

	/**
	 * Returns a full description of the sign including its block name and the text
	 * of the side the player is currently facing.
	 */
	public static Component describeSignBlock(Level level, BlockPos pos, Component blockName, LocalPlayer player) {
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
			return blockName;
		}
		SignTextSlot slot = sign.getSlotPlayerIsFacing(player);
		Component text = joinSignLines(sign.getText(slot));
		if (slot == SignTextSlot.BACK) {
			return text != null
					? Component.translatable("united_minecraft.narrate.sign_back_content", blockName, text)
					: Component.translatable("united_minecraft.narrate.sign_back_blank", blockName);
		} else {
			return text != null
					? Component.translatable("united_minecraft.narrate.sign_content", blockName, text)
					: Component.translatable("united_minecraft.narrate.sign_blank", blockName);
		}
	}

	/**
	 * The text of the side of the sign the player is facing, labelled with that side
	 * ("Front: ..."), or "Blank" if that side has no text. Used where the sign's name is
	 * already being narrated separately (the Scanner and the look-ahead read).
	 */
	public static Component describeFacingSide(SignBlockEntity sign, LocalPlayer player) {
		SignTextSlot slot = sign.getSlotPlayerIsFacing(player);
		Component text = joinSignLines(sign.getText(slot));
		if (text == null) {
			return Component.translatable("united_minecraft.narrate.scanner_sign_blank");
		}
		Component side = Component.translatable(slot == SignTextSlot.FRONT
				? "united_minecraft.narrate.sign_side_front"
				: "united_minecraft.narrate.sign_side_back");
		return Component.empty().append(side).append(Component.literal(": ")).append(text);
	}

	/**
	 * Joins non-empty lines of a {@link SignText} with commas, or returns null if all lines are blank.
	 */
	public static Component joinSignLines(SignText text) {
		if (text == null) {
			return null;
		}
		MutableComponent result = null;
		boolean filter = Minecraft.getInstance().isTextFilteringEnabled();
		for (Component line : text.getMessages(filter)) {
			String plain = line.getString().trim();
			if (plain.isEmpty()) {
				continue;
			}
			result = result == null
					? Component.literal(plain)
					: result.append(Component.literal(", ")).append(Component.literal(plain));
		}
		return result;
	}
}
