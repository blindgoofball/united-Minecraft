package com.nibblenerds.unitedminecraft.neoforge;

import com.nibblenerds.unitedminecraft.client.ClientHooks;
import com.nibblenerds.unitedminecraft.platform.Platform;
import com.nibblenerds.unitedminecraft.structure.StructuresNearbyPayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.registration.HandlerThread;

/**
 * NeoForge's client entrypoint. Installing the {@link Platform} has to come first - the config
 * load inside {@link ClientHooks#init} immediately asks it for the config directory. The
 * server-side part of structure voices lives in {@link UnitedMinecraftNeoForgeCommon}.
 */
@Mod(value = "united_minecraft", dist = Dist.CLIENT)
public class UnitedMinecraftNeoForge {
	public UnitedMinecraftNeoForge(IEventBus modBus) {
		Platform.set(new NeoForgePlatform());
		ClientHooks.init();
		NeoForgeEventBridge.register(NeoForge.EVENT_BUS);
		// The payload itself is registered by UnitedMinecraftNeoForgeCommon.
		modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(
				StructuresNearbyPayload.TYPE, HandlerThread.MAIN, (payload, context) -> ClientHooks.onStructuresNearby(payload)));
	}
}
