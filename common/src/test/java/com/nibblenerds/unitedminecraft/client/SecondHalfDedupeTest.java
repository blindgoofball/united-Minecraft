package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/** {@link SecondHalves} - Search listing a door, bed or double chest once. */
class SecondHalfDedupeTest {
	/** A found block: where it is, and where its other half is if it is the half not to list on its own. */
	private record Found(BlockPos pos, BlockPos partnerIfSecond) {
	}

	private static List<Found> dedupe(List<Found> found) {
		return SecondHalves.dropFrom(found, Found::pos, Found::partnerIfSecond);
	}

	@Test
	void aDoorWithBothHalvesFoundIsListedOnce() {
		Found lower = new Found(new BlockPos(5, 64, 5), null);
		Found upper = new Found(new BlockPos(5, 65, 5), new BlockPos(5, 64, 5));
		assertEquals(List.of(lower), dedupe(List.of(lower, upper)));
	}

	@Test
	void theOrderTheHalvesWereFoundInDoesntMatter() {
		Found lower = new Found(new BlockPos(5, 64, 5), null);
		Found upper = new Found(new BlockPos(5, 65, 5), new BlockPos(5, 64, 5));
		assertEquals(List.of(lower), dedupe(List.of(upper, lower)));
	}

	@Test
	void aSecondHalfWhoseOtherHalfWasNotFoundStays() {
		Found upper = new Found(new BlockPos(5, 65, 5), new BlockPos(5, 64, 5));
		assertEquals(List.of(upper), dedupe(List.of(upper)));
	}

	@Test
	void thingsThatAreNotTwoPartObjectsAreAllKept() {
		Found a = new Found(new BlockPos(1, 64, 1), null);
		Found b = new Found(new BlockPos(2, 64, 1), null);
		assertEquals(List.of(a, b), dedupe(List.of(a, b)));
	}

	@Test
	void eachPairIsCollapsedSeparately() {
		Found lowerA = new Found(new BlockPos(0, 64, 0), null);
		Found upperA = new Found(new BlockPos(0, 65, 0), new BlockPos(0, 64, 0));
		Found lowerB = new Found(new BlockPos(9, 64, 0), null);
		Found upperB = new Found(new BlockPos(9, 65, 0), new BlockPos(9, 64, 0));
		assertEquals(List.of(lowerA, lowerB), dedupe(List.of(lowerA, upperA, lowerB, upperB)));
	}

	@Test
	void aMutableBlockPosStillMatchesItsImmutableTwin() {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(5, 64, 5);
		Found lower = new Found(cursor, null);
		Found upper = new Found(new BlockPos(5, 65, 5), new BlockPos(5, 64, 5));
		assertEquals(1, dedupe(List.of(lower, upper)).size());
	}
}
