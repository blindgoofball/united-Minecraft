package com.nibblenerds.unitedminecraft.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link BundleSelection} - stepping a bundle's selected item with the keyboard. */
class BundleSelectionTest {
	@Test
	void steppingForwardMovesToTheNextItem() {
		assertEquals(1, BundleSelection.nextIndex(0, 1, 4));
		assertEquals(3, BundleSelection.nextIndex(2, 1, 4));
	}

	@Test
	void steppingPastTheEndWrapsAround() {
		assertEquals(0, BundleSelection.nextIndex(3, 1, 4));
		assertEquals(3, BundleSelection.nextIndex(0, -1, 4));
	}

	@Test
	void withNothingSelectedForwardStartsAtTheFirstAndBackAtTheLast() {
		assertEquals(0, BundleSelection.nextIndex(-1, 1, 4));
		assertEquals(3, BundleSelection.nextIndex(-1, -1, 4));
	}

	@Test
	void aSingleItemStaysSelected() {
		assertEquals(0, BundleSelection.nextIndex(0, 1, 1));
		assertEquals(0, BundleSelection.nextIndex(0, -1, 1));
	}
}
