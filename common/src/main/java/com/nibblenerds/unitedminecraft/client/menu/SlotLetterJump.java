package com.nibblenerds.unitedminecraft.client.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import com.nibblenerds.unitedminecraft.client.access.RecipeBookPageAccess;
import com.nibblenerds.unitedminecraft.client.keybind.Keybind;
import com.nibblenerds.unitedminecraft.client.speech.KeyboardNarration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * First-letter navigation in container screens: a plain letter jumps to the next slot in the
 * focused slot's own group whose item name starts with it, wrapping around, so repeating the
 * letter cycles through every match.
 *
 * <p>Letters are free for this because the screens' own letter actions need Ctrl in this mod's
 * scheme: Ctrl+Q drops the whole stack and Ctrl+F swaps with the off-hand, and a Ctrl or Alt
 * chord is never taken here, so it reaches vanilla untouched. A plain E is a letter too, rather
 * than closing the screen; Escape still does.
 *
 * <p>Only acts when the focus path ends in a slot, so typing into a field on the same screen (the
 * Creative search box, an anvil's name) is never intercepted.
 */
public final class SlotLetterJump {
	private SlotLetterJump() {
	}

	/** @return {@code false} if the key was consumed, the polarity of every screen key hook. */
	public static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event) {
		Character typed = firstLetterOf(event);
		if (typed == null) {
			return true;
		}
		ContainerEventHandler parent = null;
		RecipeBookPage recipePage = null;
		GuiEventListener current = screen;
		while (current instanceof ContainerEventHandler handler) {
			GuiEventListener next = handler.getFocused();
			if (next == null) {
				return true;
			}
			parent = handler;
			current = next;
			if (current instanceof RecipeBookPage page) {
				recipePage = page;
			}
		}
		if (current instanceof RecipeButton button && recipePage != null) {
			return jumpToRecipe(screen, recipePage, button, typed);
		}
		if (!(current instanceof SlotWidget focused) || parent == null) {
			return true;
		}

		List<SlotWidget> section = new ArrayList<>();
		for (GuiEventListener child : parent.children()) {
			if (child instanceof SlotWidget slot) {
				section.add(slot);
			}
		}
		int start = section.indexOf(focused);
		char letter = typed;
		for (int offset = 1; offset <= section.size(); offset++) {
			SlotWidget candidate = section.get(Math.floorMod(start + offset, section.size()));
			if (startsWithLetter(candidate.slot().getItem(), letter)) {
				parent.setFocused(candidate);
				KeyboardNarration.schedule(screen);
				return false;
			}
		}
		Minecraft.getInstance().getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.menu_no_item_with_letter", String.valueOf(letter).toUpperCase(Locale.ROOT)));
		return false;
	}

	/**
	 * First-letter navigation in the Recipe Book: jumps to the next recipe group, on this page or
	 * another, that has a recipe whose item name starts with the letter, wrapping around, so
	 * repeating the letter cycles through them. Moving to another page is done the way the page
	 * arrows do it.
	 *
	 * @return {@code false}: the key is always consumed here, even when nothing matches
	 */
	private static boolean jumpToRecipe(AbstractContainerScreen<?> screen, RecipeBookPage page, RecipeButton button, char letter) {
		Minecraft client = Minecraft.getInstance();
		RecipeBookPageAccess access = (RecipeBookPageAccess) page;
		List<RecipeCollection> collections = access.unitedMinecraft$collections();
		List<RecipeButton> buttons = access.unitedMinecraft$buttons();
		int perPage = buttons.size();
		int here = buttons.indexOf(button);
		if (perPage == 0 || here < 0 || client.level == null) {
			return true;
		}

		ContextMap context = SlotDisplayContext.fromLevel(client.level);
		List<List<String>> names = new ArrayList<>();
		for (RecipeCollection collection : collections) {
			List<String> inGroup = new ArrayList<>();
			for (RecipeDisplayEntry entry : collection.getRecipes()) {
				List<ItemStack> results = entry.resultItems(context);
				if (!results.isEmpty()) {
					inGroup.add(results.get(0).getHoverName().getString());
				}
			}
			names.add(inGroup);
		}

		int target = RecipeLetterJump.next(names, access.unitedMinecraft$currentPage() * perPage + here, letter);
		if (target < 0) {
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.menu_no_item_with_letter", String.valueOf(letter).toUpperCase(Locale.ROOT)));
			return false;
		}
		int targetPage = target / perPage;
		if (targetPage != access.unitedMinecraft$currentPage()) {
			access.unitedMinecraft$setCurrentPage(targetPage);
			access.unitedMinecraft$updateButtonsForPage();
		}
		page.focusRecipeButton(buttons.get(target % perPage));
		KeyboardNarration.schedule(screen);
		return false;
	}

	/**
	 * The letter for a plain (optionally Shifted) letter key press, or null for anything else.
	 * Modifiers are normalized so Caps Lock and Num Lock, which are reported as modifier bits,
	 * don't turn the navigation off.
	 */
	static Character firstLetterOf(KeyEvent event) {
		if ((Keybind.normalizeModifiers(event.modifiers()) & ~InputConstants.MOD_SHIFT) != 0) {
			return null;
		}
		String name = InputConstants.getKey(event).getDisplayName().getString();
		if (name.codePointCount(0, name.length()) == 1) {
			int cp = name.codePointAt(0);
			return Character.isLetter(cp) && cp <= Character.MAX_VALUE ? Character.toLowerCase((char) cp) : null;
		}
		if (event.key() >= InputConstants.KEY_A && event.key() <= InputConstants.KEY_Z) {
			return (char) ('a' + event.key() - InputConstants.KEY_A);
		}
		return null;
	}

	static boolean startsWithLetter(ItemStack stack, char letter) {
		if (stack.isEmpty()) {
			return false;
		}
		String name = stack.getHoverName().getString().strip();
		return !name.isEmpty() && Character.toLowerCase(name.charAt(0)) == letter;
	}
}
