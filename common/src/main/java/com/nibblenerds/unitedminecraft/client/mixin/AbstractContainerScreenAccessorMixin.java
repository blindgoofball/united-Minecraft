package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.nibblenerds.unitedminecraft.client.access.SlotClickAccess;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/** Exposes {@code AbstractContainerScreen#slotClicked} - see {@link SlotClickAccess}. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenAccessorMixin implements SlotClickAccess {
	@Invoker("slotClicked")
	@Override
	public abstract void unitedMinecraft$slotClicked(Slot slot, int slotId, int button, ContainerInput input);
}
