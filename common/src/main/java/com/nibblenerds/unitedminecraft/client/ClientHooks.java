package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.menu.AdvancementsController;
import com.nibblenerds.unitedminecraft.client.menu.BundleSelection;
import com.nibblenerds.unitedminecraft.client.menu.SlotChangeNarration;
import com.nibblenerds.unitedminecraft.client.menu.SlotDescription;
import com.nibblenerds.unitedminecraft.client.menu.SlotLetterJump;
import com.nibblenerds.unitedminecraft.client.scanner.MapMarkerController;
import com.nibblenerds.unitedminecraft.client.scanner.MarkerShareController;
import com.nibblenerds.unitedminecraft.client.scanner.NamedBlockController;
import com.nibblenerds.unitedminecraft.client.scanner.SavedSearchController;
import com.nibblenerds.unitedminecraft.client.scanner.ScannerFilterController;
import com.nibblenerds.unitedminecraft.client.settings.RecommendedKeysPrompt;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import com.nibblenerds.unitedminecraft.client.keybind.ClientKeyBindings;
import com.nibblenerds.unitedminecraft.client.keybind.KeybindConfig;
import com.nibblenerds.unitedminecraft.client.speech.PrismController;
import com.nibblenerds.unitedminecraft.share.SharePayloads;
import com.nibblenerds.unitedminecraft.structure.StructuresNearbyPayload;
import com.nibblenerds.unitedminecraft.world.WorldIdPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.Level;

/**
 * Everything this mod does in response to the game, expressed as plain calls the loader
 * module makes. Nothing here registers an event or knows which loader is running - the
 * loader's own bridge subscribes to its native events and calls straight into these.
 *
 * <p>Ordering within each hook is deliberate and reproduces exactly what the separate
 * per-controller event registrations used to produce, since several of these controllers
 * cooperate through shared static state and would misbehave if reordered - see the
 * individual hooks.
 */
public final class ClientHooks {
	private ClientHooks() {
	}

	/** One-time setup. The loader module installs a {@code Platform} before calling this. */
	public static void init() {
		UnitedMinecraftConfig.load();
		AiConfig.load();
		PrismController.init();
		ClientKeyBindings.register();
		KeybindConfig.load();
	}

	/**
	 * End of a client tick. The order below is the order these were registered in as
	 * separate listeners, and matters: {@link AccessibilityTickHandler#onEndTick} refreshes
	 * every keybind's just-pressed state for the tick, so it has to run before anything that
	 * reads it.
	 */
	public static void onClientTick(Minecraft client) {
		AccessibilityTickHandler.onEndTick(client);
		TitleNarrationController.tick(client);
		RecommendedKeysPrompt.tick(client);
		SlotChangeNarration.tick(client);
		WorldMigration.tick(client);
	}

	/**
	 * A screen finished {@code init()}. Fires for a same-instance re-init too, which the
	 * controllers depend on - see their own doc comments.
	 */
	public static void onScreenInit(Screen screen) {
		enableNarratorOnFirstLaunch();
		RecommendedKeysPrompt.onScreenInit(screen);
		if (screen instanceof AdvancementsScreen advancements) {
			AdvancementsController.onScreenInit(advancements);
		}
	}

	private static boolean narratorCheckDone;

	/**
	 * Turns the vanilla narrator on the first time this mod ever runs, since every narration
	 * this mod does goes through it and it ships switched off - a new player would otherwise
	 * hear nothing until they found Ctrl+B or the Accessibility menu. Done on the first screen
	 * to initialize (the title screen, in practice) because the options and narrator both exist
	 * by then and it is still before the player can reach the main menu. Only ever once, recorded
	 * in the config, so a player who turns the narrator off later is never overridden.
	 */
	private static void enableNarratorOnFirstLaunch() {
		if (narratorCheckDone) {
			return;
		}
		narratorCheckDone = true;
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		if (config.narratorAutoEnableDone) {
			return;
		}
		config.narratorAutoEnableDone = true;
		UnitedMinecraftConfig.save();
		Minecraft client = Minecraft.getInstance();
		if (client.options.narrator().get() == NarratorStatus.OFF) {
			// Goes through the option's own update callback, so the narrator engine starts and
			// announces the change exactly as if it had been switched on in the menu.
			client.options.narrator().set(NarratorStatus.ALL);
			client.options.save();
		}
	}

	/** A screen was removed - either really closed, or swapped out for another. */
	public static void onScreenRemoved(Screen screen) {
		if (screen instanceof AdvancementsScreen) {
			AdvancementsController.onScreenRemoved();
		}
	}

	/**
	 * A key was pressed while {@code screen} is open, before vanilla sees it.
	 *
	 * @return {@code true} to let vanilla handle the key as normal, {@code false} if this mod
	 *         consumed it. This is the same polarity the controllers' own {@code handleKey}
	 *         methods use, and the same one Fabric's {@code allowKeyPress} expects; a loader
	 *         whose event is phrased as "cancel" instead inverts it in its own bridge.
	 */
	public static boolean onScreenKeyPressed(Screen screen, KeyEvent event) {
		if (screen instanceof AbstractContainerScreen<?> container
				&& (!SlotDescription.handleKey(container, event) || !BundleSelection.handleKey(container, event)
						|| !SlotLetterJump.handleKey(container, event))) {
			return false;
		}
		if (screen instanceof AdvancementsScreen advancements
				&& !AdvancementsController.handleKey(advancements, event)) {
			return false;
		}
		return true;
	}

	/**
	 * The player right-clicked an entity. Purely additive - this only ever plays a sound
	 * alongside whatever the real interaction does, so there's no result to hand back.
	 */
	public static void onEntityInteract(Player player, Level level, InteractionHand hand, Entity entity) {
		AnimalFeedingController.onUseEntity(player, level, hand, entity);
	}

	/**
	 * The server sent the structures near the player - see {@link StructuresNearbyPayload}. The
	 * loader's handler calls this on the client thread.
	 */
	public static void onStructuresNearby(StructuresNearbyPayload payload) {
		StructureVoiceController.onStructuresNearby(payload);
	}

	/** The server listed the other players who can be shared with - see {@link MarkerShareController}. */
	public static void onShareTargets(SharePayloads.Targets payload) {
		MarkerShareController.onTargets(payload);
	}

	/** Another player shared a marker with this one - see {@link MarkerShareController}. */
	public static void onShareOffer(SharePayloads.Offer payload) {
		MarkerShareController.onOffer(payload);
	}

	/**
	 * The server told the client where the world spawn is, on joining and on a change of world.
	 * Called from a mixin rather than a loader bridge, since the packet is vanilla's own.
	 */
	public static void onWorldSpawn(LevelData.RespawnData spawn) {
		ServerWorldFingerprint.onSpawn(spawn);
	}

	/** The server gave this world an id - see {@link WorldIdPayload}. */
	public static void onWorldId(WorldIdPayload payload) {
		ServerWorldId.onReceived(payload.id());
	}

	/**
	 * A singleplayer save folder was deleted. Called from a mixin on vanilla's own deletion, which
	 * both the world list and the hardcore-death prompt go through.
	 */
	public static void onSingleplayerWorldDeleted(String levelId) {
		MapMarkerController.onWorldDeleted(levelId);
		NamedBlockController.onWorldDeleted(levelId);
		ScannerFilterController.onWorldDeleted(levelId);
		SavedSearchController.onWorldDeleted(levelId);
	}

	/** The client is shutting down. */
	public static void onClientStopping() {
		PrismController.shutdownIfLoaded();
	}
}
