package com.nibblenerds.unitedminecraft.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/** Shared "describe this stack for narration" logic: name, count (if more than one), and durability (if damageable). */
public final class ItemDescriptions {
	private ItemDescriptions() {
	}

	public static MutableComponent describe(ItemStack stack) {
		MutableComponent name = stack.getCount() > 1
				? Component.literal(stack.getCount() + " ").append(stack.getHoverName())
				: stack.getHoverName().copy();

		if (stack.isDamageableItem()) {
			int remaining = stack.getMaxDamage() - stack.getDamageValue();
			name = name.append(Component.literal(", ")).append(Component.translatable(
					"united_minecraft.narrate.hotbar_durability", remaining, stack.getMaxDamage()));
		}
		return name;
	}
}
