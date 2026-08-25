package com.nibblenerds.unitedminecraft.client.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Vanilla's {@code EditBox} narration always re-composes "label. {full current value}" (see
 * {@code createNarrationMessage}) whenever the value changes, so every keystroke that edits the
 * field re-reads it from the start rather than announcing just what changed. This narrates only
 * the actual delta instead - the character/word the cursor stepped over, the text just typed,
 * pasted, or deleted, or the text just selected - and suppresses the full-field re-read that
 * would otherwise immediately follow it, matching how a screen reader echoes typing in any other
 * text box. The full "label. value" narration still fires normally the moment a field gains focus
 * (or is populated programmatically, e.g. Create World's default name) - only edit-triggered
 * re-narration is suppressed.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin extends AbstractWidget {
	@Shadow
	private String value;

	@Shadow
	private int cursorPos;

	@Shadow
	private int highlightPos;

	@Unique
	private boolean unitedMinecraft$editedSinceNarration;

	protected EditBoxMixin(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@Shadow
	public abstract boolean canConsumeInput();

	@Shadow
	public abstract int getWordPosition(int direction);

	@Shadow
	protected abstract int getCursorPos(int direction);

	@Shadow
	public abstract String getHighlighted();

	@Inject(method = "keyPressed", at = @At("HEAD"))
	private void unitedMinecraft$narrateCursorMovement(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!canConsumeInput() || Minecraft.getInstance().hasShiftDown()) {
			// Shift is held, so this is extending/shrinking a selection - let
			// unitedMinecraft$narrateSelection below handle that instead.
			return;
		}

		boolean byWord = Minecraft.getInstance().hasControlDown();
		switch (event.key()) {
			case GLFW.GLFW_KEY_LEFT -> unitedMinecraft$narrate(unitedMinecraft$textBetweenCursorAnd(
					byWord ? getWordPosition(-1) : getCursorPos(-1)));
			case GLFW.GLFW_KEY_RIGHT -> unitedMinecraft$narrate(unitedMinecraft$textBetweenCursorAnd(
					byWord ? getWordPosition(1) : getCursorPos(1)));
			case GLFW.GLFW_KEY_HOME -> {
				if (!value.isEmpty()) {
					unitedMinecraft$narrate(value.substring(0, 1));
				}
			}
			case GLFW.GLFW_KEY_END -> {
				if (!value.isEmpty()) {
					unitedMinecraft$narrate(value.substring(value.length() - 1));
				}
			}
			default -> {
			}
		}
	}

	@Inject(method = "keyPressed", at = @At("RETURN"))
	private void unitedMinecraft$narrateSelection(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!canConsumeInput()) {
			return;
		}
		String selected = getHighlighted();
		if (!selected.isBlank()) {
			unitedMinecraft$narrate(selected);
		}
	}

	/**
	 * Covers every way text gets removed - Backspace/Delete (via {@code deleteChars}) and
	 * Ctrl+Backspace/Ctrl+Delete word deletion (via {@code deleteWords}) both funnel into this
	 * single method with the position being deleted to. Deleting an active selection instead goes
	 * through {@code insertText("")} (see below), so it's deliberately not double-handled here.
	 */
	@Inject(method = "deleteCharsToPos", at = @At("HEAD"))
	private void unitedMinecraft$narrateErasedText(int pos, CallbackInfo ci) {
		if (value.isEmpty() || highlightPos != cursorPos) {
			return;
		}
		unitedMinecraft$narrate(unitedMinecraft$textBetweenCursorAnd(pos));
	}

	/**
	 * Covers every way text gets added - typing a character, pasting, and IME input all funnel
	 * through this method - as well as deleting an active selection, which vanilla implements as
	 * inserting an empty string over it.
	 */
	@Inject(method = "insertText", at = @At("HEAD"))
	private void unitedMinecraft$narrateInsertedText(String text, CallbackInfo ci) {
		if (text.isEmpty()) {
			if (highlightPos != cursorPos) {
				unitedMinecraft$narrate(getHighlighted());
			}
		} else {
			unitedMinecraft$narrate(text);
		}
	}

	/**
	 * Vanilla speaks "label. {full value}" again every time the value changes, which combined
	 * with the deltas narrated above would mean every edit gets spoken twice. Skip the full
	 * re-read exactly once per edit; narration triggered by anything else (gaining focus,
	 * a screen populating the field on open) narrates normally.
	 */
	@Inject(method = "updateWidgetNarration", at = @At("HEAD"), cancellable = true)
	private void unitedMinecraft$skipRedundantFullNarration(NarrationElementOutput output, CallbackInfo ci) {
		if (unitedMinecraft$editedSinceNarration) {
			unitedMinecraft$editedSinceNarration = false;
			ci.cancel();
		}
	}

	@Unique
	private String unitedMinecraft$textBetweenCursorAnd(int otherPos) {
		int start = Math.min(otherPos, cursorPos);
		int end = Math.max(otherPos, cursorPos);
		return start == end ? "" : value.substring(start, end);
	}

	@Unique
	private void unitedMinecraft$narrate(String text) {
		if (!text.isEmpty()) {
			unitedMinecraft$editedSinceNarration = true;
			Minecraft.getInstance().getNarrator().saySystemNow(text);
		}
	}
}
