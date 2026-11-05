package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Narrates what a trial chamber vault hands out. Vanilla says nothing about it.
 *
 * <p>An unlocked vault ejects each reward as an ordinary {@link ItemEntity} just above the
 * block, one at a time, and the player collects them like any other drop. A player standing
 * next to the vault - the normal case - collects each one the instant it spawns, so the client
 * can receive the item entity and its removal within the same tick and never has it around at
 * the end of a tick to be seen (confirmed by logging a real vault: the entity count near the
 * player never changed while the vault was ejecting). Watching for the entities alone therefore
 * misses almost everything.
 *
 * <p>So the main signal is the player's own inventory: while a vault within reach is in its
 * ejecting state (the {@link VaultBlock#STATE} block property is synced to the client), any
 * item that appears in the inventory is narrated. Items that do linger - out of reach, or the
 * inventory is full - are caught by the second path, which looks for a brand new item entity
 * with a vault just below it. An item announced that way is remembered so picking it up
 * afterwards, while the vault is still ejecting, is not announced a second time.
 */
public final class VaultRewardController {
	// How far from the player a vault still counts as the one dispensing to them.
	private static final int VAULT_REACH = 6;

	// Rewards can be seen from a little way off, e.g. while backing away from the vault.
	private static final double ITEM_RADIUS = 8.0;

	// Ticks of age within which an item entity still counts as just ejected.
	private static final int MAX_AGE_TICKS = 2;

	// The item spawns ~1.2 blocks above the vault's bottom and then moves; look this far down
	// for the vault block.
	private static final int VAULT_SEARCH_DEPTH = 2;

	// Keep watching the inventory briefly after the vault leaves its ejecting state: the last
	// item is spawned as it does, and its pickup lands a tick or two later.
	private static final int GRACE_TICKS = 40;

	/** A stack kind and how many of it, for comparing inventory snapshots. */
	private static final class Tally {
		final ItemStack proto;
		int count;

		Tally(ItemStack proto, int count) {
			this.proto = proto.copyWithCount(1);
			this.count = count;
		}
	}

	private static List<Tally> snapshot = new ArrayList<>();
	// Item entities already announced via the entity path, so their later pickup is not repeated.
	private static final List<Tally> announcedEntities = new ArrayList<>();
	private static final List<Integer> announcedEntityIds = new ArrayList<>();
	private static int graceTicks;

	private VaultRewardController() {
	}

	public static void reset() {
		snapshot = new ArrayList<>();
		announcedEntities.clear();
		announcedEntityIds.clear();
		graceTicks = 0;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (vaultEjectingNearby(player)) {
			graceTicks = GRACE_TICKS;
		}
		List<Tally> current = tallyInventory(player);
		if (graceTicks > 0) {
			graceTicks--;
			announceInventoryGains(client, player, current);
			announceLingeringItems(client, player);
		} else {
			announcedEntities.clear();
			announcedEntityIds.clear();
		}
		snapshot = current;
	}

	private static boolean vaultEjectingNearby(LocalPlayer player) {
		BlockPos base = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
				base.offset(-VAULT_REACH, -VAULT_REACH, -VAULT_REACH), base.offset(VAULT_REACH, VAULT_REACH, VAULT_REACH))) {
			BlockState state = player.level().getBlockState(pos);
			if (state.is(Blocks.VAULT) && state.getValue(VaultBlock.STATE) == VaultState.EJECTING) {
				return true;
			}
		}
		return false;
	}

	private static List<Tally> tallyInventory(LocalPlayer player) {
		List<Tally> tallies = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				add(tallies, stack, stack.getCount());
			}
		}
		return tallies;
	}

	private static void add(List<Tally> tallies, ItemStack stack, int count) {
		for (Tally tally : tallies) {
			if (ItemStack.isSameItemSameComponents(tally.proto, stack)) {
				tally.count += count;
				return;
			}
		}
		tallies.add(new Tally(stack, count));
	}

	private static int countOf(List<Tally> tallies, ItemStack stack) {
		for (Tally tally : tallies) {
			if (ItemStack.isSameItemSameComponents(tally.proto, stack)) {
				return tally.count;
			}
		}
		return 0;
	}

	/**
	 * Narrates each item kind whose total went up since last tick, minus anything already
	 * announced from its entity. Queued: rewards arrive on their own schedule and shouldn't cut
	 * off other speech.
	 */
	private static void announceInventoryGains(Minecraft client, LocalPlayer player, List<Tally> current) {
		for (Tally tally : current) {
			int gained = tally.count - countOf(snapshot, tally.proto);
			if (gained <= 0) {
				continue;
			}
			for (Tally seen : announcedEntities) {
				if (gained > 0 && ItemStack.isSameItemSameComponents(seen.proto, tally.proto)) {
					int covered = Math.min(gained, seen.count);
					seen.count -= covered;
					gained -= covered;
				}
			}
			if (gained > 0) {
				client.getNarrator().saySystemQueued(
						ItemDescriptions.describe(tally.proto.copyWithCount(gained), player));
			}
		}
	}

	private static void announceLingeringItems(Minecraft client, LocalPlayer player) {
		AABB box = player.getBoundingBox().inflate(ITEM_RADIUS);
		for (Entity entity : player.level().getEntities((Entity) null, box,
				e -> e instanceof ItemEntity item && item.tickCount <= MAX_AGE_TICKS)) {
			ItemEntity item = (ItemEntity) entity;
			if (announcedEntityIds.contains(item.getId()) || !isAboveVault(player, item)) {
				continue;
			}
			announcedEntityIds.add(item.getId());
			ItemStack stack = item.getItem();
			add(announcedEntities, stack, stack.getCount());
			client.getNarrator().saySystemQueued(ItemDescriptions.describe(stack, player));
		}
	}

	private static boolean isAboveVault(LocalPlayer player, ItemEntity item) {
		BlockPos pos = item.blockPosition();
		for (int i = 0; i <= VAULT_SEARCH_DEPTH; i++) {
			if (player.level().getBlockState(pos.below(i)).is(Blocks.VAULT)) {
				return true;
			}
		}
		return false;
	}
}
