package com.nibblenerds.unitedminecraft.client.access;

import java.util.List;

import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.RecipeBookPageAccessorMixin}
 * to reach the Recipe Book page's private list of every recipe group it can show, its page
 * buttons and its current page, so a letter can jump to a recipe on a page that is not showing.
 */
public interface RecipeBookPageAccess {
	/** Every recipe group the page has, across all its pages, in the order they are shown. */
	List<RecipeCollection> unitedMinecraft$collections();

	/** The buttons that show one page of them; the same widgets are reused as the page changes. */
	List<RecipeButton> unitedMinecraft$buttons();

	int unitedMinecraft$currentPage();

	void unitedMinecraft$setCurrentPage(int page);

	/** Fills the buttons from the current page, and updates the page arrows. */
	void unitedMinecraft$updateButtonsForPage();
}
