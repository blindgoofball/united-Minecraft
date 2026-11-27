package com.nibblenerds.unitedminecraft.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** {@link ShareCommand#itemFor} - what {@code /umshare} turns its arguments into, and when it refuses. */
class ShareCommandTest {
	private static final String OVERWORLD = "minecraft:overworld";

	@Test
	void aPlainShareBecomesAMarkerAtThatSpot() {
		SharePayloads.Item item = ShareCommand.itemFor("Camp", OVERWORLD, new BlockPos(10, 70, -4));
		assertNotNull(item);
		assertEquals(SharePayloads.Kind.MARKER, item.kind());
		assertEquals("Camp", item.name());
		assertEquals(OVERWORLD, item.dimension());
		assertEquals(10, item.x());
		assertEquals(70, item.y());
		assertEquals(-4, item.z());
		assertFalse(item.marker());
	}

	@Test
	void theNameKeepsItsSpacesButNotItsEdges() {
		assertEquals("Iron Mine", ShareCommand.itemFor("  Iron Mine  ", OVERWORLD, BlockPos.ZERO).name());
	}

	@Test
	void aBlankNameIsRefused() {
		assertNull(ShareCommand.itemFor("   ", OVERWORLD, BlockPos.ZERO));
	}

	@Test
	void aTooLongNameIsCutRatherThanRefused() {
		SharePayloads.Item item = ShareCommand.itemFor("x".repeat(SharePayloads.MAX_NAME + 30), OVERWORLD, BlockPos.ZERO);
		assertNotNull(item);
		assertEquals(SharePayloads.MAX_NAME, item.name().length());
	}

	@Test
	void aPositionOutsideTheWorldIsRefused() {
		assertNull(ShareCommand.itemFor("Camp", OVERWORLD, new BlockPos(0, ShareRelay.MAX_VERTICAL + 1, 0)));
	}

	@Test
	void aSenderNameIsCutToWhatAnOfferAllows() {
		assertEquals(SharePayloads.MAX_NAME, ShareRelay.offerName("n".repeat(SharePayloads.MAX_NAME + 10)).length());
		assertEquals("Server", ShareRelay.offerName("Server"));
	}
}
