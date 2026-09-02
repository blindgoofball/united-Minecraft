package com.nibblenerds.unitedminecraft.client;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Shared "describe this stack for narration" logic: name, count (if more than one),
 * durability (if damageable and the caller wants it), and every extra tooltip line vanilla
 * itself would show -
 * enchantments (on both enchanted books and enchanted gear), a music disc's actual song,
 * a goat horn's actual instrument, potion effects, attribute modifiers, and so on.
 *
 * <p>Reusing {@link ItemStack#getTooltipLines} for that last part means none of those need
 * hand-coding item by item here: whatever a sighted player would learn by hovering the
 * stack, this narrates too, generically, and stays correct automatically as vanilla (or
 * other mods) add more tooltip content to any given item. Line 0 of that list duplicates
 * the hover name already narrated above, so only the rest is genuinely new information.
 */
public final class ItemDescriptions {
	private ItemDescriptions() {
	}

	public static MutableComponent describe(ItemStack stack, Player player) {
		return describe(stack, player, true);
	}

	/**
	 * {@code includeDurability} exists for a mob's held/worn items (see {@code ScannerController}) -
	 * a monster's sword or armor being at 40/59 durability isn't actionable information, unlike
	 * the player's own hotbar or inventory, where it is.
	 */
	public static MutableComponent describe(ItemStack stack, Player player, boolean includeDurability) {
		MutableComponent name = stack.getCount() > 1
				? Component.literal(stack.getCount() + " ").append(stack.getHoverName())
				: stack.getHoverName().copy();

		if (includeDurability && stack.isDamageableItem()) {
			int remaining = stack.getMaxDamage() - stack.getDamageValue();
			name = name.append(Component.literal(", ")).append(Component.translatable(
					"united_minecraft.narrate.hotbar_durability", remaining, stack.getMaxDamage()));
		}

		List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL);
		for (int i = 1; i < tooltip.size(); i++) {
			Component line = tooltip.get(i);
			if (!line.getString().isBlank()) {
				name = name.append(Component.literal(", ")).append(line);
			}
		}
		return name;
	}
}
