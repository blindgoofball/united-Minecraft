package com.nibblenerds.unitedminecraft.client;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.TwistingVinesPlantBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesPlantBlock;

/**
 * Things made of many blocks that the Scanner lists once rather than once per block, and which
 * kind each block belongs to. Several categories group some of these - Mechanisms the portals,
 * Crops the stalks and chorus, Terrain the climbables, and Search all of them - and each picks its
 * own set in {@link ScannerController}; this is the one place that says what a block is part of.
 *
 * <p>Blocks of one kind are grouped separately from the others, so two kinds side by side (a vine
 * on a ladder) stay two entries.
 */
enum LargeObject {
	NETHER_PORTAL(Shape.STRUCTURE),
	END_PORTAL(Shape.STRUCTURE),
	END_PORTAL_FRAME(Shape.STRUCTURE),
	CHORUS(Shape.PLANT),
	BAMBOO(Shape.STACK),
	SUGAR_CANE(Shape.STACK),
	CACTUS(Shape.STACK),
	KELP(Shape.STACK),
	LADDER(Shape.STACK),
	VINE(Shape.STACK),
	SCAFFOLDING(Shape.STACK),
	WEEPING_VINES(Shape.STACK),
	TWISTING_VINES(Shape.STACK),
	CAVE_VINES(Shape.STACK);

	/** How the blocks of one kind are grouped into entries. */
	enum Shape {
		/** Every connected block is one structure, reported at the one nearest the player - a portal or a ring of frames. */
		STRUCTURE,
		/** Every connected block is one plant, reported at its nearest stem - chorus. */
		PLANT,
		/** Each unbroken vertical run is one entry, reported at its base. */
		STACK
	}

	private final Shape shape;

	LargeObject(Shape shape) {
		this.shape = shape;
	}

	Shape shape() {
		return shape;
	}

	// A scan classifies every block in range, so the answer for each block type is worked out once.
	// Client thread only, like the rest of the Scanner.
	private static final Map<Block, Optional<LargeObject>> KINDS = new IdentityHashMap<>();

	/** Which kind {@code block} is part of, or null if it isn't part of any. */
	static LargeObject of(Block block) {
		return KINDS.computeIfAbsent(block, b -> Optional.ofNullable(classify(b))).orElse(null);
	}

	private static LargeObject classify(Block block) {
		if (block instanceof NetherPortalBlock) {
			return NETHER_PORTAL;
		}
		if (block instanceof EndPortalBlock) {
			return END_PORTAL;
		}
		if (block instanceof EndPortalFrameBlock) {
			return END_PORTAL_FRAME;
		}
		if (block instanceof ChorusPlantBlock || block instanceof ChorusFlowerBlock) {
			return CHORUS;
		}
		if (block instanceof BambooStalkBlock) {
			return BAMBOO;
		}
		if (block instanceof SugarCaneBlock) {
			return SUGAR_CANE;
		}
		if (block instanceof CactusBlock) {
			return CACTUS;
		}
		// Kelp, and the vines below, are a head block plus a separate body block type for the rest
		// of the strand; both halves are one kind so the strand stays one entry.
		if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
			return KELP;
		}
		if (block instanceof LadderBlock) {
			return LADDER;
		}
		if (block instanceof VineBlock) {
			return VINE;
		}
		if (block instanceof ScaffoldingBlock) {
			return SCAFFOLDING;
		}
		if (block instanceof WeepingVinesBlock || block instanceof WeepingVinesPlantBlock) {
			return WEEPING_VINES;
		}
		if (block instanceof TwistingVinesBlock || block instanceof TwistingVinesPlantBlock) {
			return TWISTING_VINES;
		}
		if (block instanceof CaveVinesBlock || block instanceof CaveVinesPlantBlock) {
			return CAVE_VINES;
		}
		return null;
	}
}
