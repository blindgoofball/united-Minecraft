package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import com.nibblenerds.unitedminecraft.client.access.AnvilScreenAccess;
import com.nibblenerds.unitedminecraft.client.access.CreativeModeInventoryScreenAccess;
import com.nibblenerds.unitedminecraft.client.access.SlotWrapperAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.enchantment.Enchantment;

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
 *
 * <p>Menus that implement {@link RecipeBookMenu} (crafting table, player inventory crafting,
 * furnace, smoker, blast furnace) get an extra Recipe Book section. It bypasses vanilla's
 * {@code RecipeBookComponent} widget entirely and works straight off public game state:
 * {@code player.getRecipeBook()} for the known recipes, {@link RecipeCollection#selectRecipes}
 * (using the exact same predicate the matching vanilla component uses, so it can't desync
 * that component's own view of the same shared collection objects) to compute what's valid
 * for this menu's grid shape, and {@code Minecraft.gameMode.handlePlaceRecipe} to fill the
 * grid server-side - the same single call the vanilla button click makes, no coordinate
 * simulation involved. Up/Down move between recipe groups, Left/Right move between variants
 * within a group (vanilla bundles near-duplicate recipes, e.g. different colors, into one
 * button), F toggles showing only currently-craftable recipes, and Enter/Shift+Enter place
 * the focused recipe (Shift = fill to max stack size).
 *
 * <p>{@link EnchantmentMenu} similarly gets an extra Enchant Options section, for the three
 * enchantment buttons - not slots at all, so they'd otherwise be invisible to a keyboard/
 * screen-reader user entirely. Up/Down move between the three options, narrating exactly what
 * hovering with a mouse would ({@code costs}/{@code enchantClue}/{@code levelClue}, the same
 * arrays {@code EnchantmentScreen}'s own tooltip reads): the enchantment's real name and level
 * ({@link Enchantment#getFullname} - the in-game "clue" text isn't actually hidden information,
 * just flavor styling), whether the player's experience level meets the requirement, and its
 * fixed Lapis/XP cost. Enter picks the focused option via {@code EnchantmentMenu.clickMenuButton}
 * then {@code MultiPlayerGameMode.handleInventoryButtonClick} - the same pair of calls vanilla's
 * own mouse handler makes, mirroring how slot clicks elsewhere in this class reuse vanilla's own
 * networking rather than inventing anything new.
 *
 * <p>{@link AnvilMenu} gets its own Rename section for the same underlying reason: vanilla's
 * rename box is a real {@code EditBox}, not a slot, and grabs real keyboard focus the moment the
 * anvil screen opens ({@code AnvilScreen#setInitialFocus}) with no vanilla way to leave it again
 * except a mouse click elsewhere - previously handled by unconditionally passing every key
 * straight to vanilla whenever an {@code EditBox} had focus, which made the anvil screen
 * unusable entirely once that happened, Tab included. Now Tab is still this class's own (so the
 * player can always get back to Container/Inventory/Hotbar), while every other key reaches
 * vanilla's own {@code EditBox} handling untouched for actual typing, via {@link
 * AnvilScreenAccess} (there's no public accessor for the private field otherwise). Entering or
 * leaving the section explicitly moves real focus onto or off of the box to match, since
 * {@code currentSection} and vanilla's own focus tracking are otherwise entirely independent of
 * each other.
 *
 * <p>The Creative inventory's item-picker tabs are handled separately, by
 * {@link CreativeInventoryController} - vanilla's item grid is always exactly 45 real slots
 * (a scrolling window), which doesn't fit this class's "one slot per real item" model at all.
 * This class only takes over there when the Inventory tab is selected, since that one really
 * is just the player's ordinary inventory shown inside the screen - see
 * {@link #isHandledByCreativeItemGrid}.
 */
public final class MenuAccessibilityController {
	private static Section currentSection = Section.CONTAINER;

	// Tracked by direct reference, not Slot#index: Creative's Inventory tab rebuilds its slot
	// list by mutating AbstractContainerMenu#slots directly instead of going through
	// AbstractContainerMenu#addSlot, so index (only ever set by addSlot) silently stays 0 for
	// every one of those wrapped slots - looking focus up by index there would always resolve
	// to slot 0 regardless of what was actually focused.
	private static Slot focusedSlot;

	private static List<RecipeCollection> recipeGroups = List.of();
	private static int recipeGroupIndex = -1;
	private static int recipeVariantIndex = 0;
	private static boolean recipeCraftableOnlyFilter = false;

	private static int enchantOptionIndex = 0;

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

	/** True while a Creative screen's item-picker tab (not its Inventory tab) is selected - see the class doc. */
	private static boolean isHandledByCreativeItemGrid(AbstractContainerScreen<?> screen) {
		return screen instanceof CreativeModeInventoryScreen creative
				&& ((CreativeModeInventoryScreenAccess) creative).unitedMinecraft$getSelectedTab().getType()
						!= CreativeModeTab.Type.INVENTORY;
	}

	/** Entry point {@link CreativeInventoryController} calls after switching back to the Inventory tab. */
	static void reinitializeForScreen(AbstractContainerScreen<?> screen) {
		onScreenOpened(screen);
	}

	private static void onScreenOpened(AbstractContainerScreen<?> screen) {
		if (isHandledByCreativeItemGrid(screen)) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		recipeCraftableOnlyFilter = false;
		currentSection = applicableSections(screen.getMenu())[0];
		enterSection(screen, player);
	}

	private static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event) {
		if (isHandledByCreativeItemGrid(screen)) {
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

		// Everything else (typing, Backspace, arrow-key cursor movement within the text) goes
		// straight to vanilla's own EditBox while the Rename section is focused - Tab above is
		// the only key this class needs to own here, to actually be able to leave it again.
		if (currentSection == Section.RENAME) {
			return true;
		}

		if (currentSection == Section.RECIPE_BOOK) {
			return handleRecipeBookKey(screen, player, key, shiftHeld);
		}
		if (currentSection == Section.ENCHANT_OPTIONS) {
			return handleEnchantOptionKey(screen, player, key);
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
			boolean ctrlHeld = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
			// Button 0 = left click, button 1 = right click - same distinction the real mouse
			// buttons make on AbstractContainerMenu.clicked. Right-click-pickup is what splits
			// a stack in half (or drops one item at a time back into a slot from the cursor).
			int button = ctrlHeld ? 1 : 0;
			click(screen, player, shiftHeld ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, button);
			return false;
		}

		if (key == GLFW.GLFW_KEY_DELETE) {
			return !discardCarriedItem(screen);
		}

		return true;
	}

	/**
	 * Creative's own trash slot without having to navigate to it: discards whatever's
	 * currently picked up on the cursor, exactly like dragging it onto that slot would (gone
	 * for good, not dropped in the world - this is creative mode). {@code menu.getCarried()} is
	 * shared across every tab of the same Creative screen, not just the Inventory tab this class
	 * itself handles - {@link CreativeInventoryController} calls this too, for Delete while
	 * carrying something picked up from an item-grid tab or its hotbar.
	 */
	static boolean discardCarriedItem(AbstractContainerScreen<?> screen) {
		if (!(screen.getMenu() instanceof CreativeModeInventoryScreen.ItemPickerMenu menu) || menu.getCarried().isEmpty()) {
			return false;
		}
		Component itemName = ItemDescriptions.describe(menu.getCarried(), Minecraft.getInstance().player);
		menu.setCarried(ItemStack.EMPTY);
		Minecraft.getInstance().getNarrator().saySystemNow(
				Component.translatable("united_minecraft.narrate.menu_item_discarded", itemName));
		return true;
	}

	/** Sections present for this menu type, in Tab-cycle order. Recipe Book/Equipment/Enchant Options/Rename only appear when supported. */
	private static Section[] applicableSections(AbstractContainerMenu menu) {
		List<Section> sections = new ArrayList<>();
		// The rename box sits above the anvil's own slots on screen, so it leads - matching
		// both the visual layout and vanilla's own default initial focus there.
		if (menu instanceof AnvilMenu) {
			sections.add(Section.RENAME);
		}
		// Creative's Inventory tab has no real "container" section of its own - its crafting
		// slots are parked off-screen (filtered out by sectionSlots' x < 0 check already), and
		// its one other non-player-inventory slot is just the "drag here to delete" trash icon,
		// not something worth a whole section and a Tab stop for.
		if (!(menu instanceof CreativeModeInventoryScreen.ItemPickerMenu)) {
			sections.add(Section.CONTAINER);
		}
		if (menu instanceof RecipeBookMenu) {
			sections.add(Section.RECIPE_BOOK);
		}
		if (menu instanceof EnchantmentMenu) {
			sections.add(Section.ENCHANT_OPTIONS);
		}
		if (hasEquipmentSlots(menu)) {
			sections.add(Section.EQUIPMENT);
		}
		sections.add(Section.INVENTORY);
		sections.add(Section.HOTBAR);
		return sections.toArray(new Section[0]);
	}

	/** True only for menus that expose the player's armor/offhand slots (the player's own inventory screen). */
	private static boolean hasEquipmentSlots(AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory && containerSlotOf(slot) >= 36) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A slot's real container-local index - unwrapping {@code CreativeModeInventoryScreen$SlotWrapper}
	 * first if needed, since that wrapper (used to show the real inventory inside Creative's
	 * Inventory tab) reports its own position in the creative menu's slot list instead of the
	 * wrapped slot's real position in the player's Inventory, which would otherwise silently
	 * break hotbar/equipment detection for that screen.
	 */
	private static int containerSlotOf(Slot slot) {
		if (slot instanceof SlotWrapperAccess wrapper) {
			return wrapper.unitedMinecraft$getTarget().getContainerSlot();
		}
		return slot.getContainerSlot();
	}

	private static void switchSection(AbstractContainerScreen<?> screen, LocalPlayer player, int direction) {
		Section[] sections = applicableSections(screen.getMenu());
		int i = 0;
		for (int j = 0; j < sections.length; j++) {
			if (sections[j] == currentSection) {
				i = j;
				break;
			}
		}
		currentSection = sections[Math.floorMod(i + direction, sections.length)];
		enterSection(screen, player);
	}

	private static void enterSection(AbstractContainerScreen<?> screen, LocalPlayer player) {
		if (currentSection == Section.RECIPE_BOOK) {
			refreshRecipeGroups(screen.getMenu(), player);
			recipeGroupIndex = visibleRecipeGroups().isEmpty() ? -1 : 0;
			recipeVariantIndex = 0;
			narrateRecipeFocus(player, true);
		} else if (currentSection == Section.ENCHANT_OPTIONS) {
			enchantOptionIndex = 0;
			narrateEnchantOption(screen, player, true);
		} else if (currentSection == Section.RENAME) {
			enterRenameSection(screen);
		} else {
			// Release the rename box's real focus, if it still has it from a previous visit to
			// that section - otherwise handleKey's own Rename bypass would keep swallowing every
			// key meant for slot navigation here, on the strength of currentSection alone, since
			// nothing else ever clears vanilla's own focus tracking for us.
			screen.setFocused(null);
			focusedSlot = firstSlot(screen.getMenu(), player, currentSection);
			narrateFocusedSlot(screen, player, true);
		}
	}

	/**
	 * Gives the rename box real keyboard focus (matching vanilla's own default on an anvil
	 * screen, and restoring it if the player tabbed away and back) so typing actually reaches
	 * it, then narrates its current contents - {@link #handleKey}'s own Rename bypass leaves
	 * everything past Tab to vanilla's usual {@code EditBox} handling from here on.
	 */
	private static void enterRenameSection(AbstractContainerScreen<?> screen) {
		if (!(screen instanceof AnvilScreenAccess access)) {
			return;
		}
		EditBox nameBox = access.unitedMinecraft$getNameBox();
		screen.setFocused(nameBox);

		String text = nameBox.getValue();
		Component message = sectionLabel(Section.RENAME).copy().append(Component.literal(". ")).append(
				text.isEmpty() ? Component.translatable("united_minecraft.narrate.hotbar_empty") : Component.literal(text));
		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}

	private static void moveFocus(AbstractContainerScreen<?> screen, LocalPlayer player, Direction direction) {
		AbstractContainerMenu menu = screen.getMenu();
		List<Slot> sectionSlots = sectionSlots(menu, player, currentSection);
		if (sectionSlots.isEmpty()) {
			return;
		}

		if (focusedSlot == null || !sectionSlots.contains(focusedSlot)) {
			focusedSlot = sectionSlots.get(0);
			narrateFocusedSlot(screen, player, false);
			return;
		}

		Slot next = switch (currentSection) {
			case HOTBAR -> gridNeighbor(sectionSlots, focusedSlot, direction, sectionSlots.size(), 1);
			case INVENTORY -> gridNeighbor(sectionSlots, focusedSlot, direction, 9, sectionSlots.size() / 9);
			case CONTAINER, EQUIPMENT -> nearestSpatialNeighbor(sectionSlots, focusedSlot, direction);
			// moveFocus is never called while any of these sections is active - each routes its
			// own keys to a dedicated handler (or straight to vanilla) before this method is
			// ever reached.
			case RECIPE_BOOK, ENCHANT_OPTIONS, RENAME -> null;
		};
		if (next != null) {
			focusedSlot = next;
			narrateFocusedSlot(screen, player, false);
		}
	}

	private static void click(AbstractContainerScreen<?> screen, LocalPlayer player, ContainerInput input, int button) {
		Minecraft client = Minecraft.getInstance();
		AbstractContainerMenu menu = screen.getMenu();
		Slot slot = currentSlot(menu);
		if (slot == null) {
			return;
		}

		if (slot instanceof SlotWrapperAccess wrapper) {
			// Creative's Inventory tab shows the player's real inventory through slots that
			// wrap their true player.inventoryMenu counterparts. Vanilla's own mouse handling
			// clicks through that real menu directly instead of the screen's own ItemPickerMenu
			// for this tab (see CreativeModeInventoryScreen#slotClicked's Inventory-tab branch) -
			// mirror that exactly rather than inventing a different networking path.
			Slot target = wrapper.unitedMinecraft$getTarget();
			player.inventoryMenu.clicked(target.index, button, input, player);
			player.inventoryMenu.broadcastChanges();
			narrateFocusedSlot(screen, player, false);
			return;
		}

		// handleContainerInput already calls menu.clicked(...) internally (to diff slots for
		// the outgoing packet) before sending it to the server, so calling menu.clicked()
		// ourselves too would apply the click twice - e.g. splitting an already-split stack.
		client.gameMode.handleContainerInput(menu.containerId, slot.index, button, input, player);

		narrateFocusedSlot(screen, player, false);
	}

	private static boolean handleRecipeBookKey(AbstractContainerScreen<?> screen, LocalPlayer player, int key, boolean shiftHeld) {
		switch (key) {
			case GLFW.GLFW_KEY_UP -> moveRecipeGroup(player, -1);
			case GLFW.GLFW_KEY_DOWN -> moveRecipeGroup(player, 1);
			case GLFW.GLFW_KEY_LEFT -> moveRecipeVariant(player, -1);
			case GLFW.GLFW_KEY_RIGHT -> moveRecipeVariant(player, 1);
			case GLFW.GLFW_KEY_F -> toggleCraftableFilter(player);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> placeFocusedRecipe(screen.getMenu(), player, shiftHeld);
			default -> {
				return true;
			}
		}
		return false;
	}

	private static void moveRecipeGroup(LocalPlayer player, int direction) {
		List<RecipeCollection> groups = visibleRecipeGroups();
		if (groups.isEmpty()) {
			return;
		}
		if (recipeGroupIndex < 0) {
			recipeGroupIndex = 0;
		} else {
			int next = recipeGroupIndex + direction;
			if (next < 0 || next >= groups.size()) {
				return;
			}
			recipeGroupIndex = next;
		}
		recipeVariantIndex = 0;
		narrateRecipeFocus(player, false);
	}

	private static void moveRecipeVariant(LocalPlayer player, int direction) {
		List<RecipeDisplayEntry> variants = currentRecipeVariants();
		if (variants.isEmpty()) {
			return;
		}
		int next = recipeVariantIndex + direction;
		if (next < 0 || next >= variants.size()) {
			return;
		}
		recipeVariantIndex = next;
		narrateRecipeFocus(player, false);
	}

	private static void toggleCraftableFilter(LocalPlayer player) {
		recipeCraftableOnlyFilter = !recipeCraftableOnlyFilter;
		List<RecipeCollection> groups = visibleRecipeGroups();
		recipeGroupIndex = groups.isEmpty() ? -1 : 0;
		recipeVariantIndex = 0;
		Component toggleMessage = Component.translatable(recipeCraftableOnlyFilter
				? "united_minecraft.menu.recipe_book.filter_on"
				: "united_minecraft.menu.recipe_book.filter_off");
		Minecraft.getInstance().getNarrator().saySystemNow(toggleMessage);
		narrateRecipeFocus(player, false);
	}

	private static void placeFocusedRecipe(AbstractContainerMenu menu, LocalPlayer player, boolean useMaxItems) {
		List<RecipeDisplayEntry> variants = currentRecipeVariants();
		if (variants.isEmpty()) {
			return;
		}
		RecipeDisplayId recipeId = variants.get(recipeVariantIndex).id();
		Minecraft.getInstance().gameMode.handlePlaceRecipe(menu.containerId, recipeId, useMaxItems);
		refreshRecipeGroups(menu, player);
		narrateRecipeFocus(player, false);
	}

	/** Recomputes which recipes are valid (and craftable) for this menu's current grid shape and inventory. */
	private static void refreshRecipeGroups(AbstractContainerMenu menu, LocalPlayer player) {
		recipeGroups = List.of();
		if (!(menu instanceof RecipeBookMenu recipeBookMenu)) {
			return;
		}

		StackedItemContents stackedContents = new StackedItemContents();
		player.getInventory().fillStackedContents(stackedContents);
		recipeBookMenu.fillCraftSlotsStackedContents(stackedContents);

		Predicate<RecipeDisplay> predicate;
		if (menu instanceof AbstractCraftingMenu craftingMenu) {
			int gridWidth = craftingMenu.getGridWidth();
			int gridHeight = craftingMenu.getGridHeight();
			predicate = display -> fitsCraftingGrid(display, gridWidth, gridHeight);
		} else if (menu instanceof AbstractFurnaceMenu) {
			predicate = display -> display instanceof FurnaceRecipeDisplay;
		} else {
			return;
		}

		List<RecipeCollection> result = new ArrayList<>();
		for (RecipeCollection collection : player.getRecipeBook().getCollections()) {
			collection.selectRecipes(stackedContents, predicate);
			if (collection.hasAnySelected()) {
				result.add(collection);
			}
		}
		recipeGroups = result;
	}

	/** Mirrors CraftingRecipeBookComponent's own private canDisplay check: does this recipe fit the grid? */
	private static boolean fitsCraftingGrid(RecipeDisplay display, int gridWidth, int gridHeight) {
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			return gridWidth >= shaped.width() && gridHeight >= shaped.height();
		}
		if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			return gridWidth * gridHeight >= shapeless.ingredients().size();
		}
		return false;
	}

	private static List<RecipeCollection> visibleRecipeGroups() {
		if (!recipeCraftableOnlyFilter) {
			return recipeGroups;
		}
		List<RecipeCollection> craftableOnly = new ArrayList<>();
		for (RecipeCollection group : recipeGroups) {
			if (group.hasCraftable()) {
				craftableOnly.add(group);
			}
		}
		return craftableOnly;
	}

	private static List<RecipeDisplayEntry> currentRecipeVariants() {
		List<RecipeCollection> groups = visibleRecipeGroups();
		if (recipeGroupIndex < 0 || recipeGroupIndex >= groups.size()) {
			return List.of();
		}
		return groups.get(recipeGroupIndex).getSelectedRecipes(RecipeCollection.CraftableStatus.ANY);
	}

	private static void narrateRecipeFocus(LocalPlayer player, boolean announceSection) {
		List<RecipeDisplayEntry> variants = currentRecipeVariants();
		MutableComponent message;
		if (variants.isEmpty()) {
			message = Component.translatable("united_minecraft.menu.recipe_book.empty").copy();
		} else {
			RecipeCollection group = visibleRecipeGroups().get(recipeGroupIndex);
			RecipeDisplayEntry entry = variants.get(Math.min(recipeVariantIndex, variants.size() - 1));
			List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(player.level()));
			MutableComponent itemName = results.isEmpty()
					? Component.translatable("united_minecraft.narrate.hotbar_empty")
					: ItemDescriptions.describe(results.get(0), player);
			Component status = Component.translatable(group.isCraftable(entry.id())
					? "united_minecraft.menu.recipe_book.craftable"
					: "united_minecraft.menu.recipe_book.not_craftable");
			message = itemName.copy().append(Component.literal(", ")).append(status);
			if (variants.size() > 1) {
				message = message.append(Component.literal(", ")).append(Component.translatable(
						"united_minecraft.menu.recipe_book.variant", recipeVariantIndex + 1, variants.size()));
			}
		}

		if (announceSection) {
			message = sectionLabel(Section.RECIPE_BOOK).copy().append(Component.literal(". ")).append(message);
		}
		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}

	private static boolean handleEnchantOptionKey(AbstractContainerScreen<?> screen, LocalPlayer player, int key) {
		switch (key) {
			case GLFW.GLFW_KEY_UP -> moveEnchantOption(screen, player, -1);
			case GLFW.GLFW_KEY_DOWN -> moveEnchantOption(screen, player, 1);
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> selectEnchantOption(screen, player);
			default -> {
				return true;
			}
		}
		return false;
	}

	private static void moveEnchantOption(AbstractContainerScreen<?> screen, LocalPlayer player, int direction) {
		int next = enchantOptionIndex + direction;
		if (next < 0 || next > 2) {
			return;
		}
		enchantOptionIndex = next;
		narrateEnchantOption(screen, player, false);
	}

	/**
	 * Picks the focused option via the same two calls vanilla's own mouse handler makes:
	 * {@code clickMenuButton} first for local prediction (and to check the click is actually
	 * valid), then {@code handleInventoryButtonClick} to notify the server - mirroring how
	 * {@link #click} reuses vanilla's own slot-click networking rather than inventing anything
	 * new.
	 */
	private static void selectEnchantOption(AbstractContainerScreen<?> screen, LocalPlayer player) {
		if (!(screen.getMenu() instanceof EnchantmentMenu menu)) {
			return;
		}
		if (menu.clickMenuButton(player, enchantOptionIndex)) {
			Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, enchantOptionIndex);
		}
		narrateEnchantOption(screen, player, false);
	}

	/**
	 * Narrates exactly what hovering the focused option with a mouse would, reading the same
	 * {@code costs}/{@code enchantClue}/{@code levelClue} arrays {@code EnchantmentScreen}'s own
	 * tooltip does: the revealed enchantment (see {@link #enchantmentName}), whether the
	 * player's experience level actually meets the requirement, and - only once it does, since
	 * that's what vanilla's own tooltip conditions on too - its fixed Lapis/XP cost and whether
	 * the player currently has enough Lapis for it.
	 */
	private static void narrateEnchantOption(AbstractContainerScreen<?> screen, LocalPlayer player, boolean announceSection) {
		if (!(screen.getMenu() instanceof EnchantmentMenu menu)) {
			return;
		}
		MutableComponent message = Component.translatable(
				"united_minecraft.menu.enchant_option_number", enchantOptionIndex + 1).copy();

		int levelRequirement = menu.costs[enchantOptionIndex];
		if (levelRequirement <= 0) {
			message = message.append(Component.literal(", ")).append(
					Component.translatable("united_minecraft.menu.enchant_option_none"));
		} else {
			message = message.append(Component.literal(", ")).append(enchantmentName(player, menu));
			if (player.experienceLevel < levelRequirement) {
				message = message.append(Component.literal(", ")).append(Component.translatable(
						"united_minecraft.menu.enchant_option_requires_level", levelRequirement));
			} else {
				// Fixed by row position, not levelRequirement - vanilla's own "discount" for
				// paying the bookshelf-scaled level requirement to unlock a slot.
				int lapisCost = enchantOptionIndex + 1;
				message = message.append(Component.literal(", ")).append(Component.translatable(
						"united_minecraft.menu.enchant_option_cost", lapisCost, lapisCost));
				if (menu.getGoldCount() < lapisCost) {
					message = message.append(Component.literal(", ")).append(
							Component.translatable("united_minecraft.menu.enchant_option_not_enough_lapis"));
				}
			}
		}

		if (announceSection) {
			message = sectionLabel(Section.ENCHANT_OPTIONS).copy().append(Component.literal(". ")).append(message);
		}
		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}

	/** The enchantment {@link EnchantmentMenu#enchantClue} actually reveals for the focused option, name and level included. */
	private static Component enchantmentName(LocalPlayer player, EnchantmentMenu menu) {
		Registry<Enchantment> registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Optional<Holder.Reference<Enchantment>> holder = registry.get(menu.enchantClue[enchantOptionIndex]);
		return holder.<Component>map(reference -> Enchantment.getFullname(reference, menu.levelClue[enchantOptionIndex]))
				.orElse(Component.translatable("united_minecraft.menu.enchant_option_none"));
	}

	/** {@link #focusedSlot} if it's still actually present in this menu, else null. */
	private static Slot currentSlot(AbstractContainerMenu menu) {
		return focusedSlot != null && menu.slots.contains(focusedSlot) ? focusedSlot : null;
	}

	private static Slot firstSlot(AbstractContainerMenu menu, LocalPlayer player, Section section) {
		List<Slot> slots = sectionSlots(menu, player, section);
		return slots.isEmpty() ? null : slots.get(0);
	}

	/** All slots belonging to a section, ordered row-major (by container-local index) for HOTBAR/INVENTORY. */
	private static List<Slot> sectionSlots(AbstractContainerMenu menu, LocalPlayer player, Section section) {
		List<Slot> result = new ArrayList<>();
		for (Slot slot : menu.slots) {
			if (slot.x < 0) {
				// Creative's Inventory tab keeps the (here unused) crafting grid slots around,
				// just parked off-screen instead of removed - skip anything not actually shown.
				continue;
			}
			boolean isPlayerInventory = slot.container == player.getInventory();
			// Player-inventory container-local indices: 0-8 hotbar, 9-35 main inventory (a
			// clean 27 = 9x3 grid), 36+ armor/offhand - a handful of slots InventoryMenu tacks
			// on that don't fit any grid, so they get their own non-grid Equipment section.
			boolean matches = switch (section) {
				case HOTBAR -> isPlayerInventory && Inventory.isHotbarSlot(containerSlotOf(slot));
				case INVENTORY -> isPlayerInventory && !Inventory.isHotbarSlot(containerSlotOf(slot))
						&& containerSlotOf(slot) < 36;
				case EQUIPMENT -> isPlayerInventory && containerSlotOf(slot) >= 36;
				case CONTAINER -> !isPlayerInventory;
				// None of these are slot-based; sectionSlots is never called for any of them.
				case RECIPE_BOOK, ENCHANT_OPTIONS, RENAME -> false;
			};
			if (matches) {
				result.add(slot);
			}
		}
		if (section == Section.INVENTORY || section == Section.HOTBAR) {
			result.sort(Comparator.comparingInt(MenuAccessibilityController::containerSlotOf));
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
		Slot slot = currentSlot(menu);
		if (slot == null) {
			return;
		}

		Component itemDescription = slot.getItem().isEmpty()
				? Component.translatable("united_minecraft.narrate.hotbar_empty")
				: ItemDescriptions.describe(slot.getItem(), player);

		MutableComponent message = itemDescription.copy()
				.append(Component.literal(", "))
				.append(slotRole(menu, slot, player));

		if (menu instanceof AnvilMenu anvilMenu && slot.index == AnvilMenu.RESULT_SLOT
				&& !slot.getItem().isEmpty() && anvilMenu.getCost() > 0) {
			message = message.append(Component.literal(", "))
					.append(Component.translatable("united_minecraft.menu.anvil_cost", anvilMenu.getCost()));
		}

		if (announceSection) {
			message = sectionLabel(currentSection).copy().append(Component.literal(". ")).append(message);
		}

		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty()) {
			message = message.append(Component.literal(", ")).append(
					Component.translatable("united_minecraft.narrate.menu_carrying", ItemDescriptions.describe(carried, player)));
		}

		client.getNarrator().saySystemNow(message);
	}

	private static Component sectionLabel(Section section) {
		return switch (section) {
			case HOTBAR -> Component.translatable("united_minecraft.menu.slot.hotbar");
			case INVENTORY -> Component.translatable("united_minecraft.menu.slot.inventory");
			case CONTAINER -> Component.translatable("united_minecraft.menu.section.container");
			case RECIPE_BOOK -> Component.translatable("united_minecraft.menu.section.recipe_book");
			case EQUIPMENT -> Component.translatable("united_minecraft.menu.section.equipment");
			case ENCHANT_OPTIONS -> Component.translatable("united_minecraft.menu.section.enchant_options");
			case RENAME -> Component.translatable("united_minecraft.menu.section.rename");
		};
	}

	private static Component slotRole(AbstractContainerMenu menu, Slot slot, LocalPlayer player) {
		if (slot.container == player.getInventory()) {
			int containerSlot = containerSlotOf(slot);
			// Armor/offhand (36-40) belong to the player's own Inventory container just like the
			// hotbar and main inventory do, so this generic check would otherwise catch them first
			// and mislabel every one of them "Inventory" before the InventoryMenu-specific handling
			// below ever runs.
			if (containerSlot >= 36) {
				return equipmentSlotRole(containerSlot);
			}
			return Inventory.isHotbarSlot(containerSlot)
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
			// Armor/offhand slots are handled above (their container is the player's own
			// Inventory), so nothing else in this menu falls through to here in practice.
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

	/** Container-local indices 36-40 in the player's Inventory: boots, leggings, chestplate, helmet, offhand. */
	private static Component equipmentSlotRole(int containerSlot) {
		return switch (containerSlot) {
			case 36 -> Component.translatable("united_minecraft.menu.slot.boots");
			case 37 -> Component.translatable("united_minecraft.menu.slot.leggings");
			case 38 -> Component.translatable("united_minecraft.menu.slot.chestplate");
			case 39 -> Component.translatable("united_minecraft.menu.slot.helmet");
			case 40 -> Component.translatable("united_minecraft.menu.slot.offhand");
			default -> Component.translatable("united_minecraft.menu.slot.storage");
		};
	}

	/**
	 * Visual top-to-bottom order: an anvil's rename box (above its own slots) where present,
	 * then the container's own slots, then the player's main inventory, then the hotbar.
	 */
	private enum Section {
		RENAME, CONTAINER, RECIPE_BOOK, ENCHANT_OPTIONS, EQUIPMENT, INVENTORY, HOTBAR
	}

	private enum Direction {
		LEFT, RIGHT, UP, DOWN
	}
}
