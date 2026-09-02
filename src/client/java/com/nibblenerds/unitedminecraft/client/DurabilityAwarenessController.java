package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Proactive durability warnings for worn armor and held items: narrates once when a
 * damageable stack crosses each configured threshold, and once when a stack actually
 * breaks. Runs regardless of which mode owns rotation — a pick breaking mid-mine
 * during Auto-Walk is still worth knowing about.
 *
 * <p>Each slot + threshold combination fires only once per below-threshold window.
 * When the item is repaired (Mending XP, anvil use) and the fraction rises back above
 * the warning threshold, the flags auto-rearm on the next tick — no external repair
 * hook required.
 */
public final class DurabilityAwarenessController {
	// 0–3 = armor (boots → helmet, matching EquipmentSlot FEET, LEGS, CHEST, HEAD),
	// 4 = main hand, 5 = off hand.
	private static final int SLOT_COUNT = 6;
	private static final int BOOTS = 0, LEGGINGS = 1, CHESTPLATE = 2, HELMET = 3, MAIN_HAND = 4, OFF_HAND = 5;

	private static final String[] SLOT_KEYS = {
			"united_minecraft.menu.slot.boots",
			"united_minecraft.menu.slot.leggings",
			"united_minecraft.menu.slot.chestplate",
			"united_minecraft.menu.slot.helmet",
			"united_minecraft.durability_slot.held_item",
			"united_minecraft.menu.slot.offhand",
	};

	private static final int FRACTION_PRECISION = 100; // percent scale to avoid floating-point drift

	private static final boolean[] warnedWarning = new boolean[SLOT_COUNT];
	private static final boolean[] warnedCritical = new boolean[SLOT_COUNT];
	private static final ItemStack[] lastStack = new ItemStack[SLOT_COUNT];

	static {
		reset();
	}

	private DurabilityAwarenessController() {
	}

	public static void register() {
		// AccessibilityTickHandler calls tick() directly — no separate event registration.
	}

	public static void reset() {
		for (int i = 0; i < SLOT_COUNT; i++) {
			warnedWarning[i] = false;
			warnedCritical[i] = false;
			lastStack[i] = ItemStack.EMPTY;
		}
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!UnitedMinecraftConfig.get().durabilityAwarenessEnabled) {
			return;
		}

		// World change between ticks (e.g. dimension switch) — re-arm so warnings fire in
		// the new session. client.level is the currently-loaded level, which lags player.level
		// by at most one tick after a portal, so this catches it reliably.
		if (client.level == null || client.level != player.level()) {
			reset();
			return;
		}

		checkSlot(client, player, BOOTS, player.getItemBySlot(EquipmentSlot.FEET));
		checkSlot(client, player, LEGGINGS, player.getItemBySlot(EquipmentSlot.LEGS));
		checkSlot(client, player, CHESTPLATE, player.getItemBySlot(EquipmentSlot.CHEST));
		checkSlot(client, player, HELMET, player.getItemBySlot(EquipmentSlot.HEAD));
		checkSlot(client, player, MAIN_HAND, player.getMainHandItem());
		checkSlot(client, player, OFF_HAND, player.getOffhandItem());
	}

	private static void checkSlot(Minecraft client, LocalPlayer player, int slot, ItemStack current) {
		ItemStack previous = lastStack[slot];

		// Minecraft can remove or replace a broken stack before this poll observes damage == max.
		// Treat max - 1 as the final observable state, but only announce it when the stack leaves
		// the slot so manually switching away from a nearly-broken item is not announced as a break.
		boolean previousWasNearlyBroken = !previous.isEmpty() && previous.isDamageableItem()
				&& previous.getMaxDamage() > 0
				&& previous.getDamageValue() >= previous.getMaxDamage() - 1;
		boolean stackLeftSlot = current.isEmpty()
				|| (!previous.isEmpty() && previous.getItem() != current.getItem());
		if (previousWasNearlyBroken && stackLeftSlot) {
			Component itemName = previous.getHoverName();
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.durability_broke", itemName));
		}

		// A newly equipped item must start with a fresh warning state.
		if (!previous.isEmpty() && !current.isEmpty() && previous.getItem() != current.getItem()) {
			warnedWarning[slot] = false;
			warnedCritical[slot] = false;
		}

		if (current.isEmpty()) {
			warnedWarning[slot] = false;
			warnedCritical[slot] = false;
			lastStack[slot] = ItemStack.EMPTY;
			return;
		}

		// Not damageable or no durability data — nothing to track.
		if (!current.isDamageableItem() || current.getMaxDamage() <= 0) {
			lastStack[slot] = current.copy();
			return;
		}

		int damage = current.getDamageValue();
		int max = current.getMaxDamage();
		if (max <= 0) {
			lastStack[slot] = current.copy();
			return;
		}

		int remaining = max - damage;
		// Percent-remaining on a fixed-point scale so the comparison stays stable across
		// integer division and matches what the settings screen's slider labels show.
		int pctRemaining = remaining * FRACTION_PRECISION / max;

		// Auto-rearm: if the stack has healed back above the warning threshold, clear both
		// flags so the next genuine crossing gets narrated. This is what makes Mending work
		// without any external "on repair" hook — the tick-based poll sees the healed state
		// and re-arms on its own.
		int warningThreshold = UnitedMinecraftConfig.get().durabilityWarningThreshold;
		int criticalThreshold = UnitedMinecraftConfig.get().durabilityCriticalThreshold;
		if (pctRemaining > warningThreshold) {
			warnedWarning[slot] = false;
			warnedCritical[slot] = false;
		} else if (pctRemaining > criticalThreshold) {
			// Recovered above critical but still below warning — only the "getting low"
			// warning can still fire; the more urgent "about to break" is no longer relevant.
			warnedCritical[slot] = false;
		}

		// Check the more urgent threshold first so a sharp break (e.g. 15% → 0 in one mining
		// session with Unending off) narrates "about to break" rather than firing both.
		if (!warnedCritical[slot] && pctRemaining <= criticalThreshold) {
			warnedCritical[slot] = true;
			warnedWarning[slot] = true; // supersedes — no need to also say "getting low"
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.durability_critical",
					Component.translatable(SLOT_KEYS[slot])));
		} else if (!warnedWarning[slot] && pctRemaining <= warningThreshold) {
			warnedWarning[slot] = true;
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.durability_warning",
					Component.translatable(SLOT_KEYS[slot])));
		}

		lastStack[slot] = current.copy();
	}
}
