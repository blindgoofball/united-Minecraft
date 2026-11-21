package com.nibblenerds.unitedminecraft.client.speech;

import com.nibblenerds.unitedminecraft.client.access.ScreenNarrationAccess;

import net.minecraft.client.gui.narration.NarrationTrigger;
import net.minecraft.client.gui.screens.Screen;

/**
 * Asks a screen to narrate as if a key had just been pressed. {@code Screen#scheduleNarration()}
 * says the narration came from the system, and a slot group answers that by reading the slot
 * under the mouse pointer when there is one - with its "Left-click" hint - instead of the slot
 * the keyboard is on.
 */
public final class KeyboardNarration {
	private KeyboardNarration() {
	}

	public static void schedule(Screen screen) {
		((ScreenNarrationAccess) screen).unitedMinecraft$scheduleNarration(0L, true, NarrationTrigger.KEYBOARD);
	}
}
