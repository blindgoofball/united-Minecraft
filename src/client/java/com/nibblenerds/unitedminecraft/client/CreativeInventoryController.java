package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import com.nibblenerds.unitedminecraft.client.access.CreativeModeInventoryScreenAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/**
 * Keyboard navigation for the Creative inventory's item-picker tabs (Survival Inventory,
 * Building Blocks, Combat, Search, ...). Vanilla's own model doesn't fit
 * {@link MenuAccessibilityController}'s generic slot-based approach at all here: the visible
 * grid is always exactly 45 real {@link Slot}s (a 9x5 window into a shared scratch container),
 * and scrolling doesn't create more slots - it just overwrites those same 45 with whichever
 * page of the tab's full item list (which can run past a thousand entries) is currently in
 * view. Reading only {@code menu.slots}, as the generic path does, can only ever see the page
 * that's currently visible.
 *
 * <p>Instead, this reads {@code ItemPickerMenu.items} directly (the full backing list vanilla
 * itself pages through - already public) and tracks a position within it, scrolling that
 * position into view before interacting with it. Home/End cycle tabs themselves, which are
 * custom-rendered sprites with no keyboard path in vanilla at all -
 * {@link CreativeModeInventoryScreenAccessorMixin} exposes the private tab-selection state and
 * method this needs.
 *
 * <p>Backs off entirely once the Inventory tab is selected - that one really is just the
 * player's ordinary inventory, wrapped to display inside this screen - and lets
 * {@link MenuAccessibilityController} handle it the normal way instead. Home/End still work
 * there too, since tab-cycling matters regardless of which tab is currently active.
 *
 * <p>An item-picker tab's own {@code ItemPickerMenu} only ever has two kinds of real slot:
 * the 45-slot item grid and the player's hotbar - vanilla itself doesn't show the main
 * inventory or armor there at all, on any tab but Inventory, mouse or otherwise. So Tab here
 * only ever toggles between those ({@link Section}) - plus a third, Search, only present while
 * the Search tab itself is selected, for vanilla's own search {@code EditBox} - not the full
 * section list {@link MenuAccessibilityController} offers elsewhere; reaching the main
 * inventory or equipment still means switching to the Inventory tab with Home/End, same as it
 * would for anyone clicking around with a mouse.
 *
 * <p>The Search section exists because giving the search box real focus (see
 * {@link #enterSearchSection}) is what actually lets it be typed into at all - see that
 * method's doc for the concrete bug this fixes. Typed characters themselves are never handled
 * here: vanilla's own {@code charTyped} dispatch (invisible to this mod's key-press-only Fabric
 * hooks) filters the grid on its own; this class only polls the resulting item count once per
 * tick (see {@link #recheckSearchResults}) to narrate a result-count summary.
 */
public final class CreativeInventoryController {
	private static int index;
	private static int lastItemCount = -1;
	private static List<ItemStack> trackedItems = List.of();
	private static Section section = Section.ITEM_GRID;
	private static int hotbarIndex;

	// -1 means "no baseline yet" - forces the first tick's count to always narrate rather than
	// silently matching a leftover value from a previous screen/tab.
	private static int lastSearchResultCount = -1;

	private enum Section {
		SEARCH, ITEM_GRID, HOTBAR
	}

	private CreativeInventoryController() {
	}

