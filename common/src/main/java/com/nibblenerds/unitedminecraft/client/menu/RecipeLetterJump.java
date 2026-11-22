package com.nibblenerds.unitedminecraft.client.menu;

import java.util.List;

/** Finding the next recipe group that starts with a letter. The Recipe Book wiring is in {@link SlotLetterJump}. */
final class RecipeLetterJump {
	private RecipeLetterJump() {
	}

	/**
	 * The index of the first group after {@code from} - wrapping around, and ending on {@code from}
	 * itself - with a recipe whose name starts with {@code letter}, or -1 if there is none. A group
	 * can hold several recipes (a bundle in every color, say), and matches on any of them.
	 *
	 * @param names for each group, the names of the items its recipes make
	 */
	static int next(List<List<String>> names, int from, char letter) {
		int count = names.size();
		if (count == 0) {
			return -1;
		}
		for (int offset = 1; offset <= count; offset++) {
			int index = Math.floorMod(from + offset, count);
			for (String name : names.get(index)) {
				if (startsWithLetter(name, letter)) {
					return index;
				}
			}
		}
		return -1;
	}

	static boolean startsWithLetter(String name, char letter) {
		String stripped = name.strip();
		return !stripped.isEmpty() && Character.toLowerCase(stripped.charAt(0)) == letter;
	}
}
