package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The pure tree arithmetic behind {@link AdvancementsController}: flattening a tab's advancements
 * into the order a tree view lists them in, and moving through that list. Kept free of Minecraft
 * types so it can be tested directly - the controller feeds it indices, not widgets.
 *
 * <p>The list is a pre-order walk with every branch expanded, so Up/Down are plain neighbours,
 * Left is the nearest earlier entry one level shallower, and Right is the next entry if it is one
 * level deeper.
 */
final class AdvancementTreeOrder {
	/** {@code indices[i]} is the input index listed at position {@code i}; {@code depths[i]} its depth, roots being 0. */
	record Order(int[] indices, int[] depths) {
		int size() {
			return indices.length;
		}
	}

	private AdvancementTreeOrder() {
	}

	/**
	 * @param parent {@code parent[i]} is the input index of {@code i}'s parent, or -1 for a root
	 * @param key    siblings are listed in ascending {@code key}, ties keeping input order
	 */
	static Order preorder(int[] parent, int[] key) {
		int n = parent.length;
		List<List<Integer>> children = new ArrayList<>();
		for (int i = 0; i <= n; i++) {
			children.add(new ArrayList<>());
		}
		// Slot n collects the roots, so they sort like any other sibling group.
		for (int i = 0; i < n; i++) {
			children.get(parent[i] < 0 ? n : parent[i]).add(i);
		}
		for (List<Integer> group : children) {
			group.sort(Comparator.comparingInt(i -> key[i]));
		}

		int[] indices = new int[n];
		int[] depths = new int[n];
		int count = 0;
		// Explicit stack rather than recursion: the deepest branch of a modded tree is not ours to bound.
		List<int[]> stack = new ArrayList<>();
		List<Integer> roots = children.get(n);
		for (int r = roots.size() - 1; r >= 0; r--) {
			stack.add(new int[] { roots.get(r), 0 });
		}
		while (!stack.isEmpty()) {
			int[] top = stack.remove(stack.size() - 1);
			indices[count] = top[0];
			depths[count] = top[1];
			count++;
			List<Integer> kids = children.get(top[0]);
			for (int k = kids.size() - 1; k >= 0; k--) {
				stack.add(new int[] { kids.get(k), top[1] + 1 });
			}
		}
		if (count == n) {
			return new Order(indices, depths);
		}
		// A parent cycle would leave entries unreachable; list them flat rather than lose them.
		boolean[] seen = new boolean[n];
		for (int i = 0; i < count; i++) {
			seen[indices[i]] = true;
		}
		int[] allIndices = java.util.Arrays.copyOf(indices, n);
		int[] allDepths = java.util.Arrays.copyOf(depths, n);
		for (int i = 0; i < n; i++) {
			if (!seen[i]) {
				allIndices[count] = i;
				allDepths[count] = 0;
				count++;
			}
		}
		return new Order(allIndices, allDepths);
	}

	/** Position of the parent of the entry at {@code pos}, or -1 for a root. */
	static int parentPosition(int[] depths, int pos) {
		for (int i = pos - 1; i >= 0; i--) {
			if (depths[i] < depths[pos]) {
				return i;
			}
		}
		return -1;
	}

	/** Position of the first child of the entry at {@code pos}, or -1 for a leaf. */
	static int firstChildPosition(int[] depths, int pos) {
		return pos + 1 < depths.length && depths[pos + 1] > depths[pos] ? pos + 1 : -1;
	}
}