	// Vanilla's Minecraft#setScreen re-runs init() even when reusing the same screen instance
	// (e.g. on a window resize) - gate the search-term/index reset on that so it doesn't
	// silently fire again for a redundant re-init of the same screen.
	private static CreativeModeInventoryScreen trackedScreen;

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof CreativeModeInventoryScreen creative)) {
				return;
			}
			if (creative != trackedScreen) {
				trackedScreen = creative;
				onScreenOpened(creative);
			}
			// Confirmed via ScreenMixin#beforeInit bytecode (fabric-screen-api-v1): vanilla's
			// Screen.init(int,int) reassigns this screen's Fabric key-press event to a
			// brand-new, listener-less Event object at the HEAD of every single call, including
			// a same-instance re-init - so this registration must run every time AFTER_INIT
			// fires, not just on a screen's first-ever init, or a later re-init would silently
			// leave zero listeners registered. The event being fresh each time means this can
			// never double up a listener.
			ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> handleKey(creative, event));
		});
		// Vanilla's own search filtering happens off charTyped, which this class never hooks
		// (nor could - Fabric only exposes allow/before/after events for the key-press half of
		// typing, not character input; see the class doc). Polling the resulting item count once
		// per tick is what actually lets a result-count summary get narrated as the player types.
		ClientTickEvents.END_CLIENT_TICK.register(CreativeInventoryController::recheckSearchResults);
	}

	private static CreativeModeInventoryScreenAccess access(CreativeModeInventoryScreen screen) {
		return (CreativeModeInventoryScreenAccess) screen;
	}

	private static boolean isItemGridTab(CreativeModeInventoryScreen screen) {
		return access(screen).unitedMinecraft$getSelectedTab().getType() != CreativeModeTab.Type.INVENTORY;
	}

	private static boolean isSearchTab(CreativeModeInventoryScreen screen) {
		return access(screen).unitedMinecraft$getSelectedTab().getType() == CreativeModeTab.Type.SEARCH;
	}

	private static void onScreenOpened(CreativeModeInventoryScreen screen) {
		index = 0;
		lastItemCount = -1;
		hotbarIndex = 0;
		lastSearchResultCount = -1;
		if (!isItemGridTab(screen)) {
			section = Section.ITEM_GRID;
			return;
		}
		if (isSearchTab(screen)) {
			section = Section.SEARCH;
			enterSearchSection(screen, true);
		} else {
			section = Section.ITEM_GRID;
			syncItems(screen);
			narrateCurrent(screen, true);
		}
	}

	/**
	 * Real root cause this class used to get wrong: vanilla's {@code CreativeModeInventoryScreen}
	 * never gives its search box actual {@code Screen}-level focus (it manages the box's own
	 * internal focus flag and dispatches keys/chars to it directly whenever the Search tab is
	 * selected, bypassing the normal {@code GuiEventListener} focus chain entirely) - so the old
	 * {@code screen.getFocused() instanceof EditBox} check here was always false, and every key
	 * this class's own switch statements claim (Left/Right/Up/Down/Page Up/Down/Enter) was
	 * silently stolen from the search box instead of reaching it, with no way to tell the two
	 * apart. This section makes that distinction explicit and real: entering it gives the box
	 * genuine {@link net.minecraft.client.gui.screens.Screen#setFocused} focus (mirroring
	 * {@code MenuAccessibilityController}'s anvil Rename section) and this class then gets out of
	 * its way entirely except for Tab (leave it) and Home/End (still cycle tabs).
	 */
	private static boolean handleKey(CreativeModeInventoryScreen screen, KeyEvent event) {
		if (ClientKeyBindings.CREATIVE_SWITCH_TAB_PREV.current().matches(event)) {
			switchTab(screen, -1);
			return false;
		}
		if (ClientKeyBindings.CREATIVE_SWITCH_TAB_NEXT.current().matches(event)) {
			switchTab(screen, 1);
			return false;
		}

		if (!isItemGridTab(screen)) {
			return true; // Inventory tab: MenuAccessibilityController owns this instead.
		}

		boolean switchSection = ClientKeyBindings.CONTAINER_SWITCH_SECTION_NEXT.current().matches(event)
				|| ClientKeyBindings.CONTAINER_SWITCH_SECTION_PREV.current().matches(event);

		if (section == Section.SEARCH) {
			if (switchSection) {
				toggleSection(screen);
				return false;
			}
			// Up/Down/Page Up/Page Down leave the text field and jump straight into the filtered
			// results grid at its first item - matching the same "arrow down out of a search box"
			// convention as this class's own recipe-book search prompt equivalent. Left/Right stay
			// with vanilla's EditBox (text-cursor movement while still editing the term) since
			// there's no grid concept of "previous" from a text field to move to.
			if (ClientKeyBindings.CONTAINER_NAV_UP.current().matches(event)
					|| ClientKeyBindings.CONTAINER_NAV_DOWN.current().matches(event)
					|| ClientKeyBindings.CREATIVE_PAGE_UP.current().matches(event)
					|| ClientKeyBindings.CREATIVE_PAGE_DOWN.current().matches(event)) {
				leaveSearchSection(screen);
				section = Section.ITEM_GRID;
				syncItems(screen);
				index = 0;
				narrateCurrent(screen, false);
				return false;
			}
			// Everything else - typing, Backspace, arrow-key cursor movement within the text,
			// Escape - goes straight to vanilla's own EditBox handling.
			return true;
		}

		if (switchSection) {
			toggleSection(screen);
			return false;
		}

		// Applies regardless of section - a carried item picked up from the grid or from the
		// hotbar shares the same menu.getCarried(), so the trash-slot shortcut works from
		// either. See MenuAccessibilityController#discardCarriedItem.
		if (ClientKeyBindings.CONTAINER_DISCARD.current().matches(event)) {
			return !MenuAccessibilityController.discardCarriedItem(screen);
		}

		if (section == Section.HOTBAR) {
			if (ClientKeyBindings.CONTAINER_NAV_LEFT.current().matches(event)) {
				moveHotbar(screen, -1);
			} else if (ClientKeyBindings.CONTAINER_NAV_RIGHT.current().matches(event)) {
				moveHotbar(screen, 1);
			} else if (ClientKeyBindings.CONTAINER_PICKUP.current().matches(event)) {
				clickHotbarSlot(screen, ContainerInput.PICKUP, 0);
			} else if (ClientKeyBindings.CONTAINER_PICKUP_SPLIT.current().matches(event)) {
				clickHotbarSlot(screen, ContainerInput.PICKUP, 1);
			} else if (ClientKeyBindings.CONTAINER_QUICK_MOVE.current().matches(event)) {
				clickHotbarSlot(screen, ContainerInput.QUICK_MOVE, 0);
			} else if (ClientKeyBindings.CONTAINER_DESCRIBE_SLOT.current().matches(event)) {
				describeHotbarFocus(screen);
			} else {
				return true;
			}
			return false;
		}

		if (ClientKeyBindings.CONTAINER_NAV_LEFT.current().matches(event)) {
			move(screen, -1);
		} else if (ClientKeyBindings.CONTAINER_NAV_RIGHT.current().matches(event)) {
			move(screen, 1);
		} else if (ClientKeyBindings.CONTAINER_NAV_UP.current().matches(event)) {
			move(screen, -9);
		} else if (ClientKeyBindings.CONTAINER_NAV_DOWN.current().matches(event)) {
			move(screen, 9);
		} else if (ClientKeyBindings.CREATIVE_PAGE_UP.current().matches(event)) {
			move(screen, -45);
		} else if (ClientKeyBindings.CREATIVE_PAGE_DOWN.current().matches(event)) {
			move(screen, 45);
		} else if (ClientKeyBindings.CONTAINER_PICKUP.current().matches(event)
				|| ClientKeyBindings.CONTAINER_PICKUP_SPLIT.current().matches(event)) {
			// No left/right-click distinction here - the item grid hands out an infinite copy
			// either way, so there's no stack to split; see pickUpOrPlace's own doc.
			pickUpOrPlace(screen, false);
		} else if (ClientKeyBindings.CONTAINER_QUICK_MOVE.current().matches(event)) {
			pickUpOrPlace(screen, true);
		} else if (ClientKeyBindings.CONTAINER_DESCRIBE_SLOT.current().matches(event)) {
			describeCurrent(screen);
		} else {
			return true;
		}
		return false;
	}

	/** Space, in the item grid: narrates {@link BlockDescriptions#describe} for the focused item. */
	private static void describeCurrent(CreativeModeInventoryScreen screen) {
		syncItems(screen);
		if (index >= trackedItems.size()) {
			return;
		}
		Minecraft.getInstance().getNarrator().saySystemNow(BlockDescriptions.describe(trackedItems.get(index)));
	}

	/** Space, in the hotbar section: narrates {@link BlockDescriptions#describe} for the focused hotbar slot. */
	private static void describeHotbarFocus(CreativeModeInventoryScreen screen) {
		List<Slot> slots = hotbarSlots(screen);
		if (hotbarIndex >= slots.size()) {
			return;
		}
		Minecraft.getInstance().getNarrator().saySystemNow(BlockDescriptions.describe(slots.get(hotbarIndex).getItem()));
	}

	/** Tab cycles Search (Search tab only) -> Item Grid -> Hotbar -> back around. */
	private static void toggleSection(CreativeModeInventoryScreen screen) {
		if (isSearchTab(screen)) {
			section = switch (section) {
				case SEARCH -> Section.ITEM_GRID;
				case ITEM_GRID -> Section.HOTBAR;
				case HOTBAR -> Section.SEARCH;
			};
		} else {
			section = section == Section.ITEM_GRID ? Section.HOTBAR : Section.ITEM_GRID;
		}

		if (section == Section.SEARCH) {
			enterSearchSection(screen, false);
		} else if (section == Section.HOTBAR) {
			leaveSearchSection(screen);
			hotbarIndex = Math.min(hotbarIndex, 8);
			narrateHotbarFocus(screen);
		} else {
			leaveSearchSection(screen);
			syncItems(screen);
			narrateCurrent(screen, false);
		}
	}

	/**
	 * Releases the search box's focus - both this class's own tracking of it ({@code
	 * screen.setFocused(null)}, so {@link #handleKey}'s Section.SEARCH bypass stops swallowing
	 * grid/hotbar keys) and vanilla's own internal {@code EditBox} focus flag ({@code
	 * searchBox.setFocused(false)}) - since vanilla's {@code charTyped}/{@code keyPressed}
	 * dispatch to the search box is keyed off the selected tab's type alone, not real screen
	 * focus (see {@link #enterSearchSection}'s doc): without also clearing the box's own flag,
	 * every keystroke would keep silently editing the search term even while this class's own
	 * navigation has moved on to the item grid or hotbar.
	 */
	private static void leaveSearchSection(CreativeModeInventoryScreen screen) {
		screen.setFocused(null);
		access(screen).unitedMinecraft$getSearchBox().setFocused(false);
	}

	/**
	 * Gives the search box real keyboard focus so typed characters both filter the grid and
	 * narrate via the existing global {@code EditBoxMixin} echo, then narrates its current
	 * contents. Establishes {@link #lastSearchResultCount} as a fresh baseline so the very next
	 * tick doesn't immediately re-announce a count that hasn't actually changed yet.
	 */
	private static void enterSearchSection(CreativeModeInventoryScreen screen, boolean announceTab) {
		EditBox searchBox = access(screen).unitedMinecraft$getSearchBox();
		screen.setFocused(searchBox);
		searchBox.setFocused(true);

		CreativeModeTab tab = access(screen).unitedMinecraft$getSelectedTab();
		MutableComponent message = Component.empty();
		if (announceTab) {
			message = message.append(tab.getDisplayName()).append(Component.literal(". "));
		}
		message = message.append(Component.translatable("united_minecraft.menu.section.search")).append(Component.literal(". "));
		String text = searchBox.getValue();
		message = message.append(text.isEmpty()
				? Component.translatable("united_minecraft.narrate.hotbar_empty")
				: Component.literal(text));
		Minecraft.getInstance().getNarrator().saySystemNow(message);

		syncItems(screen);
		lastSearchResultCount = trackedItems.size();
	}

	/**
	 * Polled once per client tick (see {@link #register}) rather than reacting to a specific key
	 * event, since character input into the search box happens entirely through vanilla's own
	 * charTyped dispatch, invisible to this class's key handling. Narrates a result-count summary
	 * whenever the filtered item count actually changes.
	 */
	private static void recheckSearchResults(Minecraft client) {
		if (!(client.gui.screen() instanceof CreativeModeInventoryScreen screen) || !isSearchTab(screen)) {
			lastSearchResultCount = -1;
			return;
		}
		int count = ((CreativeModeInventoryScreen.ItemPickerMenu) screen.getMenu()).items.size();
		if (count == lastSearchResultCount) {
			return;
		}
		lastSearchResultCount = count;
		syncItems(screen);

		Component summary = trackedItems.isEmpty()
				? Component.translatable("united_minecraft.narrate.scanner_empty")
				: Component.translatable("united_minecraft.narrate.creative_search_results", count)
						.append(Component.literal(", ")).append(ItemDescriptions.describe(trackedItems.get(0), client.player));
		client.getNarrator().saySystemNow(summary);
	}

	private static void moveHotbar(CreativeModeInventoryScreen screen, int delta) {
		int next = hotbarIndex + delta;
		if (next < 0 || next >= 9) {
			return;
		}
		hotbarIndex = next;
		narrateHotbarFocus(screen);
	}

	/**
	 * Mirrors vanilla's own {@code CreativeModeInventoryScreen#slotClicked} for a real (not
	 * pseudo-grid) slot: a direct, purely client-side click against the player's own {@code
	 * inventoryMenu}, not the networked {@code handleContainerInput} path - this screen has no
	 * server-side container actually open to send that packet against, so a click routed through
	 * it silently goes nowhere. {@code ItemPickerMenu#getCarried}/{@code #setCarried} already
	 * just forward to {@code player.inventoryMenu}'s own carried field (decompiled and confirmed
	 * - see {@link #pickUpOrPlace}), so this sees exactly what picking an item up from the grid
	 * or another hotbar slot just put there.
	 */
	private static void clickHotbarSlot(CreativeModeInventoryScreen screen, ContainerInput input, int button) {
		List<Slot> slots = hotbarSlots(screen);
		if (hotbarIndex >= slots.size()) {
			return;
		}
		Slot slot = slots.get(hotbarIndex);
		LocalPlayer player = Minecraft.getInstance().player;
		int inventoryMenuIndex = inventoryMenuIndexForHotbarSlot(player, slot.getContainerSlot());
		if (inventoryMenuIndex < 0) {
			return;
		}

		player.inventoryMenu.clicked(inventoryMenuIndex, button, input, player);
		player.inventoryMenu.broadcastChanges();

		narrateHotbarFocus(screen);
	}

	/** The item-picker menu's 9 real hotbar slots, in left-to-right order. */
	private static List<Slot> hotbarSlots(CreativeModeInventoryScreen screen) {
		Inventory inventory = Minecraft.getInstance().player.getInventory();
		List<Slot> result = new ArrayList<>();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container == inventory && Inventory.isHotbarSlot(slot.getContainerSlot())) {
				result.add(slot);
			}
		}
		result.sort(Comparator.comparingInt(Slot::getContainerSlot));
		return result;
	}

	private static void narrateHotbarFocus(CreativeModeInventoryScreen screen) {
		List<Slot> slots = hotbarSlots(screen);
		if (hotbarIndex >= slots.size()) {
			return;
		}
		ItemStack stack = slots.get(hotbarIndex).getItem();
		Component itemName = stack.isEmpty()
				? Component.translatable("united_minecraft.narrate.hotbar_empty")
				: ItemDescriptions.describe(stack, Minecraft.getInstance().player);
		Component message = itemName.copy()
				.append(Component.literal(", ")).append(Component.translatable("united_minecraft.menu.slot.hotbar"));
		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}

	private static void switchTab(CreativeModeInventoryScreen screen, int direction) {
		List<CreativeModeTab> tabs = CreativeModeTabs.tabs().stream().filter(CreativeModeTab::shouldDisplay).toList();
		if (tabs.isEmpty()) {
			return;
		}
		CreativeModeTab current = access(screen).unitedMinecraft$getSelectedTab();
		int i = tabs.indexOf(current);
		CreativeModeTab next = tabs.get(Math.floorMod((i < 0 ? 0 : i) + direction, tabs.size()));
		access(screen).unitedMinecraft$selectTab(next);
		index = 0;
		lastItemCount = -1;
		lastSearchResultCount = -1;

		if (!isItemGridTab(screen)) {
			section = Section.ITEM_GRID;
			MenuAccessibilityController.reinitializeForScreen(screen);
		} else if (isSearchTab(screen)) {
			section = Section.SEARCH;
			enterSearchSection(screen, true);
		} else {
			section = Section.ITEM_GRID;
			syncItems(screen);
			narrateCurrent(screen, true);
		}
	}

	private static void move(CreativeModeInventoryScreen screen, int delta) {
		syncItems(screen);
		if (trackedItems.isEmpty()) {
			return;
		}
		int next = index + delta;
		if (next < 0) {
			// Moving past the first item on the Search tab returns to the search field - the
			// mirror image of Up/Down/Page Up/Page Down leaving the field to enter the grid
			// (see handleKey's Section.SEARCH branch).
			if (isSearchTab(screen)) {
				section = Section.SEARCH;
				enterSearchSection(screen, false);
			}
			return;
		}
		if (next >= trackedItems.size()) {
			return;
		}
		index = next;
		narrateCurrent(screen, false);
	}

	/**
	 * {@link ClientKeyBindings#CONTAINER_PICKUP}/{@link ClientKeyBindings#CONTAINER_PICKUP_SPLIT}
	 * (via {@code quickMove == false}) pick the focused item up onto the cursor; {@link
	 * ClientKeyBindings#CONTAINER_QUICK_MOVE} instead writes it straight into the player's
	 * currently selected hotbar slot. No left/right-click split for the pickup case - the grid
	 * hands out an infinite copy either way, so there's no real stack to split in half.
	 *
	 * <p>Neither goes through {@code handleContainerInput} (the generic container-click
	 * networking path {@link MenuAccessibilityController} uses for real containers) - that
	 * path's default pickup behavior removes the item from its slot, which is correct for a
	 * real backing container but wrong here: the grid's 45 slots all share one scratch
	 * container that's just a rendering window, repopulated by {@code scrollTo} - it isn't the
	 * real source of the item, so emptying it doesn't do what it would for a real slot, it just
	 * leaves that grid position looking empty until the next scroll. Vanilla's own mouse
	 * handling for this exact tab ({@code CreativeModeInventoryScreen#slotClicked}) never calls
	 * {@code AbstractContainerMenu#clicked} for these slots at all - it hands out a copy via
	 * {@code ItemPickerMenu#setCarried} directly, which this mirrors instead.
	 */
	private static void pickUpOrPlace(CreativeModeInventoryScreen screen, boolean quickMove) {
		syncItems(screen);
		if (trackedItems.isEmpty()) {
			return;
		}

		CreativeModeInventoryScreen.ItemPickerMenu menu = (CreativeModeInventoryScreen.ItemPickerMenu) screen.getMenu();
		bringIntoView(menu, index);
		Slot slot = findVisibleSlot(screen, trackedItems.get(index));
		if (slot == null) {
			return;
		}
		ItemStack clicked = slot.getItem();

		if (quickMove) {
			if (writeToFirstEmptyHotbarSlot(clicked)) {
				narrateCurrent(screen, false);
			} else {
				Minecraft.getInstance().getNarrator().saySystemNow(
						Component.translatable("united_minecraft.narrate.hotbar_full"));
			}
			return;
		}

		menu.setCarried(clicked.copyWithCount(clicked.getCount()));
		narrateCurrent(screen, false);
	}

	/** Writes into the first empty hotbar slot, left to right - never overwrites an occupied one. */
	private static boolean writeToFirstEmptyHotbarSlot(ItemStack clicked) {
		Minecraft client = Minecraft.getInstance();
		Inventory inventory = client.player.getInventory();
		int hotbarSlot = -1;
		for (int i = 0; i < 9; i++) {
			if (inventory.getItem(i).isEmpty()) {
				hotbarSlot = i;
				break;
			}
		}
		if (hotbarSlot < 0) {
			return false;
		}

		int inventoryMenuIndex = inventoryMenuIndexForHotbarSlot(client.player, hotbarSlot);
		if (inventoryMenuIndex < 0) {
			return false;
		}
		ItemStack stack = clicked.copyWithCount(clicked.getMaxStackSize());
		inventory.setItem(hotbarSlot, stack);
		client.gameMode.handleCreativeModeItemAdd(stack, inventoryMenuIndex);
		return true;
	}

	/**
	 * The real {@code player.inventoryMenu}'s own slot-list position for a given hotbar slot -
	 * what {@code handleCreativeModeItemAdd} actually expects, which is not the same numbering
	 * as {@link Slot#getContainerSlot()} (0-8 for hotbar there; a different, non-contiguous
	 * range in the menu's own slot list).
	 */
	private static int inventoryMenuIndexForHotbarSlot(LocalPlayer player, int hotbarSlot) {
		for (Slot slot : player.inventoryMenu.slots) {
			if (slot.container == player.getInventory() && slot.getContainerSlot() == hotbarSlot) {
				return slot.index;
			}
		}
		return -1;
	}

	/** Scrolls the real 45-slot window so the item at {@code itemIndex} is showing somewhere in it. */
	private static void bringIntoView(CreativeModeInventoryScreen.ItemPickerMenu menu, int itemIndex) {
		int rowCount = Math.max(1, (int) Math.ceil(menu.items.size() / 9.0) - 5);
		int desiredRow = Math.min(itemIndex / 9, rowCount);
		float scrollOffs = rowCount == 0 ? 0.0f : (float) desiredRow / rowCount;
		menu.scrollTo(Math.clamp(scrollOffs, 0.0f, 1.0f));
	}

	/** Finds which of the 45 visible grid slots currently shows this exact item, after scrolling it into view. */
	private static Slot findVisibleSlot(CreativeModeInventoryScreen screen, ItemStack target) {
		List<Slot> slots = screen.getMenu().slots;
		for (int i = 0; i < slots.size() && i < 45; i++) {
			Slot slot = slots.get(i);
			if (ItemStack.matches(slot.getItem(), target)) {
				return slot;
			}
		}
		return null;
	}

	/** Re-reads the tab's full item list, clamping/resetting our tracked position if it changed size (e.g. a new search). */
	private static void syncItems(CreativeModeInventoryScreen screen) {
		trackedItems = ((CreativeModeInventoryScreen.ItemPickerMenu) screen.getMenu()).items;
		if (trackedItems.size() != lastItemCount) {
			index = 0;
			lastItemCount = trackedItems.size();
		} else if (index >= trackedItems.size()) {
			index = Math.max(0, trackedItems.size() - 1);
		}
	}

	private static void narrateCurrent(CreativeModeInventoryScreen screen, boolean announceTab) {
		CreativeModeTab tab = access(screen).unitedMinecraft$getSelectedTab();
		MutableComponent message = Component.empty();
		if (announceTab) {
			message = message.append(tab.getDisplayName()).append(Component.literal(". "));
		}

		if (trackedItems.isEmpty()) {
			message = message.append(Component.translatable("united_minecraft.narrate.scanner_empty"));
		} else {
			ItemStack stack = trackedItems.get(index);
			Component itemName = stack.isEmpty()
					? Component.translatable("united_minecraft.narrate.hotbar_empty")
					: ItemDescriptions.describe(stack, Minecraft.getInstance().player);
			message = message.append(itemName).append(Component.literal(", ")).append(Component.translatable(
					"united_minecraft.narrate.creative_item_position", index + 1, trackedItems.size()));
		}

		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}
}
