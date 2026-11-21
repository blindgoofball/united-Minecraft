package com.nibblenerds.unitedminecraft.client.access;

/**
 * Marks a vanilla container that narrates its focused child itself, adding what only it knows
 * (a grid position, a group name), so the narration fix leaves it to do that instead of reading
 * the child alone. Added by {@link com.nibblenerds.unitedminecraft.client.mixin.ScrollableLayoutContainerMixin}
 * to a class that is not visible to this code.
 */
public interface ComposesOwnNarration {
}
