package com.nibblenerds.unitedminecraft.share;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server half of marker sharing: tells a player who else has the mod, and passes a shared
 * marker from its sender to the player or players it is for. Nothing is stored - the receiving
 * client holds the offer, and the server only checks that what it forwards is sane, stamps the
 * sender's real name on it, and rate-limits each sender so nobody can be flooded.
 *
 * <p>Plain vanilla code, like {@code StructureScanner}: each loader module registers the
 * payloads, calls {@link #onTargetsRequest} and {@link #onSend} from its receivers with its own
 * way of reaching a player ({@link Network}), and {@link #clear} when the server stops.
 */
public final class ShareRelay {
	/** At most this many shares per sender within {@link #WINDOW_TICKS}; a Send to everyone counts once per player it reaches. */
	static final int MAX_SHARES_PER_WINDOW = 30;
	static final int WINDOW_TICKS = 20 * 30;
	/** Far beyond any real world border, but small enough to reject garbage. */
	static final int MAX_HORIZONTAL = 30_000_000;
	static final int MAX_VERTICAL = 4096;

	private static final Map<UUID, Deque<Integer>> recentShares = new HashMap<>();

	private ShareRelay() {
	}

	/** How the loader reaches a player - the only loader-specific part of this. */
	public interface Network {
		/** Whether this player's client has United Minecraft, i.e. registered the share payloads. */
		boolean canReceive(ServerPlayer player);

		void sendTargets(ServerPlayer player, SharePayloads.Targets payload);

		void sendOffer(ServerPlayer player, SharePayloads.Offer payload);
	}

	public static void onTargetsRequest(ServerPlayer requester, Network network) {
		MinecraftServer server = requester.level().getServer();
		List<SharePayloads.Target> targets = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player != requester && network.canReceive(player)) {
				targets.add(new SharePayloads.Target(player.getUUID(), displayName(player)));
			}
		}
		targets.sort(Comparator.comparing(target -> target.name().toLowerCase(java.util.Locale.ROOT)));
		if (targets.size() > SharePayloads.MAX_TARGETS) {
			targets = new ArrayList<>(targets.subList(0, SharePayloads.MAX_TARGETS));
		}
		network.sendTargets(requester, new SharePayloads.Targets(targets));
	}

	public static void onSend(ServerPlayer sender, SharePayloads.Send send, Network network) {
		SharePayloads.Item item = sanitize(send.item());
		if (item == null) {
			return;
		}
		MinecraftServer server = sender.level().getServer();
		List<ServerPlayer> recipients = recipientsFor(send.toAll(), server.getPlayerList().getPlayers(),
				send.toAll() ? null : server.getPlayerList().getPlayer(send.target()), sender);
		deliver(server, sender.getUUID(), displayName(sender), item, recipients, network);
	}

	/**
	 * Who a share goes to: everyone but the sender, or just {@code target} (unless that is nobody, or
	 * the sender). Always a fresh, modifiable list - an earlier version handed back {@code List.of(target)}
	 * and then removed the sender from it, which threw for every share to a single player.
	 */
	static <P> List<P> recipientsFor(boolean toAll, List<P> everyone, P target, P sender) {
		List<P> recipients = new ArrayList<>();
		if (toAll) {
			recipients.addAll(everyone);
		} else if (target != null) {
			recipients.add(target);
		}
		recipients.remove(sender);
		return recipients;
	}

	/**
	 * Sends {@code item} as an offer from {@code senderName} to each of {@code recipients} who has
	 * the mod, counting each against {@code senderId}'s allowance and stopping once it runs out.
	 * Shared by the Sharing screen and {@link ShareCommand}, so both draw on the same allowance.
	 * Returns how many offers went out.
	 */
	public static int deliver(MinecraftServer server, UUID senderId, String senderName, SharePayloads.Item item,
			List<ServerPlayer> recipients, Network network) {
		int now = server.getTickCount();
		SharePayloads.Offer offer = new SharePayloads.Offer(senderName, item);
		int sent = 0;
		for (ServerPlayer player : recipients) {
			if (!network.canReceive(player)) {
				continue;
			}
			if (!allow(senderId, now)) {
				break;
			}
			network.sendOffer(player, offer);
			sent++;
		}
		return sent;
	}

	/** Forgets rate-limit history - on server stop, so a different world never inherits it. */
	public static void clear() {
		recentShares.clear();
	}

	/** Counts one share against {@code sender}'s allowance, returning false once they are over it. */
	private static boolean allow(UUID sender, int nowTick) {
		return withinLimit(recentShares.computeIfAbsent(sender, id -> new ArrayDeque<>()), nowTick);
	}

	/**
	 * Drops the ticks in {@code history} that have aged out of the window, then records {@code
	 * nowTick} and returns true if that stays within the allowance - or returns false, recording
	 * nothing, if it would not.
	 */
	static boolean withinLimit(Deque<Integer> history, int nowTick) {
		while (!history.isEmpty() && nowTick - history.peekFirst() >= WINDOW_TICKS) {
			history.pollFirst();
		}
		if (history.size() >= MAX_SHARES_PER_WINDOW) {
			return false;
		}
		history.addLast(nowTick);
		return true;
	}

	/**
	 * The item to forward, with its text trimmed - or null if it should be dropped: a blank name,
	 * a dimension that is not a valid id, or a position no world could contain. A real client
	 * never sends these; this is for a modified one.
	 */
	static SharePayloads.Item sanitize(SharePayloads.Item item) {
		String name = item.name().strip();
		if (name.isEmpty() || Identifier.tryParse(item.dimension()) == null) {
			return null;
		}
		if (Math.abs(item.x()) > MAX_HORIZONTAL || Math.abs(item.z()) > MAX_HORIZONTAL || Math.abs(item.y()) > MAX_VERTICAL) {
			return null;
		}
		boolean marker = item.kind() == SharePayloads.Kind.NAMED_BLOCK && item.marker();
		return new SharePayloads.Item(item.kind(), name, item.dimension(), item.x(), item.y(), item.z(), marker);
	}

	private static String displayName(ServerPlayer player) {
		return player.getName().getString();
	}

	/** A sender's name as it goes in an offer, cut to what the payload allows. */
	static String offerName(String name) {
		return name.length() > SharePayloads.MAX_NAME ? name.substring(0, SharePayloads.MAX_NAME) : name;
	}
}
