package com.nibblenerds.unitedminecraft.client;

/**
 * Finer-grained than {@link KeybindContext#CONTAINER_SCREEN} for rebind-conflict detection only
 * - two {@link KeybindContext#CONTAINER_SCREEN} actions can never really collide at runtime if
 * each is only ever dispatched from a section/screen the other can't be active in at the same
 * time, the same way {@link KeybindContext#canOverlap} already reasons about the tick-loop's
 * mutually-exclusive modes. {@link KeybindAction#scope()} is {@code null} for every
 * container-screen action reachable from more than one of these (e.g. the shared slot-navigation
 * and pickup/quick-move actions, reused by the Recipe Book/Enchant Options/Trades sections as
 * well as ordinary slot navigation) - {@code null} means "treat as universally conflicting",
 * the same conservative behavior this system always had before scoping existed. Only give an
 * action an explicit scope when it is genuinely reachable from just that one place - see each
 * value's own doc for which {@link MenuAccessibilityController}/{@link CreativeInventoryController}
 * code path it corresponds to.
 */
public enum ContainerScope {
	/** {@link MenuAccessibilityController}'s Container/Inventory/Hotbar/Equipment sections, and the equivalent sections in {@link CreativeInventoryController}'s Inventory tab. */
	ORDINARY_SLOTS,
	/** {@link MenuAccessibilityController#handleRecipeBookKey} only. */
	RECIPE_BOOK,
	/**
	 * {@link CreativeInventoryController}'s own item-grid tabs, paging within them only (Page
	 * Up/Page Down) - a separate code path from every other container-screen action. Tab
	 * switching itself (Home/End) is checked before the Inventory-tab bailout in {@code
	 * CreativeInventoryController#handleKey}, so it's also reachable from the Inventory tab
	 * (where {@link #ORDINARY_SLOTS} actions are simultaneously live) - those two actions are
	 * {@code null}-scoped instead of tagged with this value, precisely because they don't meet
	 * this scope's single-place requirement.
	 */
	CREATIVE_TABS,
}
