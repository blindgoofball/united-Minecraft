package com.nibblenerds.unitedminecraft.fabric;

import java.nio.file.Path;

import com.nibblenerds.unitedminecraft.platform.Platform;

import net.fabricmc.loader.api.FabricLoader;

/** Fabric's answers to the two directories shared code asks for. */
public final class FabricPlatform implements Platform {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}
}
