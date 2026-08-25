package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

/**
 * Vanilla folds the current page's text into a written book's own screen-title narration (see
 * {@code BookViewScreen.getNarrationMessage}), but nothing re-triggers that narration when you
 * actually turn a page - the page-turn buttons and Page Up/Down just update {@code currentPage}
 * silently, leaving the new page's content to be spoken only whenever something else happens to
 * refresh the screen's narration. Narrated explicitly here instead of relying on that - the same
 * "don't depend on a fragile automatic path" approach already used for command-suggestion
 * narration (see {@link SuggestionsListMixin}).
 */
@Mixin(BookViewScreen.class)
public abstract class BookViewScreenMixin {
	@Shadow
	private BookViewScreen.BookAccess bookAccess;

	@Shadow
	private int currentPage;

	@Shadow
	protected abstract Component getPageNumberMessage();

	/** {@code setPage} is the single choke point for every way the page can change - buttons, Page Up/Down, and jumping to a specific page. */
	@Inject(method = "setPage", at = @At("TAIL"))
	private void unitedMinecraft$narratePageOnTurn(int page, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			// Already on that page (e.g. the button was disabled but still triggered) - nothing changed to narrate.
			return;
		}
		Minecraft.getInstance().getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.book_page", getPageNumberMessage(), bookAccess.getPage(currentPage)));
	}
}
