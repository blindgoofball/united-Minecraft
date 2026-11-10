package com.nibblenerds.unitedminecraft.share;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /umshare <player|all> <x y z> <name>}: sends a Map Marker to players who have the mod,
 * for people who don't - the server needs the mod, but the sender needs nothing. It feeds the
 * same relay as the Sharing screen, so the offer reaches the receiver exactly as one made there
 * does, subject to the same checks, the same rate limit and the receiver's ignore setting.
 *
 * <p>Everything here is vanilla argument types, which is what lets a client without the mod
 * parse, tab-complete and send the command. For the same reason the replies are plain text
 * rather than translation keys: a client without the mod has no copy of this mod's language file
 * and would be shown the raw key.
 *
 * <p>The marker goes in the sender's current dimension (the overworld, for the console), and
 * the position accepts {@code ~ ~ ~}, so {@code /umshare all ~ ~ ~ Camp} shares where the sender
 * is standing. Anyone may use it, like chat - the receiver still has to accept the offer.
 */
public final class ShareCommand {
	private static final String COMMAND = "umshare";
	private static final UUID CONSOLE_ID = new UUID(0L, 0L);

	private ShareCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ShareRelay.Network network) {
		dispatcher.register(Commands.literal(COMMAND)
				.then(Commands.literal("all")
						.then(positionAndName(ctx -> run(ctx, network, true))))
				.then(Commands.argument("player", EntityArgument.player())
						.then(positionAndName(ctx -> run(ctx, network, false)))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> positionAndName(
			com.mojang.brigadier.Command<CommandSourceStack> command) {
		return Commands.argument("pos", BlockPosArgument.blockPos())
				.then(Commands.argument("name", StringArgumentType.greedyString()).executes(command));
	}

	private static int run(CommandContext<CommandSourceStack> context, ShareRelay.Network network, boolean toAll)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer sender = source.getPlayer();
		BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
		SharePayloads.Item item = itemFor(StringArgumentType.getString(context, "name"),
				source.getLevel().dimension().identifier().toString(), pos);
		if (item == null) {
			source.sendFailure(Component.literal("Can't share that: it needs a name, and a position inside the world."));
			return 0;
		}

		List<ServerPlayer> candidates = new ArrayList<>();
		if (toAll) {
			candidates.addAll(source.getServer().getPlayerList().getPlayers());
		} else {
			candidates.add(EntityArgument.getPlayer(context, "player"));
		}
		candidates.remove(sender);

		List<ServerPlayer> recipients = new ArrayList<>();
		for (ServerPlayer player : candidates) {
			if (network.canReceive(player)) {
				recipients.add(player);
			}
		}
		if (recipients.isEmpty()) {
			source.sendFailure(Component.literal(toAll
					? "No other players with United Minecraft are online."
					: candidates.isEmpty()
							? "You can't share a marker with yourself."
							: candidates.get(0).getName().getString() + " doesn't have United Minecraft."));
			return 0;
		}

		UUID senderId = sender == null ? CONSOLE_ID : sender.getUUID();
		int sent = ShareRelay.deliver(source.getServer(), senderId, ShareRelay.offerName(source.getTextName()),
				item, recipients, network);
		if (sent < recipients.size()) {
			source.sendFailure(Component.literal("You're sharing too fast. Sent '" + item.name() + "' to "
					+ sent + " of " + recipients.size() + " players. Try again in a moment."));
			return sent;
		}
		source.sendSuccess(() -> Component.literal("Sent '" + item.name() + "' to " + recipients.size()
				+ (recipients.size() == 1 ? " player" : " players") + " with United Minecraft."), false);
		return sent;
	}

	/**
	 * The marker to send, or null if it shouldn't be: a blank name, or a position outside any
	 * world. A name longer than the payload allows is cut rather than refused.
	 */
	static SharePayloads.Item itemFor(String name, String dimension, BlockPos pos) {
		String trimmed = name.strip();
		if (trimmed.length() > SharePayloads.MAX_NAME) {
			trimmed = trimmed.substring(0, SharePayloads.MAX_NAME).strip();
		}
		return ShareRelay.sanitize(new SharePayloads.Item(SharePayloads.Kind.MARKER, trimmed, dimension,
				pos.getX(), pos.getY(), pos.getZ(), false));
	}
}
