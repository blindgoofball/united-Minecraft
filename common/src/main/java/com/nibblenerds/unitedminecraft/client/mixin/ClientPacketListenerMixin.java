package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.ClientHooks;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world spawn point is how two worlds on one address are told apart. Injected at the tail
 * rather than the head because a packet is first handed to the network thread, which bounces it
 * to the client thread from the top of the handler; only the second, client-thread run reaches
 * the tail.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleSetSpawn", at = @At("TAIL"))
	private void unitedMinecraft$rememberWorldSpawn(ClientboundSetDefaultSpawnPositionPacket packet, CallbackInfo ci) {
		ClientHooks.onWorldSpawn(packet.respawnData());
	}
}
