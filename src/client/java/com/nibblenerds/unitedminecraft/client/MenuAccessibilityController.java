package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

/**
 * Keyboard navigation for container menus (chest, furnace, crafting table, anvil, etc.).
 *
 * <p>The screen is split into up to three sections - the container's own slots, the
 * player's main inventory, and the hotbar - cycled with Tab/Shift+Tab. Arrow keys move
 * within whichever section is current, in a proper 2D grid, rather than searching the
 * whole menu at once: hotbar and inventory have a known, fixed grid shape (1x9 and 9x3)
 * so their movement is exact index arithmetic, while the container's own section (whose
 * shape varies wildly by menu type) falls back to nearest-slot-by-screen-position. Keeping
 * that spatial search scoped to one section only, instead of the entire menu, is what
 * makes it reliable - across the whole menu, hotbar/inventory/container slots can share
 * similar x or y coordinates and cause confusing cross-section jumps.
 *
 * <p>Enter clicks the focused slot (Shift+Enter quick-moves it) via the exact same public
 * calls a real mouse click makes ({@link AbstractContainerMenu#clicked} for local
 * prediction, then {@code Minecraft.gameMode.handleContainerInput} to notify the server),
 * so this reuses vanilla's own container networking rather than inventing anything new.
 *
 * <p>Slot *roles* within the container's own section (fuel, output, etc.) aren't generic -
 * vanilla has no built-in concept of it - so they're identified via that menu type's own
 * public slot-index constants where they exist, or known fixed slot order where they
 * don't. Unrecognized menus/slots fall back to a generic "Storage" label.
 */
public final class MenuAccessibilityController {
	private static Section currentSection = Section.CONTAINER;
	private static int focusedIndex = -1;

