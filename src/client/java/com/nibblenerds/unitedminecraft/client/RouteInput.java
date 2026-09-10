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
		this.keyPresses = new Input(true, false, false, false, jump, false, sprint);
		this.moveVector = new Vec2(0.0f, 1.0f);
	}
}
