package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.RecipeBookScreenAccess;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

/**
 * Exposes {@code AbstractRecipeBookScreen}'s private {@code recipeBookComponent} field - there's
 * no public getter otherwise, matching the same need {@link AnvilScreenAccessorMixin} fills for
 * the anvil's rename box.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenAccessorMixin implements RecipeBookScreenAccess {
	@Shadow
	private RecipeBookComponent<?> recipeBookComponent;

	@Override
	public RecipeBookComponent<?> unitedMinecraft$getRecipeBookComponent() {
		return recipeBookComponent;
	}
}
