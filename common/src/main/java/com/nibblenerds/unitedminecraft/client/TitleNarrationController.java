package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.access.HudTitleAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Narrates the title and subtitle text servers put in the middle of the screen, which vanilla
 * never says aloud.
 *
 * <p>Polled once a tick from what {@code Hud} is actually showing rather than hooked on the
 * title packets, for two reasons. A title and its subtitle arrive as separate packets (the
 * subtitle first, normally), so reacting to each would say the title twice; by the end of the
 * tick both have landed and are read together. And servers that keep a title up by re-sending it
 * would otherwise repeat it every resend - here, the same text is only said again once the old
 * one has actually faded out, so a countdown ("3", "2", "1") is read in full while a title held
 * on screen is read once.
 *
 * <p>Queued rather than interrupting: titles usually come alongside chat or other narration, and
 * a countdown should be heard in order.
 */
public final class TitleNarrationController {
	private static String lastTitle;
	private static String lastSubtitle;

	private TitleNarrationController() {
	}

	public static void reset() {
		lastTitle = null;
		lastSubtitle = null;
	}

	static void tick(Minecraft client) {
		if (client.player == null) {
			reset();
			return;
		}
		HudTitleAccess hud = (HudTitleAccess) client.gui.hud;
		Component title = hud.unitedMinecraft$getVisibleTitle();
		if (title == null) {
			// Faded out or cleared - the same text showing again later is a new title.
			reset();
			return;
		}
		String titleText = title.getString().strip();
		Component subtitle = hud.unitedMinecraft$getSubtitle();
		String subtitleText = subtitle == null ? "" : subtitle.getString().strip();
		boolean titleChanged = !titleText.equals(lastTitle);
		boolean subtitleChanged = !subtitleText.equals(lastSubtitle);
		lastTitle = titleText;
		lastSubtitle = subtitleText;
		if (!UnitedMinecraftConfig.get().titleNarrationEnabled || (!titleChanged && !subtitleChanged)) {
			return;
		}

		Component message;
		if (titleChanged) {
			if (titleText.isEmpty() && subtitleText.isEmpty()) {
				return;
			}
			// A blank title with a subtitle is a common way to show the subtitle line alone.
			message = titleText.isEmpty() ? subtitle
					: subtitleText.isEmpty() ? title
					: title.copy().append(Component.literal(". ")).append(subtitle);
		} else {
			// Only the subtitle changed under a title already read - just the new part.
			if (subtitleText.isEmpty()) {
				return;
			}
			message = subtitle;
		}
		client.getNarrator().saySystemQueued(message);
	}
}
