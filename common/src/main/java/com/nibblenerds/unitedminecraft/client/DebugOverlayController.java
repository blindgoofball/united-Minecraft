package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.keybind.ClientKeyBindings;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Navigation of the F3 overlay by section and line, on top of {@link DebugOverlayModel}'s live
 * copy. The cursor is a section id plus a line index rather than a copy of any text, so stepping
 * onto a line always reads its current value, and the cursor survives the overlay's content
 * changing around it (sections appearing or disappearing, a section getting shorter).
 * Nothing is narrated on its own - only in response to a key.
 */
final class DebugOverlayController {
	private static Identifier cursorSection;
	private static int cursorLine;
	// Whether the overlay was showing as of the last tick; null until a first tick in a world, so
	// joining one never announces a change that was not made by the player.
	private static Boolean wasShown;

	private DebugOverlayController() {
	}

	static void reset() {
		cursorSection = null;
		cursorLine = 0;
		wasShown = null;
	}

	/**
	 * Announces the overlay opening or closing. Must run after {@link DebugOverlayModel#tick} so
	 * the sections are already built on the tick the overlay opens; on opening the cursor starts
	 * at the first line of the first section and that line is read along with the announcement.
	 */
	static void tick(Minecraft client) {
		boolean shown = DebugOverlayModel.isOverlayShown(client);
		Boolean before = wasShown;
		wasShown = shown;
		if (before == null || before == shown) {
			return;
		}
		if (!shown) {
			cursorSection = null;
			cursorLine = 0;
			say(client, Component.translatable("united_minecraft.narrate.debug_closed"));
			return;
		}
		List<DebugOverlayModel.Section> sections = DebugOverlayModel.sections();
		if (sections.isEmpty()) {
			say(client, Component.translatable("united_minecraft.narrate.debug_opened"));
			return;
		}
		DebugOverlayModel.Section first = sections.get(0);
		cursorSection = first.id();
		cursorLine = 0;
		say(client, Component.translatable("united_minecraft.narrate.debug_opened_with_line",
				sectionName(first.id()), cleanLine(first.lines().get(0))));
	}

	static void handleKeys(Minecraft client) {
		if (!DebugOverlayModel.isOverlayShown(client)) {
			return;
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.DEBUG_NEXT_SECTION)) {
			moveSection(client, 1);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.DEBUG_PREVIOUS_SECTION)) {
			moveSection(client, -1);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.DEBUG_NEXT_LINE)) {
			moveLine(client, 1);
		} else if (ClientKeyBindings.pressed(ClientKeyBindings.DEBUG_PREVIOUS_LINE)) {
			moveLine(client, -1);
		}
	}

	private static void moveSection(Minecraft client, int delta) {
		List<DebugOverlayModel.Section> sections = DebugOverlayModel.sections();
		if (sections.isEmpty()) {
			say(client, Component.translatable("united_minecraft.narrate.debug_empty"));
			return;
		}
		int current = indexOfCursor(sections);
		int target = current < 0 ? (delta > 0 ? 0 : sections.size() - 1) : current + delta;
		if (target < 0 || target >= sections.size()) {
			say(client, Component.translatable(delta > 0
					? "united_minecraft.narrate.debug_last_section"
					: "united_minecraft.narrate.debug_first_section"));
			return;
		}
		cursorSection = sections.get(target).id();
		cursorLine = 0;
		narrateCursor(client, sections.get(target), true);
	}

	private static void moveLine(Minecraft client, int delta) {
		List<DebugOverlayModel.Section> sections = DebugOverlayModel.sections();
		if (sections.isEmpty()) {
			say(client, Component.translatable("united_minecraft.narrate.debug_empty"));
			return;
		}
		int current = indexOfCursor(sections);
		if (current < 0) {
			// No cursor yet, or its section is gone: start at the first section.
			cursorSection = sections.get(0).id();
			cursorLine = 0;
			narrateCursor(client, sections.get(0), true);
			return;
		}
		DebugOverlayModel.Section section = sections.get(current);
		int target = Math.min(cursorLine, section.lines().size() - 1) + delta;
		if (target < 0 || target >= section.lines().size()) {
			say(client, Component.translatable(delta > 0
					? "united_minecraft.narrate.debug_last_line"
					: "united_minecraft.narrate.debug_first_line"));
			return;
		}
		cursorLine = target;
		narrateCursor(client, section, false);
	}

	private static int indexOfCursor(List<DebugOverlayModel.Section> sections) {
		if (cursorSection == null) {
			return -1;
		}
		for (int i = 0; i < sections.size(); i++) {
			if (sections.get(i).id().equals(cursorSection)) {
				return i;
			}
		}
		return -1;
	}

	private static void narrateCursor(Minecraft client, DebugOverlayModel.Section section, boolean withSectionName) {
		String line = cleanLine(section.lines().get(Math.min(cursorLine, section.lines().size() - 1)));
		say(client, withSectionName
				? Component.translatable("united_minecraft.narrate.debug_section", sectionName(section.id()), line)
				: Component.literal(line));
	}

	private static void say(Minecraft client, Component message) {
		client.getNarrator().saySystemNow(message);
	}

	/** A translated name where we ship one, otherwise the id's path as words (other mods' entries). */
	static Component sectionName(Identifier id) {
		String key = "united_minecraft.debug_section." + id.getPath();
		if ("minecraft".equals(id.getNamespace()) && Language.getInstance().has(key)) {
			return Component.translatable(key);
		}
		return Component.literal(id.getPath().replace('_', ' ').replace('/', ' '));
	}

	/** Strips formatting codes; turns a bare biome id into the biome's translated name. */
	static String cleanLine(String line) {
		String text = ChatFormatting.stripFormatting(line);
		if (text == null) {
			return line;
		}
		String biomePrefix = "Biome: ";
		if (text.startsWith(biomePrefix)) {
			Identifier id = Identifier.tryParse(text.substring(biomePrefix.length()).trim());
			if (id != null) {
				return biomePrefix + Component.translatable(Util.makeDescriptionId("biome", id)).getString();
			}
		}
		return text;
	}
}
