package com.nibblenerds.unitedminecraft.client;

/**
 * One of this mod's rebindable actions - replaces vanilla {@link net.minecraft.client.KeyMapping}
 * as the field type in {@link ClientKeyBindings}. Held/justPressed state lives directly on the
 * instance (previously tracked externally in an {@code IdentityHashMap} against vanilla
 * {@code KeyMapping}s, back when this mod didn't own the binding type itself).
 *
 * <p>Resolution against other actions sharing the same primary key (context eligibility,
 * modifier-subset matching, most-specific-wins) happens centrally in
 * {@link ClientKeyBindings#updateAll()} - this class itself just tracks whether it won the
 * tick, and callers reading {@link #isDown()} or {@link ClientKeyBindings#pressed} never need
 * to know that resolution happened at all.
 */
public final class KeybindAction {
	private final String id;
	private final Keybind default_;
	private final KeybindContext context;
	private final KeybindCategory category;
	private final ContainerScope scope;

	private Keybind current;
	private boolean held;
	private boolean justPressed;

	KeybindAction(String id, KeybindCategory category, Keybind default_) {
		this(id, category, default_, KeybindContext.GLOBAL);
	}

	KeybindAction(String id, KeybindCategory category, Keybind default_, KeybindContext context) {
		this(id, category, default_, context, null);
	}

	/** See {@link ContainerScope}'s own doc for what {@code scope} means and when it should be non-null. */
	KeybindAction(String id, KeybindCategory category, Keybind default_, KeybindContext context, ContainerScope scope) {
		this.id = id;
		this.category = category;
		this.default_ = default_;
		this.context = context;
		this.scope = scope;
		this.current = default_;
	}

	public String id() {
		return id;
	}

	public Keybind default_() {
		return default_;
	}

	public KeybindContext context() {
		return context;
	}

	public KeybindCategory category() {
		return category;
	}

	/** {@code null} unless this is a {@link KeybindContext#CONTAINER_SCREEN} action reachable from only one {@link ContainerScope}. */
	public ContainerScope scope() {
		return scope;
	}

	public Keybind current() {
		return current;
	}

	void setCurrent(Keybind current) {
		this.current = current;
	}

	void resetToDefault() {
		this.current = default_;
	}

	boolean isEligibleNow() {
		return context.isActive();
	}

	/** Set once per tick by {@link ClientKeyBindings#updateAll()} after resolution - true only if this action won its key this tick. */
	void updateHeld(boolean down) {
		justPressed = down && !held;
		held = down;
	}

	public boolean isDown() {
		return held;
	}

	public boolean isJustPressed() {
		return justPressed;
	}

	void resetPressState() {
		held = false;
		justPressed = false;
	}

	/**
	 * Clears just-this-tick's justPressed without touching {@link #held} - see {@link
	 * ClientKeyBindings#suppressJustPressedAfterScreenClose()}'s own doc for why this exists and
	 * why it's not the same thing as {@link #resetPressState()}.
	 */
	void suppressJustPressedThisTick() {
		justPressed = false;
	}
}
