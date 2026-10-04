package com.nibblenerds.unitedminecraft.client.speech;

import com.mojang.text2speech.Narrator;

/**
 * A {@link Narrator} that speaks through Prism's best available backend
 * (preferring a running screen reader over plain TTS - Prism picks this itself),
 * and otherwise falls back to the platform narrator Minecraft would normally use.
 * Speech volume is intentionally ignored, matching the narrator this replaces;
 * Prism backends manage their own voice volume/rate settings.
 */
public class PrismNarrator implements Narrator {
	private final PrismController prism;
	private final Narrator fallback;

	private PrismNarrator(PrismController prism, Narrator fallback) {
		this.prism = prism;
		this.fallback = fallback;
	}

	/** Wraps {@code fallback} with Prism output, if a Prism backend could be loaded. */
	public static Narrator create(Narrator fallback) {
		return PrismController.getInstance()
				.<Narrator>map(controller -> new PrismNarrator(controller, fallback))
				.orElse(fallback);
	}

	@Override
	public void say(String text, boolean interrupt, float volume) {
		// Falls back per message rather than once for good: Prism may have lost its backend and be
		// waiting to pick it back up (see PrismController#speak).
		if (!prism.speak(text, interrupt)) {
			fallback.say(text, interrupt, volume);
		}
	}

	@Override
	public void clear() {
		prism.stop();
		fallback.clear();
	}

	@Override
	public boolean active() {
		return prism.isAvailable() || fallback.active();
	}

	@Override
	public void destroy() {
		// Deliberately does not shut Prism down: PrismController is a process-wide
		// singleton, but a Narrator can be destroyed and rebuilt without the process
		// exiting (e.g. toggling the accessibility narrator setting). Prism's own
		// shutdown is tied to CLIENT_STOPPING instead - see ClientHooks#onClientStopping.
		fallback.destroy();
	}
}
