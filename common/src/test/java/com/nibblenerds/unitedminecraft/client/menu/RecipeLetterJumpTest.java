package com.nibblenerds.unitedminecraft.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** {@link RecipeLetterJump} - typing a letter in the Recipe Book to jump to the next recipe starting with it. */
class RecipeLetterJumpTest {
	private static final List<List<String>> GROUPS = List.of(
			List.of("Oak Planks"),
			List.of("Stick"),
			List.of("Oak Door", "Spruce Door"),
			List.of("Torch"),
			List.of("Spruce Planks"));

	@Test
	void jumpsToTheNextGroupStartingWithTheLetter() {
		assertEquals(3, RecipeLetterJump.next(GROUPS, 0, 't'));
	}

	@Test
	void repeatingTheLetterCyclesThroughEveryMatchAndWraps() {
		assertEquals(2, RecipeLetterJump.next(GROUPS, 0, 'o'));
		assertEquals(0, RecipeLetterJump.next(GROUPS, 2, 'o'));
	}

	@Test
	void aGroupMatchesOnAnyOfItsRecipes() {
		assertEquals(2, RecipeLetterJump.next(GROUPS, 1, 's'));
		assertEquals(1, RecipeLetterJump.next(List.of(List.of("Chest"), List.of("Oak Door", "Spruce Door")), 0, 's'));
	}

	@Test
	void theOnlyMatchBeingTheCurrentGroupStaysThere() {
		assertEquals(3, RecipeLetterJump.next(GROUPS, 3, 't'));
	}

	@Test
	void noMatchIsMinusOneAndAnEmptyBookIsToo() {
		assertEquals(-1, RecipeLetterJump.next(GROUPS, 0, 'z'));
		assertEquals(-1, RecipeLetterJump.next(List.of(), 0, 'a'));
	}
}