	private MenuAccessibilityController() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			onScreenOpened(containerScreen);
			ScreenKeyboardEvents.allowKeyPress(screen).register(
					(scr, event) -> handleKey(containerScreen, event));
		});
	}

	private static void onScreenOpened(AbstractContainerScreen<?> screen) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		currentSection = Section.CONTAINER;
		focusedIndex = firstSlotIndex(screen.getMenu(), player, currentSection);
		narrateFocusedSlot(screen, player, true);
	}

	private static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event) {
		// Don't hijack arrow-key cursor movement or Enter-to-confirm in a focused text field
		// (e.g. the anvil's rename box) - text input accessibility there is a separate concern.
		if (screen.getFocused() instanceof EditBox) {
			return true;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return true;
		}

		boolean shiftHeld = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
		int key = event.key();

		if (key == GLFW.GLFW_KEY_TAB) {
			switchSection(screen, player, shiftHeld ? -1 : 1);
			return false;
		}

		Direction direction = switch (key) {
			case GLFW.GLFW_KEY_LEFT -> Direction.LEFT;
			case GLFW.GLFW_KEY_RIGHT -> Direction.RIGHT;
			case GLFW.GLFW_KEY_UP -> Direction.UP;
			case GLFW.GLFW_KEY_DOWN -> Direction.DOWN;
			default -> null;
		};
		if (direction != null) {
			moveFocus(screen, player, direction);
			return false;
		}

		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			click(screen, player, shiftHeld ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
			return false;
		}

		return true;
	}

	private static void switchSection(AbstractContainerScreen<?> screen, LocalPlayer player, int direction) {
		Section[] sections = Section.values();
		currentSection = sections[Math.floorMod(currentSection.ordinal() + direction, sections.length)];
		focusedIndex = firstSlotIndex(screen.getMenu(), player, currentSection);
		narrateFocusedSlot(screen, player, true);
	}

	private static void moveFocus(AbstractContainerScreen<?> screen, LocalPlayer player, Direction direction) {
		AbstractContainerMenu menu = screen.getMenu();
		List<Slot> sectionSlots = sectionSlots(menu, player, currentSection);
		if (sectionSlots.isEmpty()) {
			return;
		}

		Slot current = validSlotOrNull(menu, focusedIndex);
		if (current == null || !sectionSlots.contains(current)) {
			focusedIndex = sectionSlots.get(0).index;
			narrateFocusedSlot(screen, player, false);
			return;
		}

		Slot next = switch (currentSection) {
			case HOTBAR -> gridNeighbor(sectionSlots, current, direction, sectionSlots.size(), 1);
			case INVENTORY -> gridNeighbor(sectionSlots, current, direction, 9, sectionSlots.size() / 9);
			case CONTAINER -> nearestSpatialNeighbor(sectionSlots, current, direction);
		};
		if (next != null) {
			focusedIndex = next.index;
			narrateFocusedSlot(screen, player, false);
		}
	}

	private static void click(AbstractContainerScreen<?> screen, LocalPlayer player, ContainerInput input) {
		Minecraft client = Minecraft.getInstance();
		AbstractContainerMenu menu = screen.getMenu();
		Slot slot = validSlotOrNull(menu, focusedIndex);
		if (slot == null) {
			return;
		}

		// Same pair of calls AbstractContainerScreen.slotClicked makes for a real mouse click:
		// apply client-side prediction immediately, then tell the server what happened.
		menu.clicked(slot.index, 0, input, player);
		client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, input, player);

		narrateFocusedSlot(screen, player, false);
	}

	private static Slot validSlotOrNull(AbstractContainerMenu menu, int index) {
		return index >= 0 && index < menu.slots.size() ? menu.slots.get(index) : null;
	}

	private static int firstSlotIndex(AbstractContainerMenu menu, LocalPlayer player, Section section) {
		List<Slot> slots = sectionSlots(menu, player, section);
		return slots.isEmpty() ? -1 : slots.get(0).index;
	}

	/** All slots belonging to a section, ordered row-major (by container-local index) for HOTBAR/INVENTORY. */
	private static List<Slot> sectionSlots(AbstractContainerMenu menu, LocalPlayer player, Section section) {
		List<Slot> result = new ArrayList<>();
		for (Slot slot : menu.slots) {
			boolean isPlayerInventory = slot.container == player.getInventory();
			boolean matches = switch (section) {
				case HOTBAR -> isPlayerInventory && Inventory.isHotbarSlot(slot.getContainerSlot());
				case INVENTORY -> isPlayerInventory && !Inventory.isHotbarSlot(slot.getContainerSlot());
				case CONTAINER -> !isPlayerInventory;
			};
			if (matches) {
				result.add(slot);
			}
		}
		if (section != Section.CONTAINER) {
			result.sort(Comparator.comparingInt(Slot::getContainerSlot));
		}
		return result;
	}

	/** Exact grid-index arithmetic, used for hotbar (1 row) and inventory (9 columns), whose shapes never vary. */
	private static Slot gridNeighbor(List<Slot> orderedSlots, Slot current, Direction direction, int columns, int rows) {
		int i = orderedSlots.indexOf(current);
		if (i < 0) {
			return null;
		}
		int row = i / columns;
		int col = i % columns;
		int newRow = row + (direction == Direction.DOWN ? 1 : direction == Direction.UP ? -1 : 0);
		int newCol = col + (direction == Direction.RIGHT ? 1 : direction == Direction.LEFT ? -1 : 0);
		if (newRow < 0 || newRow >= rows || newCol < 0 || newCol >= columns) {
			return null;
		}
		int newIndex = newRow * columns + newCol;
		return newIndex < orderedSlots.size() ? orderedSlots.get(newIndex) : null;
	}

	/** Nearest slot (by real screen position) in the given direction, biased towards staying aligned on the cross axis. */
	private static Slot nearestSpatialNeighbor(List<Slot> slots, Slot current, Direction direction) {
		Slot best = null;
		double bestScore = Double.MAX_VALUE;
		for (Slot candidate : slots) {
			if (candidate == current) {
				continue;
			}
			int dx = candidate.x - current.x;
			int dy = candidate.y - current.y;
			boolean matches = switch (direction) {
				case LEFT -> dx < 0;
				case RIGHT -> dx > 0;
				case UP -> dy < 0;
				case DOWN -> dy > 0;
			};
			if (!matches) {
				continue;
			}

			boolean horizontal = direction == Direction.LEFT || direction == Direction.RIGHT;
			double primary = Math.abs(horizontal ? dx : dy);
			double crossAxis = Math.abs(horizontal ? dy : dx);
			double score = crossAxis * 3.0 + primary;
			if (score < bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		return best;
	}

	private static void narrateFocusedSlot(AbstractContainerScreen<?> screen, LocalPlayer player, boolean announceSection) {
		Minecraft client = Minecraft.getInstance();
		AbstractContainerMenu menu = screen.getMenu();
		Slot slot = validSlotOrNull(menu, focusedIndex);
		if (slot == null) {
			return;
		}

		Component itemDescription = slot.getItem().isEmpty()
				? Component.translatable("united_minecraft.narrate.hotbar_empty")
				: ItemDescriptions.describe(slot.getItem());

		MutableComponent message = slotRole(menu, slot, player).copy()
				.append(Component.literal(": "))
				.append(itemDescription);

		if (announceSection) {
			message = sectionLabel(currentSection).copy().append(Component.literal(". ")).append(message);
		}

		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty()) {
			message = message.append(Component.literal(", ")).append(
					Component.translatable("united_minecraft.narrate.menu_carrying", ItemDescriptions.describe(carried)));
		}

		client.getNarrator().saySystemNow(message);
	}

	private static Component sectionLabel(Section section) {
		return switch (section) {
			case HOTBAR -> Component.translatable("united_minecraft.menu.slot.hotbar");
			case INVENTORY -> Component.translatable("united_minecraft.menu.slot.inventory");
			case CONTAINER -> Component.translatable("united_minecraft.menu.section.container");
		};
	}

	private static Component slotRole(AbstractContainerMenu menu, Slot slot, LocalPlayer player) {
		if (slot.container == player.getInventory()) {
			return Inventory.isHotbarSlot(slot.getContainerSlot())
					? Component.translatable("united_minecraft.menu.slot.hotbar")
					: Component.translatable("united_minecraft.menu.slot.inventory");
		}

		if (menu instanceof AbstractFurnaceMenu) {
			if (slot.index == AbstractFurnaceMenu.FUEL_SLOT) {
				return Component.translatable("united_minecraft.menu.slot.fuel");
			}
			if (slot.index == AbstractFurnaceMenu.RESULT_SLOT) {
				return Component.translatable("united_minecraft.menu.slot.output");
			}
			return Component.translatable("united_minecraft.menu.slot.input");
		}
		if (menu instanceof AnvilMenu) {
			if (slot.index == AnvilMenu.RESULT_SLOT) {
				return Component.translatable("united_minecraft.menu.slot.output");
			}
			if (slot.index == AnvilMenu.ADDITIONAL_SLOT) {
				return Component.translatable("united_minecraft.menu.slot.material");
			}
			return Component.translatable("united_minecraft.menu.slot.input");
		}
		if (menu instanceof CraftingMenu) {
			return slot.index == CraftingMenu.RESULT_SLOT
					? Component.translatable("united_minecraft.menu.slot.output")
					: Component.translatable("united_minecraft.menu.slot.crafting_grid");
		}
		if (menu instanceof InventoryMenu) {
			if (slot.index == InventoryMenu.RESULT_SLOT) {
				return Component.translatable("united_minecraft.menu.slot.output");
			}
			if (slot.index >= 1 && slot.index <= 4) {
				return Component.translatable("united_minecraft.menu.slot.crafting_grid");
			}
			if (slot.index >= 5 && slot.index <= 8) {
				return Component.translatable("united_minecraft.menu.slot.armor");
			}
			if (slot.index == 45) {
				return Component.translatable("united_minecraft.menu.slot.offhand");
			}
			return Component.translatable("united_minecraft.menu.slot.storage");
		}
		if (menu instanceof BrewingStandMenu) {
			if (slot.index <= 2) {
				return Component.translatable("united_minecraft.menu.slot.potion");
			}
			if (slot.index == 3) {
				return Component.translatable("united_minecraft.menu.slot.ingredient");
			}
			if (slot.index == 4) {
				return Component.translatable("united_minecraft.menu.slot.fuel");
			}
			return Component.translatable("united_minecraft.menu.slot.storage");
		}
		if (menu instanceof EnchantmentMenu) {
			return slot.index == 0
					? Component.translatable("united_minecraft.menu.slot.item")
					: Component.translatable("united_minecraft.menu.slot.lapis");
		}

		return Component.translatable("united_minecraft.menu.slot.storage");
	}

	/** Visual top-to-bottom order: the container's own slots, then the player's main inventory, then the hotbar. */
	private enum Section {
		CONTAINER, INVENTORY, HOTBAR
	}

	private enum Direction {
		LEFT, RIGHT, UP, DOWN
	}
}
