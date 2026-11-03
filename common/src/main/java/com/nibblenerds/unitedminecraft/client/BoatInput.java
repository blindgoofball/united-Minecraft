package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

/**
 * Wraps the player's real input while they steer a boat, so {@link BoatController} can hold left or
 * right on their behalf. Everything else - forward, back, sneaking to get out - passes straight
 * through, and so does any left or right the player presses themselves.
 */
final class BoatInput extends ClientInput {
	private final ClientInput real;
	private int steer;

	BoatInput(ClientInput real) {
		this.real = real;
		this.keyPresses = real.keyPresses;
		this.moveVector = real.getMoveVector();
	}

	ClientInput real() {
		return real;
	}

	/** -1 holds left, 1 holds right, 0 holds nothing (the player's own keys still count either way). */
	void setSteer(int steer) {
		this.steer = steer;
	}

	/** Whether the player is turning with their own keys this tick. */
	boolean playerIsSteering() {
		Input keys = real.keyPresses;
		return keys.left() || keys.right();
	}

	@Override
	public void tick() {
		real.tick();
		Input keys = real.keyPresses;
		boolean left = keys.left() || (steer < 0 && !keys.right());
		boolean right = keys.right() || (steer > 0 && !keys.left());
		this.keyPresses = new Input(keys.forward(), keys.backward(), left, right, keys.jump(), keys.shift(), keys.sprint());
		this.moveVector = real.getMoveVector();
	}

	@Override
	public void makeJump() {
		real.makeJump();
	}
}
