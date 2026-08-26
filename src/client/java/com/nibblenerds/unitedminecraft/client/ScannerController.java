package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The "scanner": Home/End cycle a category, Page Up/Down cycle the nearest items within
 * it, and Enter targets whichever item is currently selected. Doesn't function while
 * build mode or {@link CombatModeController} is active (Page Up/Down already belong to
 * the build cursor there, and Combat Mode owns rotation the same way this scanner's own
 * lock-on does).
 *
 * <p>Enter on a block just aims the player at it. Enter on a mob starts a continuous
 * lock-on that keeps facing it every tick (via {@link CameraUtil#aimAtEntity}, which also
 * handles aiming a drawn bow with a real ballistic arc) until Delete (stop lock) is
 * pressed - which takes over rotation entirely while active, so build mode and normal
 * camera turning are blocked until it's released. Shift+Enter instead auto-walks there via
 * {@link AutoWalkController} - fully client-side, no server cooperation needed.
 *
 * <p>Backslash announces the focused item's coordinates - useful for actually finding your
 * way to something (an ore vein, say) once the scanner's found it, since distance/direction
 * alone can be hard to translate into a real destination. Works while locked on too.
 *
 * <p>Killing a locked-on hostile mob automatically re-locks onto whichever hostile mob is
 * now nearest, so tracking a fight doesn't mean re-scanning after every kill. Only fires for
 * an actual kill - checked via the mob's last synced health reaching zero, since the client
 * never actually learns *why* an entity was removed (vanilla's own removal-packet handling
 * always reports a generic reason, regardless of the real one, which stays server-only) -
 * while the Hostile Mobs category was the one locked from. The mob simply despawning or
 * wandering out of range still just drops the lock like normal. For tracking a fight against
 * more than one attacker, where sticking to a single target until it dies isn't what you
 * want, see {@link CombatModeController} instead.
 *
 * <p>The Markers category ({@link MapMarkerController}) is one exception to almost
 * everything above: it isn't range-limited or distance-sorted like every other category
 * (oldest-placed first instead), and Delete removes the focused marker outright rather than
 * meaning "stop lock" - which otherwise does nothing while nothing's locked. Players is a
 * second exception to the range limit only (still distance-sorted like a normal category) -
 * vanilla always syncs every other player in the dimension to the client regardless of
 * distance, so there's no need to cap it at {@link #scanRange()} like every other entity scan.
 *
 * <p>Crops additionally narrates "Ripe" once a crop is actually ready to harvest - silent
 * otherwise, same as Build Mode's powered-block narration - covering farmland crops, pumpkin
 * and melon stems (and the pumpkin/melon fruit itself, once one has actually grown), nether
 * wart, cocoa, sweet berry bushes, saplings (including bamboo's own sapling stage and mangrove
 * propagules), and cave vine segments actually bearing glow berries (see {@link #isCrop} and
 * {@link #isRipe}). Grown bamboo, sugar cane, and kelp are covered too, each narrating its
 * height instead of "Ripe" - one Scanner entry per stalk rather than per block, or per
 * horizontally-connected clump - see {@link #scanCrops}, {@link #addStalkClusters}, and {@link
 * #stalkHeight}.
 */
public final class ScannerController {
	private static final int LEAF_SEARCH_MARGIN = 2;

	// Biomes are a much coarser, exploration-scale thing than everything else the Scanner
	// finds - a fixed, longer range independent of the user's configurable scannerRange, and
	// sampled instead of scanned block-by-block (see scanBiomes).
	private static final double BIOME_SCAN_RANGE = 64.0;
	// Matches vanilla's own biome storage granularity (one biome value per 4x4x4 cell) - finer
	// sampling would just repeat the same answer.
	private static final int BIOME_SAMPLE_STEP = 4;

	private static final ScannerCategory[] CATEGORIES = ScannerCategory.values();

	private static int categoryIndex = -1;
	private static List<ScannerItem> items = List.of();
	private static int itemIndex;

	private static Entity lockedEntity;

	/** The Search category's current term - blank until the player enters one via {@link #openSearchPrompt}. */
	private static String searchTerm = "";

	private ScannerController() {
	}

	private static double scanRange() {
		return UnitedMinecraftConfig.get().scannerRange;
	}

	public static boolean isLocked() {
		return lockedEntity != null;
	}

	public static void reset() {
		categoryIndex = -1;
		items = List.of();
		itemIndex = 0;
		lockedEntity = null;
		searchTerm = "";
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		boolean prevCategory = ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_PREV_CATEGORY);
		boolean nextCategory = ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_NEXT_CATEGORY);
		boolean pageDown = ClientKeyBindings.pressed(ClientKeyBindings.PAGE_DOWN);
		boolean pageUp = ClientKeyBindings.pressed(ClientKeyBindings.PAGE_UP);
		boolean targetPressed = ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_TARGET);
		boolean stopPressed = ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_STOP_LOCK);
		boolean coordinatesPressed = ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_COORDINATES);

		if (isLocked()) {
			if (stopPressed) {
				stopLock(client);
			}
			if (coordinatesPressed) {
				announceCoordinates(client, lockedEntity.blockPosition());
			}
			if (targetPressed) {
				interactWithLocked(client, player);
			}
			return;
		}
		if (BuildModeController.isActive() || CombatModeController.isActive()) {
			return;
		}

		if (prevCategory) {
			switchCategory(client, player, -1);
		}
		if (nextCategory) {
			switchCategory(client, player, 1);
		}
		if (pageDown) {
			if (ClientKeyBindings.isModifierDown(client)) {
				stepItemSameType(client, player, 1);
			} else {
				stepItem(client, player, 1);
			}
		}
		if (pageUp) {
			if (ClientKeyBindings.isModifierDown(client)) {
				stepItemSameType(client, player, -1);
			} else {
				stepItem(client, player, -1);
			}
		}
		if (targetPressed) {
			target(client, player);
		}
		if (ClientKeyBindings.pressed(ClientKeyBindings.SCANNER_REMOVE_MARKER)
				&& categoryIndex >= 0 && CATEGORIES[categoryIndex] == ScannerCategory.MARKERS) {
			removeCurrentMarker(client, player);
		}
		if (coordinatesPressed) {
			ScannerItem item = currentItem();
			if (item == null) {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_no_item"));
			} else {
				announceCoordinates(client, item.entity() != null ? item.entity().blockPosition() : item.blockPos());
			}
		}
	}

	private static void removeCurrentMarker(Minecraft client, LocalPlayer player) {
		ScannerItem item = currentItem();
		if (item == null || item.label() == null) {
			return;
		}
		MapMarkerController.MapMarker marker = MapMarkerController.findAt(player.level().dimension(), item.blockPos());
		if (marker == null) {
			return;
		}
		MapMarkerController.remove(client, marker);
		items = scan(ScannerCategory.MARKERS, player);
		itemIndex = items.isEmpty() ? 0 : Math.min(itemIndex, items.size() - 1);
	}

	/**
	 * Opens a name prompt for the focused item's block, sharing {@link
	 * ClientKeyBindings#PLACE_MARKER} via Shift the same way several other keys layer a
	 * second action - Markers already have their own naming flow (plain U places one) so
	 * this is for every other block-based category, most usefully doors and chests. Once
	 * named, that name replaces the block's ordinary derived name everywhere the Scanner
	 * narrates it (cycling with Page Up/Down, targeting, etc.), the same way a Map Marker's
	 * name does - see {@link #itemName}. Entities have no fixed position to key a saved name
	 * off of, so this narrates a no-op message while one is focused instead of doing nothing
	 * silently. The Search category has no "focused item" to name yet the first time it's
	 * opened - Shift+U there means "enter a search term" instead, via {@link
	 * #openSearchPrompt}.
	 */
	public static void nameFocusedItem(Minecraft client, LocalPlayer player) {
		if (categoryIndex != -1 && CATEGORIES[categoryIndex] == ScannerCategory.SEARCH) {
			openSearchPrompt(client, player);
			return;
		}
		if (categoryIndex == -1 || CATEGORIES[categoryIndex] == ScannerCategory.MARKERS) {
			return;
		}
		ScannerItem item = currentItem();
		if (item == null || item.entity() != null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_no_item"));
			return;
		}
		ResourceKey<Level> dimension = player.level().dimension();
		BlockPos pos = item.blockPos();
		String currentName = NamedBlockController.findAt(dimension, pos);
		NamedBlockController.openNameScreen(client, dimension, pos, currentName,
				() -> rescanAndRefocus(player, refreshed -> pos.equals(refreshed.blockPos())));
	}

	/**
	 * Opens a prompt for the Search category's term - typing something like "glowstone" then
	 * re-scans for every visible block whose name contains it, the same "don't x-ray"
	 * visibility rule Ores and Liquids use (see {@link #scanSearch}). Pre-filled with whatever
	 * term is already set, so adjusting a search doesn't mean retyping it from scratch.
	 */
	private static void openSearchPrompt(Minecraft client, LocalPlayer player) {
		client.gui.setScreen(new MarkerNameScreen(
				Component.translatable("united_minecraft.search_screen.title"),
				Component.translatable("united_minecraft.narrate.search_prompt"),
				Component.translatable("united_minecraft.narrate.search_cancelled"),
				Component.translatable("united_minecraft.search_screen.term"),
				searchTerm,
				term -> {
					searchTerm = term == null ? "" : term.trim();
					items = scan(ScannerCategory.SEARCH, player);
					itemIndex = 0;
					Component summary = items.isEmpty()
							? Component.translatable("united_minecraft.narrate.scanner_empty")
							: Component.translatable("united_minecraft.narrate.scanner_count", items.size())
									.append(Component.literal(". "))
									.append(describeItem(ScannerCategory.SEARCH, items.get(0), player));
					client.getNarrator().saySystemNow(ScannerCategory.SEARCH.label().append(Component.literal(", ")).append(summary));
				}));
	}

	private static void announceCoordinates(Minecraft client, BlockPos pos) {
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.coordinates", pos.getX(), pos.getY(), pos.getZ()));
	}

	/** Continuously re-aims at the locked entity; called every tick instead of normal camera handling while locked. */
	public static void tickLock(Minecraft client, LocalPlayer player) {
		if (lockedEntity == null) {
			return;
		}
		if (lockedEntity.isAlive() && lockedEntity.level() == player.level()) {
			CameraUtil.aimAtEntity(player, lockedEntity);
			return;
		}

		// The client never actually learns *why* an entity was removed - vanilla's own
		// entity-removal packet handling always reports RemovalReason.DISCARDED regardless of
		// the real server-side reason, which is never sent over the network. The mob's last
		// synced health is the closest thing to a reliable "it died" signal actually available
		// client-side (the server syncs health to 0 at or before removal on death).
		boolean killed = lockedEntity instanceof LivingEntity living && living.isDeadOrDying();
		lockedEntity = null;
		if (killed && categoryIndex >= 0 && CATEGORIES[categoryIndex] == ScannerCategory.HOSTILE_MOBS
				&& relockNearestHostile(client, player)) {
			return;
		}
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_lock_lost"));
	}

	/** Re-scans for hostile mobs and locks onto the nearest one, if any are left. */
	private static boolean relockNearestHostile(Minecraft client, LocalPlayer player) {
		items = scan(ScannerCategory.HOSTILE_MOBS, player);
		itemIndex = 0;
		if (items.isEmpty() || items.get(0).entity() == null) {
			return false;
		}
		targetEntity(client, player, items.get(0).entity(), false);
		return true;
	}

	/** Drops the lock without narrating anything - used when Combat Mode is taking over as the exclusive aim-owner. */
	static void cancelLock() {
		lockedEntity = null;
	}

	/**
	 * Interacts with the locked entity directly - the same real, server-authoritative
	 * interaction a right-click sends, but targeting the exact locked entity by identity
	 * instead of whatever the crosshair's raycast happens to hit. Meant for when another entity
	 * is physically in the way of the one actually locked on (two chickens crowded together
	 * trying to breed them, say) - camera aim alone can't distinguish which one gets hit in that
	 * case, but the Scanner already knows exactly which one is locked.
	 *
	 * <p>Mirrors vanilla's own main-hand-then-offhand fallback (see {@code Minecraft.startUseItem}
	 * and {@link BuildModeController#attemptPlace}, which does the same for block placement):
	 * stop on a hand that actually consumes the interaction, or on an explicit failure.
	 *
	 * <p>Going straight to {@code gameMode.interact} like this skips {@code Minecraft}'s own
	 * mouse-click handling entirely - including the hook {@link AnimalFeedingController}
	 * normally relies on for its feed-confirmation sound - so that's triggered explicitly here
	 * too, same as a real click would.
	 */
	private static void interactWithLocked(Minecraft client, LocalPlayer player) {
		if (lockedEntity == null || !lockedEntity.isAlive()) {
			return;
		}
		EntityHitResult hitResult = new EntityHitResult(lockedEntity, lockedEntity.getBoundingBox().getCenter());
		for (InteractionHand hand : InteractionHand.values()) {
			AnimalFeedingController.playFeedSoundIfSuccessful(lockedEntity, player.getItemInHand(hand));
			InteractionResult result = client.gameMode.interact(player, lockedEntity, hitResult, hand);
			if (result instanceof InteractionResult.Success success) {
				if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
					player.swing(hand);
				}
				return;
			}
			if (result instanceof InteractionResult.Fail) {
				return;
			}
		}
	}

	private static void stopLock(Minecraft client) {
		if (lockedEntity == null) {
			return;
		}
		lockedEntity = null;
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_lock_stopped"));
	}

	private static void switchCategory(Minecraft client, LocalPlayer player, int direction) {
		if (categoryIndex == -1) {
			categoryIndex = direction > 0 ? 0 : CATEGORIES.length - 1;
		} else {
			categoryIndex = Math.floorMod(categoryIndex + direction, CATEGORIES.length);
		}

		ScannerCategory category = CATEGORIES[categoryIndex];
		items = scan(category, player);
		itemIndex = 0;

		if (category == ScannerCategory.SEARCH && searchTerm.isBlank()) {
			client.getNarrator().saySystemNow(category.label().append(Component.literal(", "))
					.append(Component.translatable("united_minecraft.narrate.search_no_term")));
			return;
		}
		if (items.isEmpty()) {
			client.getNarrator().saySystemNow(category.label().append(Component.literal(", "))
					.append(Component.translatable("united_minecraft.narrate.scanner_empty")));
			return;
		}
		// "Trees, 13" - just the category and count, no extra wording - then the nearest item.
		client.getNarrator().saySystemNow(category.label().append(Component.literal(", "))
				.append(Component.translatable("united_minecraft.narrate.scanner_count", items.size()))
				.append(Component.literal(". "))
				.append(describeItem(category, items.get(0), player)));
	}

	private static void stepItem(Minecraft client, LocalPlayer player, int direction) {
		if (categoryIndex == -1 || items.isEmpty()) {
			return;
		}
		itemIndex = Math.floorMod(itemIndex + direction, items.size());
		client.getNarrator().saySystemNow(
				describeItemWithPosition(CATEGORIES[categoryIndex], items.get(itemIndex), player, itemIndex, items.size()));
	}

	/**
	 * Jumps to the next/previous item that's the "same kind of thing" as the currently
	 * selected one - e.g. skipping past every cow and pig to reach the next sheep - instead
	 * of stepping one item at a time through the whole distance-sorted list like {@link
	 * #stepItem}. Falls back to re-narrating the current item if nothing else in the list
	 * matches. See {@link #sameType} for what "same kind" means per category.
	 */
	private static void stepItemSameType(Minecraft client, LocalPlayer player, int direction) {
		if (categoryIndex == -1 || items.isEmpty()) {
			return;
		}
		ScannerCategory category = CATEGORIES[categoryIndex];
		ScannerItem current = items.get(itemIndex);
		Level level = player.level();
		int size = items.size();
		for (int step = 1; step <= size; step++) {
			int candidate = Math.floorMod(itemIndex + direction * step, size);
			if (sameType(category, current, items.get(candidate), level)) {
				itemIndex = candidate;
				client.getNarrator().saySystemNow(describeItemWithPosition(category, items.get(itemIndex), player, itemIndex, size));
				return;
			}
		}
		client.getNarrator().saySystemNow(describeItemWithPosition(category, current, player, itemIndex, size));
	}

	/**
	 * Whether {@code a} and {@code b} are the same species/block type - entities compare by
	 * {@link net.minecraft.world.entity.EntityType}, blocks by their live {@link Block}
	 * instance at each item's stored position (the same live-lookup pattern {@link
	 * #describeItem}/{@link #itemName} already use, since a scanned block can change between
	 * scan time and now). Markers are individually named rather than typed, so they never match.
	 */
	private static boolean sameType(ScannerCategory category, ScannerItem a, ScannerItem b, Level level) {
		if (category == ScannerCategory.MARKERS) {
			return false;
		}
		if (a.entity() != null && b.entity() != null) {
			return a.entity().getType() == b.entity().getType();
		}
		if (a.blockPos() != null && b.blockPos() != null) {
			return level.getBlockState(a.blockPos()).getBlock() == level.getBlockState(b.blockPos()).getBlock();
		}
		return false;
	}

	private static void target(Minecraft client, LocalPlayer player) {
		ScannerItem item = currentItem();
		if (item == null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_no_item"));
			return;
		}

		boolean walkThere = ClientKeyBindings.isShiftDown(client);
		ScannerCategory category = CATEGORIES[categoryIndex];
		if (item.entity() != null) {
			if (category == ScannerCategory.ITEMS) {
				// Dropped items don't move on their own and aren't a threat to track - just
				// look at them once, like a block, rather than starting a continuous lock-on.
				targetEntityOnce(client, player, item.entity(), walkThere);
			} else {
				targetEntity(client, player, item.entity(), walkThere);
			}
		} else {
			targetBlock(client, player, item.blockPos(), itemName(category, item, player), walkThere);
		}
	}

	private static void targetEntity(Minecraft client, LocalPlayer player, Entity entity, boolean walkThere) {
		if (!entity.isAlive()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_target_gone"));
			return;
		}
		if (walkThere) {
			// Once Auto-Walk actually arrives, lock on exactly as if Enter had been pressed
			// there - re-checking aliveness, since the entity could've died or wandered off
			// mid-walk. Also re-scans the category first, so Page Up/Down cycling afterward
			// continues from wherever this entity now falls in the freshly re-sorted list,
			// instead of a stale index computed before the player moved.
			AutoWalkController.start(client, player, entity.blockPosition(), mobDisplayName(entity),
					() -> {
						if (entity.isAlive()) {
							rescanAndRefocus(player, item -> item.entity() == entity);
							lockOnto(client, player, entity);
						}
					});
			return;
		}
		lockOnto(client, player, entity);
	}

	private static void lockOnto(Minecraft client, LocalPlayer player, Entity entity) {
		lockedEntity = entity;
		CameraUtil.aimAt(player, entity.getBoundingBox().getCenter());
		client.getNarrator().saySystemNow(
				Component.translatable("united_minecraft.narrate.scanner_lock_started", mobDisplayName(entity)));
	}

	private static void targetEntityOnce(Minecraft client, LocalPlayer player, Entity entity, boolean walkThere) {
		if (!entity.isAlive()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_target_gone"));
			return;
		}
		if (walkThere) {
			AutoWalkController.start(client, player, entity.blockPosition(), mobDisplayName(entity),
					() -> {
						if (entity.isAlive()) {
							rescanAndRefocus(player, item -> item.entity() == entity);
							aimOnceAtEntity(client, player, entity);
						}
					});
			return;
		}
		aimOnceAtEntity(client, player, entity);
	}

	private static void aimOnceAtEntity(Minecraft client, LocalPlayer player, Entity entity) {
		CameraUtil.aimAt(player, entity.getBoundingBox().getCenter());
		client.getNarrator().saySystemNow(
				Component.translatable("united_minecraft.narrate.scanner_facing", mobDisplayName(entity)));
	}

	/**
	 * {@code LivingEntity.isBaby()} covers both breedable animals ({@link
	 * net.minecraft.world.entity.AgeableMob}) and baby-capable hostiles like zombies, each with
	 * their own separate synced flag underneath - checking it on the base class here picks up
	 * both without needing to special-case either.
	 */
	private static Component mobDisplayName(Entity entity) {
		if (entity instanceof LivingEntity living && living.isBaby()) {
			return Component.translatable("united_minecraft.narrate.baby_mob", entity.getDisplayName());
		}
		return entity.getDisplayName();
	}

	private static void targetBlock(Minecraft client, LocalPlayer player, BlockPos pos, Component name, boolean walkThere) {
		if (walkThere) {
			AutoWalkController.start(client, player, pos, name, () -> {
				rescanAndRefocus(player, item -> pos.equals(item.blockPos()));
				aimOnceAtBlock(client, player, pos, name);
			});
			return;
		}
		aimOnceAtBlock(client, player, pos, name);
	}

	private static void aimOnceAtBlock(Minecraft client, LocalPlayer player, BlockPos pos, Component name) {
		CameraUtil.aimAt(player, interactionPoint(player.level(), pos));
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_facing", name));
	}

	/**
	 * Re-runs the current category's scan and moves focus to whichever item now matches {@code
	 * originalItem} - used once Auto-Walk arrives, since the walk itself can take long enough
	 * that distances (and thus sort order) have shifted, or new items have appeared/disappeared,
	 * leaving the pre-walk index stale. Falls back to clamping the existing index if the
	 * original item can no longer be found (it was mined, picked up, etc).
	 */
	private static void rescanAndRefocus(LocalPlayer player, Predicate<ScannerItem> originalItem) {
		if (categoryIndex == -1) {
			return;
		}
		items = scan(CATEGORIES[categoryIndex], player);
		for (int i = 0; i < items.size(); i++) {
			if (originalItem.test(items.get(i))) {
				itemIndex = i;
				return;
			}
		}
		itemIndex = items.isEmpty() ? 0 : Math.min(itemIndex, items.size() - 1);
	}

	/**
	 * The point actually worth aiming at to interact with a block - the center of its real
	 * outline shape, not the full-cube center {@link Vec3#atCenterOf} would give. For a full
	 * block those are the same point, but a door, trapdoor, fence gate, button, or lever only
	 * occupies a thin slice of its block space, and that slice moves as the block's state
	 * changes (a door swings open, say) - aiming at the cube center can land outside the real
	 * shape entirely, which is exactly why re-targeting an open door to close it used to need a
	 * manual nudge left or right to actually land back on it.
	 */
	private static Vec3 interactionPoint(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		var shape = state.getShape(level, pos);
		if (shape.isEmpty()) {
			return Vec3.atCenterOf(pos);
		}
		AABB bounds = shape.bounds();
		return new Vec3(
				pos.getX() + (bounds.minX + bounds.maxX) / 2.0,
				pos.getY() + (bounds.minY + bounds.maxY) / 2.0,
				pos.getZ() + (bounds.minZ + bounds.maxZ) / 2.0);
	}

	private static ScannerItem currentItem() {
		if (categoryIndex == -1 || items.isEmpty() || itemIndex >= items.size()) {
			return null;
		}
		return items.get(itemIndex);
	}

	/** Beyond this many blocks of vertical separation, above/below is narrated alongside the compass heading. */
	private static final double VERTICAL_DIRECTION_THRESHOLD = 5.0;

	private static Component describeItem(ScannerCategory category, ScannerItem item, LocalPlayer player) {
		int distance = (int) Math.round(item.distance());
		Vec3 from = player.position();
		Vec3 to = targetPosition(item);
		Component direction = CameraUtil.compassDirectionTo(from, to);
		Component vertical = verticalDirection(from, to);
		if (vertical != null) {
			direction = direction.copy().append(Component.literal(", ")).append(vertical);
		}
		Component name = itemName(category, item, player);
		if (category == ScannerCategory.CROPS) {
			BlockState state = player.level().getBlockState(item.blockPos());
			if (isRipe(state)) {
				name = name.copy().append(Component.literal(", ")).append(Component.translatable("united_minecraft.narrate.scanner_ripe"));
			}
			Component height = stalkHeight(player.level(), item.blockPos());
			if (height != null) {
				name = name.copy().append(Component.literal(", ")).append(height);
			}
		}
		if (category == ScannerCategory.INTERACTABLES) {
			BlockState state = player.level().getBlockState(item.blockPos());
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.OCCUPIED)) {
				name = name.copy().append(Component.literal(", ")).append(Component.translatable("united_minecraft.narrate.scanner_occupied"));
			}
			if (state.getBlock() instanceof SignBlock) {
				Component signText = describeSignText(player.level(), item.blockPos());
				name = signText != null
						? name.copy().append(Component.literal(", ")).append(signText)
						: name.copy().append(Component.literal(", ")).append(Component.translatable("united_minecraft.narrate.scanner_sign_blank"));
			}
		}
		return Component.translatable("united_minecraft.narrate.scanner_item", name, distance, direction);
	}

	/** {@link #describeItem} plus "item N of M" - used when cycling ({@link #stepItem}/{@link #stepItemSameType}), not on category select. */
	private static Component describeItemWithPosition(ScannerCategory category, ScannerItem item, LocalPlayer player, int index, int total) {
		return describeItem(category, item, player).copy()
				.append(Component.literal(", "))
				.append(Component.translatable("united_minecraft.narrate.scanner_position", index + 1, total));
	}

	/** Null when {@code to} is within the normal vertical range for a plain compass heading. */
	private static Component verticalDirection(Vec3 from, Vec3 to) {
		double dy = to.y() - from.y();
		if (Math.abs(dy) <= VERTICAL_DIRECTION_THRESHOLD) {
			return null;
		}
		int blocks = (int) Math.round(Math.abs(dy));
		Component word = Component.translatable(dy > 0
				? "united_minecraft.direction.above"
				: "united_minecraft.direction.below");
		return Component.translatable("united_minecraft.narrate.scanner_vertical", word, blocks);
	}

	private static Vec3 targetPosition(ScannerItem item) {
		return item.entity() != null ? item.entity().getBoundingBox().getCenter() : Vec3.atCenterOf(item.blockPos());
	}

	private static Component itemName(ScannerCategory category, ScannerItem item, LocalPlayer player) {
		if (item.label() != null) {
			return Component.literal(item.label());
		}
		if (item.entity() != null) {
			if (category == ScannerCategory.ITEMS && item.entity() instanceof ItemEntity itemEntity) {
				return ItemDescriptions.describe(itemEntity.getItem(), player);
			}
			return mobDisplayName(item.entity());
		}
		Level level = player.level();
		if (category == ScannerCategory.TREES) {
			return describeTree(level, item.blockPos());
		}
		if (category == ScannerCategory.BIOMES) {
			return AccessibilityTickHandler.biomeName(level.getBiome(item.blockPos()));
		}
		return level.getBlockState(item.blockPos()).getBlock().getName();
	}

	private static Component describeTree(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(BlockTags.LOGS)) {
			// Chopped down or otherwise changed since the scan; fall back to whatever's there now.
			return state.getBlock().getName();
		}

		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		String path = id.getPath();
		String trimmed = path.endsWith("_log") ? path.substring(0, path.length() - 4) : path;

		StringBuilder name = new StringBuilder();
		for (String word : trimmed.split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!name.isEmpty()) {
				name.append(' ');
			}
			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		name.append(" Tree");
		return Component.literal(name.toString());
	}

	/** Joins a sign's non-blank lines (front first, falling back to back if the front is empty) - null if both sides are blank. */
	private static Component describeSignText(Level level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
			return null;
		}
		Component front = joinSignLines(sign.getText(true));
		return front != null ? front : joinSignLines(sign.getText(false));
	}

	private static Component joinSignLines(SignText text) {
		MutableComponent result = null;
		for (Component line : text.getMessages(false)) {
			String plain = line.getString();
			if (plain.isBlank()) {
				continue;
			}
			result = result == null ? Component.literal(plain) : result.append(Component.literal(" ")).append(Component.literal(plain));
		}
		return result;
	}

	private static List<ScannerItem> scan(ScannerCategory category, LocalPlayer player) {
		return switch (category) {
			// Beds don't have a menu (sleeping/setting spawn isn't a GUI), but they're
			// still something you right-click to do something with, not a lever/button/door
			// style toggle - closer in spirit to this category than to Mechanisms. Both beds
			// and double chests are two blocks sharing one real-world object - only match the
			// head half of a bed and the non-right half of a chest, so each shows up once.
			// Signs have no menu either, but reading one is the same kind of "approach and get
			// information from it" action as everything else here.
			case INTERACTABLES -> scanBlocks(player, (pos, state) -> {
				if (state.getBlock() instanceof BedBlock) {
					return state.getValue(BedBlock.PART) == BedPart.HEAD;
				}
				if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) == ChestType.RIGHT) {
					return false;
				}
				if (state.getBlock() instanceof SignBlock) {
					return true;
				}
				return state.getMenuProvider(player.level(), pos) != null;
			});
			// A door is two block positions (HALF=LOWER/UPPER) sharing one real-world object -
			// only match the lower half so each door shows up once, the same fix already applied
			// to beds and double chests above.
			case MECHANISMS -> scanBlocks(player, (pos, state) -> {
				if (state.getBlock() instanceof DoorBlock) {
					return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
				}
				return isMechanism(state.getBlock());
			});
			case ITEMS -> scanEntities(player, entity -> entity instanceof ItemEntity);
			case TREES -> scanTrees(player);
			// Deliberately not x-ray: OreDetection.isExposed only counts ore already
			// bordering air or a fluid, i.e. actually visible through a gap, not buried.
			case ORES -> scanBlocks(player, (pos, state) ->
					OreDetection.isValuableOre(state) && OreDetection.isExposed(player.level(), pos, player.getEyePosition()));
			case LIQUIDS -> scanLiquids(player);
			case CROPS -> scanCrops(player);
			case SEARCH -> scanSearch(player);
			case BIOMES -> scanBiomes(player);
			// instanceof Animal alone missed anything that isn't a beast - villagers, wandering
			// traders, iron/snow/copper golems, bats, squids, allays - since none of those extend
			// Animal. Classify by spawn MobCategory instead so any non-hostile Mob counts.
			case PASSIVE_MOBS -> scanEntities(player, entity ->
					entity instanceof Mob mob && !(entity instanceof Enemy) && mob.getType().getCategory() != MobCategory.MONSTER);
			case HOSTILE_MOBS -> scanEntities(player, entity -> entity instanceof Enemy);
			case MARKERS -> scanMarkers(player);
			case PLAYERS -> scanPlayers(player);
			case VEHICLES -> scanEntities(player, entity ->
					entity instanceof AbstractMinecart || entity instanceof Boat || entity instanceof ArmorStand);
		};
	}

	private static boolean isMechanism(Block block) {
		return block instanceof LeverBlock
				|| block instanceof ButtonBlock
				|| block instanceof DoorBlock
				|| block instanceof TrapDoorBlock
				|| block instanceof FenceGateBlock;
	}

	/**
	 * Covers wheat/carrots/potatoes/beetroot/torchflower (all {@link CropBlock}), pumpkin/melon
	 * stems (both the growing {@link StemBlock} stage and the {@link AttachedStemBlock} stage
	 * once a fruit has actually formed), the fruit itself (plain {@code Blocks.PUMPKIN}/{@code
	 * Blocks.MELON} - neither has its own dedicated block class), nether wart, cocoa, sweet
	 * berries, saplings (including mangrove propagules - a {@link SaplingBlock} subclass),
	 * bamboo's own single-block sapling stage, and cave vines (any segment, head or body alike -
	 * {@link #isRipe} is what actually distinguishes a glow-berry-bearing one). Grown bamboo
	 * stalks, sugar cane, and kelp are deliberately not here - see {@link #addStalkClusters} for
	 * why they need clustering instead of a flat per-block match like everything else in this
	 * category. Package-private - {@link BuildModeController} reuses this (and {@link #isRipe})
	 * so its cursor narrates "Ripe" the same way the Scanner does.
	 */
	static boolean isCrop(Block block) {
		return block instanceof CropBlock
				|| block instanceof StemBlock
				|| block instanceof AttachedStemBlock
				|| block instanceof NetherWartBlock
				|| block instanceof CocoaBlock
				|| block instanceof SweetBerryBushBlock
				|| block instanceof SaplingBlock
				|| block instanceof BambooSaplingBlock
				|| block instanceof CaveVines
				|| block == Blocks.PUMPKIN
				|| block == Blocks.MELON;
	}

	/**
	 * Whether a crop block is ready to harvest. Most crops report this via their own age
	 * property reaching its documented max; sweet berry bushes are the one exception - they're
	 * pickable (with berries actually dropping) starting at age 2 of 3, not only at full growth.
	 * Saplings and bamboo's own sapling stage have no such concept and never report ripe.
	 */
	static boolean isRipe(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (block instanceof SweetBerryBushBlock) {
			return state.getValue(SweetBerryBushBlock.AGE) >= 2;
		}
		if (block instanceof StemBlock) {
			return state.getValue(StemBlock.AGE) >= StemBlock.MAX_AGE;
		}
		if (block instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		}
		if (block instanceof CocoaBlock) {
			return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
		}
		if (block instanceof CaveVines) {
			return CaveVines.hasGlowBerries(state);
		}
		return false;
	}

	/**
	 * How tall the vertical stack of identical blocks starting at {@code base} actually is right
	 * now - bamboo, sugar cane, and kelp all grow straight up as a stack of otherwise-identical
	 * blocks with no single property that records the whole stalk's height, so this counts by
	 * walking upward through matching blocks live rather than relying on anything cached from
	 * scan time (the same reasoning {@link #isRipe} and the bed-occupied check above it use).
	 * Null for anything else in the Crops category, which is always a single block already.
	 */
	private static Component stalkHeight(Level level, BlockPos base) {
		Block block = level.getBlockState(base).getBlock();
		Predicate<Block> partOfStalk;
		if (block instanceof BambooStalkBlock) {
			partOfStalk = candidate -> candidate instanceof BambooStalkBlock;
		} else if (block instanceof SugarCaneBlock) {
			partOfStalk = candidate -> candidate instanceof SugarCaneBlock;
		} else if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
			partOfStalk = candidate -> candidate instanceof KelpBlock || candidate instanceof KelpPlantBlock;
		} else {
			return null;
		}

		int height = 1;
		BlockPos pos = base.above();
		while (partOfStalk.test(level.getBlockState(pos).getBlock())) {
			height++;
			pos = pos.above();
		}
		return Component.translatable("united_minecraft.narrate.scanner_height", height);
	}

	private static List<ScannerItem> scanBlocks(LocalPlayer player, BiPredicate<BlockPos, BlockState> predicate) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockPos center = player.blockPosition();
		int r = (int) scanRange();

		List<ScannerItem> results = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			BlockState state = level.getBlockState(pos);
			if (!predicate.test(pos, state)) {
				continue;
			}
			double distance = eye.distanceTo(Vec3.atCenterOf(pos));
			if (distance <= scanRange()) {
				BlockPos immutable = pos.immutable();
				String label = NamedBlockController.findAt(level.dimension(), immutable);
				results.add(new ScannerItem(immutable, null, distance, label));
			}
		}
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	/**
	 * Matches against a player-entered search term instead of a fixed set of block types - see
	 * {@link #openSearchPrompt}. Deliberately not x-ray, same as Ores/Liquids: {@link
	 * OreDetection#isExposed} only counts a match already bordering air or a fluid and actually
	 * visible through that gap, not sealed behind unmined blocks.
	 */
	private static List<ScannerItem> scanSearch(LocalPlayer player) {
		if (searchTerm.isBlank()) {
			return List.of();
		}
		String term = searchTerm.toLowerCase(Locale.ROOT);
		return scanBlocks(player, (pos, state) -> !state.isAir()
				&& matchesSearchTerm(state, term)
				&& OreDetection.isExposed(player.level(), pos, player.getEyePosition()));
	}

	/** Matches either the block's localized display name or its registry id (underscores treated as spaces) against {@code term}. */
	private static boolean matchesSearchTerm(BlockState state, String term) {
		if (state.getBlock().getName().getString().toLowerCase(Locale.ROOT).contains(term)) {
			return true;
		}
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().replace('_', ' ');
		return path.contains(term);
	}

	private static List<ScannerItem> scanEntities(LocalPlayer player, Predicate<Entity> predicate) {
		Vec3 eye = player.getEyePosition();
		AABB box = player.getBoundingBox().inflate(scanRange());

		List<ScannerItem> results = new ArrayList<>();
		for (Entity entity : player.level().getEntities(player, box, e -> e.isAlive() && predicate.test(e))) {
			double distance = eye.distanceTo(entity.getBoundingBox().getCenter());
			if (distance <= scanRange()) {
				results.add(new ScannerItem(null, entity, distance, null));
			}
		}
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	private static List<ScannerItem> scanTrees(LocalPlayer player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockPos center = player.blockPosition();
		int r = (int) scanRange();

		Set<BlockPos> logPositions = new HashSet<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			if (level.getBlockState(pos).is(BlockTags.LOGS)) {
				logPositions.add(pos.immutable());
			}
		}

		List<ScannerItem> results = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		for (BlockPos start : logPositions) {
			if (visited.contains(start)) {
				continue;
			}
			Set<BlockPos> cluster = new HashSet<>();
			BlockPos trunkBase = lowestLog(level, floodFillCluster(start, logPositions, visited, cluster));
			if (!hasNearbyLeaves(level, cluster)) {
				// A log cluster with no leaves anywhere near it is more likely a player-built
				// structure (cabin, bridge, etc.) than an actual tree.
				continue;
			}
			double distance = eye.distanceTo(Vec3.atCenterOf(trunkBase));
			if (distance <= scanRange()) {
				results.add(new ScannerItem(trunkBase, null, distance, null));
			}
		}
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	/**
	 * Water and lava, each clustered into connected bodies the same way {@link #scanTrees}
	 * clusters logs into trees - a lake or ocean is one Scanner entry, not one per block.
	 */
	private static List<ScannerItem> scanLiquids(LocalPlayer player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockPos center = player.blockPosition();
		int r = (int) scanRange();

		// Kept separate per fluid so a lava flow spilling into a lake doesn't cluster the two
		// into one body with an ambiguous name.
		Set<BlockPos> waterPositions = new HashSet<>();
		Set<BlockPos> lavaPositions = new HashSet<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.WATER)) {
				waterPositions.add(pos.immutable());
			} else if (state.is(Blocks.LAVA)) {
				lavaPositions.add(pos.immutable());
			}
		}

		List<ScannerItem> results = new ArrayList<>();
		addLiquidClusters(level, eye, waterPositions, results);
		addLiquidClusters(level, eye, lavaPositions, results);
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	/**
	 * Floods {@code positions} (one fluid type at a time, already range-limited) into connected
	 * bodies via {@link #floodFillCluster}, then reports each body once, at whichever of its
	 * blocks is both nearest the player and actually {@link OreDetection#isExposed exposed} -
	 * skipping a body entirely if none of it is, the same "don't x-ray" rule Ores follows (lava
	 * sealed behind unmined stone shouldn't show up until there's actually a way to see it).
	 */
	private static void addLiquidClusters(Level level, Vec3 eye, Set<BlockPos> positions, List<ScannerItem> results) {
		Set<BlockPos> visited = new HashSet<>();
		for (BlockPos start : positions) {
			if (visited.contains(start)) {
				continue;
			}
			Set<BlockPos> cluster = new HashSet<>();
			floodFillCluster(start, positions, visited, cluster);

			List<BlockPos> byDistance = new ArrayList<>(cluster);
			byDistance.sort(Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos))));
			for (BlockPos pos : byDistance) {
				double distance = eye.distanceTo(Vec3.atCenterOf(pos));
				if (distance > scanRange()) {
					break;
				}
				if (OreDetection.isExposed(level, pos, eye)) {
					results.add(new ScannerItem(pos.immutable(), null, distance, null));
					break;
				}
			}
		}
	}

	/**
	 * Crops, saplings (including bamboo's own single-block sapling stage), pumpkins/melons and
	 * their stems, and cave vine segments actually bearing glow berries are all sparse
	 * single-block matches - one Scanner entry per block, same as every other flat category.
	 * Grown bamboo, sugar cane, and kelp are the exception: each grows as a vertical stack of
	 * otherwise-identical blocks, so a flat per-block match would narrate the same stalk over
	 * and over while cycling up it. Each stack instead gets exactly one entry, at its base, via
	 * {@link #addStalkClusters} - see {@link #stalkHeight} for the height that goes with it.
	 */
	private static List<ScannerItem> scanCrops(LocalPlayer player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockPos center = player.blockPosition();
		int r = (int) scanRange();

		Set<BlockPos> bambooPositions = new HashSet<>();
		Set<BlockPos> canePositions = new HashSet<>();
		Set<BlockPos> kelpPositions = new HashSet<>();
		List<ScannerItem> results = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			BlockState state = level.getBlockState(pos);
			Block block = state.getBlock();
			if (block instanceof BambooStalkBlock) {
				bambooPositions.add(pos.immutable());
				continue;
			}
			if (block instanceof SugarCaneBlock) {
				canePositions.add(pos.immutable());
				continue;
			}
			if (block instanceof KelpBlock || block instanceof KelpPlantBlock) {
				kelpPositions.add(pos.immutable());
				continue;
			}
			if (!isCrop(block)) {
				continue;
			}
			double distance = eye.distanceTo(Vec3.atCenterOf(pos));
			if (distance <= scanRange()) {
				results.add(new ScannerItem(pos.immutable(), null, distance, null));
			}
		}

		addStalkClusters(eye, bambooPositions, results);
		addStalkClusters(eye, canePositions, results);
		addStalkClusters(eye, kelpPositions, results);
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	/**
	 * Reports each contiguous vertical run within {@code positions} (already range-limited, and
	 * already narrowed to a single stalk type by the caller) exactly once, at its base -
	 * deliberately only merging straight up/down, not the full 26-connected flood fill {@link
	 * #scanTrees} and {@link #addLiquidClusters} use, so two stalks planted right next to each
	 * other stay two separate entries instead of merging into one "clump" the way a dense
	 * horizontal thicket of bamboo otherwise would.
	 */
	private static void addStalkClusters(Vec3 eye, Set<BlockPos> positions, List<ScannerItem> results) {
		Set<BlockPos> visited = new HashSet<>();
		for (BlockPos pos : positions) {
			if (visited.contains(pos) || positions.contains(pos.below())) {
				// Either already reported as part of another stalk's climb, or not actually this
				// stalk's base - the block below is part of the same run and will find it instead.
				continue;
			}
			BlockPos top = pos;
			visited.add(top);
			while (positions.contains(top.above())) {
				top = top.above();
				visited.add(top);
			}
			double distance = eye.distanceTo(Vec3.atCenterOf(pos));
			if (distance <= scanRange()) {
				results.add(new ScannerItem(pos, null, distance, null));
			}
		}
	}

	/**
	 * Nearby distinct biomes, one entry per biome type at whichever sampled point of it is
	 * nearest - meant for deciding which direction to explore, not for finding an exact border.
	 * Sampled on a {@link #BIOME_SAMPLE_STEP}-block grid (matching vanilla's own biome storage
	 * granularity) at each column's world surface height rather than scanned block-by-block like
	 * every other category - a full 3D scan out to {@link #BIOME_SCAN_RANGE} would be millions of
	 * positions for something that barely varies vertically above ground. That surface-height
	 * sampling does mean a biome that only exists underground (dripstone caves, the deep dark)
	 * won't show up here - this category is about surface exploration, not cave prospecting.
	 * The biome the player is already standing in is skipped, since it's not "nearby" in any
	 * useful sense.
	 */
	private static List<ScannerItem> scanBiomes(LocalPlayer player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockPos center = player.blockPosition();
		Holder<Biome> currentBiome = level.getBiome(center);
		int r = (int) BIOME_SCAN_RANGE;

		Map<Holder<Biome>, BlockPos> nearestPos = new HashMap<>();
		Map<Holder<Biome>, Double> nearestDist = new HashMap<>();
		for (int dx = -r; dx <= r; dx += BIOME_SAMPLE_STEP) {
			for (int dz = -r; dz <= r; dz += BIOME_SAMPLE_STEP) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				BlockPos pos = new BlockPos(x, y, z);
				Holder<Biome> biome = level.getBiome(pos);
				if (biome.equals(currentBiome)) {
					continue;
				}
				double distance = eye.distanceTo(Vec3.atCenterOf(pos));
				if (distance > BIOME_SCAN_RANGE) {
					continue;
				}
				Double best = nearestDist.get(biome);
				if (best == null || distance < best) {
					nearestDist.put(biome, distance);
					nearestPos.put(biome, pos);
				}
			}
		}

		List<ScannerItem> results = new ArrayList<>();
		for (Map.Entry<Holder<Biome>, BlockPos> entry : nearestPos.entrySet()) {
			results.add(new ScannerItem(entry.getValue(), null, nearestDist.get(entry.getKey()), null));
		}
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	/** Every marker in the player's current dimension, oldest first - not distance-filtered or sorted, unlike every other category. */
	private static List<ScannerItem> scanMarkers(LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		List<ScannerItem> results = new ArrayList<>();
		for (MapMarkerController.MapMarker marker : MapMarkerController.inDimension(player.level().dimension())) {
			BlockPos pos = marker.pos();
			double distance = eye.distanceTo(Vec3.atCenterOf(pos));
			results.add(new ScannerItem(pos, null, distance, marker.name()));
		}
		return results;
	}

	/** Every other player in the dimension, distance-sorted but never range-filtered - vanilla syncs them all regardless. */
	private static List<ScannerItem> scanPlayers(LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		List<ScannerItem> results = new ArrayList<>();
		for (Player other : player.level().players()) {
			if (other == player || !other.isAlive()) {
				continue;
			}
			double distance = eye.distanceTo(other.getBoundingBox().getCenter());
			results.add(new ScannerItem(null, other, distance, null));
		}
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	/**
	 * Floods out from {@code start} through 26-connected members of {@code candidates},
	 * collecting every position into {@code cluster} and returning the lowest one - the trunk
	 * base for {@link #scanTrees}'s use, and the base of the stalk(s) for {@link
	 * #addBambooClusters}'s; {@link #addLiquidClusters} ignores the return value and just uses
	 * {@code cluster} itself.
	 */
	private static BlockPos floodFillCluster(BlockPos start, Set<BlockPos> candidates, Set<BlockPos> visited, Set<BlockPos> cluster) {
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		visited.add(start);
		cluster.add(start);
		BlockPos lowest = start;

		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			if (current.getY() < lowest.getY()) {
				lowest = current;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos neighbor = current.offset(dx, dy, dz);
						if (candidates.contains(neighbor) && visited.add(neighbor)) {
							cluster.add(neighbor);
							queue.add(neighbor);
						}
					}
				}
			}
		}
		return lowest;
	}

	/**
	 * Walks straight down from {@code lowest} through the real world - not the range-limited
	 * {@code logPositions} set {@link #floodFillCluster} searched - until the log column
	 * actually ends. {@code floodFillCluster} only ever sees log blocks that were in scan range
	 * to begin with, so its own "lowest" is really just "lowest block that happened to be in
	 * range" - for a tall tree scanned from a distance or from slightly elevated ground, that
	 * can sit a log or two above the trunk's true base, which is exactly the block {@link
	 * #target} needs to face to actually connect when chopping it.
	 */
	private static BlockPos lowestLog(Level level, BlockPos lowest) {
		BlockPos below = lowest.below();
		while (level.getBlockState(below).is(BlockTags.LOGS)) {
			lowest = below;
			below = lowest.below();
		}
		return lowest;
	}

	/** Checks the cluster's bounding box, expanded by a small margin, for any leaves at all. */
	private static boolean hasNearbyLeaves(Level level, Set<BlockPos> cluster) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : cluster) {
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());
		}

		BlockPos from = new BlockPos(minX - LEAF_SEARCH_MARGIN, minY - LEAF_SEARCH_MARGIN, minZ - LEAF_SEARCH_MARGIN);
		BlockPos to = new BlockPos(maxX + LEAF_SEARCH_MARGIN, maxY + LEAF_SEARCH_MARGIN, maxZ + LEAF_SEARCH_MARGIN);
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
				return true;
			}
		}
		return false;
	}

	/** {@code label} is only ever set for Markers - everything else derives its narrated name from the block/entity itself. */
	private record ScannerItem(BlockPos blockPos, Entity entity, double distance, String label) {
	}
}
