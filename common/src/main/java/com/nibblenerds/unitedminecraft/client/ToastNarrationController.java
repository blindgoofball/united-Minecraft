package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Narrates vanilla's toast popups (recipe unlocked, advancement earned, now-playing,
 * system warnings, tutorial hints) via the mixins in {@link
 * com.nibblenerds.unitedminecraft.client.mixin} - they display visually but say nothing
 * out loud on their own.
 *
 * <p>Skips re-narrating the exact same text back to back - needed because {@code
 * SystemToast} funnels both a brand new toast and an in-place update (e.g. a persisting
 * low-disk-space warning) through the same method, and {@code NowPlayingToast} recomputes
 * its text every render frame while visible rather than once. A single shared cache
 * (rather than per-toast-type state) is enough since two different toasts producing the
 * exact same text back to back is vanishingly unlikely and narrating it twice wouldn't
 * be wrong anyway.
 */
public final class ToastNarrationController {
	private static String lastNarratedText;

	private ToastNarrationController() {
	}

	public static void narrate(Component message) {
		String text = message.getString();
		if (text.equals(lastNarratedText)) {
			return;
		}
		lastNarratedText = text;
		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}
}
