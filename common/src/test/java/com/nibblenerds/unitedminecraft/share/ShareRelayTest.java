package com.nibblenerds.unitedminecraft.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nibblenerds.unitedminecraft.share.SharePayloads.Item;
import com.nibblenerds.unitedminecraft.share.SharePayloads.Kind;

/** {@link ShareRelay}'s checks on what a client asks it to forward, and its per-sender rate limit. */
class ShareRelayTest {
	private static Item item(String name, String dimension, int x, int y, int z) {
		return new Item(Kind.MARKER, name, dimension, x, y, z, false);
	}

	@Test
	void aSaneItemIsForwardedWithItsNameTrimmed() {
		Item result = ShareRelay.sanitize(item("  Iron Mine ", "minecraft:overworld", 10, 64, -20));
		assertNotNull(result);
		assertEquals("Iron Mine", result.name());
	}

	@Test
	void aBlankNameIsDropped() {
		assertNull(ShareRelay.sanitize(item("   ", "minecraft:overworld", 0, 0, 0)));
	}

	@Test
	void anInvalidDimensionIsDropped() {
		assertNull(ShareRelay.sanitize(item("Mine", "Not A Dimension!", 0, 0, 0)));
	}

	@Test
	void aPositionOutsideAnyWorldIsDropped() {
		assertNull(ShareRelay.sanitize(item("Mine", "minecraft:overworld", ShareRelay.MAX_HORIZONTAL + 1, 0, 0)));
		assertNull(ShareRelay.sanitize(item("Mine", "minecraft:overworld", 0, 0, -ShareRelay.MAX_HORIZONTAL - 1)));
		assertNull(ShareRelay.sanitize(item("Mine", "minecraft:overworld", 0, ShareRelay.MAX_VERTICAL + 1, 0)));
	}

	@Test
	void theMarkerFlagOnlyMeansSomethingForANamedBlock() {
		Item plain = new Item(Kind.MARKER, "Mine", "minecraft:overworld", 0, 0, 0, true);
		assertFalse(ShareRelay.sanitize(plain).marker());
		Item block = new Item(Kind.NAMED_BLOCK, "Door", "minecraft:overworld", 0, 0, 0, true);
		assertTrue(ShareRelay.sanitize(block).marker());
	}

	@Test
	void aShareToOnePlayerGoesToThatPlayer() {
		assertEquals(List.of("bea"), ShareRelay.recipientsFor(false, List.of("ann", "bea", "cal"), "bea", "ann"));
	}

	@Test
	void aShareToEveryoneLeavesOutTheSender() {
		assertEquals(List.of("bea", "cal"), ShareRelay.recipientsFor(true, List.of("ann", "bea", "cal"), null, "ann"));
	}

	@Test
	void aShareToAPlayerWhoIsNoLongerOnlineGoesToNobody() {
		assertEquals(List.of(), ShareRelay.recipientsFor(false, List.of("ann", "bea"), null, "ann"));
	}

	@Test
	void aShareToYourselfGoesToNobody() {
		assertEquals(List.of(), ShareRelay.recipientsFor(false, List.of("ann", "bea"), "ann", "ann"));
	}

	@Test
	void theListHandedBackCanBeChangedAndTheOneGivenIsLeftAlone() {
		List<String> everyone = List.of("ann", "bea");
		List<String> single = ShareRelay.recipientsFor(false, everyone, "bea", "ann");
		single.add("extra");
		List<String> all = ShareRelay.recipientsFor(true, everyone, null, "ann");
		all.add("extra");
		assertEquals(List.of("ann", "bea"), everyone);
	}

	@Test
	void aSenderIsLimitedWithinTheWindow() {
		Deque<Integer> history = new ArrayDeque<>();
		for (int i = 0; i < ShareRelay.MAX_SHARES_PER_WINDOW; i++) {
			assertTrue(ShareRelay.withinLimit(history, 100), "share " + i);
		}
		assertFalse(ShareRelay.withinLimit(history, 100));
		assertEquals(ShareRelay.MAX_SHARES_PER_WINDOW, history.size(), "a refused share is not recorded");
	}

	@Test
	void theAllowanceComesBackOnceTheWindowHasPassed() {
		Deque<Integer> history = new ArrayDeque<>();
		for (int i = 0; i < ShareRelay.MAX_SHARES_PER_WINDOW; i++) {
			ShareRelay.withinLimit(history, 100);
		}
		assertTrue(ShareRelay.withinLimit(history, 100 + ShareRelay.WINDOW_TICKS));
	}
}
