package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.client.gui.components.EditBox;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.RecipeBookComponentAccessorMixin}
 * to expose {@code RecipeBookComponent}'s private search {@link EditBox} - vanilla's own
 * per-tick {@code checkSearchStringUpdate} already diffs this box's value against its own last
 * known search string and re-filters when it changes, so simply writing into it from
 * {@link com.nibblenerds.unitedminecraft.client.MenuAccessibilityController} is enough to
 * mirror our own recipe-book search term into vanilla's overlay without needing to also
 * reach or re-implement its private re-filtering logic.
 */
public interface RecipeBookComponentAccess {
	EditBox unitedMinecraft$getSearchBox();
}
