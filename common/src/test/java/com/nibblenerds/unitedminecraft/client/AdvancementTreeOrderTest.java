package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AdvancementTreeOrderTest {
	// Input order is deliberately not tree order:
	//   0 root
	//   1 child of 0, y=2     3 grandchild of 1
	//   2 child of 0, y=1     4 grandchild of 2
	private static final int[] PARENT = { -1, 0, 0, 1, 2 };
	private static final int[] KEY = { 0, 2, 1, 5, 4 };

	@Test
	void listsDepthFirstWithSiblingsByKey() {
		AdvancementTreeOrder.Order order = AdvancementTreeOrder.preorder(PARENT, KEY);

		assertArrayEquals(new int[] { 0, 2, 4, 1, 3 }, order.indices());
		assertArrayEquals(new int[] { 0, 1, 2, 1, 2 }, order.depths());
	}

	@Test
	void equalKeysKeepInputOrder() {
		AdvancementTreeOrder.Order order = AdvancementTreeOrder.preorder(new int[] { -1, 0, 0, 0 }, new int[] { 0, 3, 3, 3 });

		assertArrayEquals(new int[] { 0, 1, 2, 3 }, order.indices());
	}

	@Test
	void parentIsNearestShallowerEntryBefore() {
		int[] depths = AdvancementTreeOrder.preorder(PARENT, KEY).depths();

		assertEquals(-1, AdvancementTreeOrder.parentPosition(depths, 0));
		assertEquals(0, AdvancementTreeOrder.parentPosition(depths, 1));
		assertEquals(1, AdvancementTreeOrder.parentPosition(depths, 2));
		assertEquals(0, AdvancementTreeOrder.parentPosition(depths, 3));
		assertEquals(3, AdvancementTreeOrder.parentPosition(depths, 4));
	}

	@Test
	void firstChildIsNextEntryOnlyWhenDeeper() {
		int[] depths = AdvancementTreeOrder.preorder(PARENT, KEY).depths();

		assertEquals(1, AdvancementTreeOrder.firstChildPosition(depths, 0));
		assertEquals(2, AdvancementTreeOrder.firstChildPosition(depths, 1));
		assertEquals(-1, AdvancementTreeOrder.firstChildPosition(depths, 2));
		assertEquals(4, AdvancementTreeOrder.firstChildPosition(depths, 3));
		assertEquals(-1, AdvancementTreeOrder.firstChildPosition(depths, 4));
	}

	@Test
	void emptyTreeIsEmpty() {
		assertEquals(0, AdvancementTreeOrder.preorder(new int[0], new int[0]).size());
	}

	@Test
	void aParentCycleStillListsEveryEntry() {
		AdvancementTreeOrder.Order order = AdvancementTreeOrder.preorder(new int[] { -1, 2, 1 }, new int[] { 0, 0, 0 });

		assertEquals(3, order.size());
	}
}
