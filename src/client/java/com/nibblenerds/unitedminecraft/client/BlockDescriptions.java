package com.nibblenerds.unitedminecraft.client;

import java.util.List;
import java.util.Set;

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
			"_hanging_sign", "_wall_hanging_sign", "_pressure_plate", "_fence_gate", "_brick_fence",
			"_brick_stairs", "_brick_slab", "_brick_wall", "_wall_sign", "_wall_skull", "_wall_head",
			"_stairs", "_slab", "_wall", "_fence", "_door", "_trapdoor", "_button", "_sign",
			"_planks", "_wood", "_log", "_hyphae", "_stem", "_shelf", "_bricks", "_skull", "_head", "_torch");

	/** Every dye color a block family can be prefixed with (wool, concrete, stained glass, banners, ...). */
	private static final List<String> DYE_COLORS = List.of(
			"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	/**
	 * Ids that happen to start with a dye-color word for reasons that have nothing to do with
	 * dyeing - the color word is just part of the block's actual name - so stripping it would
	 * collide with an unrelated block: {@code red_sand}/{@code red_sandstone} (and its cut
	 * forms) would collapse onto plain {@code sand}/{@code sandstone}, {@code red_mushroom}/
	 * {@code brown_mushroom}(_block) onto each other, {@code red_nether_bricks} onto plain
	 * {@code nether_bricks}, and {@code blue_ice} onto plain {@code ice} - none of which are
	 * actually the same material. Checked as a prefix (not exact match) so it also covers each
	 * one's own stairs/slab/wall/block forms.
	 */
	private static final List<String> COLOR_STRIP_EXCEPTIONS = List.of(
			"red_sand", "red_mushroom", "brown_mushroom", "red_nether_brick", "blue_ice");

	/**
	 * The far rarer opposite problem: an undyed block and its {@code <color>_} family share the
	 * same word once the color is stripped, but they're visually distinct (plain {@code
	 * terracotta}'s solid earthy orange vs. a dyed terracotta's mottled color-streaked pattern;
	 * an unwaxed {@code candle}'s natural cream wax vs. a dyed one's solid color; the default
	 * {@code shulker_box}'s mottled purple-gray vs. a dyed one's flat color). Only ever applied
	 * when a color prefix was actually stripped (see {@link #materialKeyFor}), so the plain
	 * block's own key is untouched.
	 */
	private static final Set<String> AMBIGUOUS_WITH_UNDYED = Set.of("terracotta", "candle", "candle_cake", "shulker_box");

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
		// Unlike stone/stone_bricks or tuff/tuff_bricks (same material, just a different cut -
		// fine to share one description), mud_bricks is a dried, warm tan brick while plain mud
		// is a dark, wet block - different enough in color that collapsing them into one "mud"
		// key the way the generic _bricks suffix normally would is actually wrong here.
		if (path.startsWith("mud_brick")) {
			return "mud_bricks";
		}
		// Waxing a copper block (or lightning rod) is purely a gameplay flag that stops further
		// oxidation - it never changes the texture (confirmed: there's no separate waxed_*
		// texture file for any of them), so a waxed and unwaxed block of the same oxidation
		// stage should share the exact same description.
		String unwaxedPath = path.startsWith("waxed_") ? path.substring("waxed_".length()) : path;

		// Checked against the untouched (but de-waxed) registry path, not the partially-reduced
		// key: by the time a shape suffix like "_bricks" has already been stripped from e.g.
		// "red_nether_bricks", there's nothing left for a "starts with red_nether_brick" check
		// to match against, and the exception would silently stop working.
		boolean skipColorStrip = COLOR_STRIP_EXCEPTIONS.stream().anyMatch(unwaxedPath::startsWith);

		String key = normalizeWallInfix(unwaxedPath);
		key = normalizeIrregularPlural(key);
		key = stripShapeSuffix(key);

		boolean colorWasStripped = false;
		if (!skipColorStrip) {
			String beforeColor = key;
			key = stripColorPrefix(key);
			colorWasStripped = !key.equals(beforeColor);
		}

		key = stripShapeSuffix(key);
		if (colorWasStripped && AMBIGUOUS_WITH_UNDYED.contains(key)) {
			key = "dyed_" + key;
		}
		return key;
	}

	/**
	 * {@code deepslate_tiles} (the plain block) doesn't reduce to the same key as {@code
	 * deepslate_tile_slab}/{@code _stairs}/{@code _wall} without this: unlike {@code
	 * X_bricks}/{@code X_brick_stairs}, which stay consistently plural/singular and already
	 * match via {@link #SHAPE_SUFFIXES}' own {@code _bricks}, Mojang used singular "tile" in
	 * the compound names here but plural "tiles" for the base block - an irregular case, not a
	 * pattern worth generalizing into the suffix table.
	 *
	 * <p>Plain {@code bricks} (the terracotta-red brick block) has the same problem the other
	 * way around: it has no material prefix of its own, so its cut forms are just {@code
	 * brick_stairs}/{@code brick_slab}/{@code brick_wall} - the generic {@code _stairs}/{@code
	 * _slab}/{@code _wall} suffixes strip those down to singular "brick", never matching the
	 * base block's own plural "bricks" key.
	 */
	private static String normalizeIrregularPlural(String path) {
		if (path.equals("deepslate_tiles")) {
			return "deepslate_tile";
		}
		if (path.equals("brick_stairs") || path.equals("brick_slab") || path.equals("brick_wall")) {
			return "bricks";
		}
		return path;
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
