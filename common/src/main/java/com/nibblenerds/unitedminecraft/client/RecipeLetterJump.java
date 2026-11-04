package com.nibblenerds.unitedminecraft.client;

import java.util.List;
import java.util.Locale;

/**
 * First-letter navigation for the Recipe Book section: finds the next recipe whose result's name
 * starts with a typed letter. Recipes are laid out as groups (vanilla bundles near-duplicates such
 * as different wood colors) each holding variants, so the search walks every variant of every group
 * in the order Up/Down and Left/Right would, starting just after where the player is and wrapping
 * around, so pressing the same letter again cycles through every match. Kept apart from {@link
 * MenuAccessibilityController} so it can be tested without the game.
 */
final class RecipeLetterJump {
	private RecipeLetterJump() {
	}

	/**
	 * The {@code {group, variant}} of the next recipe starting with {@code letter}, or null if none
	 * does. {@code names} holds each group's variants' result names, in the order they are visited.
	 * A {@code groupIndex} outside the list (nothing focused yet) starts the search from the very
	 * first recipe. If the focused recipe is the only match, it is found again after a full lap.
	 */
	static int[] next(List<List<String>> names, int groupIndex, int variantIndex, char letter) {
		int total = 0;
		for (List<String> group : names) {
			total += group.size();
		}
		if (total == 0) {
			return null;
		}

		int start = -1;
		if (groupIndex >= 0 && groupIndex < names.size() && !names.get(groupIndex).isEmpty()) {
			int before = 0;
			for (int g = 0; g < groupIndex; g++) {
				before += names.get(g).size();
			}
			start = before + Math.clamp(variantIndex, 0, names.get(groupIndex).size() - 1);
		}

		for (int offset = 1; offset <= total; offset++) {
			int flat = Math.floorMod(start + offset, total);
			for (int g = 0; g < names.size(); g++) {
				int size = names.get(g).size();
				if (flat < size) {
					if (startsWith(names.get(g).get(flat), letter)) {
						return new int[] {g, flat};
					}
					break;
				}
				flat -= size;
			}
		}
		return null;
	}

	static boolean startsWith(String name, char letter) {
		String stripped = name.strip();
		return !stripped.isEmpty() && stripped.toLowerCase(Locale.ROOT).charAt(0) == Character.toLowerCase(letter);
	}
}
