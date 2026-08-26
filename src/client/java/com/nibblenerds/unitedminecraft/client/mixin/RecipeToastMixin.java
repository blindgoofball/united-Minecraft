package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.ItemDescriptions;
import com.nibblenerds.unitedminecraft.client.ToastNarrationController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Narrates which recipe just unlocked - {@code RecipeToast}'s own title/description
 * ("Recipe unlocked!") is a fixed string that doesn't say which one, so this instead
 * names the actually-unlocked item, taken from {@code addItem}'s parameter rather than
 * the toast's own fields (which never store a {@link Component} at all here).
 */
@Mixin(RecipeToast.class)
public class RecipeToastMixin {
	@Inject(method = "addItem", at = @At("TAIL"))
	private void unitedMinecraft$narrate(ItemStack categoryItem, ItemStack unlockedItem, CallbackInfo ci) {
		LocalPlayer player = Minecraft.getInstance().player;
		Component name = player != null ? ItemDescriptions.describe(unlockedItem, player) : unlockedItem.getHoverName();
		ToastNarrationController.narrate(Component.translatable("united_minecraft.narrate.recipe_unlocked", name));
	}
}
