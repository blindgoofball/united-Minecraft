package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Build mode": a virtual cursor for exploring and targeting blocks without
 * needing to physically turn to face them.
 *
 * <p>While active, the arrow keys and Page Up/Down step a {@link BlockPos}
 * cursor instead of turning the camera, narrating the block, coordinates, and
 * whether it's placeable at each step. Movement is fixed to true compass
 * directions (Left/Right = west/east, Up/Down = north/south) regardless of
 * which way the player is currently facing - deliberately, not a bug: it's a
 * consistent, learnable mapping rather than one that flips depending on facing.
 *
 * <p>Both {@link #place} and {@link #breakBlock} act on the cursor directly,
 * through the client's own placement/mining API, entirely independent of
 * where the player is actually looking. Earlier versions snapped the player's
 * camera to face the cursor after every move so vanilla's own aim-based
 * click handling would target it "for free" - but a real raycast can never
 * reach a neighbor's face that's hidden behind the neighbor's own bulk
 * (unavoidable extending a line of blocks directly away from where you're
 * standing, since every prior block in the chain sits squarely between your
 * eye and the cursor), so that approach was fundamentally unreliable for
 * placement, and the constant camera snapping was disorienting besides.
 * Bypassing facing entirely for both actions removes the need for it, so
 * build mode no longer touches the camera at all.
 *
 * <p>Placing into a replaceable cell (air, tall grass, water, etc.) needs a
 * real, sturdy neighboring face to rest against - see {@link #findSupportFace}
 * for how that neighbor is chosen, and {@link #place} for why it's tried
 * against the real placement call rather than trusted outright.
 */
public final class BuildModeController {
	private static final double FACE_EPSILON = 0.001;

	private static boolean active;
	private static BlockPos cursor;

	// Rising-edge state for the movement keys - see the note on movementPressed() for why
	// this replaces KeyMapping's own click-queue tracking for these six keys.
	private static boolean leftHeld;
	private static boolean rightHeld;
	private static boolean upHeld;
	private static boolean downHeld;
	private static boolean pageUpHeld;
	private static boolean pageDownHeld;

	// Break is a hold, not a click - mirrors a real held mouse button so survival mining
	// time still applies (see breakBlock()).
	private static boolean breakHeld;

	private BuildModeController() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void reset() {
		active = false;
		cursor = null;
		leftHeld = rightHeld = upHeld = downHeld = pageUpHeld = pageDownHeld = false;
		breakHeld = false;
	}

	public static void toggle(Minecraft client, LocalPlayer player) {
		active = !active;
		if (active) {
			// A stray backlog on the "place" key (e.g. idly pressed while inactive) shouldn't
			// fire the instant build mode turns on.
			drainClicks(ClientKeyBindings.BUILD_PLACE);
			// Prime edge-detection to the keys' current physical state, so a key already held
			// at the exact moment build mode turns on isn't misread as a fresh press.
			leftHeld = ClientKeyBindings.LOOK_LEFT.isDown();
			rightHeld = ClientKeyBindings.LOOK_RIGHT.isDown();
			upHeld = ClientKeyBindings.LOOK_UP.isDown();
			downHeld = ClientKeyBindings.LOOK_DOWN.isDown();
			pageUpHeld = ClientKeyBindings.PAGE_UP.isDown();
			pageDownHeld = ClientKeyBindings.PAGE_DOWN.isDown();
			breakHeld = ClientKeyBindings.BUILD_BREAK.isDown();
			cursor = player.blockPosition();
			Component message = Component.translatable("united_minecraft.narrate.build_mode_on")
					.append(Component.literal(" "))
					.append(describeCursor(player));
			client.getNarrator().saySystemNow(message);
		} else {
			if (breakHeld) {
				// Cleanly cancel any in-progress mining rather than leaving it dangling.
				client.gameMode.stopDestroyBlock();
				breakHeld = false;
			}
			cursor = null;
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_mode_off"));
		}
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		boolean moved = false;
		if (movementPressed(ClientKeyBindings.LOOK_LEFT, leftHeld)) {
			moved |= tryMove(client, player, cursor.west());
		}
		leftHeld = ClientKeyBindings.LOOK_LEFT.isDown();
		if (movementPressed(ClientKeyBindings.LOOK_RIGHT, rightHeld)) {
			moved |= tryMove(client, player, cursor.east());
		}
		rightHeld = ClientKeyBindings.LOOK_RIGHT.isDown();
		if (movementPressed(ClientKeyBindings.LOOK_UP, upHeld)) {
			moved |= tryMove(client, player, cursor.north());
		}
		upHeld = ClientKeyBindings.LOOK_UP.isDown();
		if (movementPressed(ClientKeyBindings.LOOK_DOWN, downHeld)) {
			moved |= tryMove(client, player, cursor.south());
		}
		downHeld = ClientKeyBindings.LOOK_DOWN.isDown();
		if (movementPressed(ClientKeyBindings.PAGE_UP, pageUpHeld)) {
			moved |= tryMove(client, player, cursor.above());
		}
		pageUpHeld = ClientKeyBindings.PAGE_UP.isDown();
		if (movementPressed(ClientKeyBindings.PAGE_DOWN, pageDownHeld)) {
			moved |= tryMove(client, player, cursor.below());
		}
		pageDownHeld = ClientKeyBindings.PAGE_DOWN.isDown();

		if (moved) {
			client.getNarrator().saySystemNow(describeCursor(player));
		}

		if (ClientKeyBindings.BUILD_PLACE.consumeClick()) {
			place(client, player);
		}

		boolean breakDown = ClientKeyBindings.BUILD_BREAK.isDown();
		if (breakDown) {
			breakBlock(client, player, !breakHeld);
		} else if (breakHeld) {
			client.gameMode.stopDestroyBlock();
		}
		breakHeld = breakDown;
	}

	/**
	 * True only on the tick a movement key transitions from up to down.
	 *
	 * <p>GLFW fires repeated key events for a key that's simply being held (not just the
	 * initial press) - which is exactly what {@link KeyMapping#consumeClick()}'s click queue
	 * is designed to preserve, one queued move per repeat event. That's fine for the vanilla
	 * uses it was built for, but it's the wrong behavior here: it meant holding an arrow key
	 * even briefly past the OS's repeat-delay threshold - easy to do without seeing the
	 * cursor move - queued up a burst of steps that kept advancing the cursor well after the
	 * key was released, landing it somewhere other than intended. Polling {@link
	 * KeyMapping#isDown()} and tracking our own held-state sidesteps the click queue (and its
	 * repeat events) entirely: a key can only ever produce one move per physical press,
	 * however long it's held.
	 */
	private static boolean movementPressed(KeyMapping mapping, boolean wasHeld) {
		return mapping.isDown() && !wasHeld;
	}

	/**
	 * Places directly into the cursor cell via a manually constructed
	 * {@link BlockHitResult}, rather than relying on vanilla's own aim-based
	 * raycast - see the class doc for why the raycast can't be trusted here.
	 *
	 * <p>{@link #isPlaceable} (used for the "Placeable" narration while moving) only checks
	 * that the cursor is replaceable and has <em>some</em> sturdy neighbor - it doesn't, and
	 * can't cheaply, replicate the actual item's own placement rules ({@code canSurvive},
	 * {@code isUnobstructed}, orientation via {@code getStateForPlacement}), which can still
	 * reject a specific face those checks didn't rule out. Rather than commit to one
	 * analytically "best" face and quietly give up if vanilla's real pipeline disagrees, this
	 * tries every sturdy candidate face in priority order against the real {@code useItemOn}
	 * call and takes the first one vanilla actually accepts - the same "just try it and see"
	 * approach other block-placement utility mods use, since nothing short of the genuine
	 * placement call can fully predict whether a given face will be accepted.
	 */
	private static void place(Minecraft client, LocalPlayer player) {
		Level level = player.level();
		BlockState cursorState = level.getBlockState(cursor);
		if (!cursorState.canBeReplaced() || player.getBoundingBox().intersects(cursor)) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_cannot_place"));
			return;
		}

		for (Direction face : FACE_PRIORITY) {
			BlockPos neighborPos = cursor.relative(face);
			BlockState neighborState = level.getBlockState(neighborPos);
			if (!neighborState.isFaceSturdy(level, neighborPos, face.getOpposite())) {
				continue;
			}
			Vec3 hitLocation = Vec3.atCenterOf(cursor).add(
					face.getStepX() * (0.5 - FACE_EPSILON),
					face.getStepY() * (0.5 - FACE_EPSILON),
					face.getStepZ() * (0.5 - FACE_EPSILON));
			BlockHitResult hit = new BlockHitResult(hitLocation, face.getOpposite(), neighborPos, false);
			if (attemptPlace(client, player, hit)) {
				client.getNarrator().saySystemNow(describeCursor(player));
				return;
			}
		}

		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_cannot_place"));
	}

	/**
	 * Tries one candidate hit against the real placement call. Mirrors vanilla's own
	 * main-hand-then-offhand fallback (Minecraft.startUseItem): stop on a hand that actually
	 * consumes the interaction, or on an explicit failure - but unlike vanilla, a failure here
	 * just means the caller should try the next candidate face, not give up outright.
	 */
	private static boolean attemptPlace(Minecraft client, LocalPlayer player, BlockHitResult hit) {
		for (InteractionHand hand : InteractionHand.values()) {
			InteractionResult result = client.gameMode.useItemOn(player, hand, hit);
			if (result instanceof InteractionResult.Success success) {
				if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
					player.swing(hand);
				}
				return true;
			}
			if (result instanceof InteractionResult.Fail) {
				return false;
			}
		}
		return false;
	}

	/**
	 * Mines the cursor's block directly, via {@link net.minecraft.client.multiplayer.MultiPlayerGameMode}'s
	 * own start/continue/stop mining calls - the same ones a real held mouse button drives -
	 * rather than vanilla's aim-based targeting, for the same reason {@link #place} bypasses
	 * it. Held rather than a single click so survival's real mining time still applies;
	 * creative mode still breaks instantly, since that's handled inside {@code
	 * startDestroyBlock} itself.
	 */
	private static void breakBlock(Minecraft client, LocalPlayer player, boolean justStarted) {
		Level level = player.level();
		if (level.getBlockState(cursor).isAir()) {
			if (justStarted) {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_cannot_break"));
			}
			return;
		}

		if (justStarted) {
			client.gameMode.startDestroyBlock(cursor, Direction.UP);
		} else {
			client.gameMode.continueDestroyBlock(cursor, Direction.UP);
		}

		if (level.getBlockState(cursor).isAir()) {
			client.getNarrator().saySystemNow(describeCursor(player));
		}
	}

	private static void drainClicks(KeyMapping... mappings) {
		for (KeyMapping mapping : mappings) {
			while (mapping.consumeClick()) {
				// Discard backlog.
			}
		}
	}

	private static boolean tryMove(Minecraft client, LocalPlayer player, BlockPos candidate) {
		// Use the player's real placement reach (varies by game mode/attributes) rather than
		// a fixed guess - a cursor allowed further out than vanilla can actually place at
		// would just silently fail to place once you acted on it.
		if (player.getEyePosition().distanceTo(Vec3.atCenterOf(candidate)) > player.blockInteractionRange()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_out_of_reach"));
			return false;
		}
		cursor = candidate;
		return true;
	}

	private static Component describeCursor(LocalPlayer player) {
		Level level = player.level();
		Component blockName = level.getBlockState(cursor).getBlock().getName();
		MutableComponent message = Component.translatable(
				"united_minecraft.narrate.build_cursor", blockName, cursor.getX(), cursor.getY(), cursor.getZ());
		if (isPlaceable(level, player)) {
			message = message.append(Component.literal(" ")).append(Component.translatable("united_minecraft.narrate.build_placeable"));
		}
		return message;
	}

	/** Would placing right now actually put a block at the cursor? */
	private static boolean isPlaceable(Level level, LocalPlayer player) {
		BlockState state = level.getBlockState(cursor);
		if (!state.canBeReplaced()) {
			return false;
		}
		if (player.getBoundingBox().intersects(cursor)) {
			return false;
		}
		return findSupportFace(level, cursor) != null;
	}

	// Checked in this order when looking for a sturdy neighbor to place against: sideways
	// neighbors first (attaching to whatever you just built next to the cursor is almost
	// always the intent once one exists), then the ground below, then finally the block
	// above as a last resort.
	private static final Direction[] FACE_PRIORITY = {
			Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN, Direction.UP,
	};

	/**
	 * Finds the neighboring face of {@code pos} that a placement would rest against.
	 *
	 * <p>This used to pick whichever sturdy neighbor was most "facing the player" (by
	 * dot product with the direction to the eye), on the theory that it'd be the one a
	 * real raycast could actually reach. In practice that heuristic was fighting a losing
	 * battle against the player's own eye height: the vertical offset from a ground-level
	 * cursor to standing eye height (~1.1-1.6 blocks) is large and roughly constant, while
	 * the horizontal alignment to an immediately-adjacent sideways neighbor is small and
	 * shifts with the player's exact, sub-block standing position - not something a player
	 * can precisely control. The two would frequently be close enough that ordinary,
	 * unpredictable stance drift flipped the winner from the intended sideways neighbor to
	 * the ground below, placing the new block on top of the last one instead of beside it.
	 * Now that {@link #place} bypasses vanilla's raycast entirely (see the class doc), that
	 * visibility-driven heuristic no longer serves any purpose - a fixed, deterministic
	 * priority is both simpler and far more predictable.
	 */
	private static Direction findSupportFace(Level level, BlockPos pos) {
		for (Direction face : FACE_PRIORITY) {
			BlockPos neighborPos = pos.relative(face);
			BlockState neighborState = level.getBlockState(neighborPos);
			if (neighborState.isFaceSturdy(level, neighborPos, face.getOpposite())) {
				return face;
			}
		}
		return null;
	}
}
