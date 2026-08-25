package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nibblenerds.unitedminecraft.client.duck.MultilineTextFieldExt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;

/**
 * The book-and-quill page editor's actual text state (wrapped by {@code MultiLineEditBox}, the
 * narratable widget - see {@link MultiLineEditBoxMixin}). Same incremental-narration approach as
 * {@link EditBoxMixin}: narrate only the delta - the text stepped over, typed, pasted, or
 * deleted, or the text just selected - instead of vanilla's "re-read the whole page" narration,
 * which would otherwise fire on every keystroke the same way chat and other text fields did
 * before those were fixed.
 *
 * <p>Every cursor-moving key (Left/Right/Home/End, Ctrl+word-jump, and Up/Down between wrapped
 * lines) funnels through {@code seekCursor} here, unlike {@code EditBox} where each key needed
 * its own case - so this only needs to hook that one method, plus {@code insertText} (which
 * itself is what every insert, paste, and delete-of-a-selection funnels through - deleting
 * without a selection first creates one, then also calls {@code insertText("")}).
 */
@Mixin(MultilineTextField.class)
public abstract class MultilineTextFieldMixin implements MultilineTextFieldExt {
	@Shadow
	private String value;

	@Shadow
	private int cursor;

	@Shadow
	private int selectCursor;

	@Unique
	private int unitedMinecraft$cursorBeforeSeek;

	@Unique
	private boolean unitedMinecraft$editedSinceNarration;

	@Shadow
	public abstract boolean hasSelection();

	@Inject(method = "seekCursor", at = @At("HEAD"))
	private void unitedMinecraft$captureCursorBeforeSeek(Whence whence, int amount, CallbackInfo ci) {
		unitedMinecraft$cursorBeforeSeek = cursor;
	}

	@Inject(method = "seekCursor", at = @At("RETURN"))
	private void unitedMinecraft$narrateCursorMovement(Whence whence, int amount, CallbackInfo ci) {
		if (selectCursor != cursor) {
			// Shift extended (or shrank) a selection - narrate the whole current selection
			// rather than just the span this particular seek covered.
			unitedMinecraft$narrate(value.substring(Math.min(selectCursor, cursor), Math.max(selectCursor, cursor)));
			return;
		}
		unitedMinecraft$narrate(unitedMinecraft$textBetween(unitedMinecraft$cursorBeforeSeek, cursor));
	}

	/**
	 * Covers every way text gets added (typing, pasting) and, since deleting a selection is
	 * implemented as inserting an empty string over it, that case too. Plain deletion without a
	 * selection ({@code deleteText}) first creates one from the cursor, then also calls this.
	 */
	@Inject(method = "insertText", at = @At("HEAD"))
	private void unitedMinecraft$narrateInsertedText(String text, CallbackInfo ci) {
		if (text.isEmpty()) {
			if (hasSelection()) {
				unitedMinecraft$narrate(unitedMinecraft$textBetween(selectCursor, cursor));
			}
		} else {
			unitedMinecraft$narrate(text);
		}
	}

	/** Also called directly when a page switches to a different page's text - see {@link BookEditScreenMixin}. */
	@Inject(method = "setValue(Ljava/lang/String;Z)V", at = @At("TAIL"))
	private void unitedMinecraft$suppressFullNarrationAfterProgrammaticSet(String newValue, boolean resetCursor, CallbackInfo ci) {
		unitedMinecraft$editedSinceNarration = true;
	}

	@Unique
	private String unitedMinecraft$textBetween(int start, int end) {
		int from = Math.min(start, end);
		int to = Math.max(start, end);
		return from == to ? "" : value.substring(from, to);
	}

	@Unique
	private void unitedMinecraft$narrate(String text) {
		if (!text.isEmpty()) {
			unitedMinecraft$editedSinceNarration = true;
			Minecraft.getInstance().getNarrator().saySystemNow(text);
		}
	}

	@Override
	public boolean unitedMinecraft$consumeEditedFlag() {
		boolean edited = unitedMinecraft$editedSinceNarration;
		unitedMinecraft$editedSinceNarration = false;
		return edited;
	}
}
