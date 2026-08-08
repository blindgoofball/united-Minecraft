package com.nibblenerds.unitedminecraft.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
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
 * real, non-replaceable neighboring face to rest against - see {@link #findSupportFace}
 * for how that neighbor is chosen (any solid face works, not just a full "sturdy" one -
 * a slab's or stair's half-height side is a perfectly valid thing to place beside), and
 * {@link #place} for why it's tried against the real placement call rather than trusted
 * outright.
 *
 * <p>Buckets are the one item this "bypass facing entirely" approach can't fully cover -
 * vanilla gives water/lava placement no click-a-face alternative at all, only a real,
 * rotation-based raycast - so {@link #startBucketUse} fakes the player's rotation to look
 * exactly at the cursor and lets that raycast do the rest. See its doc for why that's still
 * strictly no more restrictive than what a sighted player pouring into a hole from directly
 * above already relies on.
 *
 * <p>The cursor can roam a 65x65 horizontal area (32 blocks either way from
 * wherever build mode was toggled on - fixed for the session, not recentered
 * as the player moves) with unrestricted height, well beyond the player's
 * actual placement/mining reach - useful for surveying or laying out a build
 * before walking into range of any particular part of it. Moving the cursor
 * never requires being in reach; only {@link #place} and {@link #breakBlock}
 * do, and both check for themselves and narrate plainly if not. {@link
 * #walkToCursor} drives the player there automatically via the same
 * client-side pathfinding {@link AutoWalkController} already uses elsewhere.
 *
 * <p>The cursor's own block gets its orientation narrated too, generically: any
 * {@code Direction}-valued property (covers both {@code FACING} and {@code
 * HORIZONTAL_FACING} - vanilla blocks share those two property instances rather
 * than each defining their own), found by scanning {@link BlockState#getProperties()}
 * rather than special-casing block types - see {@link #directionPropertyOf}.
 * Redstone power is checked generically too, via {@link Level#hasNeighborSignal}
 * rather than a block-specific "powered" property (plenty of blocks respond to
 * power without exposing one), and only narrated when actually powered - silence
 * otherwise, since knowing a block isn't powered is rarely as actionable as
 * knowing it is. A few redstone components carry state that generic property
 * scanning can't surface (a repeater's delay and a comparator's mode are both
 * plain {@code IntegerProperty}/{@code EnumProperty} values indistinguishable
 * from any other block's without knowing what they mean), so those, plus a
 * daylight sensor's inverted flag, are narrated with dedicated checks in {@link
 * #describeCursor} instead.
 *
 * <p>Pressing Place while the cursor holds an existing block that reacts to a
 * click - a door, chest, lever, repeater, and so on, see {@link #isInteractable} -
 * interacts with it instead of failing outright, the same priority a real
 * right-click gives its own block over whatever's in hand; see {@link
 * #interactWithCursor}. Pressing Place while holding nothing placeable (empty
 * hand, or an item that isn't a block at all) narrates that specifically rather
 * than the generic "Can't place there", which is reserved for a placeable item
 * that got rejected by the destination itself.
 *
 * <p>{@link #cyclePlacementFacing} lets a chosen direction override where the
 * next placed block's own {@code FACING} points, via a trick rather than any
 * new placement API: the block's real {@code getStateForPlacement} already
 * derives that from the player's look direction (see {@link #facingMirrorsPlayerLook}
 * for the one nuance in how), so {@link #startRotatedPlacement} rotates the
 * player to face the direction that would produce the desired result - but
 * placing can't happen in that same tick, or the server ends up computing the
 * block's orientation from the player's real, not-yet-updated rotation and
 * silently corrects it back; see that method's own doc for why. The same
 * chosen direction also gets tried first as the face to place against
 * (falling back to the normal search if that particular neighbor is itself
 * replaceable, e.g. tall grass), which is what makes placing directly onto a
 * chosen side of an existing block possible at all - previously the neighbor
 * was picked for you.
 */
public final class BuildModeController {
	private static final double FACE_EPSILON = 0.001;

	// How far the cursor can roam from the anchor set in toggle(), in blocks.
	private static final int GRID_RADIUS = 32;

	// Cycle order for the placement-facing override; null (not in this array) means "automatic".
	private static final Direction[] FACING_CYCLE = {
			Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN,
	};

	// How many ticks to hold a faked rotation before actually placing - see the class doc on
	// #startRotatedPlacement for why this delay exists at all.
	private static final int ROTATION_SYNC_DELAY_TICKS = 2;

	// Per-Block memoization for #declaresUseWithoutItem - a block's class never changes at
	// runtime, so the reflective hierarchy walk only ever needs to happen once per distinct block.
	private static final Map<Block, Boolean> INTERACTABLE_CACHE = new HashMap<>();

	private static boolean active;
	private static BlockPos cursor;
	private static int anchorX;
	private static int anchorZ;
	private static Direction selectedFacing;

	// Set only while a rotated placement is waiting out its sync delay (see startRotatedPlacement
	// and startBucketUse) - pendingPlaceTicks counts down to 0, at which point pendingAction fires.
	private static int pendingPlaceTicks = -1;
	private static PendingAction pendingAction;
	private static InteractionHand pendingBucketHand;
	private static float pendingSavedYaw;
	private static float pendingSavedPitch;

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

	/** The cursor's current position. Only valid while {@link #isActive()}. */
	public static BlockPos getCursor() {
		return cursor;
	}

	public static void reset() {
		active = false;
		cursor = null;
		selectedFacing = null;
		pendingPlaceTicks = -1;
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
			selectedFacing = null;
			cursor = player.blockPosition();
			anchorX = cursor.getX();
			anchorZ = cursor.getZ();
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

		tickPendingPlacement(client, player);

		if (ClientKeyBindings.BUILD_PLACE.consumeClick()) {
			place(client, player);
		}

		if (ClientKeyBindings.BUILD_CYCLE_FACING.consumeClick()) {
			cyclePlacementFacing(client, ClientKeyBindings.isModifierDown(client) ? -1 : 1);
		}

		boolean breakDown = ClientKeyBindings.BUILD_BREAK.isDown();
		if (breakDown) {
			breakBlock(client, player, !breakHeld);
		} else if (breakHeld) {
			client.gameMode.stopDestroyBlock();
		}
		breakHeld = breakDown;

		if (ClientKeyBindings.BUILD_WALK_TO_CURSOR.consumeClick()) {
			walkToCursor(client, player);
		}
	}

	/** Walks the player to within reach of the cursor, if a path there exists. */
	private static void walkToCursor(Minecraft client, LocalPlayer player) {
		if (isInReach(player, cursor)) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_already_in_reach"));
			return;
		}
		AutoWalkController.start(client, player, cursor, Component.translatable("united_minecraft.narrate.build_cursor_name"));
	}

	/** Steps {@link #selectedFacing} through null (automatic) and the six {@link Direction}s. */
	private static void cyclePlacementFacing(Minecraft client, int step) {
		int currentIndex = selectedFacing == null ? -1 : indexOf(FACING_CYCLE, selectedFacing);
		int nextIndex = currentIndex + step;
		if (nextIndex < -1) {
			nextIndex = FACING_CYCLE.length - 1;
		} else if (nextIndex >= FACING_CYCLE.length) {
			nextIndex = -1;
		}
		selectedFacing = nextIndex == -1 ? null : FACING_CYCLE[nextIndex];

		Component message = selectedFacing == null
				? Component.translatable("united_minecraft.narrate.build_facing_auto")
				: Component.translatable("united_minecraft.narrate.build_placement_facing", directionName(selectedFacing));
		client.getNarrator().saySystemNow(message);
	}

	private static int indexOf(Direction[] values, Direction target) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == target) {
				return i;
			}
		}
		return -1;
	}

	private static Component directionName(Direction direction) {
		String key = switch (direction) {
			case NORTH -> "united_minecraft.direction.north";
			case SOUTH -> "united_minecraft.direction.south";
			case EAST -> "united_minecraft.direction.east";
			case WEST -> "united_minecraft.direction.west";
			case UP -> "united_minecraft.direction.up";
			case DOWN -> "united_minecraft.direction.down";
		};
		return Component.translatable(key);
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
	 * that the cursor is replaceable and has <em>some</em> non-replaceable neighbor - it doesn't,
	 * and can't cheaply, replicate the actual item's own placement rules ({@code canSurvive},
	 * {@code isUnobstructed}, orientation via {@code getStateForPlacement}), which can still
	 * reject a specific face those checks didn't rule out. Rather than commit to one
	 * analytically "best" face and quietly give up if vanilla's real pipeline disagrees, this
	 * tries every non-replaceable candidate face in priority order against the real {@code
	 * useItemOn} call and takes the first one vanilla actually accepts - the same "just try it
	 * and see" approach other block-placement utility mods use, since nothing short of the
	 * genuine placement call can fully predict whether a given face will be accepted. Requiring
	 * only "not replaceable" rather than a full, flat {@code isFaceSturdy} face deliberately
	 * allows attaching to things like a slab's or stair's half-height side - real vanilla
	 * placement doesn't require a sturdy face either, only that clicking it wouldn't itself
	 * replace something else (tall grass, snow layers, etc.) instead of the cursor.
	 *
	 * <p>With no {@link #selectedFacing} override, this fires immediately. With one set, it
	 * defers to {@link #startRotatedPlacement} instead - see that method for why placing with a
	 * forced rotation can't happen in the same tick the rotation is set.
	 *
	 * <p>A half slab already sitting at the cursor is a special case - see {@link
	 * #isMatchingHalfSlab} - and short-circuits straight to {@link #attemptSlabMerge} instead of
	 * the usual replace-and-search-for-a-neighbor flow below, since combining it into a double
	 * isn't a "place at the cursor" operation in the usual sense at all.
	 */
	private static void place(Minecraft client, LocalPlayer player) {
		if (!isInReach(player, cursor)) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_out_of_reach"));
			return;
		}

		InteractionHand bucketHand = bucketHand(player);
		if (bucketHand != null) {
			startBucketUse(player, bucketHand);
			return;
		}

		Level level = player.level();
		BlockState cursorState = level.getBlockState(cursor);
		if (player.getBoundingBox().intersects(cursor)) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_cannot_place"));
			return;
		}

		if (isMatchingHalfSlab(cursorState, player)) {
			attemptSlabMerge(client, player, cursorState);
			return;
		}
		if (!cursorState.canBeReplaced()) {
			if (isInteractable(cursorState.getBlock()) && interactWithCursor(client, player)) {
				narrateAfterAction(client, player);
			} else {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_cannot_place"));
			}
			return;
		}

		Block placing = placingBlock(player);
		if (placing == null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_not_a_block"));
			return;
		}

		if (selectedFacing != null && !facingIsOppositeOfClickedFace(placing)) {
			startRotatedPlacement(player);
			return;
		}
		attemptPlacementSequence(client, player);
	}

	/**
	 * Whether pressing Place should interact with the cursor's own block (open a chest,
	 * flip a lever, cycle a repeater's delay, etc.) instead of failing outright just because
	 * the cell isn't empty. Mirrors a real right-click's own block-before-item priority - see
	 * {@link #interactWithCursor} - but only actually attempted for blocks that define click
	 * behavior in the first place (see {@link #declaresUseWithoutItem}), so an ordinary solid
	 * block (stone, dirt) with a placeable item in hand still narrates "Can't place there"
	 * instead of silently placing on top of it, which a real click's item-placement fallback
	 * would otherwise do for a block with no click behavior of its own.
	 */
	private static boolean isInteractable(Block block) {
		return INTERACTABLE_CACHE.computeIfAbsent(block, BuildModeController::declaresUseWithoutItem);
	}

	/**
	 * Whether {@code block}'s own class (or one of its superclasses, short of {@link
	 * BlockBehaviour} itself) overrides {@code useWithoutItem} - the exact same method vanilla's
	 * client dispatches to on a real right-click once it's decided the block, not the held item,
	 * owns the interaction. A plain block like stone or dirt never overrides it and just inherits
	 * {@link BlockBehaviour}'s no-op default, so checking for an override is a generic stand-in
	 * for "does right-clicking this empty-handed actually do something" - without hand-maintaining
	 * a list of which vanilla (or modded) blocks that's true for. {@code getDeclaredMethod} only
	 * needs to see the method exists, not call it, so the fact that it's {@code protected}
	 * doesn't matter here.
	 */
	private static boolean declaresUseWithoutItem(Block block) {
		for (Class<?> type = block.getClass(); type != null && type != BlockBehaviour.class; type = type.getSuperclass()) {
			try {
				type.getDeclaredMethod("useWithoutItem", BlockState.class, Level.class, BlockPos.class, Player.class, BlockHitResult.class);
				return true;
			} catch (NoSuchMethodException ignored) {
				// Not declared at this level - keep walking up toward BlockBehaviour.
			}
		}
		return false;
	}

	/**
	 * Interacts with the cursor's own block exactly as a real right-click would - through the
	 * same real placement call {@link #attemptPlace} (and thus {@link #place}'s other callers)
	 * already use, just aimed at the cursor's own position instead of a neighbor's. Which face
	 * gets passed barely matters here: none of {@link #isInteractable}'s block types change
	 * behavior by clicked face, unlike actual placement.
	 */
	private static boolean interactWithCursor(Minecraft client, LocalPlayer player) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cursor), Direction.UP, cursor, false);
		return attemptPlace(client, player, hit);
	}

	/**
	 * Whether the cursor already holds a non-double half slab matching whatever slab item the
	 * player's holding. Vanilla's own {@code SlabBlock.canBeReplaced} only allows combining two
	 * matching halves into a double slab via this exact combination - the plain, context-less
	 * {@link BlockState#canBeReplaced()} check {@link #place} otherwise gates on has no idea
	 * about item-specific replaceability like this and always reports false for an existing
	 * slab, which would wrongly reject the merge before ever trying the real placement call.
	 */
	private static boolean isMatchingHalfSlab(BlockState cursorState, LocalPlayer player) {
		return cursorState.getBlock() instanceof SlabBlock
				&& cursorState.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
				&& cursorState.is(placingBlock(player));
	}

	/**
	 * Combines the cursor's existing half slab into a double by clicking it directly - not a
	 * neighbor, the slab itself - on whichever face vanilla's merge logic actually keys off:
	 * the top face of a bottom half, or the bottom face of a top half (any other face, or a
	 * side face outside its own half's Y range, fails the merge - see {@code
	 * SlabBlock.canBeReplaced}, which this only needs to satisfy, not replicate).
	 */
	private static void attemptSlabMerge(Minecraft client, LocalPlayer player, BlockState cursorState) {
		Direction face = cursorState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? Direction.UP : Direction.DOWN;
		Vec3 hitLocation = Vec3.atCenterOf(cursor).add(0, face.getStepY() * (0.5 - FACE_EPSILON), 0);
		BlockHitResult hit = new BlockHitResult(hitLocation, face, cursor, false);
		if (attemptPlace(client, player, hit)) {
			narrateAfterAction(client, player);
		} else {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_cannot_place"));
		}
	}

	/**
	 * Fakes the player's rotation to produce {@link #selectedFacing}, then waits out {@link
	 * #ROTATION_SYNC_DELAY_TICKS} before {@link #tick} actually fires the placement - rather
	 * than placing immediately, the way an unrotated placement does.
	 *
	 * <p>The reason: {@code useItemOn}'s block orientation isn't decided client-side. It's
	 * previewed locally right away, but the authoritative result comes from the server
	 * recomputing it from whatever rotation <em>it</em> has on record for the player - which
	 * only updates when {@link LocalPlayer}'s own per-tick netcode notices the rotation changed
	 * and sends a packet, a step that happens earlier in the tick than this controller runs.
	 * Faking the rotation and placing within the same tick sends the placement packet before
	 * that rotation update packet even exists, so the server places using the <em>old</em>
	 * rotation and immediately corrects the block back to it - indistinguishable from the
	 * override doing nothing. Waiting a couple of ticks first lets that rotation packet actually
	 * go out - and since packets on one connection are delivered in the order they're sent, the
	 * placement packet sent afterward is guaranteed to arrive at the server after its rotation
	 * update already has, and gets the answer we asked for.
	 */
	private static void startRotatedPlacement(LocalPlayer player) {
		if (pendingPlaceTicks >= 0) {
			// Already waiting on one - a second press before it fires would just desync the
			// saved rotation to restore afterward.
			return;
		}
		pendingSavedYaw = player.getYRot();
		pendingSavedPitch = player.getXRot();
		faceDirection(player, lookDirectionFor(selectedFacing, placingBlock(player)));
		pendingAction = PendingAction.PLACE;
		pendingPlaceTicks = ROTATION_SYNC_DELAY_TICKS;
	}

	/**
	 * Buckets (filling or emptying alike) don't implement vanilla's block-targeted {@code
	 * useOn} at all - only the general, no-target {@code use()}, which does its own internal
	 * raycast from the player's real eye position and rotation rather than accepting a
	 * manufactured {@link BlockHitResult} the way every other item {@link #attemptPlacementSequence}
	 * places does. There's no way around that: unlike solid blocks, water/lava placement has no
	 * click-a-neighboring-face alternative in vanilla, so this fakes the player's rotation to
	 * look exactly at the cursor - the same real, unobstructed line of sight a sighted player
	 * pouring into a hole from directly above already needs, this just aims it for you - then
	 * defers to {@link #attemptBucketUse} via the same rotation-sync delay {@link
	 * #startRotatedPlacement} needs and for the same reason.
	 */
	private static void startBucketUse(LocalPlayer player, InteractionHand hand) {
		if (pendingPlaceTicks >= 0) {
			return;
		}
		pendingSavedYaw = player.getYRot();
		pendingSavedPitch = player.getXRot();
		CameraUtil.aimAt(player, Vec3.atCenterOf(cursor));
		pendingAction = PendingAction.BUCKET;
		pendingBucketHand = hand;
		pendingPlaceTicks = ROTATION_SYNC_DELAY_TICKS;
	}

	/** Advances an action started by {@link #startRotatedPlacement} or {@link #startBucketUse}, firing it once the sync delay elapses. */
	private static void tickPendingPlacement(Minecraft client, LocalPlayer player) {
		if (pendingPlaceTicks < 0) {
			return;
		}
		if (pendingPlaceTicks > 0) {
			pendingPlaceTicks--;
			return;
		}
		pendingPlaceTicks = -1;
		try {
			switch (pendingAction) {
				case PLACE -> attemptPlacementSequence(client, player);
				case BUCKET -> attemptBucketUse(client, player);
			}
		} finally {
			player.setYRot(pendingSavedYaw);
			player.setXRot(pendingSavedPitch);
			player.setOldRot();
			player.setYHeadRot(pendingSavedYaw);
		}
	}

	/**
	 * Fires the real, general item-use call {@link #startBucketUse} rotated the player for -
	 * the bucket's own raycast (now aimed exactly at the cursor) decides what actually happens,
	 * same as {@link #attemptPlace} defers to {@code useItemOn} for every other item.
	 */
	private static void attemptBucketUse(Minecraft client, LocalPlayer player) {
		InteractionResult result = client.gameMode.useItem(player, pendingBucketHand);
		if (result instanceof InteractionResult.Success success) {
			if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
				player.swing(pendingBucketHand);
			}
			narrateAfterAction(client, player);
		} else {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_cannot_place"));
		}
	}

	/** Whichever hand holds a bucket (filling or emptying alike), main hand first - null if neither does. */
	private static InteractionHand bucketHand(LocalPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).getItem() instanceof BucketItem) {
				return hand;
			}
		}
		return null;
	}

	/** Tries every candidate face in {@link #faceTryOrder} against the real placement call. */
	private static void attemptPlacementSequence(Minecraft client, LocalPlayer player) {
		Level level = player.level();
		for (Direction face : faceTryOrder(placingBlock(player))) {
			BlockPos neighborPos = cursor.relative(face);
			BlockState neighborState = level.getBlockState(neighborPos);
			if (neighborState.canBeReplaced()) {
				continue;
			}
			Vec3 hitLocation = Vec3.atCenterOf(cursor).add(
					face.getStepX() * (0.5 - FACE_EPSILON),
					face.getStepY() * (0.5 - FACE_EPSILON),
					face.getStepZ() * (0.5 - FACE_EPSILON));
			BlockHitResult hit = new BlockHitResult(hitLocation, face.getOpposite(), neighborPos, false);
			if (attemptPlace(client, player, hit)) {
				narrateAfterAction(client, player);
				return;
			}
		}
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_cannot_place"));
	}

	/**
	 * {@link #FACE_PRIORITY}, but with the neighbor that has {@link #selectedFacing} as its own
	 * face (if set) moved to the front.
	 *
	 * <p>Each entry in this array is "which direction from the cursor to find the neighbor to
	 * place against" - the opposite of the face of the neighbor you actually end up touching
	 * (a neighbor to your west is the one whose <em>east</em> face you're placing against). That
	 * indirection is fine for {@link #FACE_PRIORITY}'s own entries, which were never meant to
	 * name a face at all, just an internal search order - but {@link #selectedFacing} is the
	 * direction the placed block should actually end up facing, so it needs converting.
	 *
	 * <p>For most directional blocks (attach-to-surface ones like torches/buttons/ladders, whose
	 * {@code FACING} equals the clicked face directly, and rotation-based ones like
	 * dispensers/pistons, where the clicked face doesn't affect the result at all) that means
	 * looking for the neighbor to the <em>opposite</em> side of the cursor - to attach to a
	 * neighbor's west face, look for that neighbor to the east. {@link
	 * #facingIsOppositeOfClickedFace} blocks invert that: their own placement logic already
	 * flips the clicked face once, so this has to search on the <em>same</em> side as {@link
	 * #selectedFacing} instead, or the result comes out backwards.
	 */
	private static Direction[] faceTryOrder(Block block) {
		if (selectedFacing == null) {
			return FACE_PRIORITY;
		}
		Direction searchDirection = facingIsOppositeOfClickedFace(block) ? selectedFacing : selectedFacing.getOpposite();
		Direction[] order = new Direction[FACE_PRIORITY.length];
		order[0] = searchDirection;
		int i = 1;
		for (Direction face : FACE_PRIORITY) {
			if (face != searchDirection) {
				order[i++] = face;
			}
		}
		return order;
	}

	/** Whichever hand actually holds something placeable, main hand first - null if neither does. */
	private static Block placingBlock(LocalPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof BlockItem blockItem) {
				return blockItem.getBlock();
			}
		}
		return null;
	}

	// Most directional blocks' getStateForPlacement stores FACING as the opposite of the
	// player's look direction - their "front" ends up facing back toward whoever placed them
	// (dispensers/droppers, pistons, repeaters, comparators, and more). Observer and stairs are
	// the two notable vanilla exceptions: an observer's arrow points the way you were looking,
	// and stairs ascend the way you were facing, matching how you'd naturally place either. This
	// generic heuristic can't know every block's own convention, so if a specific block doesn't
	// rotate as expected, it likely needs to join this exception list.
	private static boolean facingMirrorsPlayerLook(Block block) {
		return block instanceof ObserverBlock || block instanceof StairBlock;
	}

	/** The look direction that would make a block's real FACING placement logic land on {@code desired}. */
	private static Direction lookDirectionFor(Direction desired, Block block) {
		return block != null && facingMirrorsPlayerLook(block) ? desired : desired.getOpposite();
	}

	// Hopper is neither look-based nor a plain "FACING equals the clicked face" block: its own
	// getStateForPlacement ignores player rotation entirely and instead sets FACING to the
	// *opposite* of whichever face got clicked (forced to DOWN if that face is on the vertical
	// axis, which is why a hopper placed on top of a floor - the only sturdy neighbor being
	// straight down - always ends up facing down, matching vanilla). Faking rotation for it (as
	// done for every other directional block) does nothing, and feeding its own FACING straight
	// into faceTryOrder's usual "click the opposite side" search produces the exact opposite of
	// the requested direction. Add any future block with the same clicked-face-opposite
	// convention here.
	private static boolean facingIsOppositeOfClickedFace(Block block) {
		return block instanceof HopperBlock;
	}

	/** Points the player exactly at {@code direction} - yaw/pitch, no smoothing, for one placement call. */
	private static void faceDirection(LocalPlayer player, Direction direction) {
		float yaw = switch (direction) {
			case SOUTH -> 0.0f;
			case WEST -> 90.0f;
			case NORTH -> 180.0f;
			case EAST -> 270.0f;
			// Yaw doesn't matter when looking straight up/down - keep whatever it already was.
			case UP, DOWN -> player.getYRot();
		};
		float pitch = switch (direction) {
			case UP -> -90.0f;
			case DOWN -> 90.0f;
			default -> 0.0f;
		};
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setOldRot();
		player.setYHeadRot(yaw);
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
	 *
	 * <p>Bypassing the raycast removes the "must be looking at it" restriction, but not the
	 * "must actually be able to see it at all" one - unlike a real raycast, {@code
	 * startDestroyBlock}/{@code continueDestroyBlock} don't check line of sight themselves,
	 * so without an explicit {@link OreDetection#isExposed} check here the cursor could mine
	 * straight through walls to something otherwise fully sealed off, the one thing every
	 * other "don't x-ray" feature in this mod (Ores, Liquids, Mining Radar) deliberately rules
	 * out.
	 */
	private static void breakBlock(Minecraft client, LocalPlayer player, boolean justStarted) {
		if (!isInReach(player, cursor)) {
			if (justStarted) {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_out_of_reach"));
			} else {
				// Reach was lost mid-hold (e.g. the player moved away) - cancel cleanly rather
				// than leaving mining state stuck on with nothing progressing it further.
				client.gameMode.stopDestroyBlock();
			}
			return;
		}
		Level level = player.level();
		if (level.getBlockState(cursor).isAir()) {
			if (justStarted) {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_cannot_break"));
			}
			return;
		}
		if (!OreDetection.isExposed(level, cursor, player.getEyePosition())) {
			if (justStarted) {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_not_visible"));
			} else {
				// Line of sight was lost mid-hold (e.g. another block was placed in the way) -
				// cancel cleanly, same as losing reach above.
				client.gameMode.stopDestroyBlock();
			}
			return;
		}

		if (justStarted) {
			client.gameMode.startDestroyBlock(cursor, Direction.UP);
		} else {
			client.gameMode.continueDestroyBlock(cursor, Direction.UP);
		}

		if (level.getBlockState(cursor).isAir()) {
			narrateAfterAction(client, player);
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
		// The cursor is free to roam the whole grid regardless of reach - place()/breakBlock()
		// check reach for themselves at the point it actually matters. Only the grid's own
		// horizontal bounds and the world's real height limits stop movement outright.
		if (Math.abs(candidate.getX() - anchorX) > GRID_RADIUS || Math.abs(candidate.getZ() - anchorZ) > GRID_RADIUS) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_edge_of_area"));
			return false;
		}
		Level level = player.level();
		if (candidate.getY() < level.getMinY() || candidate.getY() > level.getMaxY()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_edge_of_area"));
			return false;
		}
		cursor = candidate;
		return true;
	}

	private static boolean isInReach(LocalPlayer player, BlockPos pos) {
		return player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= player.blockInteractionRange();
	}

	/**
	 * Re-narrates the cursor after a successful place/break/interact, gated behind {@link
	 * UnitedMinecraftConfig#buildModeActionNarrationEnabled} - unlike the movement narration
	 * in {@link #tick}, which always fires (that's the only way to hear where the cursor is at
	 * all), this one just confirms an action's own result, which some players already know from
	 * the sound/visual feedback and would rather not hear repeated on every single action.
	 */
	private static void narrateAfterAction(Minecraft client, LocalPlayer player) {
		if (UnitedMinecraftConfig.get().buildModeActionNarrationEnabled) {
			client.getNarrator().saySystemNow(describeCursor(player));
		}
	}

	private static Component describeCursor(LocalPlayer player) {
		Level level = player.level();
		BlockState state = level.getBlockState(cursor);
		Component blockName = state.getBlock().getName();
		MutableComponent message = Component.translatable(
				"united_minecraft.narrate.build_cursor", blockName, cursor.getX(), cursor.getY(), cursor.getZ());
		if (!isInReach(player, cursor)) {
			// Not actionable yet either way, whatever the block - lead with that rather than
			// also claiming "Placeable" for something you can't actually place at right now.
			message = message.append(Component.literal(" ")).append(Component.translatable("united_minecraft.narrate.build_out_of_reach"));
		} else if (isPlaceable(level, player)) {
			message = message.append(Component.literal(" ")).append(Component.translatable("united_minecraft.narrate.build_placeable"));
		}

		Direction facing = directionPropertyOf(state);
		if (facing != null) {
			message = message.append(Component.literal(" "))
					.append(Component.translatable("united_minecraft.narrate.build_facing", directionName(facing)));
		}
		if (level.hasNeighborSignal(cursor)) {
			message = message.append(Component.literal(" ")).append(Component.translatable("united_minecraft.narrate.build_powered"));
		}
		if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
			message = message.append(Component.literal(" ")).append(Component.translatable("united_minecraft.narrate.build_double_slab"));
		}
		if (ScannerController.isCrop(state.getBlock()) && ScannerController.isRipe(state)) {
			message = message.append(Component.literal(" ")).append(Component.translatable("united_minecraft.narrate.scanner_ripe"));
		}
		if (state.getBlock() instanceof RepeaterBlock) {
			message = message.append(Component.literal(" ")).append(
					Component.translatable("united_minecraft.narrate.build_repeater_delay", state.getValue(RepeaterBlock.DELAY)));
		}
		if (state.getBlock() instanceof ComparatorBlock) {
			Component mode = state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT
					? Component.translatable("united_minecraft.narrate.build_comparator_subtract")
					: Component.translatable("united_minecraft.narrate.build_comparator_compare");
			message = message.append(Component.literal(" ")).append(mode);
		}
		if (state.getBlock() instanceof DaylightDetectorBlock && state.getValue(DaylightDetectorBlock.INVERTED)) {
			message = message.append(Component.literal(" ")).append(Component.translatable("united_minecraft.narrate.build_daylight_inverted"));
		}
		return message;
	}

	/** The block's own {@code FACING}/{@code HORIZONTAL_FACING} value, whichever it has - null if neither. */
	private static Direction directionPropertyOf(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property.getValueClass() == Direction.class) {
				return (Direction) getValue(state, property);
			}
		}
		return null;
	}

	private static <T extends Comparable<T>> T getValue(BlockState state, Property<T> property) {
		return state.getValue(property);
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

	// Checked in this order when looking for a non-replaceable neighbor to place against:
	// sideways neighbors first (attaching to whatever you just built next to the cursor is
	// almost always the intent once one exists), then the ground below, then finally the
	// block above as a last resort.
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
	 *
	 * <p>Requires the neighbor to merely be non-replaceable, not a full {@code isFaceSturdy}
	 * face - matching {@link #attemptPlacementSequence}'s own, less restrictive requirement
	 * (see {@link #place}'s doc for why), so this narrates "Placeable" in exactly the cases
	 * a real placement attempt would actually succeed - e.g. against a slab's or stair's
	 * half-height side, not just a full cube.
	 */
	private static Direction findSupportFace(Level level, BlockPos pos) {
		for (Direction face : FACE_PRIORITY) {
			BlockPos neighborPos = pos.relative(face);
			BlockState neighborState = level.getBlockState(neighborPos);
			if (!neighborState.canBeReplaced()) {
				return face;
			}
		}
		return null;
	}

	/** What to actually do once a rotation faked by {@link #startRotatedPlacement}/{@link #startBucketUse} has synced. */
	private enum PendingAction {
		PLACE, BUCKET
	}
}
