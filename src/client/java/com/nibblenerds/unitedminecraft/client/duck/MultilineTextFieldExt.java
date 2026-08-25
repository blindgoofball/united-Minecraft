package com.nibblenerds.unitedminecraft.client.duck;

/**
 * Lets {@code MultiLineEditBoxMixin} (the narratable widget, {@code MultiLineEditBox}) find out
 * whether {@code MultilineTextFieldMixin} (the actual text state, {@code MultilineTextField})
 * already narrated the last change itself - the two live in different classes with no shared
 * supertype, so this is the bridge between them. Deliberately outside the mixin package (see
 * {@code united_minecraft.client.mixins.json}'s {@code "package"} entry) - Mixin reserves that
 * whole package for {@code @Mixin} classes and refuses to load anything else in it directly, at
 * runtime, when it's referenced by name (as a cast target) the way this interface is.
 */
public interface MultilineTextFieldExt {
	/** Returns whether the value changed since the last call, clearing the flag either way. */
	boolean unitedMinecraft$consumeEditedFlag();
}
