package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.client.gui.components.EditBox;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.AnvilScreenAccessorMixin}
 * to expose {@code AnvilScreen}'s private rename {@link EditBox}, since the compiler has no way
 * to know about members a mixin injects into a vanilla class otherwise.
 */
public interface AnvilScreenAccess {
	EditBox unitedMinecraft$getNameBox();
}
