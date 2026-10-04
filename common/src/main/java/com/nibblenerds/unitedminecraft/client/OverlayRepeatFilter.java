package com.nibblenerds.unitedminecraft.client;

/**
 * Decides which action-bar messages are worth narrating - see {@code ChatOverlayNarrationMixin}.
 * Text that differs from the last one is always narrated. The same text again is skipped while
 * the previous copy is still on screen - a server re-sending a HUD to keep it up - but narrated
 * once that copy would have faded, since then it's a genuine repeat (a second "You may not rest
 * now" at a bed).
 */
public final class OverlayRepeatFilter {
	/** Vanilla's {@code Hud#setOverlayMessage} shows an overlay for 60 ticks. */
	static final long OVERLAY_VISIBLE_MILLIS = 3000;

	private String lastText;
	/** When any overlay was last received - each resend restarts vanilla's display timer. */
	private long lastReceivedMillis = Long.MIN_VALUE / 2;

	/** Records that {@code text} was just received at {@code nowMillis}, and returns whether to narrate it. */
	public boolean shouldNarrate(String text, long nowMillis) {
		boolean stillShowing = nowMillis - lastReceivedMillis < OVERLAY_VISIBLE_MILLIS;
		lastReceivedMillis = nowMillis;
		if (stillShowing && text.equals(lastText)) {
			return false;
		}
		lastText = text;
		return true;
	}
}
