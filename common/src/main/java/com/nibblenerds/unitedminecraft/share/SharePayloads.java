package com.nibblenerds.unitedminecraft.share;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The four payloads behind marker sharing. The server only relays - it never stores a shared
 * marker, and the receiving player decides whether to keep one (see {@link ShareRelay}).
 *
 * <ul>
 * <li>{@link TargetsRequest}, client to server: which players can I share with?
 * <li>{@link Targets}, server to client: the answer - the other players who have the mod.
 * <li>{@link Send}, client to server: share this item with one player, or with all of them.
 * <li>{@link Offer}, server to client: another player is sharing this item with you.
 * </ul>
 *
 * <p>Registered by each loader module as optional, like {@code StructuresNearbyPayload}: a
 * client without the mod can join a server that has it, and a client with it can join one
 * without (it just can't share there).
 */
public final class SharePayloads {
	/** Longest name or sender accepted - the name prompts are capped at 64 as well. */
	public static final int MAX_NAME = 64;
	public static final int MAX_DIMENSION = 128;
	public static final int MAX_TARGETS = 100;

	private SharePayloads() {
	}

	/** What kind of thing is being shared - decides what accepting it creates. */
	public enum Kind {
		/** A plain Map Marker: a name at a spot. */
		MARKER,
		/** A named block; {@code Item#marker} says whether it also shows under Markers. */
		NAMED_BLOCK;

		static Kind of(int ordinal) {
			Kind[] values = values();
			if (ordinal < 0 || ordinal >= values.length) {
				throw new IllegalArgumentException("Unknown share kind " + ordinal);
			}
			return values[ordinal];
		}
	}

	/**
	 * One shareable thing. {@code marker} only means something for {@link Kind#NAMED_BLOCK}, and
	 * is false for a plain marker.
	 */
	public record Item(Kind kind, String name, String dimension, int x, int y, int z, boolean marker) {
		static void write(FriendlyByteBuf buf, Item item) {
			buf.writeByte(item.kind().ordinal());
			buf.writeUtf(item.name(), MAX_NAME);
			buf.writeUtf(item.dimension(), MAX_DIMENSION);
			buf.writeInt(item.x());
			buf.writeInt(item.y());
			buf.writeInt(item.z());
			buf.writeBoolean(item.marker());
		}

		static Item read(FriendlyByteBuf buf) {
			Kind kind = Kind.of(buf.readByte());
			String name = buf.readUtf(MAX_NAME);
			String dimension = buf.readUtf(MAX_DIMENSION);
			return new Item(kind, name, dimension, buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean());
		}
	}

	/** Client to server: asks for the {@link Targets} list. */
	public record TargetsRequest() implements CustomPacketPayload {
		public static final Type<TargetsRequest> TYPE = payloadType("share_targets_request");
		public static final StreamCodec<ByteBuf, TargetsRequest> STREAM_CODEC = StreamCodec.unit(new TargetsRequest());

		@Override
		public Type<TargetsRequest> type() {
			return TYPE;
		}
	}

	/** A player the sender can share with. */
	public record Target(UUID id, String name) {
	}

	/** Server to client: every other player who has the mod, by name. */
	public record Targets(List<Target> targets) implements CustomPacketPayload {
		public static final Type<Targets> TYPE = payloadType("share_targets");
		public static final StreamCodec<ByteBuf, Targets> STREAM_CODEC = StreamCodec.of(
				(buf, payload) -> {
					FriendlyByteBuf out = new FriendlyByteBuf(buf);
					out.writeVarInt(payload.targets().size());
					for (Target target : payload.targets()) {
						out.writeUUID(target.id());
						out.writeUtf(target.name(), MAX_NAME);
					}
				},
				buf -> {
					FriendlyByteBuf in = new FriendlyByteBuf(buf);
					int count = in.readVarInt();
					if (count < 0 || count > MAX_TARGETS) {
						throw new IllegalArgumentException("Too many share targets: " + count);
					}
					List<Target> targets = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						targets.add(new Target(in.readUUID(), in.readUtf(MAX_NAME)));
					}
					return new Targets(targets);
				});

		@Override
		public Type<Targets> type() {
			return TYPE;
		}
	}

	/**
	 * Client to server: share {@code item} with the player {@code target}, or - when {@code toAll}
	 * - with every other player who has the mod, in which case {@code target} is ignored.
	 */
	public record Send(boolean toAll, UUID target, Item item) implements CustomPacketPayload {
		public static final Type<Send> TYPE = payloadType("share_send");
		public static final StreamCodec<ByteBuf, Send> STREAM_CODEC = StreamCodec.of(
				(buf, payload) -> {
					FriendlyByteBuf out = new FriendlyByteBuf(buf);
					out.writeBoolean(payload.toAll());
					out.writeUUID(payload.target());
					Item.write(out, payload.item());
				},
				buf -> {
					FriendlyByteBuf in = new FriendlyByteBuf(buf);
					return new Send(in.readBoolean(), in.readUUID(), Item.read(in));
				});

		@Override
		public Type<Send> type() {
			return TYPE;
		}
	}

	/** Server to client: {@code sender} is sharing {@code item} with you. The server fills in the sender itself. */
	public record Offer(String sender, Item item) implements CustomPacketPayload {
		public static final Type<Offer> TYPE = payloadType("share_offer");
		public static final StreamCodec<ByteBuf, Offer> STREAM_CODEC = StreamCodec.of(
				(buf, payload) -> {
					FriendlyByteBuf out = new FriendlyByteBuf(buf);
					out.writeUtf(payload.sender(), MAX_NAME);
					Item.write(out, payload.item());
				},
				buf -> {
					FriendlyByteBuf in = new FriendlyByteBuf(buf);
					return new Offer(in.readUtf(MAX_NAME), Item.read(in));
				});

		@Override
		public Type<Offer> type() {
			return TYPE;
		}
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("united_minecraft", path));
	}
}
