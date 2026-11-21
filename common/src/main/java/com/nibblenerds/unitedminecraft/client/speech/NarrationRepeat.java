package com.nibblenerds.unitedminecraft.client.speech;

import net.minecraft.client.gui.narration.NarratedElementType;

/**
 * Lets the focused widget's narration repeat when focus moves to a different widget that reads
 * the same. Vanilla's per-screen narration collector marks an entry as already narrated when its
 * text matches what it last said, so stepping from one empty slot to the next is silent - the
 * player cannot tell they moved. {@code ScreenNarratedWidgetMixin} turns this on around the
 * focused widget's own narration only when focus has moved, and {@code
 * NarrationEntryMixin} clears the already-narrated mark on entries written meanwhile. The screen
 * title and the list name are written outside that window, so they are still said once. Only
 * title entries repeat: a slot group also writes a usage hint, which would otherwise be said on
 * every move.
 */
public final class NarrationRepeat {
	private static boolean forcing;
	private static NarratedElementType currentType;

	private NarrationRepeat() {
	}

	public static void force(boolean on) {
		forcing = on;
	}

	/** Whether the entry being written right now should be said again: only title text is. */
	public static boolean isForcing() {
		return forcing && currentType == NarratedElementType.TITLE;
	}

	/** Noted as each entry is written, so {@link #isForcing} knows what kind it is. */
	public static void entryType(NarratedElementType type) {
		currentType = type;
	}
}
