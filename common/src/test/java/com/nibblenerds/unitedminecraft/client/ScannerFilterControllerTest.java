package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nibblenerds.unitedminecraft.client.ScannerFilterController.AddResult;
import com.nibblenerds.unitedminecraft.client.ScannerFilterController.StoredFilter;

/** {@link ScannerFilterController}'s name matching and the rules for adding a filter. */
class ScannerFilterControllerTest {
	private static final String STORAGE = ScannerCategory.STORAGE.name();
	private static final String WORKSTATIONS = ScannerCategory.WORKSTATIONS.name();

	private final List<StoredFilter> saved = new ArrayList<>();
	private final List<StoredFilter> session = new ArrayList<>();

	private AddResult add(String category, String text, boolean persistent) {
		return ScannerFilterController.addTo(saved, session, new StoredFilter(category, text.strip()), persistent);
	}

	@Test
	void caseAndSurroundingSpacesDontMatter() {
		assertEquals(ScannerFilterController.normalize("Hopper"), ScannerFilterController.normalize("  hOPPER "));
	}

	@Test
	void runsOfSpacesInsideAreOneSpace() {
		assertEquals(ScannerFilterController.normalize("Oak  Chest"), ScannerFilterController.normalize("oak chest"));
	}

	@Test
	void aFilterMatchesTheWholeNameOnly() {
		String hopper = ScannerFilterController.normalize("hopper");
		assertNotEquals(hopper, ScannerFilterController.normalize("Hopper Minecart"));
		assertNotEquals(hopper, ScannerFilterController.normalize("Hop"));
	}

	@Test
	void aNewFilterIsASessionFilterUnlessAskedOtherwise() {
		assertEquals(AddResult.ADDED, add(STORAGE, "Hopper", false));
		assertEquals(1, session.size());
		assertTrue(saved.isEmpty());
	}

	@Test
	void aPersistentFilterIsSaved() {
		assertEquals(AddResult.ADDED, add(STORAGE, "Hopper", true));
		assertEquals(1, saved.size());
		assertTrue(session.isEmpty());
	}

	@Test
	void theSameFilterIsNotAddedTwiceWhateverItsCase() {
		add(STORAGE, "Hopper", false);
		assertEquals(AddResult.ALREADY_FILTERED, add(STORAGE, "hopper", false));
		assertEquals(1, session.size());
	}

	@Test
	void aSavedFilterIsNotAddedAgainAsASessionOne() {
		add(STORAGE, "Hopper", true);
		assertEquals(AddResult.ALREADY_FILTERED, add(STORAGE, "Hopper", false));
		assertEquals(AddResult.ALREADY_FILTERED, add(STORAGE, "Hopper", true));
		assertEquals(1, saved.size());
		assertTrue(session.isEmpty());
	}

	@Test
	void savingASessionFilterMovesItAcross() {
		add(STORAGE, "Hopper", false);
		assertEquals(AddResult.MADE_PERSISTENT, add(STORAGE, "hopper", true));
		assertEquals(1, saved.size());
		assertTrue(session.isEmpty());
	}

	@Test
	void theSameNameInAnotherCategoryIsAnotherFilter() {
		add(STORAGE, "Chest", false);
		assertEquals(AddResult.ADDED, add(WORKSTATIONS, "Chest", false));
		assertEquals(2, session.size());
	}

	@Test
	void aBlankFilterIsRefused() {
		assertEquals(AddResult.BLANK, add(STORAGE, "   ", true));
		assertTrue(saved.isEmpty());
		assertTrue(session.isEmpty());
	}
}
