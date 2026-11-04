package com.nibblenerds.unitedminecraft.client;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.reflect.TypeToken;

import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Searches the player has saved as categories: a name and the term to search for. Each shows up
 * in the Scanner's category order after Search and behaves exactly like Search with that term
 * already entered - see {@link CategoryRef}. Saved per world through {@link WorldScopedStore},
 * like markers and named blocks.
 *
 * <p>Each has an id of its own, so renaming one doesn't lose the filters kept for it (they are
 * filed under the id - see {@link CategoryRef#filterKey}).
 */
public final class SavedSearchController {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/saved_searches");
	private static final Type SEARCH_LIST_TYPE = new TypeToken<List<SavedSearch>>() {
	}.getType();

	/** Every Home/End press probes each category, so there is a limit to how many are worth having. */
	static final int MAX_SAVED = 20;
	/** The longest name or term, matching the other name prompts. */
	static final int MAX_TEXT = 64;

	private static final WorldScopedStore<SavedSearch> STORE =
			new WorldScopedStore<>("saved_searches", SEARCH_LIST_TYPE, LOGGER);

	/** Bumped whenever the list changes, so the Scanner knows to rebuild its category order. */
	private static int version;

	private SavedSearchController() {
	}

	record SavedSearch(String id, String name, String term) {
	}

	enum SaveResult {
		ADDED,
		/** A saved search with that name already existed; its term was replaced. */
		UPDATED,
		FULL,
		/** The name or the term was empty. */
		BLANK,
		/** Another saved search already has that name (only when editing one). */
		NAME_TAKEN,
		NOT_FOUND
	}

	public static void tick(Minecraft client) {
		if (STORE.tick(client)) {
			version++;
		}
	}

	static void onWorldDeleted(String levelId) {
		STORE.forgetSingleplayerWorld(levelId);
	}

	public static void reset() {
		STORE.reset();
		version++;
	}

	static int version() {
		return version;
	}

	static List<SavedSearch> all() {
		return List.copyOf(STORE.entries());
	}

	/** Saves a search under {@code name}, or replaces the term of one already saved under that name. */
	static SaveResult save(String name, String term) {
		SaveResult result = saveTo(STORE.entries(), name, term, () -> UUID.randomUUID().toString(), MAX_SAVED);
		if (result == SaveResult.ADDED || result == SaveResult.UPDATED) {
			STORE.save();
			version++;
		}
		return result;
	}

	/** Changes the name and term of the saved search with this id. */
	static SaveResult update(String id, String name, String term) {
		SaveResult result = updateIn(STORE.entries(), id, name, term);
		if (result == SaveResult.UPDATED) {
			STORE.save();
			version++;
		}
		return result;
	}

	static void remove(String id) {
		if (STORE.entries().removeIf(saved -> saved.id().equals(id))) {
			STORE.save();
			version++;
		}
	}

	static SaveResult saveTo(List<SavedSearch> saved, String name, String term, Supplier<String> newId, int max) {
		String cleanName = name.strip();
		String cleanTerm = term.strip();
		if (cleanName.isEmpty() || cleanTerm.isEmpty()) {
			return SaveResult.BLANK;
		}
		int existing = indexOfName(saved, cleanName, null);
		if (existing >= 0) {
			SavedSearch old = saved.get(existing);
			saved.set(existing, new SavedSearch(old.id(), old.name(), cleanTerm));
			return SaveResult.UPDATED;
		}
		if (saved.size() >= max) {
			return SaveResult.FULL;
		}
		saved.add(new SavedSearch(newId.get(), cleanName, cleanTerm));
		return SaveResult.ADDED;
	}

	static SaveResult updateIn(List<SavedSearch> saved, String id, String name, String term) {
		String cleanName = name.strip();
		String cleanTerm = term.strip();
		if (cleanName.isEmpty() || cleanTerm.isEmpty()) {
			return SaveResult.BLANK;
		}
		int index = indexOfId(saved, id);
		if (index < 0) {
			return SaveResult.NOT_FOUND;
		}
		if (indexOfName(saved, cleanName, id) >= 0) {
			return SaveResult.NAME_TAKEN;
		}
		saved.set(index, new SavedSearch(id, cleanName, cleanTerm));
		return SaveResult.UPDATED;
	}

	/** Case doesn't matter in a name; {@code exceptId} lets a saved search keep its own name when edited. */
	private static int indexOfName(List<SavedSearch> saved, String name, String exceptId) {
		for (int i = 0; i < saved.size(); i++) {
			if (!saved.get(i).id().equals(exceptId) && saved.get(i).name().equalsIgnoreCase(name)) {
				return i;
			}
		}
		return -1;
	}

	private static int indexOfId(List<SavedSearch> saved, String id) {
		for (int i = 0; i < saved.size(); i++) {
			if (saved.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}
}
