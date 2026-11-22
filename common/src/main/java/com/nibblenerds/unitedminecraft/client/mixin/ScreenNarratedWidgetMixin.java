package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nibblenerds.unitedminecraft.client.speech.NarrationRepeat;

import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationSupplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Vanilla describes whatever is focused, and does it well everywhere but in one place: a screen
 * built around a selection list (Key Binds, Options, World Selection, Resource Packs, ...). Narration
 * logs captured on the Music &amp; Sounds options screen showed that the list's composition (Screen
 * -> list -> row -> widget) regularly drops a row's actual name or value - sometimes down to just
 * "Selected list row 4 out of 9" with nothing else - while other rows narrate correctly seconds
 * later. Vanilla still tracks which widget is genuinely focused correctly throughout; what is
 * unreliable is the self-reporting layer each row uses to compose itself.
 *
 * <p>So that is the only case this replaces. When the focus path passes through a selection list,
 * the focused widget is read directly, with the list's name ahead of it if it has one (vanilla
 * leaves every list's name empty). The name goes in one level below the screen title, which vanilla
 * keeps in the top-depth TITLE slot - writing there would swap the title out while a list is
 * focused and back in when focus leaves, which reads as a change both times - and the row one
 * level further down, so the name is spoken ahead of it, once on entering the list.
 *
 * <p>Everywhere else - inventories, the Recipe Book, tabs, plain screens - vanilla composes the
 * narration itself, with the names, positions and hints it adds, and only the repeat described
 * on {@link NarrationRepeat} is added around it.
 */
@Mixin(Screen.class)
public abstract class ScreenNarratedWidgetMixin {
	/** The widget narrated last, so a move onto a different one can be told from a change in the same one. */
	@Unique
	private GuiEventListener unitedMinecraft$lastNarratedWidget;

	@Inject(method = "updateNarratedWidget", at = @At("HEAD"), cancellable = true)
	private void unitedMinecraft$describeFocusedWidget(NarrationElementOutput output, CallbackInfo ci) {
		GuiEventListener current = (GuiEventListener) (Object) this;
		GuiEventListener next;
		Component listName = null;
		boolean inList = false;
		while (current instanceof ContainerEventHandler handler && (next = handler.getFocused()) != null) {
			current = next;
			if (current instanceof AbstractSelectionList<?> list) {
				inList = true;
				if (!list.getMessage().getString().isEmpty()) {
					listName = list.getMessage();
				}
			}
		}

		// A different widget is always worth saying, so the player knows they moved; the same
		// widget changing is left alone. Vanilla stays silent when the text matches what it last
		// said, which stepping between two identical widgets (two empty slots) does.
		boolean moved = current != unitedMinecraft$lastNarratedWidget;
		unitedMinecraft$lastNarratedWidget = current;

		if (!inList) {
			// Vanilla composes this one; only the repeat is ours, until the method returns.
			NarrationRepeat.force(moved);
			return;
		}

		if (current instanceof NarrationSupplier narratable) {
			NarrationElementOutput widgetOutput = output.nest();
			if (listName != null) {
				widgetOutput.add(NarratedElementType.TITLE, listName);
				widgetOutput = widgetOutput.nest();
			}
			NarrationRepeat.force(moved);
			try {
				narratable.updateNarration(widgetOutput);
			} finally {
				NarrationRepeat.force(false);
			}
		} else {
			// Matches vanilla's own fallback for "nothing focused yet" (e.g. right after a
			// screen opens via mouse, before Tab has been pressed at all).
			output.add(NarratedElementType.USAGE, Component.translatable("narration.component_list.usage"));
		}
		ci.cancel();
	}

	/** Ends the repeat started at the head when vanilla did the composing. */
	@Inject(method = "updateNarratedWidget", at = @At("RETURN"))
	private void unitedMinecraft$endRepeat(NarrationElementOutput output, CallbackInfo ci) {
		NarrationRepeat.force(false);
	}
}
