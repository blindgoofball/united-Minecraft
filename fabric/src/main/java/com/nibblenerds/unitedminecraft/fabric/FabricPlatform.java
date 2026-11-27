package com.nibblenerds.unitedminecraft.fabric;

import java.nio.file.Path;

import com.nibblenerds.unitedminecraft.platform.Platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric's answers to what shared code asks of the loader. */
public final class FabricPlatform implements Platform {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	@Override
	public boolean serverAccepts(CustomPacketPayload.Type<?> type) {
		return ClientPlayNetworking.canSend(type);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
