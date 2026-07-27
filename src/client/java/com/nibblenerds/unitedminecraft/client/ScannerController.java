package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The "scanner": Home/End cycle a category, Page Up/Down cycle the nearest items within
 * it, and Enter targets whichever item is currently selected. Doesn't function while
 * build mode is active (Page Up/Down already belong to the build cursor there).
 *
 * <p>Enter on a block just aims the player at it. Enter on a mob starts a continuous
 * lock-on that keeps facing it every tick until Delete (stop lock) is pressed - which
 * takes over rotation entirely while active, so build mode and normal camera turning are
 * blocked until it's released. Shift+Enter instead auto-walks there via
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
 * wandering out of range still just drops the lock like normal.
 *
 * <p>Drawing a bow while locked on switches from {@link CameraUtil#aimAt} (a flat, straight-line
 * look) to {@link CameraUtil#aimBallisticAt}, which accounts for arrow drop - flat aim only
 * actually lands a hit at point-blank range, since gravity pulls a real arrow down in flight.
 */
public final class ScannerController {
	private static final double SCAN_RANGE = 32.0;
	private static final int LEAF_SEARCH_MARGIN = 2;

	private static final ScannerCategory[] CATEGORIES = ScannerCategory.values();

	private static int categoryIndex = -1;
	private static List<ScannerItem> items = List.of();
	private static int itemIndex;

	private static Entity lockedEntity;

	private ScannerController() {
	}

	public static boolean isLocked() {
		return lockedEntity != null;
	}

	public static void reset() {
		categoryIndex = -1;
		items = List.of();
		itemIndex = 0;
		lockedEntity = null;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		boolean prevCategory = ClientKeyBindings.SCANNER_PREV_CATEGORY.consumeClick();
		boolean nextCategory = ClientKeyBindings.SCANNER_NEXT_CATEGORY.consumeClick();
		boolean pageDown = ClientKeyBindings.PAGE_DOWN.consumeClick();
		boolean pageUp = ClientKeyBindings.PAGE_UP.consumeClick();
		boolean targetPressed = ClientKeyBindings.SCANNER_TARGET.consumeClick();
		boolean stopPressed = ClientKeyBindings.SCANNER_STOP_LOCK.consumeClick();
		boolean coordinatesPressed = ClientKeyBindings.SCANNER_COORDINATES.consumeClick();

		if (isLocked()) {
			if (stopPressed) {
				stopLock(client);
			}
			if (coordinatesPressed) {
				announceCoordinates(client, lockedEntity.blockPosition());
			}
			return;
		}
		if (BuildModeController.isActive()) {
			return;
		}

		if (prevCategory) {
			switchCategory(client, player, -1);
		}
		if (nextCategory) {
			switchCategory(client, player, 1);
		}
		if (pageDown) {
			stepItem(client, player, -1);
		}
		if (pageUp) {
			stepItem(client, player, 1);
		}
		if (targetPressed) {
			target(client, player);
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
			Vec3 target = lockedEntity.getBoundingBox().getCenter();
			float drawPower = drawingBowPower(player);
			// Below BowItem's own 0.1 firing threshold there's not enough draw speed to solve a
			// sane arc from yet - a barely-started draw would otherwise snap the pitch to a wild
			// extreme. Plain aim is a fine placeholder until the draw is far enough along to mean
			// something.
			if (drawPower >= 0.1f) {
				CameraUtil.aimBallisticAt(player, target, drawPower * 3.0f);
			} else {
				CameraUtil.aimAt(player, target);
			}
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

	/** Current bow draw power (0..1, matching {@link BowItem#getPowerForTime}), or 0 if not drawing a bow. */
	private static float drawingBowPower(LocalPlayer player) {
		if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof BowItem)) {
			return 0.0f;
		}
		return BowItem.getPowerForTime(player.getTicksUsingItem());
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

		Component message = category.label().append(Component.literal(": ")).append(
				items.isEmpty()
						? Component.translatable("united_minecraft.narrate.scanner_empty")
						: describeItem(category, items.get(0), player));
		client.getNarrator().saySystemNow(message);
	}

	private static void stepItem(Minecraft client, LocalPlayer player, int direction) {
		if (categoryIndex == -1 || items.isEmpty()) {
			return;
		}
		itemIndex = Math.floorMod(itemIndex + direction, items.size());
		client.getNarrator().saySystemNow(describeItem(CATEGORIES[categoryIndex], items.get(itemIndex), player));
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
			targetBlock(client, player, item.blockPos(), walkThere);
		}
	}

	private static void targetEntity(Minecraft client, LocalPlayer player, Entity entity, boolean walkThere) {
		if (!entity.isAlive()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_target_gone"));
			return;
		}
		if (walkThere) {
			AutoWalkController.start(client, player, entity.blockPosition(), entity.getDisplayName());
			return;
		}
		lockedEntity = entity;
		CameraUtil.aimAt(player, entity.getBoundingBox().getCenter());
		client.getNarrator().saySystemNow(
				Component.translatable("united_minecraft.narrate.scanner_lock_started", entity.getDisplayName()));
	}

	private static void targetEntityOnce(Minecraft client, LocalPlayer player, Entity entity, boolean walkThere) {
		if (!entity.isAlive()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.scanner_target_gone"));
			return;
		}
		if (walkThere) {
			AutoWalkController.start(client, player, entity.blockPosition(), entity.getDisplayName());
			return;
		}
		CameraUtil.aimAt(player, entity.getBoundingBox().getCenter());
		client.getNarrator().saySystemNow(
				Component.translatable("united_minecraft.narrate.scanner_facing", entity.getDisplayName()));
	}

	private static void targetBlock(Minecraft client, LocalPlayer player, BlockPos pos, boolean walkThere) {
		if (walkThere) {
			AutoWalkController.start(client, player, pos, player.level().getBlockState(pos).getBlock().getName());
			return;
		}
		CameraUtil.aimAt(player, Vec3.atCenterOf(pos));
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.scanner_facing", player.level().getBlockState(pos).getBlock().getName()));
	}

	private static ScannerItem currentItem() {
		if (categoryIndex == -1 || items.isEmpty() || itemIndex >= items.size()) {
			return null;
		}
		return items.get(itemIndex);
	}

	private static Component describeItem(ScannerCategory category, ScannerItem item, LocalPlayer player) {
		int distance = (int) Math.round(item.distance());
		Component direction = CameraUtil.compassDirectionTo(player.position(), targetPosition(item));
		return Component.translatable("united_minecraft.narrate.scanner_item",
				itemName(category, item, player.level()), distance, direction);
	}

	private static Vec3 targetPosition(ScannerItem item) {
		return item.entity() != null ? item.entity().getBoundingBox().getCenter() : Vec3.atCenterOf(item.blockPos());
	}

	private static Component itemName(ScannerCategory category, ScannerItem item, Level level) {
		if (item.entity() != null) {
			if (category == ScannerCategory.ITEMS && item.entity() instanceof ItemEntity itemEntity) {
				ItemStack stack = itemEntity.getItem();
				return stack.getCount() > 1
						? Component.literal(stack.getCount() + " ").append(stack.getHoverName())
						: stack.getHoverName();
			}
			return item.entity().getDisplayName();
		}
		return category == ScannerCategory.TREES
				? describeTree(level, item.blockPos())
				: level.getBlockState(item.blockPos()).getBlock().getName();
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

	private static List<ScannerItem> scan(ScannerCategory category, LocalPlayer player) {
		return switch (category) {
			case INTERACTABLES -> scanBlocks(player, (pos, state) -> state.getMenuProvider(player.level(), pos) != null);
			case MECHANISMS -> scanBlocks(player, (pos, state) -> isMechanism(state.getBlock()));
			case ITEMS -> scanEntities(player, entity -> entity instanceof ItemEntity);
			case TREES -> scanTrees(player);
			// Deliberately not x-ray: OreDetection.isExposed only counts ore already
			// bordering air or a fluid, i.e. actually visible through a gap, not buried.
			case ORES -> scanBlocks(player, (pos, state) ->
					OreDetection.isValuableOre(state) && OreDetection.isExposed(player.level(), pos, player.getEyePosition()));
			case PASSIVE_MOBS -> scanEntities(player, entity -> entity instanceof Animal);
			case HOSTILE_MOBS -> scanEntities(player, entity -> entity instanceof Enemy);
		};
	}

	private static boolean isMechanism(Block block) {
		return block instanceof LeverBlock
				|| block instanceof ButtonBlock
				|| block instanceof DoorBlock
				|| block instanceof TrapDoorBlock
				|| block instanceof FenceGateBlock;
	}

	private static List<ScannerItem> scanBlocks(LocalPlayer player, BiPredicate<BlockPos, BlockState> predicate) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockPos center = player.blockPosition();
		int r = (int) SCAN_RANGE;

		List<ScannerItem> results = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			BlockState state = level.getBlockState(pos);
			if (!predicate.test(pos, state)) {
				continue;
			}
			double distance = eye.distanceTo(Vec3.atCenterOf(pos));
			if (distance <= SCAN_RANGE) {
				results.add(new ScannerItem(pos.immutable(), null, distance));
			}
		}
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	private static List<ScannerItem> scanEntities(LocalPlayer player, Predicate<Entity> predicate) {
		Vec3 eye = player.getEyePosition();
		AABB box = player.getBoundingBox().inflate(SCAN_RANGE);

		List<ScannerItem> results = new ArrayList<>();
		for (Entity entity : player.level().getEntities(player, box, e -> e.isAlive() && predicate.test(e))) {
			double distance = eye.distanceTo(entity.getBoundingBox().getCenter());
			if (distance <= SCAN_RANGE) {
				results.add(new ScannerItem(null, entity, distance));
			}
		}
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	private static List<ScannerItem> scanTrees(LocalPlayer player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockPos center = player.blockPosition();
		int r = (int) SCAN_RANGE;

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
			BlockPos trunkBase = floodFillCluster(start, logPositions, visited, cluster);
			if (!hasNearbyLeaves(level, cluster)) {
				// A log cluster with no leaves anywhere near it is more likely a player-built
				// structure (cabin, bridge, etc.) than an actual tree.
				continue;
			}
			double distance = eye.distanceTo(Vec3.atCenterOf(trunkBase));
			if (distance <= SCAN_RANGE) {
				results.add(new ScannerItem(trunkBase, null, distance));
			}
		}
		results.sort(Comparator.comparingDouble(ScannerItem::distance));
		return results;
	}

	/**
	 * Floods out from {@code start} through 26-connected log blocks, collecting every
	 * position into {@code cluster} and returning the lowest one as the trunk base.
	 */
	private static BlockPos floodFillCluster(BlockPos start, Set<BlockPos> logPositions, Set<BlockPos> visited, Set<BlockPos> cluster) {
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
						if (logPositions.contains(neighbor) && visited.add(neighbor)) {
							cluster.add(neighbor);
							queue.add(neighbor);
						}
					}
				}
			}
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

	private record ScannerItem(BlockPos blockPos, Entity entity, double distance) {
	}
}
