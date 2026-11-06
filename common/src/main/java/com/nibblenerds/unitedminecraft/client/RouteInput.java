package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Reports "forward" (and optionally "jump"/"sprint") as held, exactly like real keyboard input
 * would - the synthetic {@link ClientInput} every route-following mode swaps into {@link
 * net.minecraft.client.player.LocalPlayer#input} while it drives movement. Shared by {@link
 * AutoWalkController}, {@link WaterExitController} and {@link TrailController}, which each used
 * to declare their own byte-identical copy of it.
 *
 * <p>Going through the same {@code Input} mechanism real keys use is what makes movement and
 * network sync just work, with no server cooperation - see {@link AutoWalkController}'s class
 * doc for the full reasoning.
 */
final class RouteInput extends ClientInput {
	/** Walk forward, jumping when {@code jump} - "jump" doubles as vanilla's swim-up input underwater. */
	void setWalking(boolean jump) {
		setWalking(jump, false);
	}

	void setWalking(boolean jump, boolean sprint) {
		this.queuedKeys = null;
		this.keyPresses = new Input(true, false, false, false, jump, false, sprint);
		this.moveVector = new Vec2(0.0f, 1.0f);
	}

	private Input queuedKeys;
	private Vec2 queuedMove;

	/**
	 * Ridden mount: push {@code forward} and/or hold the jump key, which a horse reads as charging a
	 * jump. Takes effect in {@link #tick()} rather than at once, the way a real keyboard's does: the
	 * player compares the jump key from before its input update with the one after it, and only sees
	 * the key come up (which is what launches the jump) if the update is where the keys change.
	 */
	void setMounted(boolean forward, boolean jump) {
		this.queuedKeys = new Input(forward, false, false, false, jump, false, false);
		this.queuedMove = new Vec2(0.0f, forward ? 1.0f : 0.0f);
	}

	@Override
	public void tick() {
		if (this.queuedKeys != null) {
			this.keyPresses = this.queuedKeys;
			this.moveVector = this.queuedMove;
			this.queuedKeys = null;
			this.queuedMove = null;
		}
	}

	/** Hold nothing at all - standing still while a door swings open. */
	void setIdle() {
		this.queuedKeys = null;
		this.keyPresses = Input.EMPTY;
		this.moveVector = Vec2.ZERO;
	}

	/**
	 * Inside a ladder or vine: jump climbs, and so does pushing forward into a wall, so descending
	 * means holding nothing at all (the player slides down on their own) and {@code forward} is
	 * only for nudging toward the cell's centre.
	 */
	void setClimbing(boolean up, boolean forward) {
		this.queuedKeys = null;
		this.keyPresses = new Input(forward, false, false, false, up, false, false);
		this.moveVector = new Vec2(0.0f, forward ? 1.0f : 0.0f);
	}
}
