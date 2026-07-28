package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import com.nibblenerds.unitedminecraft.client.access.CreativeModeInventoryScreenAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

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
 * only ever toggles between those two ({@link Section}), not the full section list
 * {@link MenuAccessibilityController} offers elsewhere; reaching the main inventory or
 * equipment still means switching to the Inventory tab with Home/End, same as it would for
 * anyone clicking around with a mouse.
 */
public final class CreativeInventoryController {
	private static int index;
	private static int lastItemCount = -1;
	private static List<ItemStack> trackedItems = List.of();
	private static Section section = Section.ITEM_GRID;
	private static int hotbarIndex;

	private enum Section {
		ITEM_GRID, HOTBAR
	}

	private CreativeInventoryController() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof CreativeModeInventoryScreen creative)) {
				return;
			}
			onScreenOpened(creative);
			ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> handleKey(creative, event));
		});
	}

	private static CreativeModeInventoryScreenAccess access(CreativeModeInventoryScreen screen) {
		return (CreativeModeInventoryScreenAccess) screen;
	}

	private static boolean isItemGridTab(CreativeModeInventoryScreen screen) {
		return access(screen).unitedMinecraft$getSelectedTab().getType() != CreativeModeTab.Type.INVENTORY;
	}

	private static void onScreenOpened(CreativeModeInventoryScreen screen) {
		index = 0;
		lastItemCount = -1;
		section = Section.ITEM_GRID;
		hotbarIndex = 0;
		if (isItemGridTab(screen)) {
			syncItems(screen);
			narrateCurrent(screen, true);
		}
	}

	private static boolean handleKey(CreativeModeInventoryScreen screen, KeyEvent event) {
		int key = event.key();

		// The search box eats most keys while focused (it's the only text field this screen
		// has) - Home/End still get through below to switch tabs regardless.
		boolean searchFocused = screen.getFocused() instanceof EditBox;
		if (searchFocused && key != GLFW.GLFW_KEY_HOME && key != GLFW.GLFW_KEY_END) {
			return true;
		}

		if (key == GLFW.GLFW_KEY_HOME) {
			switchTab(screen, -1);
			return false;
		}
		if (key == GLFW.GLFW_KEY_END) {
			switchTab(screen, 1);
			return false;
		}

		if (!isItemGridTab(screen)) {
			return true; // Inventory tab: MenuAccessibilityController owns this instead.
		}

		if (key == GLFW.GLFW_KEY_TAB) {
			toggleSection(screen);
			return false;
		}

		if (section == Section.HOTBAR) {
			switch (key) {
				case GLFW.GLFW_KEY_LEFT -> moveHotbar(screen, -1);
				case GLFW.GLFW_KEY_RIGHT -> moveHotbar(screen, 1);
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> clickHotbarSlot(screen, event);
				default -> {
					return true;
				}
			}
			return false;
		}

		switch (key) {
			case GLFW.GLFW_KEY_LEFT -> move(screen, -1);
			case GLFW.GLFW_KEY_RIGHT -> move(screen, 1);
			case GLFW.GLFW_KEY_UP -> move(screen, -9);
			case GLFW.GLFW_KEY_DOWN -> move(screen, 9);
			case GLFW.GLFW_KEY_PAGE_UP -> move(screen, -45);
			case GLFW.GLFW_KEY_PAGE_DOWN -> move(screen, 45);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> pickUpOrPlace(screen, event);
			default -> {
				return true;
			}
		}
		return false;
	}

	private static void toggleSection(CreativeModeInventoryScreen screen) {
		section = section == Section.ITEM_GRID ? Section.HOTBAR : Section.ITEM_GRID;
		if (section == Section.HOTBAR) {
			hotbarIndex = Math.min(hotbarIndex, 8);
			narrateHotbarFocus(screen);
		} else {
			syncItems(screen);
			narrateCurrent(screen, false);
		}
	}

	private static void moveHotbar(CreativeModeInventoryScreen screen, int delta) {
		int next = hotbarIndex + delta;
		if (next < 0 || next >= 9) {
			return;
		}
		hotbarIndex = next;
		narrateHotbarFocus(screen);
	}

	private static void clickHotbarSlot(CreativeModeInventoryScreen screen, KeyEvent event) {
		List<Slot> slots = hotbarSlots(screen);
		if (hotbarIndex >= slots.size()) {
			return;
		}
		Slot slot = slots.get(hotbarIndex);

		boolean shiftHeld = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
		boolean ctrlHeld = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
		int button = ctrlHeld ? 1 : 0;
		Minecraft client = Minecraft.getInstance();
		client.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, button,
				shiftHeld ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, client.player);

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
				: ItemDescriptions.describe(stack);
		Component message = Component.translatable("united_minecraft.menu.slot.hotbar").copy()
				.append(Component.literal(": ")).append(itemName);
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
		section = Section.ITEM_GRID;

		if (isItemGridTab(screen)) {
			syncItems(screen);
			narrateCurrent(screen, true);
		} else {
			MenuAccessibilityController.reinitializeForScreen(screen);
		}
	}

	private static void move(CreativeModeInventoryScreen screen, int delta) {
		syncItems(screen);
		if (trackedItems.isEmpty()) {
			return;
		}
		int next = index + delta;
		if (next < 0 || next >= trackedItems.size()) {
			return;
		}
		index = next;
		narrateCurrent(screen, false);
	}

	private static void pickUpOrPlace(CreativeModeInventoryScreen screen, KeyEvent event) {
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

		boolean shiftHeld = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
		boolean ctrlHeld = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
		// Button 0 = left click, button 1 = right click - matches MenuAccessibilityController's
		// own Enter handling. Right-click-pickup takes a single item instead of the whole stack.
		int button = ctrlHeld ? 1 : 0;
		Minecraft client = Minecraft.getInstance();
		client.gameMode.handleContainerInput(menu.containerId, slot.index, button,
				shiftHeld ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, client.player);

		narrateCurrent(screen, false);
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
					: ItemDescriptions.describe(stack);
			message = message.append(itemName).append(Component.literal(", ")).append(Component.translatable(
					"united_minecraft.narrate.creative_item_position", index + 1, trackedItems.size()));
		}

		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}
}
