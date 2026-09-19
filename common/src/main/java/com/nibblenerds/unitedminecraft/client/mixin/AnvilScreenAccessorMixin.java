package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.AnvilScreenAccess;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;

/**
 * Exposes {@code AnvilScreen}'s private rename {@code EditBox} - vanilla focuses it
 * automatically on open with no public accessor, which {@link
 * com.nibblenerds.unitedminecraft.client.MenuAccessibilityController} needs both to read
 * (for narration) and to explicitly re-focus when Tab brings the player back to its own
 * Rename section.
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenAccessorMixin implements AnvilScreenAccess {
	@Shadow
	private EditBox name;

	@Override
	public EditBox unitedMinecraft$getNameBox() {
		return name;
	}
}
