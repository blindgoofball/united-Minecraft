package com.nibblenerds.unitedminecraft.network;

import com.nibblenerds.unitedminecraft.UnitedMinecraft;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server request from the scanner's Shift+Target teleport: "put me at this
 * exact position and facing, if it's still safe when you check." The server re-validates
 * safety itself rather than trusting the client's own pre-check, and performs the actual
 * teleport authoritatively (see {@link net.minecraft.server.level.ServerPlayer#teleportTo}),
 * since a client just setting its own position directly would get corrected as implausible
 * movement instead of actually moving anywhere.
 */
public record TeleportRequestPayload(double x, double y, double z, float yaw, float pitch) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TeleportRequestPayload> TYPE =
			new CustomPacketPayload.Type<>(UnitedMinecraft.id("teleport_request"));

	public static final StreamCodec<ByteBuf, TeleportRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, TeleportRequestPayload::x,
			ByteBufCodecs.DOUBLE, TeleportRequestPayload::y,
			ByteBufCodecs.DOUBLE, TeleportRequestPayload::z,
			ByteBufCodecs.FLOAT, TeleportRequestPayload::yaw,
			ByteBufCodecs.FLOAT, TeleportRequestPayload::pitch,
			TeleportRequestPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
