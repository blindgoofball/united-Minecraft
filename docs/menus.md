# Menus and inventory

[Documentation index](README.md)

On this page: [Moving around](#moving-around) | [Moving items](#moving-items) | [Bundles](#bundles) | [Crafting and the Recipe Book](#crafting-and-the-recipe-book) | [Special stations](#special-stations) | [Creative inventory](#creative-inventory) | [Advancements](#advancements)

Full keyboard navigation for container menus (chests, furnaces, crafting tables,
anvils, brewing stands, enchanting tables) and your own inventory. All of these
keys are rebindable in the Keybindings screen.

## Moving around

- **Tab / Shift+Tab** cycle between sections: the container's own slots,
  equipment, main inventory, hotbar, and a Recipe Book section where there is one.
- **Arrow keys** move within a section as a 2D grid.
- **Letters** jump to the next slot in the section whose item name starts with
  that letter, wrapping around, so repeating it cycles through every match. E does
  not close these screens; Escape does.
- **Space** on a slot holding a block describes what it is made of: grain, color
  and texture, written per material, so a slab, stairs, wall and fence of the same
  stone share one description.

## Moving items

| Key | Action |
| --- | --- |
| Enter | Pick up or place the focused slot's stack |
| Shift+Enter | Quick-move |
| Ctrl+Enter | Split a stack in half onto the cursor, or place one item at a time back from it |
| Alt+Enter | Gather every other stack of the same item, from anywhere on the screen, into the carried one, up to a full stack |
| Alt+Shift+Enter | Quick-move every stack of that item out of the same inventory |
| 1-9 | Swap the focused slot with that hotbar slot |
| Ctrl+F | Swap the focused slot with your off-hand |
| Backspace | Drop one item from the focused slot |
| Ctrl+Backspace | Drop the whole focused stack |
| Delete | Drop the carried stack (the Creative inventory discards it instead) |
| Ctrl+Delete | Drop one carried item |

Holding **Ctrl** while moving with the arrow keys, with a stack picked up, drags
it across every slot you pass over as an even split, like holding the left mouse
button. **Alt** does the same but puts one item in each slot, like the right
button. The stack drops when you let go of the key, and Escape backs out first.

## Bundles

A bundle narrates how full it is, as a percentage, and what is inside it with the
selected stack first. Page Up/Down on a focused bundle selects the previous or next
stack, which is the one Ctrl+Enter takes out.

## Crafting and the Recipe Book

Wherever a crafting grid and Recipe Book are both present:

- **Up/Down** move between recipe groups and **Left/Right** between variants within
  one (vanilla bundles near-duplicates such as different wood colors). Each
  narrates the result, whether it is craftable, and the ingredients it needs, which
  vanilla's recipe book never says aloud.
- **Home/End** jump to the first or last group; **Page Up/Down** cycle vanilla's
  recipe-book category tabs.
- **F** shows only recipes you can craft now.
- **Space** opens a search prompt. The term also filters vanilla's own search box
  if it is open, so a sighted player sees the same results.
- **Enter** places the focused recipe; **Shift+Enter** fills the grid to the max
  stack size.

## Special stations

These controls are not slots, so each gets its own section.

- **Enchanting table:** an Enchant Options section. Up/Down move between the three
  options, narrating what hovering would: the enchantment, whether your level
  meets the requirement, and its Lapis and XP cost. Enter selects one.
- **Stonecutter and loom:** a section after the input slots, empty until something
  is in them. Up/Down move through the recipes or patterns, narrating what each
  makes (the item and count, or the pattern in your dye's color). Enter selects
  one, and the result waits in the output slot, one Shift+Tab back.
- **Anvil:** a Rename section ahead of its slots. Tab reaches it and leaves it like
  any section, and every other key reaches the text field normally.

## Creative inventory

- **Home/End** cycle through every Creative tab.
- **Arrow keys and Page Up/Down** browse an entire tab's items, not only what is
  scrolled into view, narrating name and position.
- **Letters** jump through the tab's full item list, and across the hotbar section,
  wrapping on repeats.
- **Enter** picks an item up; **Shift+Enter** drops it into the first empty hotbar
  slot (and tells you when the hotbar is full rather than overwriting anything).
- **Tab** reaches your hotbar directly from any item tab.
- **Delete** while carrying an item discards it, from anywhere in the Creative
  screen.
- The Inventory tab works like the regular inventory screen, with your equipment.
- **The Search tab's text field** is keyboard-accessible: Tab reaches it, typing
  filters the grid and narrates the result count, and Up/Down (or Page Up/Down)
  leave the field for the first result. Up from the first result returns to the
  field.

## Advancements

Each tab of the advancements screen is a tree, and the keys walk it like one. Opening
the screen narrates the current tab and how many of its advancements are done.

- **Up/Down** move through the tab from top to bottom, every branch open.
- **Right** enters an advancement's first child; **Left** returns to its parent.
- **Home/End** jump to the first or last advancement.
- **Tab / Shift+Tab** change tab, narrating its name and progress.

Each stop narrates the title (plus Challenge or Goal where it is one), whether it is
completed or its progress so far, the description, and its place in the list. The
screen scrolls to it and shows its usual hover box, so a sighted helper sees the same
thing. Advancements that are hidden until earned stay out of the list until you earn
them. The arrows and Tab are the same actions as the container menus' and rebind
with them.
