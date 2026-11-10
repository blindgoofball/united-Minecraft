package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.ClientHooks;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both packets carry the world's obfuscated seed. Injected at the tail rather than the head
 * because a packet is first handed to the network thread, which bounces it to the client thread
 * from the top of the handler; only the second, client-thread run reaches the tail.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleLogin", at = @At("TAIL"))
	private void unitedMinecraft$rememberLoginSeed(ClientboundLoginPacket packet, CallbackInfo ci) {
		ClientHooks.onWorldSpawnInfo(packet.commonPlayerSpawnInfo());
	}

	@Inject(method = "handleRespawn", at = @At("TAIL"))
	private void unitedMinecraft$rememberRespawnSeed(ClientboundRespawnPacket packet, CallbackInfo ci) {
		ClientHooks.onWorldSpawnInfo(packet.commonPlayerSpawnInfo());
	}
}
