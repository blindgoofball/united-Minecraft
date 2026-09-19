package com.nibblenerds.unitedminecraft.fabric;

import com.nibblenerds.unitedminecraft.client.ClientHooks;
import com.nibblenerds.unitedminecraft.platform.Platform;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric's entrypoint. Installing the {@link Platform} has to come first - the config load
 * inside {@link ClientHooks#init} immediately asks it for the config directory.
 */
public class UnitedMinecraftFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Platform.set(new FabricPlatform());
		ClientHooks.init();
		FabricEventBridge.register();
	}
}
