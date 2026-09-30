package com.nibblenerds.unitedminecraft.neoforge;

import com.nibblenerds.unitedminecraft.structure.StructureScanner;
import com.nibblenerds.unitedminecraft.structure.StructuresNearbyPayload;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * NeoForge's common (both-sides) entrypoint - the only part of the mod that runs on a server. It
 * powers structure voices: {@link StructureScanner} runs on the integrated server in single
 * player, and on a dedicated server when the mod is installed there too. The payload is
 * registered as optional, so players without the mod can still join such a server, and the mod
 * on a client can still join a server without it. The client-side handler is registered by
 * {@link UnitedMinecraftNeoForge}.
 */
@Mod("united_minecraft")
public class UnitedMinecraftNeoForgeCommon {
	private static final StructureScanner.Network NETWORK = new StructureScanner.Network() {
		@Override
		public boolean canSend(ServerPlayer player) {
			return player.connection.hasChannel(StructuresNearbyPayload.TYPE);
		}

		@Override
		public void send(ServerPlayer player, StructuresNearbyPayload payload) {
			PacketDistributor.sendToPlayer(player, payload);
		}
	};

	public UnitedMinecraftNeoForgeCommon(IEventBus modBus) {
		modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional()
				.playToClient(StructuresNearbyPayload.TYPE, StructuresNearbyPayload.STREAM_CODEC));
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> StructureScanner.tick(event.getServer(), NETWORK));
		NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> StructureScanner.clear());
	}
}
