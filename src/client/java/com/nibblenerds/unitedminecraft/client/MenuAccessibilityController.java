package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import com.nibblenerds.unitedminecraft.client.access.AnvilScreenAccess;
import com.nibblenerds.unitedminecraft.client.access.CreativeModeInventoryScreenAccess;
import com.nibblenerds.unitedminecraft.client.access.RecipeBookComponentAccess;
import com.nibblenerds.unitedminecraft.client.access.RecipeBookScreenAccess;
import com.nibblenerds.unitedminecraft.client.access.SlotWrapperAccess;

import net.minecraft.client.KeyMapping;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
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
 * button), Home/End jump to the first/last visible group, Page Up/Down cycle vanilla's own
 * recipe-book categories (derived from each recipe's real {@code RecipeBookCategory} rather
 * than hardcoded per-screen tab lists, since furnace-family screens use a different subset than
 * crafting does), Space opens a Scanner-style search prompt for a recipe name filter (mirrored
 * into vanilla's own recipe-book search box too, when reachable - see {@link #applyRecipeSearch}),
 * F toggles showing only currently-craftable recipes, and Enter/Shift+Enter place the focused
 * recipe (Shift = fill to max stack size). All three filters (category, search, craftable-only)
 * are independent and apply together. Focusing a recipe also narrates its
 * ingredients - see {@link #recipeIngredients} - since vanilla's own recipe book never says
 * what a recipe actually needs anywhere except by looking at the grid preview itself.
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
 * <p>{@link MerchantMenu} (villager/wandering trader) gets its own Trades section for the
 * same reason as Enchant Options - the trade list is a column of real {@code Button}s, not
 * slots, so it's otherwise invisible to keyboard/screen-reader navigation entirely, which is
 * also why "the trades don't show" was previously indistinguishable from the screen just not
 * exposing them: nothing here ever read {@link MerchantMenu#getOffers}. Up/Down move between
 * offers, narrating what the trade button's own tooltip shows - both cost items, the result,
 * and whether it's out of stock - and Enter selects the focused trade via the same three calls
 * vanilla's own {@code TradeOfferButton} click makes: {@code setSelectionHint} and {@code
 * tryMoveItems} for local prediction (the latter is what actually auto-fills the payment slots
 * from the player's inventory), then a {@link ServerboundSelectTradePacket} to notify the
 * server. The menu's own two payment slots and result slot also get proper role labels now
 * (they used to fall through every {@code instanceof} check in {@link #slotRole} to a generic
 * "Storage", since none of them recognized {@link MerchantMenu} at all).
 *
 * <p>The Container section's own announced name is the screen's real {@link
 * AbstractContainerScreen#getTitle}, not a fixed "Container" label - so a chest says "Chest", a
 * furnace says "Furnace", the villager's own menu says the villager's name, and so on, matching
 * whatever vanilla itself puts at the top of that screen. See {@link
 * #pendingInitialSlotRecheck} for a related fix to the very first slot narrated after a screen
 * opens, which could otherwise describe a non-empty slot as empty.
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
	// null = "All" - no category filter applied.
	private static RecipeBookCategory currentRecipeCategory = null;
	private static String recipeSearchTerm = "";

	private static int enchantOptionIndex = 0;

	private static int tradeIndex = 0;

	// The container's real contents haven't necessarily synced yet at ScreenEvents.AFTER_INIT
	// time - the server sends the open-screen packet and the initial ClientboundContainerSetContentPacket
	// separately, and the screen (so AFTER_INIT) fires as soon as the first is handled, before the
	// second has necessarily been processed. Narrating the initial focused slot immediately would
	// then describe it as empty even when it actually has an item, until the player moves focus and
	// happens to re-narrate a since-populated slot. Set true right after that initial narration so
	// the very next tick (by which point the content packet has arrived, in every observed case) can
	// re-narrate with the real contents - but only if they actually differ, so a slot that was
	// correctly empty doesn't get spoken twice.
	private static boolean pendingInitialSlotRecheck = false;
	private static ItemStack lastNarratedSlotItem = ItemStack.EMPTY;

	private MenuAccessibilityController() {
	}

	// Tracks whichever screen instance this class last set up for, so a redundant re-init of
	// that *same* instance - e.g. the recipe-book search prompt's returnTo (see
	// MarkerNameScreen), which calls Minecraft#setScreen on the screen it was opened from
	// rather than a new instance, and vanilla's setScreen re-runs init() even when reusing the
	// same instance - doesn't silently wipe the just-confirmed search term/section back to
	// defaults. Registering the allowKeyPress listener, unlike that state, is NOT gated on this
	// check - see the comment at that registration call for why.
	private static AbstractContainerScreen<?> trackedScreen;

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			if (containerScreen != trackedScreen) {
				trackedScreen = containerScreen;
				onScreenOpened(containerScreen);
			}
			// Confirmed via ScreenMixin#beforeInit bytecode (fabric-screen-api-v1): vanilla's
			// Screen.init(int,int) unconditionally reassigns this screen's Fabric key-press
			// event to a brand-new, listener-less Event object at the HEAD of every single call
			// - not just a screen's first-ever init. So this registration must run on every
			// AFTER_INIT firing, including a same-instance re-init (e.g. returning from the
			// recipe-book search prompt) - skipping it there (as an earlier version of this fix
			// did, to avoid what looked like a double-registration risk) instead left the freshly
			// recreated event with zero listeners, silently killing all key handling on this
			// screen from that point on. Since the event is genuinely fresh each time, doing this
			// unconditionally cannot double up a listener - there's nothing there yet to double.
			ScreenKeyboardEvents.allowKeyPress(screen).register(
					(scr, event) -> handleKey(containerScreen, event));
		});
		ClientTickEvents.END_CLIENT_TICK.register(MenuAccessibilityController::recheckInitialSlotNarration);
		ClientTickEvents.END_CLIENT_TICK.register(MenuAccessibilityController::clearStrayFocus);
		ClientTickEvents.END_CLIENT_TICK.register(MenuAccessibilityController::openPendingSearchPrompt);
	}

	private static AbstractContainerScreen<?> pendingSearchPromptScreen;
	private static LocalPlayer pendingSearchPromptPlayer;

	/**
	 * Space, like any other printable key, fires both a key-press event (what this class
	 * intercepts) and a separate character-typed event for the same physical keystroke - both
	 * queued together and delivered back-to-back within the same input-polling pass, before any
	 * client tick runs. Opening the search prompt screen synchronously from the key-press
	 * handler would make that trailing character event land on the prompt's freshly-focused
	 * text field instead of the (unfocused, so harmlessly ignored) crafting screen it was
	 * actually meant for - typing a literal leading space into the term before the player's own
	 * typing even starts. Deferring the actual screen swap to the next tick lets that trailing
	 * character event get delivered and ignored first.
	 */
	private static void openPendingSearchPrompt(Minecraft client) {
		if (pendingSearchPromptScreen == null) {
			return;
		}
		AbstractContainerScreen<?> screen = pendingSearchPromptScreen;
		LocalPlayer player = pendingSearchPromptPlayer;
		pendingSearchPromptScreen = null;
		pendingSearchPromptPlayer = null;
		openRecipeSearchPrompt(screen, player);
	}

	/**
	 * The virtual-index-driven sections (Recipe Book, Enchant Options, Trades) narrate purely
	 * off their own tracked index, never real widget focus - so real focus should always be
	 * null while one of them is current. Vanilla re-focuses a sensible default widget (confirmed
	 * live - a screen reader announced a real, vanilla-narrated "button, press Enter to
	 * activate") as an accessibility fallback so a narrator-mode screen never sits with nothing
	 * focused for a sighted-navigation user; re-clearing every tick keeps that fallback from
	 * ever winning against this class's own narration model, which needs real focus to stay out
	 * of the way entirely (both so Enter/arrow keys aren't absorbed by whatever that fallback
	 * focused, and so screen readers don't narrate a stray, unlabeled widget on top of this
	 * class's own narration).
	 */
	private static void clearStrayFocus(Minecraft client) {
		if (trackedScreen == null || trackedScreen.getFocused() == null) {
			return;
		}
		if (currentSection == Section.RECIPE_BOOK || currentSection == Section.ENCHANT_OPTIONS
				|| currentSection == Section.TRADES) {
			trackedScreen.setFocused(null);
		}
	}

	private static void recheckInitialSlotNarration(Minecraft client) {
		if (!pendingInitialSlotRecheck) {
			return;
		}
		pendingInitialSlotRecheck = false;
		if (client.player == null || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		Slot slot = currentSlot(screen.getMenu());
		if (slot != null && !ItemStack.matches(slot.getItem(), lastNarratedSlotItem)) {
			narrateFocusedSlot(screen, client.player, true);
		}
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
		currentRecipeCategory = null;
		recipeSearchTerm = "";
		currentSection = applicableSections(screen.getMenu())[0];
		enterSection(screen, player, true);
	}

	private static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event) {
		if (isHandledByCreativeItemGrid(screen)) {
			return true;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return true;
		}

		boolean ctrlHeld = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;

		if (ClientKeyBindings.CONTAINER_SWITCH_SECTION_NEXT.current().matches(event)) {
			switchSection(screen, player, 1);
			return false;
		}
		if (ClientKeyBindings.CONTAINER_SWITCH_SECTION_PREV.current().matches(event)) {
			switchSection(screen, player, -1);
			return false;
		}

		// Everything else (typing, Backspace, arrow-key cursor movement within the text) goes
		// straight to vanilla's own EditBox while the Rename section is focused - Tab above is
		// the only key this class needs to own here, to actually be able to leave it again.
		if (currentSection == Section.RENAME) {
			return true;
		}

		if (currentSection == Section.RECIPE_BOOK) {
			return handleRecipeBookKey(screen, player, event);
		}
		if (currentSection == Section.ENCHANT_OPTIONS) {
			return handleEnchantOptionKey(screen, player, event);
		}
		if (currentSection == Section.TRADES) {
			return handleTradeKey(screen, event);
		}

		Direction direction = null;
		if (ClientKeyBindings.CONTAINER_NAV_LEFT.current().matches(event)) {
			direction = Direction.LEFT;
		} else if (ClientKeyBindings.CONTAINER_NAV_RIGHT.current().matches(event)) {
			direction = Direction.RIGHT;
		} else if (ClientKeyBindings.CONTAINER_NAV_UP.current().matches(event)) {
			direction = Direction.UP;
		} else if (ClientKeyBindings.CONTAINER_NAV_DOWN.current().matches(event)) {
			direction = Direction.DOWN;
		}
		if (direction != null) {
			moveFocus(screen, player, direction);
			return false;
		}

		// Button 0 = left click, button 1 = right click - same distinction the real mouse
		// buttons make on AbstractContainerMenu.clicked. Right-click-pickup is what splits a
		// stack in half (or places one item at a time back into a slot from the cursor).
		if (ClientKeyBindings.CONTAINER_PICKUP.current().matches(event)) {
			click(screen, player, ContainerInput.PICKUP, 0);
			return false;
		}
		if (ClientKeyBindings.CONTAINER_PICKUP_SPLIT.current().matches(event)) {
			click(screen, player, ContainerInput.PICKUP, 1);
			return false;
		}
		if (ClientKeyBindings.CONTAINER_QUICK_MOVE.current().matches(event)) {
			click(screen, player, ContainerInput.QUICK_MOVE, 0);
			return false;
		}

		if (ClientKeyBindings.CONTAINER_DISCARD.current().matches(event)) {
			return !discardCarriedItem(screen);
		}

		if (ClientKeyBindings.CONTAINER_DESCRIBE_SLOT.current().matches(event)) {
			describeFocusedSlot(screen.getMenu());
			return false;
		}

		if (handleHotbarSwapOrDrop(screen, player, event, ctrlHeld)) {
			return false;
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

	/** Space: narrates {@link BlockDescriptions#describe} for whatever's in the focused slot. */
	private static void describeFocusedSlot(AbstractContainerMenu menu) {
		Slot slot = currentSlot(menu);
		if (slot == null) {
			return;
		}
		Minecraft.getInstance().getNarrator().saySystemNow(BlockDescriptions.describe(slot.getItem()));
	}

	/**
	 * Mirrors {@code AbstractContainerScreen#checkHotbarKeyPressed}/{@code #keyPressed}'s own
	 * hotbar-swap and drop handling, just off the focused slot instead of whatever the real
	 * mouse happens to be hovering - keyboard navigation moves {@link #focusedSlot} without
	 * moving the actual cursor, so vanilla's own hover-only handling of these two keybindings
	 * never fires for a screen-reader user at all. Reads the real (rebindable) {@code
	 * Options.keyHotbarSlots}/{@code Options.keyDrop} keybindings rather than fixed key codes,
	 * matching whatever vanilla itself has bound. Same guards as vanilla: hotbar-swap only
	 * fires with an empty cursor (swapping while carrying something is undefined there), drop
	 * only fires on a non-empty slot.
	 */
	private static boolean handleHotbarSwapOrDrop(AbstractContainerScreen<?> screen, LocalPlayer player, KeyEvent event, boolean ctrlHeld) {
		AbstractContainerMenu menu = screen.getMenu();
		Slot slot = currentSlot(menu);
		if (slot == null) {
			return false;
		}

		if (slot.hasItem() && Minecraft.getInstance().options.keyDrop.matches(event)) {
			// Button 0 = drop one item, button 1 (Ctrl held) = drop the whole stack.
			click(screen, player, ContainerInput.THROW, ctrlHeld ? 1 : 0);
			return true;
		}

		if (menu.getCarried().isEmpty()) {
			KeyMapping[] hotbarKeys = Minecraft.getInstance().options.keyHotbarSlots;
			for (int i = 0; i < hotbarKeys.length; i++) {
				if (hotbarKeys[i].matches(event)) {
					click(screen, player, ContainerInput.SWAP, i);
					return true;
				}
			}
		}

		return false;
	}

	/** Sections present for this menu type, in Tab-cycle order. Recipe Book/Equipment/Enchant Options/Rename only appear when supported. */
	private static Section[] applicableSections(AbstractContainerMenu menu) {
		List<Section> sections = new ArrayList<>();
		// The rename box sits above the anvil's own slots on screen, so it leads - matching
		// both the visual layout and vanilla's own default initial focus there.
		if (menu instanceof AnvilMenu) {
			sections.add(Section.RENAME);
		}
		// Leads for the same reason Rename does above: the trade list is the actual point of
		// this screen, and sits to the left of the container's own slots on screen.
		if (menu instanceof MerchantMenu) {
			sections.add(Section.TRADES);
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
		enterSection(screen, player, false);
	}

	private static void enterSection(AbstractContainerScreen<?> screen, LocalPlayer player, boolean isInitialOpen) {
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
		} else if (currentSection == Section.TRADES) {
			tradeIndex = 0;
			narrateTradeFocus(screen, true);
		} else {
			// Release the rename box's real focus, if it still has it from a previous visit to
			// that section - otherwise handleKey's own Rename bypass would keep swallowing every
			// key meant for slot navigation here, on the strength of currentSection alone, since
			// nothing else ever clears vanilla's own focus tracking for us.
			screen.setFocused(null);
			focusedSlot = firstSlot(screen.getMenu(), player, currentSection);
			narrateFocusedSlot(screen, player, true);
			// See the field doc on pendingInitialSlotRecheck - only the very first narration right
			// after a screen opens can race the container's initial content sync; a Tab switch into
			// this section later (isInitialOpen false) is long past that window.
			pendingInitialSlotRecheck = isInitialOpen;
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
			case RECIPE_BOOK, ENCHANT_OPTIONS, RENAME, TRADES -> null;
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

	/**
	 * Up/Down/Left/Right/Enter/Shift+Enter reuse the same general container actions as ordinary
	 * slot navigation (move focus, pick up / quick-move) - place a recipe is this section's own
	 * equivalent of "activate the focused thing", and Shift's "do the bulk version" reading
	 * matches vanilla's own shift-click-to-craft-max convention. Only the gestures unique to the
	 * recipe book (jump to first/last group, cycle category, open search, toggle the craftable
	 * filter) get their own actions.
	 */
	private static boolean handleRecipeBookKey(AbstractContainerScreen<?> screen, LocalPlayer player, KeyEvent event) {
		if (ClientKeyBindings.CONTAINER_NAV_UP.current().matches(event)) {
			moveRecipeGroup(player, -1);
		} else if (ClientKeyBindings.CONTAINER_NAV_DOWN.current().matches(event)) {
			moveRecipeGroup(player, 1);
		} else if (ClientKeyBindings.CONTAINER_NAV_LEFT.current().matches(event)) {
			moveRecipeVariant(player, -1);
		} else if (ClientKeyBindings.CONTAINER_NAV_RIGHT.current().matches(event)) {
			moveRecipeVariant(player, 1);
		} else if (ClientKeyBindings.RECIPE_BOOK_JUMP_TO_FIRST_GROUP.current().matches(event)) {
			jumpRecipeGroup(player, true);
		} else if (ClientKeyBindings.RECIPE_BOOK_JUMP_TO_LAST_GROUP.current().matches(event)) {
			jumpRecipeGroup(player, false);
		} else if (ClientKeyBindings.RECIPE_BOOK_PREV_CATEGORY.current().matches(event)) {
			cycleRecipeCategory(player, -1);
		} else if (ClientKeyBindings.RECIPE_BOOK_NEXT_CATEGORY.current().matches(event)) {
			cycleRecipeCategory(player, 1);
		} else if (ClientKeyBindings.RECIPE_BOOK_SEARCH.current().matches(event)) {
			// Deferred a tick rather than opened immediately - see pendingSearchPromptScreen's
			// doc for why opening synchronously here leaks a literal space into the prompt.
			pendingSearchPromptScreen = screen;
			pendingSearchPromptPlayer = player;
		} else if (ClientKeyBindings.RECIPE_BOOK_TOGGLE_CRAFTABLE_FILTER.current().matches(event)) {
			toggleCraftableFilter(player);
		} else if (ClientKeyBindings.CONTAINER_PICKUP.current().matches(event)
				|| ClientKeyBindings.CONTAINER_PICKUP_SPLIT.current().matches(event)) {
			// No left/right-click distinction for placing a recipe - both act the same here.
			placeFocusedRecipe(screen.getMenu(), player, false);
		} else if (ClientKeyBindings.CONTAINER_QUICK_MOVE.current().matches(event)) {
			placeFocusedRecipe(screen.getMenu(), player, true);
		} else {
			return true;
		}
		return false;
	}

	/** Home/End: jump straight to the first/last visible recipe group, respecting the active category/craftable/search filters. */
	private static void jumpRecipeGroup(LocalPlayer player, boolean first) {
		List<RecipeCollection> groups = visibleRecipeGroups();
		if (groups.isEmpty()) {
			return;
		}
		recipeGroupIndex = first ? 0 : groups.size() - 1;
		recipeVariantIndex = 0;
		narrateRecipeFocus(player, false);
	}

	/**
	 * Page Up/Down cycle {@code [All] + distinct categories actually present among the current
	 * recipe groups}, in a stable order (registration order in {@code RECIPE_BOOK_CATEGORY}) -
	 * mirroring vanilla's own category tabs, which this class otherwise bypasses entirely (see
	 * the class doc). Independent of - and applied on top of - the craftable-only filter and
	 * search term, same as the craftable filter is independent of category.
	 */
	private static void cycleRecipeCategory(LocalPlayer player, int direction) {
		List<RecipeBookCategory> options = new ArrayList<>();
		options.add(null);
		options.addAll(presentRecipeCategories());

		int i = options.indexOf(currentRecipeCategory);
		currentRecipeCategory = options.get(Math.floorMod((i < 0 ? 0 : i) + direction, options.size()));

		List<RecipeCollection> groups = visibleRecipeGroups();
		recipeGroupIndex = groups.isEmpty() ? -1 : 0;
		recipeVariantIndex = 0;
		Minecraft.getInstance().getNarrator().saySystemNow(recipeCategoryLabel(currentRecipeCategory));
		narrateRecipeFocus(player, false);
	}

	/** Every {@link RecipeBookCategory} actually used by a recipe in {@link #recipeGroups} (unfiltered), registry order. */
	private static List<RecipeBookCategory> presentRecipeCategories() {
		List<RecipeBookCategory> result = new ArrayList<>();
		for (RecipeCollection group : recipeGroups) {
			for (RecipeDisplayEntry entry : group.getSelectedRecipes(RecipeCollection.CraftableStatus.ANY)) {
				RecipeBookCategory category = entry.category();
				if (!result.contains(category)) {
					result.add(category);
				}
			}
		}
		result.sort(Comparator.comparingInt(BuiltInRegistries.RECIPE_BOOK_CATEGORY::getId));
		return result;
	}

	/**
	 * {@code RecipeBookCategory} is icon-only in vanilla - no display string of its own - so this
	 * resolves one via the category's real registry key ({@code BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey})
	 * rather than inventing per-menu-type labels.
	 */
	private static Component recipeCategoryLabel(RecipeBookCategory category) {
		if (category == null) {
			return Component.translatable("united_minecraft.menu.recipe_book.category.all");
		}
		Identifier id = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(category);
		return Component.translatable("united_minecraft.menu.recipe_book.category." + id.getPath());
	}

	/**
	 * Opens a Scanner-style search prompt (see {@link ScannerController}'s own Search category
	 * prompt) for the recipe book's own search term. Reopens this same container screen
	 * afterwards instead of closing to the game world, unlike the Scanner's or Map Marker's
	 * prompts.
	 */
	private static void openRecipeSearchPrompt(AbstractContainerScreen<?> screen, LocalPlayer player) {
		Minecraft.getInstance().gui.setScreen(new MarkerNameScreen(
				Component.translatable("united_minecraft.search_screen.title"),
				Component.translatable("united_minecraft.narrate.search_prompt"),
				Component.translatable("united_minecraft.narrate.search_cancelled"),
				Component.translatable("united_minecraft.search_screen.term"),
				recipeSearchTerm,
				screen,
				term -> applyRecipeSearch(screen, player, term)));
	}

	/**
	 * Applies a confirmed (possibly blank, e.g. Escape) search term: re-filters this class's own
	 * recipe-group narration/navigation, then also mirrors the same term into vanilla's real
	 * recipe-book search box (see {@link RecipeBookComponentAccess}) so the on-screen panel, if
	 * the player has it open, shows the same filtered results a sighted user would see. Vanilla
	 * re-checks that box's value against its own last-known search string once per tick on its
	 * own (see {@code RecipeBookComponent.checkSearchStringUpdate}), so writing the value here is
	 * enough - no need to also trigger its private re-filtering directly.
	 */
	private static void applyRecipeSearch(AbstractContainerScreen<?> screen, LocalPlayer player, String term) {
		recipeSearchTerm = term == null ? "" : term.trim();

		List<RecipeCollection> groups = visibleRecipeGroups();
		recipeGroupIndex = groups.isEmpty() ? -1 : 0;
		recipeVariantIndex = 0;

		if (screen instanceof RecipeBookScreenAccess access
				&& access.unitedMinecraft$getRecipeBookComponent() instanceof RecipeBookComponentAccess componentAccess) {
			EditBox vanillaSearchBox = componentAccess.unitedMinecraft$getSearchBox();
			if (vanillaSearchBox != null) {
				vanillaSearchBox.setValue(recipeSearchTerm);
			}
		}

		narrateRecipeFocus(player, false);
	}

	/**
	 * Case-insensitive substring match against either the recipe's resolved result name OR any
	 * one of its ingredients' names - vanilla's own recipe-book search matches both (e.g.
	 * searching "log" surfaces every recipe that *uses* a log, not just ones literally named
	 * "log"), and matching only the result name here would silently show far fewer matches than
	 * the vanilla search box this class mirrors its term into, making navigation look broken
	 * when it's really just a much narrower filter.
	 */
	private static boolean matchesRecipeSearch(RecipeDisplayEntry entry, LocalPlayer player) {
		if (recipeSearchTerm.isBlank()) {
			return true;
		}
		String term = recipeSearchTerm.toLowerCase(Locale.ROOT);
		ContextMap context = SlotDisplayContext.fromLevel(player.level());

		// Plain hover name, not ItemDescriptions.describe() - that builds the item's full
		// tooltip (enchantments, attribute modifiers, potion effects, and so on via
		// getTooltipLines), which this is called for on every candidate recipe on every
		// keypress/filter change and never actually needed for a name match in the first
		// place (see this method's own doc: matching is against names, not full descriptions).
		List<ItemStack> results = entry.resultItems(context);
		for (ItemStack result : results) {
			if (result.getHoverName().getString().toLowerCase(Locale.ROOT).contains(term)) {
				return true;
			}
		}
		for (ItemStack ingredient : recipeIngredients(entry.display(), context)) {
			if (ingredient.getHoverName().getString().toLowerCase(Locale.ROOT).contains(term)) {
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

	/**
	 * The ingredients a recipe actually needs, one stack per distinct item with duplicate grid
	 * slots merged into a single count (e.g. a recipe needing planks in six different grid
	 * cells narrates as one "6 Oak Planks" instead of six separate "Oak Planks"). Each slot is
	 * resolved to a single representative stack via {@link SlotDisplay#resolveForFirstStack} -
	 * the same "currently shown" item vanilla's own recipe book icon cycles through for a slot
	 * that accepts several interchangeable items (any plank, any log...), rather than trying to
	 * narrate every acceptable alternative for a slot at once.
	 */
	private static List<ItemStack> recipeIngredients(RecipeDisplay display, ContextMap context) {
		List<SlotDisplay> slots;
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			slots = shaped.ingredients();
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			slots = shapeless.ingredients();
		} else if (display instanceof FurnaceRecipeDisplay furnace) {
			slots = List.of(furnace.ingredient());
		} else {
			return List.of();
		}

		List<ItemStack> merged = new ArrayList<>();
		for (SlotDisplay slot : slots) {
			ItemStack stack = slot.resolveForFirstStack(context);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack existing = merged.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack)).findFirst().orElse(null);
			if (existing != null) {
				existing.grow(stack.getCount());
			} else {
				merged.add(stack.copy());
			}
		}
		return merged;
	}

	/**
	 * Groups with at least one variant surviving all three independent filters (craftable-only,
	 * category, search term) - each applied via {@link #matchingVariantsIn}, so a group mixing
	 * matching and non-matching variants (e.g. oak planks craftable, jungle planks not; or only
	 * some colors matching a search term) still shows up here as long as at least one survives.
	 */
	private static List<RecipeCollection> visibleRecipeGroups() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return List.of();
		}
		List<RecipeCollection> result = new ArrayList<>();
		for (RecipeCollection group : recipeGroups) {
			if (!matchingVariantsIn(group, player).isEmpty()) {
				result.add(group);
			}
		}
		return result;
	}

	/** The variants of one group surviving the craftable-only filter, the category filter, and the search term - all independent of each other. */
	private static List<RecipeDisplayEntry> matchingVariantsIn(RecipeCollection group, LocalPlayer player) {
		List<RecipeDisplayEntry> result = new ArrayList<>();
		for (RecipeDisplayEntry entry : group.getSelectedRecipes(RecipeCollection.CraftableStatus.ANY)) {
			if (recipeCraftableOnlyFilter && !group.isCraftable(entry.id())) {
				continue;
			}
			if (currentRecipeCategory != null && entry.category() != currentRecipeCategory) {
				continue;
			}
			if (!matchesRecipeSearch(entry, player)) {
				continue;
			}
			result.add(entry);
		}
		return result;
	}

	private static List<RecipeDisplayEntry> currentRecipeVariants() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<RecipeCollection> groups = visibleRecipeGroups();
		if (player == null || recipeGroupIndex < 0 || recipeGroupIndex >= groups.size()) {
			return List.of();
		}
		return matchingVariantsIn(groups.get(recipeGroupIndex), player);
	}

	private static void narrateRecipeFocus(LocalPlayer player, boolean announceSection) {
		// visibleRecipeGroups() re-filters every recipe group against the search term (and thus
		// re-runs matchesRecipeSearch over every candidate) - compute it once here rather than
		// via currentRecipeVariants() and then again below for the group itself.
		List<RecipeCollection> groups = visibleRecipeGroups();
		List<RecipeDisplayEntry> variants = recipeGroupIndex >= 0 && recipeGroupIndex < groups.size()
				? matchingVariantsIn(groups.get(recipeGroupIndex), player)
				: List.of();
		MutableComponent message;
		if (variants.isEmpty()) {
			message = Component.translatable("united_minecraft.menu.recipe_book.empty").copy();
		} else {
			RecipeCollection group = groups.get(recipeGroupIndex);
			RecipeDisplayEntry entry = variants.get(Math.min(recipeVariantIndex, variants.size() - 1));
			ContextMap context = SlotDisplayContext.fromLevel(player.level());
			List<ItemStack> results = entry.resultItems(context);
			MutableComponent itemName = results.isEmpty()
					? Component.translatable("united_minecraft.narrate.hotbar_empty")
					: ItemDescriptions.describe(results.get(0), player);
			Component status = Component.translatable(group.isCraftable(entry.id())
					? "united_minecraft.menu.recipe_book.craftable"
					: "united_minecraft.menu.recipe_book.not_craftable");
			message = itemName.copy().append(Component.literal(", ")).append(status);

			List<ItemStack> ingredients = recipeIngredients(entry.display(), context);
			if (!ingredients.isEmpty()) {
				MutableComponent ingredientList = ItemDescriptions.describe(ingredients.get(0), player).copy();
				for (int i = 1; i < ingredients.size(); i++) {
					ingredientList = ingredientList.append(Component.literal(", ")).append(ItemDescriptions.describe(ingredients.get(i), player));
				}
				message = message.append(Component.literal(", ")).append(
						Component.translatable("united_minecraft.menu.recipe_book.requires", ingredientList));
			}

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

	/** Up/Down/Enter reuse the same general container actions as ordinary slot navigation - no dedicated Enchant Options actions needed. */
	private static boolean handleEnchantOptionKey(AbstractContainerScreen<?> screen, LocalPlayer player, KeyEvent event) {
		if (ClientKeyBindings.CONTAINER_NAV_UP.current().matches(event)) {
			moveEnchantOption(screen, player, -1);
		} else if (ClientKeyBindings.CONTAINER_NAV_DOWN.current().matches(event)) {
			moveEnchantOption(screen, player, 1);
		} else if (ClientKeyBindings.CONTAINER_PICKUP.current().matches(event)
				|| ClientKeyBindings.CONTAINER_PICKUP_SPLIT.current().matches(event)) {
			selectEnchantOption(screen, player);
		} else {
			return true;
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

	/** Up/Down/Enter reuse the same general container actions as ordinary slot navigation - no dedicated Trades actions needed. */
	private static boolean handleTradeKey(AbstractContainerScreen<?> screen, KeyEvent event) {
		if (ClientKeyBindings.CONTAINER_NAV_UP.current().matches(event)) {
			moveTrade(screen, -1);
		} else if (ClientKeyBindings.CONTAINER_NAV_DOWN.current().matches(event)) {
			moveTrade(screen, 1);
		} else if (ClientKeyBindings.CONTAINER_PICKUP.current().matches(event)
				|| ClientKeyBindings.CONTAINER_PICKUP_SPLIT.current().matches(event)) {
			selectTrade(screen);
		} else {
			return true;
		}
		return false;
	}

	private static void moveTrade(AbstractContainerScreen<?> screen, int direction) {
		if (!(screen.getMenu() instanceof MerchantMenu menu)) {
			return;
		}
		int size = menu.getOffers().size();
		int next = tradeIndex + direction;
		if (size == 0 || next < 0 || next >= size) {
			return;
		}
		tradeIndex = next;
		narrateTradeFocus(screen, false);
	}

	/**
	 * Selects the focused trade via the same three calls vanilla's own {@code TradeOfferButton}
	 * click makes ({@code MerchantScreen#postButtonClick}): {@code setSelectionHint} and {@code
	 * tryMoveItems} locally (the latter is what actually moves matching items from the player's
	 * inventory into the payment slots - not something clicking a slot does on its own), then a
	 * {@link ServerboundSelectTradePacket} so the server does the same.
	 */
	private static void selectTrade(AbstractContainerScreen<?> screen) {
		if (!(screen.getMenu() instanceof MerchantMenu menu) || menu.getOffers().isEmpty()) {
			return;
		}
		menu.setSelectionHint(tradeIndex);
		menu.tryMoveItems(tradeIndex);
		Minecraft.getInstance().getConnection().send(new ServerboundSelectTradePacket(tradeIndex));
		narrateTradeFocus(screen, false);
	}

	private static void narrateTradeFocus(AbstractContainerScreen<?> screen, boolean announceSection) {
		if (!(screen.getMenu() instanceof MerchantMenu menu)) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		MerchantOffers offers = menu.getOffers();
		MutableComponent message;
		if (offers.isEmpty() || tradeIndex >= offers.size()) {
			message = Component.translatable("united_minecraft.menu.trade.empty").copy();
		} else {
			MerchantOffer offer = offers.get(tradeIndex);
			message = ItemDescriptions.describe(offer.getCostA(), player).copy();
			if (!offer.getCostB().isEmpty()) {
				message = message.append(Component.literal(", ")).append(ItemDescriptions.describe(offer.getCostB(), player));
			}
			message = message.append(Component.literal(", ")).append(Component.translatable(
					"united_minecraft.menu.trade.result", ItemDescriptions.describe(offer.getResult(), player)));
			if (offer.isOutOfStock()) {
				message = message.append(Component.literal(", ")).append(
						Component.translatable("united_minecraft.menu.trade.out_of_stock"));
			}
			message = message.append(Component.literal(", ")).append(Component.translatable(
					"united_minecraft.menu.trade.number", tradeIndex + 1, offers.size()));
		}

		if (announceSection) {
			message = sectionLabel(Section.TRADES).copy().append(Component.literal(". ")).append(message);
		}
		Minecraft.getInstance().getNarrator().saySystemNow(message);
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
				case RECIPE_BOOK, ENCHANT_OPTIONS, RENAME, TRADES -> false;
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
		lastNarratedSlotItem = slot.getItem().copy();

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
			// CONTAINER is a stand-in for whatever this menu actually is (chest, furnace, crafting
			// table, the villager's own name...) - the screen's own title is exactly that, the same
			// text vanilla itself puts at the top of the screen, rather than a fixed generic label.
			Component sectionName = currentSection == Section.CONTAINER ? screen.getTitle() : sectionLabel(currentSection);
			message = sectionName.copy().append(Component.literal(". ")).append(message);
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
			case TRADES -> Component.translatable("united_minecraft.menu.section.trades");
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
		// Covers both the crafting table's 3x3 grid and the player inventory's own 2x2 one.
		// getInputGridSlots() lists slots in the same left-to-right, top-to-bottom order they're
		// added in (see AbstractCraftingMenu#addCraftingGridSlots), so its list index converts
		// straight to a 1-based column/row - "Crafting 2, 1" for the second slot from the left on
		// the top row - instead of every grid slot narrating as the same bare "Crafting".
		if (menu instanceof AbstractCraftingMenu craftingMenu) {
			if (slot == craftingMenu.getResultSlot()) {
				return Component.translatable("united_minecraft.menu.slot.output");
			}
			int gridIndex = craftingMenu.getInputGridSlots().indexOf(slot);
			if (gridIndex >= 0) {
				int width = craftingMenu.getGridWidth();
				return Component.translatable(
						"united_minecraft.menu.slot.crafting_grid_position", gridIndex % width + 1, gridIndex / width + 1);
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
		// MerchantMenu.PAYMENT1_SLOT/PAYMENT2_SLOT/RESULT_SLOT are protected, not public (unlike
		// every other menu type handled here), so these are the same 0/1/2 literals they're
		// defined as - confirmed against the game's own MerchantMenu class file.
		if (menu instanceof MerchantMenu) {
			return switch (slot.index) {
				case 0 -> Component.translatable("united_minecraft.menu.slot.payment_1");
				case 1 -> Component.translatable("united_minecraft.menu.slot.payment_2");
				case 2 -> Component.translatable("united_minecraft.menu.slot.output");
				default -> Component.translatable("united_minecraft.menu.slot.storage");
			};
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
		RENAME, TRADES, CONTAINER, RECIPE_BOOK, ENCHANT_OPTIONS, EQUIPMENT, INVENTORY, HOTBAR
	}

	private enum Direction {
		LEFT, RIGHT, UP, DOWN
	}
}
