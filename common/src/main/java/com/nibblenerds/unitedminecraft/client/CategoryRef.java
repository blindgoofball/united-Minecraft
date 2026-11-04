package com.nibblenerds.unitedminecraft.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * One entry in the Scanner's category order: either a built-in {@link ScannerCategory}, or a
 * saved search ({@code saved} set), which is Search with a term already entered and a name of the
 * player's own. All of the Scanner's behaviour is keyed off {@link #kind} - a saved search's kind
 * is {@link ScannerCategory#SEARCH} - so a saved search needs nothing special beyond its term, its
 * name, and the filters kept for it.
 */
record CategoryRef(ScannerCategory kind, SavedSearchController.SavedSearch saved) {
	static CategoryRef of(ScannerCategory kind) {
		return new CategoryRef(kind, null);
	}

	boolean isSavedSearch() {
		return saved != null;
	}

	/** What the Scanner calls this category aloud. */
	MutableComponent label() {
		return saved != null ? Component.literal(saved.name()) : kind.label();
	}

	/**
	 * What this category's filters are filed under - see {@link ScannerFilterController}. A built-in
	 * keeps its enum name; a saved search uses its id, so it has filters of its own and renaming it
	 * doesn't lose them.
	 */
	String filterKey() {
		return saved != null ? "saved:" + saved.id() : kind.name();
	}

	/** The term Search should look for here: the saved one, or {@code freeTerm} for plain Search. */
	String searchTerm(String freeTerm) {
		return saved != null ? saved.term() : freeTerm;
	}

	/** Search, but only the plain one - the one whose term the player types each time. */
	boolean isFreeSearch() {
		return kind == ScannerCategory.SEARCH && saved == null;
	}
}
