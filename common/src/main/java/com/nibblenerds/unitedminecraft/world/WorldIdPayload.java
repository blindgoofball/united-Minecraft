package com.nibblenerds.unitedminecraft.world;

import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: the random id this world was given the first time the mod ran on it. A vanilla
 * server sends a client nothing that tells two worlds apart, so on a server with the mod this is
 * how markers and block names find the world they belong to - see {@link WorldIdRelay}.
 *
 * <p>Registered by each loader module as an optional clientbound payload, like the others.
 */
public record WorldIdPayload(UUID id) implements CustomPacketPayload {
	public static final Type<WorldIdPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("united_minecraft", "world_id"));

	public static final StreamCodec<ByteBuf, WorldIdPayload> STREAM_CODEC =
			UUIDUtil.STREAM_CODEC.map(WorldIdPayload::new, WorldIdPayload::id);

	@Override
	public Type<WorldIdPayload> type() {
		return TYPE;
	}
}
