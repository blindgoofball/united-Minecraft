package com.nibblenerds.unitedminecraft.client.speech;

import com.mojang.text2speech.Narrator;

/**
 * A {@link Narrator} that speaks through NVDA when it's running, and otherwise
 * falls back to the platform narrator Minecraft would normally use (SAPI on
 * Windows). Speech volume is intentionally ignored for NVDA output, since NVDA
 * manages its own voice volume/rate settings.
 */
public class NvdaNarrator implements Narrator {
	private final NvdaController nvda;
	private final Narrator fallback;

	private NvdaNarrator(NvdaController nvda, Narrator fallback) {
		this.nvda = nvda;
		this.fallback = fallback;
	}

	/** Wraps {@code fallback} with NVDA output, if NVDA's controller client could be loaded. */
	public static Narrator create(Narrator fallback) {
		return NvdaController.getInstance()
				.<Narrator>map(controller -> new NvdaNarrator(controller, fallback))
				.orElse(fallback);
	}

	@Override
	public void say(String text, boolean interrupt, float volume) {
		if (nvda.isRunning()) {
			if (interrupt) {
				nvda.cancel();
			}
			nvda.speak(text);
		} else {
			fallback.say(text, interrupt, volume);
		}
	}

	@Override
	public void clear() {
		nvda.cancel();
		fallback.clear();
	}

	@Override
	public boolean active() {
		return nvda.isRunning() || fallback.active();
	}

	@Override
	public void destroy() {
		fallback.destroy();
	}
}
