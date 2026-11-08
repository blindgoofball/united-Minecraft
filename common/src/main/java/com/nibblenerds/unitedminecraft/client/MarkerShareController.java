package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.audio.CueVolume;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nibblenerds.unitedminecraft.platform.Platform;
import com.nibblenerds.unitedminecraft.share.SharePayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Sharing Map Markers and marked named blocks with other players who have the mod. The server
 * only relays (see {@code ShareRelay}); what arrives here is an offer, held in memory until the
 * player accepts or declines it in {@link MarkerShareScreen} - nothing is ever saved on its own.
 *
 * <p>Receiving can be switched off with {@link UnitedMinecraftConfig#ignoreMarkerShares}, in
 * which case an offer is dropped without a sound or a word, and the sender is never told.
 *
 * <p>Accepting a share onto a spot where the player already has a different name does not
 * overwrite it straight away: the first Accept says so and keeps the offer, and a second Accept
 * on the same offer replaces it.
 */
public final class MarkerShareController {
	/** Oldest offers are dropped past this, so an unattended client can't pile up without limit. */
	static final int MAX_PENDING = 20;

	/** One offer waiting for a decision. */
	record PendingOffer(String sender, SharePayloads.Item item) {
	}

	/** How an incoming share relates to what the player already has at that spot. */
	enum Conflict {
		/** Nothing there (or only an upgrade to the same name): just save it. */
		NONE,
		/** The player already has exactly this. */
		SAME,
		/** A different name is already there: needs a second Accept to replace it. */
		DIFFERENT
	}

	private static final List<PendingOffer> pending = new ArrayList<>();
	private static List<SharePayloads.Target> targets = List.of();
	/** Bumped whenever {@link #targets} changes, so an open screen knows to refresh. */
	private static int targetsVersion;
	/** The offer whose first Accept already warned about a different name - a second Accept replaces. */
	private static PendingOffer replaceArmedFor;

	private MarkerShareController() {
	}

	/** World exit: nothing carries over to the next session. */
	public static void reset() {
		pending.clear();
		targets = List.of();
		targetsVersion++;
		replaceArmedFor = null;
	}

	static List<PendingOffer> pending() {
		return List.copyOf(pending);
	}

	static List<SharePayloads.Target> targets() {
		return targets;
	}

	static int targetsVersion() {
		return targetsVersion;
	}

	/** Everything the player can share: plain markers first, then marked named blocks, all dimensions. */
	static List<SharePayloads.Item> shareableItems() {
		List<SharePayloads.Item> items = new ArrayList<>(MapMarkerController.shareItems());
		items.addAll(NamedBlockController.shareItems());
		return items;
	}

	/** Opens the Sharing screen, or says why it can't be used on this server. */
	public static void open(Minecraft client) {
		if (!Platform.get().serverAccepts(SharePayloads.Send.TYPE)) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.share_unavailable"));
			return;
		}
		Platform.get().sendToServer(new SharePayloads.TargetsRequest());
		client.gui.setScreen(new MarkerShareScreen());
	}

	// --- Receiving -----------------------------------------------------------------------------

	public static void onTargets(SharePayloads.Targets payload) {
		targets = List.copyOf(payload.targets());
		targetsVersion++;
	}

	public static void onOffer(SharePayloads.Offer payload) {
		if (UnitedMinecraftConfig.get().ignoreMarkerShares) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !addOffer(pending, new PendingOffer(payload.sender(), payload.item()), MAX_PENDING)) {
			return;
		}
		playCue(client, client.player);
		client.getNarrator().saySystemQueued(Component.translatable(
				"united_minecraft.narrate.share_offer_received", payload.sender(), payload.item().name()));
	}

	/**
	 * Adds {@code offer} unless an identical one (same sender, same item) is already waiting,
	 * dropping the oldest first if the list is full. Returns whether it was added.
	 */
	static boolean addOffer(List<PendingOffer> offers, PendingOffer offer, int max) {
		if (offers.contains(offer)) {
			return false;
		}
		while (offers.size() >= max) {
			offers.remove(0);
		}
		offers.add(offer);
		return true;
	}

	private static void playCue(Minecraft client, LocalPlayer player) {
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER,
				CueVolume.scale(0.7f), 1.3f, player.getRandom(), player.getX(), player.getY(), player.getZ()));
	}

	// --- Deciding ------------------------------------------------------------------------------

	static void clearReplaceConfirmation() {
		replaceArmedFor = null;
	}

	static void decline(Minecraft client, PendingOffer offer) {
		if (pending.remove(offer)) {
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.share_declined", offer.item().name(), offer.sender()));
		}
	}

	static void accept(Minecraft client, PendingOffer offer) {
		if (!pending.contains(offer)) {
			return;
		}
		SharePayloads.Item item = offer.item();
		BlockPos pos = new BlockPos(item.x(), item.y(), item.z());
		Conflict conflict = conflict(item, existingName(item, pos), existingIsMarker(item, pos));
		if (conflict == Conflict.SAME) {
			pending.remove(offer);
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.share_already_have", item.name()));
			return;
		}
		if (conflict == Conflict.DIFFERENT && !offer.equals(replaceArmedFor)) {
			replaceArmedFor = offer;
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.share_replace_prompt", existingName(item, pos)));
			return;
		}
		if (item.kind() == SharePayloads.Kind.NAMED_BLOCK) {
			NamedBlockController.importNamedBlock(item.name(), item.dimension(), pos, item.marker());
		} else {
			MapMarkerController.importMarker(item.name(), item.dimension(), pos);
		}
		pending.remove(offer);
		replaceArmedFor = null;
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.share_accepted", item.name(), offer.sender()));
	}

	private static String existingName(SharePayloads.Item item, BlockPos pos) {
		if (item.kind() == SharePayloads.Kind.NAMED_BLOCK) {
			return NamedBlockController.nameAt(item.dimension(), pos);
		}
		MapMarkerController.MapMarker marker = MapMarkerController.findAt(item.dimension(), pos);
		return marker == null ? null : marker.name();
	}

	private static boolean existingIsMarker(SharePayloads.Item item, BlockPos pos) {
		return item.kind() == SharePayloads.Kind.NAMED_BLOCK
				? NamedBlockController.isMarkerAt(item.dimension(), pos)
				: true;
	}

	/**
	 * How {@code item} relates to what the player has at that spot. A named block that arrives
	 * flagged for Markers onto the same name without that flag is an upgrade, not a clash, and
	 * just saves.
	 */
	static Conflict conflict(SharePayloads.Item item, String existingName, boolean existingIsMarker) {
		if (existingName == null) {
			return Conflict.NONE;
		}
		if (!existingName.equals(item.name())) {
			return Conflict.DIFFERENT;
		}
		return item.marker() && !existingIsMarker ? Conflict.NONE : Conflict.SAME;
	}

	// --- Sending -------------------------------------------------------------------------------

	static void send(Minecraft client, SharePayloads.Item item, SharePayloads.Target target) {
		Platform.get().sendToServer(new SharePayloads.Send(false, target.id(), forSending(item)));
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.share_sent", item.name(), target.name()));
	}

	static void sendToAll(Minecraft client, SharePayloads.Item item) {
		Platform.get().sendToServer(new SharePayloads.Send(true, new UUID(0L, 0L), forSending(item)));
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.share_sent_all", item.name()));
	}

	/** The item with its name cut to what the payload allows - a stored name can in principle be longer. */
	static SharePayloads.Item forSending(SharePayloads.Item item) {
		String name = item.name().length() > SharePayloads.MAX_NAME
				? item.name().substring(0, SharePayloads.MAX_NAME)
				: item.name();
		return new SharePayloads.Item(item.kind(), name, item.dimension(), item.x(), item.y(), item.z(), item.marker());
	}

	// --- Describing ----------------------------------------------------------------------------

	/** "Iron Mine, 214 blocks away", or "Iron Mine, in the nether" when it is in another dimension than the player's. */
	static Component describe(SharePayloads.Item item, LocalPlayer player) {
		return Component.translatable("united_minecraft.share_screen.item_entry", item.name(), where(item, player));
	}

	static Component describe(PendingOffer offer, LocalPlayer player) {
		return Component.translatable("united_minecraft.share_screen.offer_entry",
				offer.sender(), offer.item().name(), where(offer.item(), player));
	}

	private static Component where(SharePayloads.Item item, LocalPlayer player) {
		if (player == null) {
			return Component.empty();
		}
		if (item.dimension().equals(player.level().dimension().identifier().toString())) {
			long distance = Math.round(Math.sqrt(player.distanceToSqr(item.x() + 0.5, item.y() + 0.5, item.z() + 0.5)));
			return Component.translatable("united_minecraft.share_screen.blocks_away", distance);
		}
		return Component.translatable("united_minecraft.share_screen.other_dimension", dimensionName(item.dimension()));
	}

	/** {@code minecraft:the_nether} as "the nether" - good enough for modded dimensions, which read their own path the same way. */
	static String dimensionName(String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		String path = id == null ? dimension : id.getPath();
		return path.replace('_', ' ');
	}
}
