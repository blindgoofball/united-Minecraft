package com.nibblenerds.unitedminecraft.neoforge;

import com.nibblenerds.unitedminecraft.client.ClientHooks;

import net.minecraft.client.Minecraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Subscribes NeoForge's events and forwards each straight to {@link ClientHooks} - the
 * NeoForge counterpart of the Fabric module's bridge. All the decisions live in ClientHooks;
 * this only translates event shapes.
 *
 * <p>Checked against the patched sources rather than assumed, since two of these differ in
 * shape from Fabric's equivalents:
 *
 * <ul>
 * <li>{@code Gui#setScreen} posts {@link ScreenEvent.Closing} immediately before calling the
 *     outgoing screen's {@code removed()}, under the same {@code screen != old} condition
 *     vanilla uses - so it fires on a genuine close and on a swap alike, matching what the
 *     Fabric side gets from the removal event.
 * <li>{@code screen.init(...)} at the end of that same method is <em>not</em> guarded by
 *     {@code screen != old}, so {@link ScreenEvent.Init.Post} still fires for a same-instance
 *     re-init - which the menu controllers depend on.
 * </ul>
 *
 * <p>Unlike Fabric, these are global bus events rather than per-screen ones, so they are
 * registered once here and dispatched on screen type inside ClientHooks.
 */
public final class NeoForgeEventBridge {
	private NeoForgeEventBridge() {
	}

	public static void register(IEventBus bus) {
		bus.addListener(ClientTickEvent.Post.class,
				event -> ClientHooks.onClientTick(Minecraft.getInstance()));

		bus.addListener(ScreenEvent.Init.Post.class,
				event -> ClientHooks.onScreenInit(event.getScreen()));

		bus.addListener(ScreenEvent.Closing.class,
				event -> ClientHooks.onScreenRemoved(event.getScreen()));

		bus.addListener(ScreenEvent.KeyPressed.Pre.class, event -> {
			// NeoForge phrases this as "cancel" - cancelling stops the key going any further -
			// where onScreenKeyPressed returns true to let vanilla have it, the polarity Fabric's
			// allowKeyPress uses. Hence the inversion here rather than in shared code.
			if (!ClientHooks.onScreenKeyPressed(event.getScreen(), event.getKeyEvent())) {
				event.setCanceled(true);
			}
		});

		bus.addListener(PlayerInteractEvent.EntityInteract.class,
				event -> ClientHooks.onEntityInteract(
						event.getEntity(), event.getLevel(), event.getHand(), event.getTarget()));

		bus.addListener(ClientStoppingEvent.class, event -> ClientHooks.onClientStopping());
	}
}
