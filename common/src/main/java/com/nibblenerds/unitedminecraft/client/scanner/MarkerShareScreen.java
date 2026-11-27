package com.nibblenerds.unitedminecraft.client.scanner;

import com.nibblenerds.unitedminecraft.client.PickList;
import com.nibblenerds.unitedminecraft.client.keybind.ClientKeyBindings;
import com.nibblenerds.unitedminecraft.client.keybind.KeybindScreen;
import java.util.ArrayList;
import java.util.List;

import com.nibblenerds.unitedminecraft.share.SharePayloads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The Sharing screen ({@link ClientKeyBindings#OPEN_SHARING}): the offers other players have
 * sent, with Accept and Decline for the selected one, then a list of players and a list of your
 * markers to pick from, with Send for that pair and Send to All Players for the marker alone.
 * Escape or Close leaves.
 *
 * <p>Each list is a {@link PickList}, the same kind of list {@link KeybindScreen} uses, so
 * they behave and narrate the way that screen's list does. A list is named through its widget
 * message, which the narration fix reads on entering it; one with nothing to pick shows a single
 * "none" row so it can still be tabbed to.
 */
public final class MarkerShareScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int GAP = 6;
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW_HEIGHT = 20;
	private static final int MAX_WIDTH = 420;

	// Selection survives a resize, which rebuilds every widget.
	private int offerIndex;
	private int playerIndex;
	private int itemIndex;

	private PickList<MarkerShareController.PendingOffer> offerList;
	private PickList<SharePayloads.Target> playerList;
	private PickList<SharePayloads.Item> itemList;
	private int seenTargetsVersion = -1;

	MarkerShareScreen() {
		super(Component.translatable("united_minecraft.share_screen.title"));
	}

	@Override
	protected void init() {
		LocalPlayer player = this.minecraft.player;
		int fullWidth = Math.min(this.width - 2 * MARGIN, MAX_WIDTH);
		int left = (this.width - fullWidth) / 2;
		int halfWidth = (fullWidth - GAP) / 2;

		// Top to bottom: offers, the Accept/Decline row, the two lists side by side, then the
		// Send/Send to All/Close row. Offers get a bit less than the pair below.
		int bottomRowY = this.height - MARGIN - BUTTON_HEIGHT;
		int available = bottomRowY - GAP - MARGIN - BUTTON_HEIGHT - 2 * GAP;
		int offersHeight = Math.max(ROW_HEIGHT * 2, available * 2 / 5);
		int lowerHeight = Math.max(ROW_HEIGHT * 2, available - offersHeight);
		int acceptRowY = MARGIN + offersHeight + GAP;
		int lowerY = acceptRowY + BUTTON_HEIGHT + GAP;

		offerList = new PickList<>(this.minecraft, this.font, left, MARGIN, fullWidth, offersHeight,
				Component.translatable("united_minecraft.share_screen.offers"),
				Component.translatable("united_minecraft.share_screen.no_offers"),
				index -> offerIndex = index, MarkerShareController::clearReplaceConfirmation);
		playerList = new PickList<>(this.minecraft, this.font, left, lowerY, halfWidth, lowerHeight,
				Component.translatable("united_minecraft.share_screen.send_to"),
				Component.translatable("united_minecraft.share_screen.no_players"),
				index -> playerIndex = index, () -> {
				});
		itemList = new PickList<>(this.minecraft, this.font, left + halfWidth + GAP, lowerY, halfWidth, lowerHeight,
				Component.translatable("united_minecraft.share_screen.markers"),
				Component.translatable("united_minecraft.share_screen.no_markers"),
				index -> itemIndex = index, () -> {
				});

		addRenderableWidget(offerList);
		refreshOffers();
		int half = (fullWidth - GAP) / 2;
		addButton(left, acceptRowY, half, "united_minecraft.share_screen.accept", this::acceptSelected);
		addButton(left + half + GAP, acceptRowY, half, "united_minecraft.share_screen.decline", this::declineSelected);

		addRenderableWidget(playerList);
		seenTargetsVersion = -1;
		refreshPlayers();
		addRenderableWidget(itemList);
		itemList.setItems(itemLabels(player), MarkerShareController.shareableItems(), itemIndex);

		int third = (fullWidth - 2 * GAP) / 3;
		addButton(left, bottomRowY, third, "united_minecraft.share_screen.send", this::sendSelected);
		addButton(left + third + GAP, bottomRowY, third, "united_minecraft.share_screen.send_all", this::sendToAll);
		addButton(left + 2 * (third + GAP), bottomRowY, third, "united_minecraft.share_screen.close", this::onClose);

		// Pending offers are what the player came here for when there are any.
		setInitialFocus(offerList.hasItems() ? offerList : playerList);
	}

	private void addButton(int x, int y, int width, String labelKey, Runnable onPress) {
		addRenderableWidget(Button.builder(Component.translatable(labelKey), button -> onPress.run())
				.bounds(x, y, width, BUTTON_HEIGHT)
				.build());
	}

	@Override
	public void tick() {
		super.tick();
		// The server's list of players arrives a moment after the screen opens.
		if (MarkerShareController.targetsVersion() != seenTargetsVersion) {
			refreshPlayers();
		}
	}

	private void refreshOffers() {
		List<MarkerShareController.PendingOffer> offers = MarkerShareController.pending();
		List<Component> labels = new ArrayList<>();
		for (MarkerShareController.PendingOffer offer : offers) {
			labels.add(MarkerShareController.describe(offer, this.minecraft.player));
		}
		offerList.setItems(labels, offers, offerIndex);
	}

	private void refreshPlayers() {
		seenTargetsVersion = MarkerShareController.targetsVersion();
		List<SharePayloads.Target> targets = MarkerShareController.targets();
		List<Component> labels = new ArrayList<>();
		for (SharePayloads.Target target : targets) {
			labels.add(Component.literal(target.name()));
		}
		playerList.setItems(labels, targets, playerIndex);
	}

	private static List<Component> itemLabels(LocalPlayer player) {
		List<Component> labels = new ArrayList<>();
		for (SharePayloads.Item item : MarkerShareController.shareableItems()) {
			labels.add(MarkerShareController.describe(item, player));
		}
		return labels;
	}

	private void acceptSelected() {
		MarkerShareController.PendingOffer offer = selectedOffer();
		if (offer != null) {
			MarkerShareController.accept(this.minecraft, offer);
			refreshOffers();
		}
	}

	private void declineSelected() {
		MarkerShareController.PendingOffer offer = selectedOffer();
		if (offer != null) {
			MarkerShareController.decline(this.minecraft, offer);
			refreshOffers();
		}
	}

	/** The selected offer, or null after saying why there isn't one. */
	private MarkerShareController.PendingOffer selectedOffer() {
		MarkerShareController.PendingOffer offer = offerList.selectedValue();
		if (offer == null) {
			say(offerList.hasItems() ? "united_minecraft.narrate.share_select_offer" : "united_minecraft.narrate.share_no_offers");
		}
		return offer;
	}

	private void sendSelected() {
		SharePayloads.Target target = playerList.selectedValue();
		SharePayloads.Item item = itemList.selectedValue();
		if (!playerList.hasItems()) {
			say("united_minecraft.narrate.share_no_players");
		} else if (!itemList.hasItems()) {
			say("united_minecraft.narrate.share_no_markers");
		} else if (target == null || item == null) {
			say("united_minecraft.narrate.share_select_both");
		} else {
			MarkerShareController.send(this.minecraft, item, target);
		}
	}

	private void sendToAll() {
		SharePayloads.Item item = itemList.selectedValue();
		if (!playerList.hasItems()) {
			say("united_minecraft.narrate.share_no_players");
		} else if (!itemList.hasItems()) {
			say("united_minecraft.narrate.share_no_markers");
		} else if (item == null) {
			say("united_minecraft.narrate.share_select_marker");
		} else {
			MarkerShareController.sendToAll(this.minecraft, item);
		}
	}

	private void say(String key) {
		this.minecraft.getNarrator().saySystemNow(Component.translatable(key));
	}
}
