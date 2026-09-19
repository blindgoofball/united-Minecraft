package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.List;

import com.mojang.brigadier.suggestion.Suggestion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.network.chat.Component;

/**
 * Vanilla narrates command-suggestion cycling with a direct {@code GameNarrator.saySystemNow}
 * call from {@code select}/{@code cycle}, completely independent of {@code Screen}'s own
 * narration-state machinery - and by design never narrates the very first suggestion that
 * appears as you type: its own dedup check ({@code lastNarratedEntry == current}) starts out
 * equal, so that initial call is silently skipped. That leaves suggestion narration relying
 * entirely on an unscheduled, easily-preempted direct call, which is fragile next to our own
 * narration in {@link EditBoxMixin} - both ultimately go through the same interrupt-and-speak
 * narrator call, so whichever fires last wins. Narrating explicitly here - the top suggestion the
 * moment a fresh list appears, and the chosen one the moment it's accepted - makes suggestion
 * narration reliable instead of depending on the timing of two uncoordinated narrator calls.
 */
@Mixin(CommandSuggestions.SuggestionsList.class)
public abstract class SuggestionsListMixin {
	@Shadow
	@Final
	private List<Suggestion> suggestionList;

	@Shadow
	private int current;

	@Shadow
	protected abstract Component getNarrationMessage();

	@Inject(method = "<init>", at = @At("RETURN"))
	private void unitedMinecraft$narrateFirstSuggestion(CallbackInfo ci) {
		Minecraft.getInstance().getNarrator().saySystemNow(getNarrationMessage());
	}

	@Inject(method = "useSuggestion", at = @At("HEAD"))
	private void unitedMinecraft$narrateAcceptedSuggestion(CallbackInfo ci) {
		Minecraft.getInstance().getNarrator().saySystemNow(suggestionList.get(current).getText());
	}
}
