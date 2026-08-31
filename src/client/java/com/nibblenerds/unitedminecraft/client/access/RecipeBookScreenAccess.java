package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.AbstractRecipeBookScreenAccessorMixin}
 * to expose {@code AbstractRecipeBookScreen}'s private {@code recipeBookComponent} - vanilla's
 * own recipe-book overlay widget, which {@link com.nibblenerds.unitedminecraft.client.MenuAccessibilityController}
 * needs to reach (via {@link RecipeBookComponentAccess}) in order to mirror a Scanner-style
 * search term into vanilla's own search box, so the on-screen panel (when visible) filters the
 * same way this mod's own recipe-book narration does.
 */
public interface RecipeBookScreenAccess {
	RecipeBookComponent<?> unitedMinecraft$getRecipeBookComponent();
}
