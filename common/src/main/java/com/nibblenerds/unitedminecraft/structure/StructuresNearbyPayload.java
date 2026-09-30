package com.nibblenerds.unitedminecraft.structure;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: the generated structures (villages, monuments, ...) with a piece within
 * {@link StructureScanner#MAX_RANGE} blocks of the player, each with the point of its nearest
 * piece. Structure locations only exist on the server, so this is the only way a client can
 * know about them - see {@link StructureScanner}.
 *
 * <p>Registered by each loader module as an optional clientbound payload: a client without the
 * mod can still join a server that has it, and a client with it can join one without.
 *
 * @param entries nearest first
 */
public record StructuresNearbyPayload(List<Entry> entries) implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 32;

	public static final Type<StructuresNearbyPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("united_minecraft", "structures_nearby"));

	public static final StreamCodec<ByteBuf, StructuresNearbyPayload> STREAM_CODEC =
			ByteBufCodecs.<ByteBuf, Entry, List<Entry>>collection(ArrayList::new, Entry.STREAM_CODEC, MAX_ENTRIES)
					.map(StructuresNearbyPayload::new, StructuresNearbyPayload::entries);

	/**
	 * @param structure the structure's registry id, e.g. {@code minecraft:village_plains}
	 * @param start the packed chunk position of the structure's start - together with {@code
	 *        structure} it identifies one particular village, monument, etc. in a dimension
	 * @param x the point of the structure's nearest piece, as seen from the player
	 */
	public record Entry(Identifier structure, long start, double x, double y, double z) {
		public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, Entry::structure,
				ByteBufCodecs.VAR_LONG, Entry::start,
				ByteBufCodecs.DOUBLE, Entry::x,
				ByteBufCodecs.DOUBLE, Entry::y,
				ByteBufCodecs.DOUBLE, Entry::z,
				Entry::new);
	}

	@Override
	public Type<StructuresNearbyPayload> type() {
		return TYPE;
	}
}
