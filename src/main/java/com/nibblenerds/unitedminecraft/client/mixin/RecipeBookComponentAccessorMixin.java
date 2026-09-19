package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.RecipeBookComponentAccess;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

/**
 * Exposes {@code RecipeBookComponent}'s private search {@link EditBox} - there's no public
 * getter otherwise. See {@link com.nibblenerds.unitedminecraft.client.access.RecipeBookComponentAccess}
 * for why writing into it is sufficient (vanilla polls it for changes itself every tick).
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentAccessorMixin implements RecipeBookComponentAccess {
	@Shadow
	private EditBox searchBox;

	@Override
	public EditBox unitedMinecraft$getSearchBox() {
		return searchBox;
	}
}
