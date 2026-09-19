package com.nibblenerds.unitedminecraft.neoforge;

import java.nio.file.Path;

import com.nibblenerds.unitedminecraft.platform.Platform;

import net.neoforged.fml.loading.FMLPaths;

/** NeoForge's answers to the two directories shared code asks for. */
public final class NeoForgePlatform implements Platform {
	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
	}
}
