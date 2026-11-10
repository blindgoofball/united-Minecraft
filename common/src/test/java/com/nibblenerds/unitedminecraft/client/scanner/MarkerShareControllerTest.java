package com.nibblenerds.unitedminecraft.client.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nibblenerds.unitedminecraft.client.scanner.MarkerShareController.Conflict;
import com.nibblenerds.unitedminecraft.client.scanner.MarkerShareController.PendingOffer;
import com.nibblenerds.unitedminecraft.share.SharePayloads;
import com.nibblenerds.unitedminecraft.share.SharePayloads.Item;
import com.nibblenerds.unitedminecraft.share.SharePayloads.Kind;

/** {@link MarkerShareController}'s offer queue and the rule for a share landing on a spot that's already named. */
class MarkerShareControllerTest {
	private static Item marker(String name) {
		return new Item(Kind.MARKER, name, "minecraft:overworld", 1, 64, 2, false);
	}

	private static Item namedBlock(String name, boolean marker) {
		return new Item(Kind.NAMED_BLOCK, name, "minecraft:overworld", 1, 64, 2, marker);
	}

	private static PendingOffer offer(String sender, Item item) {
		return new PendingOffer(sender, item);
	}

	@Test
	void anIdenticalOfferIsNotQueuedTwice() {
		List<PendingOffer> offers = new ArrayList<>();
		assertTrue(MarkerShareController.addOffer(offers, offer("Alex", marker("Mine")), 5));
		assertFalse(MarkerShareController.addOffer(offers, offer("Alex", marker("Mine")), 5));
		assertEquals(1, offers.size());
	}

	@Test
	void theSameItemFromAnotherSenderIsAnotherOffer() {
		List<PendingOffer> offers = new ArrayList<>();
		MarkerShareController.addOffer(offers, offer("Alex", marker("Mine")), 5);
		assertTrue(MarkerShareController.addOffer(offers, offer("Sam", marker("Mine")), 5));
		assertEquals(2, offers.size());
	}

	@Test
	void theOldestOfferIsDroppedWhenFull() {
		List<PendingOffer> offers = new ArrayList<>();
		MarkerShareController.addOffer(offers, offer("A", marker("one")), 2);
		MarkerShareController.addOffer(offers, offer("A", marker("two")), 2);
		MarkerShareController.addOffer(offers, offer("A", marker("three")), 2);
		assertEquals(List.of(offer("A", marker("two")), offer("A", marker("three"))), offers);
	}

	@Test
	void nothingAtTheSpotIsNoConflict() {
		assertEquals(Conflict.NONE, MarkerShareController.conflict(marker("Mine"), null, true));
	}

	@Test
	void theSameNameAtTheSpotIsAlreadyHad() {
		assertEquals(Conflict.SAME, MarkerShareController.conflict(marker("Mine"), "Mine", true));
	}

	@Test
	void aDifferentNameAtTheSpotNeedsConfirming() {
		assertEquals(Conflict.DIFFERENT, MarkerShareController.conflict(marker("Mine"), "Quarry", true));
	}

	@Test
	void aMarkedBlockOntoTheSameNameWithoutTheFlagJustUpgradesIt() {
		assertEquals(Conflict.NONE, MarkerShareController.conflict(namedBlock("Door", true), "Door", false));
	}

	@Test
	void aMarkedBlockOntoTheSameMarkedNameIsAlreadyHad() {
		assertEquals(Conflict.SAME, MarkerShareController.conflict(namedBlock("Door", true), "Door", true));
	}

	@Test
	void anUnmarkedBlockOntoTheSameNameIsAlreadyHadWhateverTheFlag() {
		assertEquals(Conflict.SAME, MarkerShareController.conflict(namedBlock("Door", false), "Door", true));
		assertEquals(Conflict.SAME, MarkerShareController.conflict(namedBlock("Door", false), "Door", false));
	}

	@Test
	void aLongNameIsCutToWhatThePayloadAllows() {
		Item sent = MarkerShareController.forSending(marker("x".repeat(SharePayloads.MAX_NAME + 20)));
		assertEquals(SharePayloads.MAX_NAME, sent.name().length());
	}

	@Test
	void dimensionNamesReadAsWords() {
		assertEquals("the nether", MarkerShareController.dimensionName("minecraft:the_nether"));
		assertEquals("overworld", MarkerShareController.dimensionName("minecraft:overworld"));
	}
}
