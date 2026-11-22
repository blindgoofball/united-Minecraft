package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nibblenerds.unitedminecraft.client.ItemDescriptions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * A slot's narration names its item and how many there are, which for a bundle is "Bundle" and no
 * more: how full it is, and what is in it, only appear as a picture in the tooltip. Add them, the
 * stack that would be taken out first leading, as {@link ItemDescriptions} always has.
 */
@Mixin(SlotWidget.class)
public abstract class SlotWidgetMixin {
	@Inject(method = "getItemNarration", at = @At("RETURN"), cancellable = true)
	private static void unitedMinecraft$addBundleContents(ItemStack stack, CallbackInfoReturnable<Component> cir) {
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle == null || Minecraft.getInstance().player == null) {
			return;
		}
		cir.setReturnValue(Component.empty()
				.append(cir.getReturnValue())
				.append(Component.literal(", "))
				.append(ItemDescriptions.describeBundle(bundle, Minecraft.getInstance().player)));
	}
}
