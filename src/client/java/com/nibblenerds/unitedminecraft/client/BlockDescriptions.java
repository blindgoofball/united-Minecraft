package com.nibblenerds.unitedminecraft.client;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * "What is this block made of?" narration for the inventory - deliberately scoped to
 * *material*, not per-block-state trivia: a slab, stairs, wall, fence, sign etc. of a given
 * material all narrate the same description, since what a blind player actually needs here is
 * "what does oak wood feel/look like", not sixteen near-duplicate entries for one material's
 * every cut.
 *
 * <p>{@link #materialKeyFor} derives that grouping from each block's real registry id (verified
 * against every id in this Minecraft version's own {@code Blocks} class and {@code en_us.json} -
 * not guessed) rather than hand-listing every block: a fixed, longest-match-first list of shape
 * suffixes (stairs/slab/wall/fence/door/...) and dye-color prefixes gets stripped to find the
 * shared material key, with two explicit exceptions - {@code _leaves} and {@code _sapling}/
 * {@code _propagule} - collapsed straight to a generic "leaves"/"sapling" key instead of being
 * grouped by species, since e.g. oak leaves and oak wood are visually and texturally unrelated
 * despite sharing the {@code oak_} prefix (a sighted player can tell them apart on sight; this
 * class must not conflate them). Blocks whose id doesn't reduce to a key with a real {@code
 * united_minecraft.narrate.material.<key>} translation (most of them, until that content is
 * written) fall back to a generic "no description available" message rather than silently
 * describing nothing.
 */
public final class BlockDescriptions {
	private BlockDescriptions() {
	}

	/**
	 * Longest-match-first: a suffix earlier in this list that's also a suffix of a later one
	 * (e.g. "_bricks" inside "_brick_stairs") would otherwise strip the shorter match first and
	 * leave a stray "_brick" behind.
	 */
	private static final List<String> SHAPE_SUFFIXES = List.of(
			"_hanging_sign", "_wall_hanging_sign", "_pressure_plate", "_fence_gate",
			"_brick_stairs", "_brick_slab", "_brick_wall", "_wall_sign", "_wall_skull", "_wall_head",
			"_stairs", "_slab", "_fence", "_door", "_trapdoor", "_button", "_sign",
			"_planks", "_wood", "_log", "_hyphae", "_stem", "_shelf", "_bricks", "_skull", "_head", "_torch");

	/** Every dye color a block family can be prefixed with (wool, concrete, stained glass, banners, ...). */
	private static final List<String> DYE_COLORS = List.of(
			"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	public static MutableComponent describe(ItemStack stack) {
		if (stack.isEmpty()) {
			return Component.translatable("united_minecraft.narrate.hotbar_empty").copy();
		}

		if (stack.getItem() instanceof BlockItem blockItem) {
			String key = "united_minecraft.narrate.material." + materialKeyFor(blockItem.getBlock());
			if (Language.getInstance().has(key)) {
				return Component.translatable(key).copy();
			}
		}

		return Component.translatable("united_minecraft.narrate.no_description_available").copy();
	}

	/**
	 * Reduces a block to the material key its description is shared under - see the class doc
	 * for why this is derived rather than hand-listed.
	 */
	private static String materialKeyFor(Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		String path = id.getPath();

		if (path.endsWith("_leaves")) {
			return "leaves";
		}
		if (path.endsWith("_sapling") || path.endsWith("_propagule")) {
			return "sapling";
		}

		String key = normalizeWallInfix(path);
		key = stripShapeSuffix(key);
		key = stripColorPrefix(key);
		key = stripShapeSuffix(key);
		return key;
	}

	/**
	 * Torches/skulls/heads/signs put "wall" as an infix or leading marker ({@code wall_torch},
	 * {@code redstone_wall_torch}) rather than a trailing shape suffix the way stairs/slabs/etc.
	 * do - fold it away first so e.g. {@code redstone_torch} and {@code redstone_wall_torch}
	 * still land on the same "redstone" key.
	 */
	private static String normalizeWallInfix(String path) {
		if (path.startsWith("wall_")) {
			path = path.substring("wall_".length());
		}
		return path.replace("_wall_", "_");
	}

	private static String stripShapeSuffix(String path) {
		for (String suffix : SHAPE_SUFFIXES) {
			if (path.length() > suffix.length() && path.endsWith(suffix)) {
				return path.substring(0, path.length() - suffix.length());
			}
		}
		return path;
	}

	private static String stripColorPrefix(String path) {
		for (String color : DYE_COLORS) {
			String prefix = color + "_";
			if (path.length() > prefix.length() && path.startsWith(prefix)) {
				return path.substring(prefix.length());
			}
		}
		return path;
	}
}
