package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.nibblenerds.unitedminecraft.client.access.ComposesOwnNarration;

/**
 * The container inside a {@code ScrollableLayout}, which the Recipe Book's grid and the enchanting,
 * loom, stonecutter and trading screens use. It carries a grid or list position and a usage hint
 * for its choices - see {@link ComposesOwnNarration}.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ScrollableLayout$Container")
public abstract class ScrollableLayoutContainerMixin implements ComposesOwnNarration {
}
