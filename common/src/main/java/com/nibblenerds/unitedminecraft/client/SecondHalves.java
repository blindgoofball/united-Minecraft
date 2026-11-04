package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.core.BlockPos;

/**
 * Lists a two-block object once. A door, a tall plant, a bed and a double chest are each two
 * blocks, so a search that matches the block finds both; see {@link ScannerController#scanSearch}.
 * Kept out of that class so it can be tested without the game's registries.
 */
final class SecondHalves {
	private SecondHalves() {
	}

	/**
	 * Drops each result that is the second half of a two-block object when its other half is in
	 * the results too. A second half whose partner is <em>not</em> in the results stays - Search
	 * only lists what is visible, and it can be the only half that is.
	 *
	 * @param position where a result is
	 * @param partnerIfSecondHalf where the other half is, if the result is the half not to list on
	 *        its own; null for anything else
	 */
	static <T> List<T> dropFrom(List<T> found, Function<T, BlockPos> position, Function<T, BlockPos> partnerIfSecondHalf) {
		Set<BlockPos> listed = new HashSet<>();
		for (T entry : found) {
			listed.add(position.apply(entry).immutable());
		}
		List<T> kept = new ArrayList<>(found.size());
		for (T entry : found) {
			BlockPos partner = partnerIfSecondHalf.apply(entry);
			if (partner == null || !listed.contains(partner)) {
				kept.add(entry);
			}
		}
		return kept;
	}
}
