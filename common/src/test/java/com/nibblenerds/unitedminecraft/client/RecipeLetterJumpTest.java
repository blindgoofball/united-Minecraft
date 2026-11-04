package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** {@link RecipeLetterJump} - typing a letter in the Recipe Book to jump to the next recipe starting with it. */
class RecipeLetterJumpTest {
	// Groups: [Oak Planks, Birch Planks], [Stick], [Bowl, Bread], [Barrel]
	private static final List<List<String>> RECIPES = List.of(
			List.of("Oak Planks", "Birch Planks"),
			List.of("Stick"),
			List.of("Bowl", "Bread"),
			List.of("Barrel"));

	private static void assertAt(int group, int variant, int[] found) {
		assertArrayEquals(new int[] {group, variant}, found);
	}

	@Test
	void jumpsToTheNextRecipeAfterTheFocusedOne() {
		assertAt(2, 0, RecipeLetterJump.next(RECIPES, 1, 0, 'b'));
	}

	@Test
	void findsAMatchAmongTheVariantsOfAGroup() {
		assertAt(0, 1, RecipeLetterJump.next(List.of(List.of("Bowl", "Bread")), 0, 0, 'b'));
	}

	@Test
	void pressingTheSameLetterAgainCyclesThroughEveryMatch() {
		assertAt(0, 1, RecipeLetterJump.next(RECIPES, 0, 0, 'b'));
		assertAt(2, 0, RecipeLetterJump.next(RECIPES, 0, 1, 'b'));
		assertAt(2, 1, RecipeLetterJump.next(RECIPES, 2, 0, 'b'));
		assertAt(3, 0, RecipeLetterJump.next(RECIPES, 2, 1, 'b'));
	}

	@Test
	void wrapsAroundPastTheEnd() {
		assertAt(0, 1, RecipeLetterJump.next(RECIPES, 3, 0, 'b'));
	}

	@Test
	void aLoneMatchIsFoundAgainAfterAFullLap() {
		assertAt(1, 0, RecipeLetterJump.next(RECIPES, 1, 0, 's'));
	}

	@Test
	void nothingFocusedYetStartsFromTheFirstRecipe() {
		assertAt(0, 0, RecipeLetterJump.next(RECIPES, -1, 0, 'o'));
		assertAt(0, 0, RecipeLetterJump.next(RECIPES, 99, 0, 'o'));
	}

	@Test
	void caseDoesntMatter() {
		assertAt(1, 0, RecipeLetterJump.next(RECIPES, 0, 0, 'S'));
		assertTrue(RecipeLetterJump.startsWith("  stick", 'S'));
	}

	@Test
	void noRecipeStartingWithTheLetterIsNull() {
		assertNull(RecipeLetterJump.next(RECIPES, 0, 0, 'z'));
	}

	@Test
	void anEmptyBookOrEmptyNamesFindNothing() {
		assertNull(RecipeLetterJump.next(List.of(), -1, 0, 'a'));
		assertNull(RecipeLetterJump.next(List.of(List.of()), 0, 0, 'a'));
		assertFalse(RecipeLetterJump.startsWith("   ", 'a'));
	}

	@Test
	void anOutOfRangeVariantIsTreatedAsTheLastOne() {
		assertAt(1, 0, RecipeLetterJump.next(RECIPES, 0, 9, 's'));
	}
}
