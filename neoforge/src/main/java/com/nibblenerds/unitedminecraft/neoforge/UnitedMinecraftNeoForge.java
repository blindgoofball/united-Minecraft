package com.nibblenerds.unitedminecraft.neoforge;

import com.nibblenerds.unitedminecraft.client.ClientHooks;
import com.nibblenerds.unitedminecraft.platform.Platform;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge's entrypoint. Installing the {@link Platform} has to come first - the config load
 * inside {@link ClientHooks#init} immediately asks it for the config directory.
 */
@Mod(value = "united_minecraft", dist = Dist.CLIENT)
public class UnitedMinecraftNeoForge {
	public UnitedMinecraftNeoForge() {
		Platform.set(new NeoForgePlatform());
		ClientHooks.init();
		NeoForgeEventBridge.register(NeoForge.EVENT_BUS);
	}
}
