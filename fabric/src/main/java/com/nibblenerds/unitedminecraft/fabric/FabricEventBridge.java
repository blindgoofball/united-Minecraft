package com.nibblenerds.unitedminecraft.fabric;

import com.nibblenerds.unitedminecraft.client.ClientHooks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.world.InteractionResult;

/**
 * Subscribes Fabric's events and forwards each straight to {@link ClientHooks}. All the
 * decisions about what to do live there; this class only translates between Fabric's event
 * shapes and plain calls, so that porting to another loader means writing a second file
 * like this one rather than touching any of the mod's actual behavior.
 */
public final class FabricEventBridge {
	private FabricEventBridge() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientHooks::onClientTick);

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			ClientHooks.onScreenInit(screen);
			// Confirmed via ScreenMixin#beforeInit bytecode (fabric-screen-api-v1): vanilla's
			// Screen.init(int,int) unconditionally reassigns this screen's Fabric key-press and
			// removal events to brand-new, listener-less Event objects at the HEAD of every
			// single call - not just a screen's first-ever init. So these registrations must run
			// on every AFTER_INIT firing, including a same-instance re-init (e.g. returning from
			// the recipe-book search prompt) - skipping it there (as an earlier version of this
			// fix did, to avoid what looked like a double-registration risk) instead left the
			// freshly recreated event with zero listeners, silently killing all key handling on
			// this screen from that point on. Since the events are genuinely fresh each time,
			// doing this unconditionally cannot double up a listener - there's nothing there yet
			// to double.
			//
			// Fabric phrases this as "allow": returning false blocks the key from going any
			// further, which is the same polarity onScreenKeyPressed uses.
			ScreenKeyboardEvents.allowKeyPress(screen).register(ClientHooks::onScreenKeyPressed);
			ScreenEvents.remove(screen).register(ClientHooks::onScreenRemoved);
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			ClientHooks.onEntityInteract(player, level, hand, entity);
			// Never overrides vanilla's own result - this only ever adds a sound alongside
			// whatever the real interaction does.
			return InteractionResult.PASS;
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientHooks.onClientStopping());
	}
}
