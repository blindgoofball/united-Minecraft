package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * When a {@link KeybindAction} is eligible to fire, mirroring the mutually-exclusive
 * rotation-owner chain {@link AccessibilityTickHandler#onEndTick} already documents and
 * enforces (Auto-Walk > Scanner lock-on > Combat Mode > Build Mode > normal camera look) -
 * that existing chain is the source of truth here, this enum just lets a keybinding declare
 * which link in it applies.
 *
 * <p>Two actions bound to the identical chord only genuinely conflict
 * ({@link #canOverlap}) if their contexts could both be active at once - since the five
 * non-{@link #GLOBAL} contexts are pairwise mutually exclusive by construction, e.g.
 * {@code build_mode_recenter_cursor} (BUILD_MODE, Alt+I) never conflicts with some other
 * action a player rebinds to Alt+I outside Build Mode. Where two eligible actions still
 * share a primary key (e.g. plain arrow-key camera look, GLOBAL, vs. a NORMAL_LOOK snap-turn
 * chorded onto the same key), {@link ClientKeyBindings#updateAll()} resolves the winner by
 * specificity (most modifier bits required and currently satisfied wins), not by context -
 * see that method's doc.
 *
 * <p>Every context here except {@link #CONTAINER_SCREEN} also requires no screen to be open at
 * all (chat, a command, a sign, an inventory, the pause menu, etc.) - folded directly into {@link
 * #isActive()} rather than left to each call site in {@link AccessibilityTickHandler} to remember
 * individually. That used to be exactly the failure mode this had: most, but not reliably all, of
 * {@code AccessibilityTickHandler}'s own {@code ClientKeyBindings.pressed(...)} reads were
 * separately wrapped in a {@code client.gui.screen() == null} check, which meant a newly-added
 * action (or a call site someone forgot) could silently skip it and fire while the player was
 * typing in chat. Building the requirement into {@code isActive()} instead makes it structural:
 * {@link ClientKeyBindings#updateAll()} already routes every GLOBAL/mode-context action's
 * held/justPressed state through this method, so a screen being open now makes those actions
 * ineligible before any consumer gets a chance to forget to check. The {@code
 * client.gui.screen() == null} checks still present in {@code AccessibilityTickHandler} are
 * therefore redundant for gating keybind reads specifically - left in place because several of
 * them also gate non-keybind logic (ambient controller ticks, mode-dispatch) in the same
 * conditional, not because the keybind gating still depends on them.
 *
 * <p><b>{@link #CONTAINER_SCREEN} is the one exception to all of the above</b> - it's eligible
 * whenever a container screen (inventory, chest, anvil, etc.) is open, which can genuinely
 * coincide with e.g. {@link #BUILD_MODE} still being toggled on in the background. That's safe
 * here only because nothing reads a {@link #CONTAINER_SCREEN} action through {@link
 * ClientKeyBindings#updateAll()}'s polling/{@code isDown()} path the way every other context's
 * actions are - {@link MenuAccessibilityController} and {@link CreativeInventoryController}
 * dispatch those actions directly off the screen's own key-press events instead (matching each
 * event's key/modifiers against the action's current {@link Keybind} themselves), which only
 * ever runs while that screen genuinely owns keyboard input. Were a container-screen action ever
 * polled the same way as the others, two simultaneously-"active" non-{@link #GLOBAL} contexts
 * sharing a key would make {@link ClientKeyBindings#updateAll()}'s winner arbitrary (declaration
 * order) instead of well-defined - so keep new container-screen actions on that same
 * event-dispatch path rather than routing them through {@code isDown()}/{@code pressed()}.
 */
public enum KeybindContext {
	/** Eligible whenever no screen is open; overlaps with every other context for conflict-detection purposes. */
	GLOBAL,
	AUTO_WALK,
	WATER_ESCAPE,
	TRAIL,
	SCANNER_LOCKED,
	COMBAT_MODE,
	BUILD_MODE,
	/** None of the above own rotation - {@link AccessibilityTickHandler#onEndTick}'s own "else" branch. */
	NORMAL_LOOK,
	/** See this enum's own doc for why actions in this context must stay event-dispatched, never polled. */
	CONTAINER_SCREEN;

	public boolean isActive() {
		boolean noScreenOpen = Minecraft.getInstance().gui.screen() == null;
		return switch (this) {
			case GLOBAL -> noScreenOpen;
			case AUTO_WALK -> noScreenOpen && AutoWalkController.isActive();
			case WATER_ESCAPE -> noScreenOpen && WaterExitController.isActive();
			case TRAIL -> noScreenOpen && TrailController.isActive();
			case SCANNER_LOCKED -> noScreenOpen && ScannerController.isLocked();
			case COMBAT_MODE -> noScreenOpen && CombatModeController.isActive();
			case BUILD_MODE -> noScreenOpen && BuildModeController.isActive();
			case NORMAL_LOOK -> noScreenOpen && !AutoWalkController.isActive() && !WaterExitController.isActive()
					&& !TrailController.isActive() && !ScannerController.isLocked()
					&& !CombatModeController.isActive() && !BuildModeController.isActive();
			case CONTAINER_SCREEN -> Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen;
		};
	}

	public static boolean canOverlap(KeybindContext a, KeybindContext b) {
		return a == GLOBAL || b == GLOBAL || a == b;
	}
}
