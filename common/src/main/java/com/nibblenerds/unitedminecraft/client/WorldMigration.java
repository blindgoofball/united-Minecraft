package com.nibblenerds.unitedminecraft.client;

import java.util.List;

import net.minecraft.client.Minecraft;

/**
 * Finds a multiplayer world the markers and block names Minecraft 26.3 saved for its server
 * address, once, when the player joins it. See {@link LegacyWorldSets} for how the old files are
 * found and why they cannot be matched to a world automatically.
 *
 * <ul>
 * <li>No old sets, or the world already has saved data of its own: nothing happens.
 * <li>One old set: it is the world's, as the bare-address files always were, and moves in without
 * a word.
 * <li>Several: {@link LegacyWorldScreen} asks which one this is. That repeats for each different
 * world joined on the address, until the sets are all claimed.
 * </ul>
 *
 * <p>Asked once per world per session: closing the question leaves the sets where they are, and it
 * comes back the next time the world is joined.
 */
public final class WorldMigration {
	private static String checked;

	private WorldMigration() {
	}

	public static void tick(Minecraft client) {
		if (client.level == null || client.gui.screen() != null) {
			return;
		}
		String worldId = WorldScopedStore.currentMultiplayerId(client);
		if (worldId == null || worldId.equals(checked)) {
			return;
		}
		checked = worldId;
		if (LegacyWorldSets.hasData(worldId)) {
			return;
		}
		List<LegacyWorldSets.LegacySet> sets = LegacyWorldSets.find(client.getCurrentServer().ip);
		if (sets.size() == 1) {
			LegacyWorldSets.claim(sets.get(0), worldId);
			WorldScopedStore.reloadAll();
		} else if (sets.size() > 1) {
			client.gui.setScreen(new LegacyWorldScreen(worldId, sets));
		}
	}

	public static void reset() {
		checked = null;
	}
}
