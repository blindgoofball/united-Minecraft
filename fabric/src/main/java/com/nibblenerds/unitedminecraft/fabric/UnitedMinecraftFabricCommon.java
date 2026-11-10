package com.nibblenerds.unitedminecraft.fabric;

import com.nibblenerds.unitedminecraft.share.ShareCommand;
import com.nibblenerds.unitedminecraft.share.ShareRelay;
import com.nibblenerds.unitedminecraft.share.SharePayloads;
import com.nibblenerds.unitedminecraft.structure.StructureScanner;
import com.nibblenerds.unitedminecraft.structure.StructuresNearbyPayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric's common (both-sides) entrypoint - the only part of the mod that runs on a server. It
 * powers structure voices: {@link StructureScanner} runs on the integrated server in single
 * player, and on a dedicated server when the mod is installed there too. Players without the
 * mod never receive anything from it, and the mod on a client works as before on a server
 * without it.
 */
public class UnitedMinecraftFabricCommon implements ModInitializer {
	private static final StructureScanner.Network NETWORK = new StructureScanner.Network() {
		@Override
		public boolean canSend(ServerPlayer player) {
			return ServerPlayNetworking.canSend(player, StructuresNearbyPayload.TYPE);
		}

		@Override
		public void send(ServerPlayer player, StructuresNearbyPayload payload) {
			ServerPlayNetworking.send(player, payload);
		}
	};

	private static final ShareRelay.Network SHARE_NETWORK = new ShareRelay.Network() {
		@Override
		public boolean canReceive(ServerPlayer player) {
			return ServerPlayNetworking.canSend(player, SharePayloads.Offer.TYPE);
		}

		@Override
		public void sendTargets(ServerPlayer player, SharePayloads.Targets payload) {
			ServerPlayNetworking.send(player, payload);
		}

		@Override
		public void sendOffer(ServerPlayer player, SharePayloads.Offer payload) {
			ServerPlayNetworking.send(player, payload);
		}
	};

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(StructuresNearbyPayload.TYPE, StructuresNearbyPayload.STREAM_CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> StructureScanner.tick(server, NETWORK));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			StructureScanner.clear();
			ShareRelay.clear();
		});

		PayloadTypeRegistry.serverboundPlay().register(SharePayloads.TargetsRequest.TYPE, SharePayloads.TargetsRequest.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SharePayloads.Targets.TYPE, SharePayloads.Targets.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SharePayloads.Send.TYPE, SharePayloads.Send.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SharePayloads.Offer.TYPE, SharePayloads.Offer.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SharePayloads.TargetsRequest.TYPE,
				(payload, context) -> ShareRelay.onTargetsRequest(context.player(), SHARE_NETWORK));
		ServerPlayNetworking.registerGlobalReceiver(SharePayloads.Send.TYPE,
				(payload, context) -> ShareRelay.onSend(context.player(), payload, SHARE_NETWORK));
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> ShareCommand.register(dispatcher, SHARE_NETWORK));
	}
}
