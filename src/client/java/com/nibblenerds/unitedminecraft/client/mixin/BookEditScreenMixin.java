package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;

/**
 * Unlike a written book's read-only view ({@link BookViewScreenMixin}), the book-and-quill
 * editor's own narration never includes page text at all, not even passively - {@code
 * getNarrationMessage} only ever composes the title and page number. {@code updatePageContent}
 * is the single place that both loads the current page when the screen first opens and reloads
 * it on every page switch (via {@code pageBack}/{@code pageForward}), so narrating there covers
 * both cases: opening the book reads page one, and turning the page reads the new one.
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {
	@Shadow
	private List<String> pages;

	@Shadow
	private int currentPage;

	@Shadow
	protected abstract Component getPageNumberMessage();

	@Inject(method = "updatePageContent", at = @At("TAIL"))
	private void unitedMinecraft$narratePage(CallbackInfo ci) {
		Minecraft.getInstance().getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.book_page", getPageNumberMessage(), pages.get(currentPage)));
	}
}
