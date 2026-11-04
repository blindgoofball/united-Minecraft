package com.nibblenerds.unitedminecraft.client;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.reflect.TypeToken;

import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Names the Scanner should leave out of a category: a filter of "hopper" on Storage hides every
 * hopper from that category's list. A filter matches the whole spoken name of an item, ignoring
 * case and surrounding spaces - "hopper" hides Hopper but not Hopper Minecart - and is checked
 * against the same text the Scanner narrates, so it also matches a name the player gave
 * something. Filters belong to one category, and see {@link ScannerController} for where they
 * are applied.
 *
 * <p>A filter is either session-only (the default), gone when the world is left, or persistent,
 * saved per world through {@link WorldScopedStore} like markers and block names.
 */
public final class ScannerFilterController {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/scanner_filters");
	private static final Type FILTER_LIST_TYPE = new TypeToken<List<StoredFilter>>() {
	}.getType();

	private static final WorldScopedStore<StoredFilter> STORE =
			new WorldScopedStore<>("scanner_filters", FILTER_LIST_TYPE, LOGGER);
	private static final List<StoredFilter> session = new ArrayList<>();

	/** Each category's filters in {@link #normalize}d form, built on first use after a change. */
	private static Map<ScannerCategory, Set<String>> normalized = new EnumMap<>(ScannerCategory.class);

	private ScannerFilterController() {
	}

	/**
	 * A filter as saved: the category's enum name rather than the enum itself, so a category that
	 * is later renamed or removed just stops matching instead of breaking the whole file.
	 */
	record StoredFilter(String category, String text) {
	}

	/** A filter as the filter screen shows it. */
	record Filter(ScannerCategory category, String text, boolean persistent) {
	}

	enum AddResult {
		ADDED,
		/** Already filtering this for the session, and now saved too. */
		MADE_PERSISTENT,
		ALREADY_FILTERED,
		/** Nothing but spaces. */
		BLANK
	}

	public static void tick(Minecraft client) {
		if (STORE.tick(client)) {
			normalized = new EnumMap<>(ScannerCategory.class);
		}
	}

	static void onWorldDeleted(String levelId) {
		STORE.forgetSingleplayerWorld(levelId);
	}

	/** World exit: session filters go, and the saved ones are reloaded for whichever world is joined next. */
	public static void reset() {
		STORE.reset();
		session.clear();
		normalized = new EnumMap<>(ScannerCategory.class);
	}

	static boolean hasFilters(ScannerCategory category) {
		return !normalizedFor(category).isEmpty();
	}

	/** Whether an item with this spoken name is hidden in {@code category}. */
	static boolean isFiltered(ScannerCategory category, String spokenName) {
		return normalizedFor(category).contains(normalize(spokenName));
	}

	/** This category's filters: the saved ones, then the session's, each in the order added. */
	static List<Filter> filtersFor(ScannerCategory category) {
		List<Filter> result = new ArrayList<>();
		for (StoredFilter stored : STORE.entries()) {
			if (stored.category().equals(category.name())) {
				result.add(new Filter(category, stored.text(), true));
			}
		}
		for (StoredFilter stored : session) {
			if (stored.category().equals(category.name())) {
				result.add(new Filter(category, stored.text(), false));
			}
		}
		return result;
	}

	static AddResult add(ScannerCategory category, String text, boolean persistent) {
		AddResult result = addTo(STORE.entries(), session, new StoredFilter(category.name(), text.strip()), persistent);
		if (result == AddResult.ADDED || result == AddResult.MADE_PERSISTENT) {
			if (persistent) {
				STORE.save();
			}
			normalized = new EnumMap<>(ScannerCategory.class);
		}
		return result;
	}

	static void remove(Filter filter) {
		StoredFilter target = new StoredFilter(filter.category().name(), filter.text());
		if (filter.persistent()) {
			if (STORE.entries().remove(target)) {
				STORE.save();
			}
		} else {
			session.remove(target);
		}
		normalized = new EnumMap<>(ScannerCategory.class);
	}

	/**
	 * Adds {@code filter} to {@code saved} (if {@code persistent}) or {@code sessionOnly}. A filter
	 * the same as one already there - ignoring case - is not added twice; asking for it to be
	 * persistent when it is only a session filter moves it across.
	 */
	static AddResult addTo(List<StoredFilter> saved, List<StoredFilter> sessionOnly, StoredFilter filter, boolean persistent) {
		String wanted = normalize(filter.text());
		if (wanted.isEmpty()) {
			return AddResult.BLANK;
		}
		if (indexOf(saved, filter.category(), wanted) >= 0) {
			return AddResult.ALREADY_FILTERED;
		}
		int inSession = indexOf(sessionOnly, filter.category(), wanted);
		if (inSession >= 0) {
			if (!persistent) {
				return AddResult.ALREADY_FILTERED;
			}
			saved.add(sessionOnly.remove(inSession));
			return AddResult.MADE_PERSISTENT;
		}
		(persistent ? saved : sessionOnly).add(filter);
		return AddResult.ADDED;
	}

	private static int indexOf(List<StoredFilter> filters, String category, String normalizedText) {
		for (int i = 0; i < filters.size(); i++) {
			if (filters.get(i).category().equals(category) && normalize(filters.get(i).text()).equals(normalizedText)) {
				return i;
			}
		}
		return -1;
	}

	/** Case and surrounding spaces don't matter, nor does how many spaces separate words. */
	static String normalize(String text) {
		return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static Set<String> normalizedFor(ScannerCategory category) {
		return normalized.computeIfAbsent(category, key -> {
			Set<String> texts = new HashSet<>();
			for (StoredFilter stored : STORE.entries()) {
				if (stored.category().equals(key.name())) {
					texts.add(normalize(stored.text()));
				}
			}
			for (StoredFilter stored : session) {
				if (stored.category().equals(key.name())) {
					texts.add(normalize(stored.text()));
				}
			}
			return texts;
		});
	}
}
