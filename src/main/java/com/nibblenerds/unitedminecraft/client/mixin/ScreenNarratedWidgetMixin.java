package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationSupplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Replaces vanilla's own "describe whatever's focused" narration with a direct read of
 * whichever widget genuinely has keyboard focus right now, instead of vanilla's per-row/list
 * composition (Screen -> list -> row -> widget). Narration logs captured while navigating the
 * Music &amp; Sounds options screen showed that composition regularly drops a row's actual
 * name/value - sometimes down to just "Selected list row 4 out of 9" with nothing else - while
 * other rows narrate correctly seconds later. Vanilla still tracks which widget is genuinely
 * focused correctly throughout (that's how it knows which row you're on at all, even when it
 * fails to describe it); what's unreliable is the self-reporting layer each row/widget uses to
 * compose itself into the combined narration text.
 *
 * <p>This replaces only that composition step, in the one place Screen itself calls it, so
 * everything else about vanilla's narration - timing, the screen title, deduping repeats,
 * announcing whatever's focused by default when a screen first opens - is untouched, and every
 * screen built on this system (Key Binds, Options, World Selection, Resource Packs, ...) is
 * covered without per-screen code. Keeping this to a single call site also avoids narrating
 * twice: an earlier version of this fix drove a second, separate narration pass alongside
 * vanilla's own, which worked but spoke everything twice wherever vanilla's original
 * composition happened to still succeed.
 */
@Mixin(Screen.class)
public abstract class ScreenNarratedWidgetMixin {
	@Inject(method = "updateNarratedWidget", at = @At("HEAD"), cancellable = true)
	private void unitedMinecraft$describeFocusedWidgetDirectly(NarrationElementOutput output, CallbackInfo ci) {
		GuiEventListener current = (GuiEventListener) (Object) this;
		GuiEventListener next;
		while (current instanceof ContainerEventHandler handler && (next = handler.getFocused()) != null) {
			current = next;
		}

		if (current instanceof NarrationSupplier narratable) {
			narratable.updateNarration(output.nest());
		} else {
			// Matches vanilla's own fallback for "nothing focused yet" (e.g. right after a
			// screen opens via mouse, before Tab has been pressed at all).
			output.add(NarratedElementType.USAGE, Component.translatable("narration.component_list.usage"));
		}
		ci.cancel();
	}
}
