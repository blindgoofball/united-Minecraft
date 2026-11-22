package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.nibblenerds.unitedminecraft.client.access.RecipeBookPageAccess;

import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;

/** Exposes the Recipe Book page's private state - see {@link RecipeBookPageAccess}. */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageAccessorMixin implements RecipeBookPageAccess {
	@Accessor("recipeCollections")
	@Override
	public abstract List<RecipeCollection> unitedMinecraft$collections();

	@Accessor("buttons")
	@Override
	public abstract List<RecipeButton> unitedMinecraft$buttons();

	@Accessor("currentPage")
	@Override
	public abstract int unitedMinecraft$currentPage();

	@Accessor("currentPage")
	@Override
	public abstract void unitedMinecraft$setCurrentPage(int page);

	@Invoker("updateButtonsForPage")
	@Override
	public abstract void unitedMinecraft$updateButtonsForPage();
}
