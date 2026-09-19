package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.speech.PrismController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
		MenuAccessibilityController.recheckInitialSlotNarration(client);
		MenuAccessibilityController.clearStrayFocus(client);
		MenuAccessibilityController.openPendingSearchPrompt(client);
		CreativeInventoryController.recheckSearchResults(client);
	}

	/**
	 * A screen finished {@code init()}. Fires for a same-instance re-init too, which both
	 * controllers depend on - see their own doc comments.
	 *
	 * <p>{@link CreativeModeInventoryScreen} is itself an {@link AbstractContainerScreen}, so
	 * a creative screen legitimately goes through both controllers; they divide the screen
	 * between them at key-handling time rather than here.
	 */
	public static void onScreenInit(Screen screen) {
		if (screen instanceof AbstractContainerScreen<?> container) {
			MenuAccessibilityController.onScreenInit(container);
		}
		if (screen instanceof CreativeModeInventoryScreen creative) {
			CreativeInventoryController.onScreenInit(creative);
		}
	}

	/** A screen was removed - either really closed, or swapped out for another. */
	public static void onScreenRemoved(Screen screen) {
		if (screen instanceof AbstractContainerScreen<?> container) {
			MenuAccessibilityController.onScreenRemoved(container);
		}
	}

	/**
	 * A key was pressed while {@code screen} is open, before vanilla sees it.
	 *
	 * @return {@code true} to let vanilla handle the key as normal, {@code false} if this mod
	 *         consumed it. This is the same polarity the controllers' own {@code handleKey}
	 *         methods use, and the same one Fabric's {@code allowKeyPress} expects; a loader
	 *         whose event is phrased as "cancel" instead inverts it in its own bridge.
	 *
	 * <p>Both controllers get a look on a creative screen, Menu first, stopping at the first
	 * one that consumes - exactly what registering both listeners on the same per-screen event
	 * used to do. Each defers to the other for the parts of the screen it doesn't own
	 * ({@code isHandledByCreativeItemGrid} / {@code isItemGridTab}), so the order is load-bearing.
	 */
	public static boolean onScreenKeyPressed(Screen screen, KeyEvent event) {
		if (screen instanceof AbstractContainerScreen<?> container
				&& !MenuAccessibilityController.handleKey(container, event)) {
			return false;
		}
		if (screen instanceof CreativeModeInventoryScreen creative
				&& !CreativeInventoryController.handleKey(creative, event)) {
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

	/** The client is shutting down. */
	public static void onClientStopping() {
		PrismController.shutdownIfLoaded();
	}
}
