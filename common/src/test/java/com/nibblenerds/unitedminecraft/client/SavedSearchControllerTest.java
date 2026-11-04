package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.nibblenerds.unitedminecraft.client.SavedSearchController.SaveResult;
import com.nibblenerds.unitedminecraft.client.SavedSearchController.SavedSearch;

/** {@link SavedSearchController}'s rules for saving and editing, and {@link CategoryRef}'s keys. */
class SavedSearchControllerTest {
	private final List<SavedSearch> saved = new ArrayList<>();
	private final AtomicInteger ids = new AtomicInteger();
	private final Supplier<String> nextId = () -> "id" + ids.incrementAndGet();

	private SaveResult save(String name, String term) {
		return SavedSearchController.saveTo(saved, name, term, nextId, 3);
	}

	@Test
	void aNewSearchIsAddedWithTheNameAndTermTrimmed() {
		assertEquals(SaveResult.ADDED, save("  ore ", " ore  "));
		assertEquals(new SavedSearch("id1", "ore", "ore"), saved.get(0));
	}

	@Test
	void savingUnderAnExistingNameReplacesItsTermAndKeepsItsId() {
		save("ore", "ore");
		assertEquals(SaveResult.UPDATED, save("ORE", "iron ore"));
		assertEquals(1, saved.size());
		assertEquals(new SavedSearch("id1", "ore", "iron ore"), saved.get(0));
	}

	@Test
	void nothingMoreIsSavedOnceTheLimitIsReached() {
		save("a", "a");
		save("b", "b");
		save("c", "c");
		assertEquals(SaveResult.FULL, save("d", "d"));
		assertEquals(3, saved.size());
	}

	@Test
	void anExistingNameCanStillBeUpdatedWhenFull() {
		save("a", "a");
		save("b", "b");
		save("c", "c");
		assertEquals(SaveResult.UPDATED, save("a", "alpha"));
	}

	@Test
	void aBlankNameOrTermIsRefused() {
		assertEquals(SaveResult.BLANK, save("  ", "ore"));
		assertEquals(SaveResult.BLANK, save("ore", "   "));
		assertTrue(saved.isEmpty());
	}

	@Test
	void editingChangesTheNameAndTermButNotTheId() {
		save("ore", "ore");
		assertEquals(SaveResult.UPDATED, SavedSearchController.updateIn(saved, "id1", "Metals", "iron"));
		assertEquals(new SavedSearch("id1", "Metals", "iron"), saved.get(0));
	}

	@Test
	void aSavedSearchMayKeepItsOwnNameWhenEdited() {
		save("ore", "ore");
		assertEquals(SaveResult.UPDATED, SavedSearchController.updateIn(saved, "id1", "ore", "iron ore"));
	}

	@Test
	void editingToAnotherSearchesNameIsRefused() {
		save("ore", "ore");
		save("wood", "log");
		assertEquals(SaveResult.NAME_TAKEN, SavedSearchController.updateIn(saved, "id2", "Ore", "log"));
		assertEquals("wood", saved.get(1).name());
	}

	@Test
	void editingASearchThatIsGoneIsReported() {
		assertEquals(SaveResult.NOT_FOUND, SavedSearchController.updateIn(saved, "nope", "a", "b"));
	}

	@Test
	void aBuiltInCategoryIsFiledUnderItsEnumNameAndASavedSearchUnderItsId() {
		assertEquals("STORAGE", CategoryRef.of(ScannerCategory.STORAGE).filterKey());
		CategoryRef ref = new CategoryRef(ScannerCategory.SEARCH, new SavedSearch("id9", "ore", "ore"));
		assertEquals("saved:id9", ref.filterKey());
		assertNotEquals(CategoryRef.of(ScannerCategory.SEARCH).filterKey(), ref.filterKey());
	}

	@Test
	void renamingASavedSearchKeepsItsFilterKey() {
		CategoryRef before = new CategoryRef(ScannerCategory.SEARCH, new SavedSearch("id9", "ore", "ore"));
		CategoryRef after = new CategoryRef(ScannerCategory.SEARCH, new SavedSearch("id9", "Metals", "iron"));
		assertEquals(before.filterKey(), after.filterKey());
	}

	@Test
	void onlyPlainSearchTakesItsTermFromTheKeyboardEachTime() {
		CategoryRef plain = CategoryRef.of(ScannerCategory.SEARCH);
		CategoryRef preset = new CategoryRef(ScannerCategory.SEARCH, new SavedSearch("id9", "ore", "ore"));
		assertTrue(plain.isFreeSearch());
		assertFalse(preset.isFreeSearch());
		assertFalse(CategoryRef.of(ScannerCategory.STORAGE).isFreeSearch());
		assertEquals("typed", plain.searchTerm("typed"));
		assertEquals("ore", preset.searchTerm("typed"));
	}
}
