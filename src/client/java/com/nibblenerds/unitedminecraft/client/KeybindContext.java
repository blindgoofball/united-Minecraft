package com.nibblenerds.unitedminecraft.client;

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
 * <p>Extensible for future mode-scoped rebinding (e.g. inventory-screen controls) without any
 * change to this system itself - add a value here and an {@link #isActive} case for it.
 */
public enum KeybindContext {
	/** Always eligible; overlaps with every other context for conflict-detection purposes. */
	GLOBAL,
	AUTO_WALK,
	WATER_ESCAPE,
	TRAIL,
	SCANNER_LOCKED,
	COMBAT_MODE,
	BUILD_MODE,
	/** None of the above own rotation - {@link AccessibilityTickHandler#onEndTick}'s own "else" branch. */
	NORMAL_LOOK;

	public boolean isActive() {
		return switch (this) {
			case GLOBAL -> true;
			case AUTO_WALK -> AutoWalkController.isActive();
			case WATER_ESCAPE -> WaterExitController.isActive();
			case TRAIL -> TrailController.isActive();
			case SCANNER_LOCKED -> ScannerController.isLocked();
			case COMBAT_MODE -> CombatModeController.isActive();
			case BUILD_MODE -> BuildModeController.isActive();
			case NORMAL_LOOK -> !AutoWalkController.isActive() && !WaterExitController.isActive()
					&& !TrailController.isActive() && !ScannerController.isLocked()
					&& !CombatModeController.isActive() && !BuildModeController.isActive();
		};
	}

	public static boolean canOverlap(KeybindContext a, KeybindContext b) {
		return a == GLOBAL || b == GLOBAL || a == b;
	}
}
