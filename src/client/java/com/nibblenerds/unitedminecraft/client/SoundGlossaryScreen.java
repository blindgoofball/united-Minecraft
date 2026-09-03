package com.nibblenerds.unitedminecraft.client;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import org.lwjgl.glfw.GLFW;

/**
 * A browsable reference of every audio cue this mod plays, since it's not always obvious
 * what a given sound means the first time you hear it - reached from {@link SettingsScreen}.
 * Activating an entry replays its sound at the player's position, so it doubles as a way to
 * preview one on demand. Uses vanilla's {@link ObjectSelectionList} for scrolling rather than
 * {@link SettingsScreen}'s hand-rolled row layout - {@code ScreenNarratedWidgetMixin} already
 * walks to the actually-focused leaf widget, so list rows narrate correctly here too.
 */
public final class SoundGlossaryScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private static final int TOP_MARGIN = 32;
	private static final int BOTTOM_MARGIN = 36;

	/** Volume/pitch mirror exactly what the source controller actually plays - see each one's {@code playXxxCue}. */
	private record GlossaryEntry(String descriptionKey, Supplier<SoundEvent> sound, float volume, float pitch) {
	}

	private static final List<GlossaryEntry> ENTRIES = List.of(
			new GlossaryEntry("united_minecraft.glossary.hostile_radar_alert", SoundEvents.NOTE_BLOCK_BELL::value, 0.7f, 1.0f),
			new GlossaryEntry("united_minecraft.glossary.melee_alert", SoundEvents.NOTE_BLOCK_HAT::value, 0.6f, 1.4f),
			new GlossaryEntry("united_minecraft.glossary.fall_warning_safe", SoundEvents.NOTE_BLOCK_PLING::value, 1.5f, 1.3f),
			new GlossaryEntry("united_minecraft.glossary.fall_warning_damaging", () -> SoundEvents.ANVIL_LAND, 1.0f, 0.8f),
			new GlossaryEntry("united_minecraft.glossary.fall_warning_hazard", SoundEvents.NOTE_BLOCK_DIDGERIDOO::value, 1.0f, 0.7f),
			new GlossaryEntry("united_minecraft.glossary.mining_radar_ore", () -> SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f),
			new GlossaryEntry("united_minecraft.glossary.nav_radar_clear", () -> SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f),
			new GlossaryEntry("united_minecraft.glossary.arrow_hit", () -> SoundEvents.ARROW_HIT_PLAYER, 1.0f, 1.0f),
			new GlossaryEntry("united_minecraft.glossary.autowalk_arrived", SoundEvents.NOTE_BLOCK_CHIME::value, 0.7f, 1.4f),
			new GlossaryEntry("united_minecraft.glossary.autowalk_stopped", SoundEvents.NOTE_BLOCK_BASS::value, 0.7f, 0.7f),
			new GlossaryEntry("united_minecraft.glossary.combat_cue", SoundEvents.NOTE_BLOCK_XYLOPHONE::value, 0.5f, 1.4f));

	private GlossaryList list;

	public SoundGlossaryScreen() {
		super(Component.translatable("united_minecraft.sound_glossary_screen.title"));
	}

	@Override
	protected void init() {
		int top = TOP_MARGIN;
		int bottom = this.height - BOTTOM_MARGIN;
		list = new GlossaryList(this.minecraft, this.font, this.width, Math.max(bottom - top, ROW_HEIGHT), top, ROW_HEIGHT);
		addRenderableWidget(list);

		int buttonWidth = 200;
		addRenderableWidget(Button.builder(Component.translatable("united_minecraft.settings_screen.done"),
				button -> onClose())
				.bounds(this.width / 2 - buttonWidth / 2, this.height - BOTTOM_MARGIN + 8, buttonWidth, ROW_HEIGHT)
				.build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(new SettingsScreen());
	}

	private static final class GlossaryList extends ObjectSelectionList<GlossaryList.Entry> {
		private final Font font;

		GlossaryList(Minecraft minecraft, Font font, int width, int height, int y0, int itemHeight) {
			super(minecraft, width, height, y0, itemHeight);
			this.font = font;
			for (GlossaryEntry entry : ENTRIES) {
				addEntry(new Entry(entry));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(320, this.width - 20);
		}

		private final class Entry extends ObjectSelectionList.Entry<Entry> {
			private final GlossaryEntry entry;
			private final Component narration;

			Entry(GlossaryEntry entry) {
				this.entry = entry;
				this.narration = Component.translatable(entry.descriptionKey());
			}

			@Override
			public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
				int textX = getContentX() + 2;
				int textY = getContentY() + (getContentHeight() - font.lineHeight) / 2;
				String text = Component.translatable(entry.descriptionKey()).getString();
				guiGraphics.text(font, font.plainSubstrByWidth(text, getContentWidth() - 4), textX, textY, -1);
			}

			@Override
			public Component getNarration() {
				return narration;
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				playSound();
				return super.mouseClicked(event, doubleClick);
			}

			/** Lets a keyboard-only user preview the sound with Enter, matching this mod's keyboard-first navigation. */
			@Override
			public boolean keyPressed(KeyEvent event) {
				if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
					playSound();
					return true;
				}
				return false;
			}

			private void playSound() {
				Minecraft client = Minecraft.getInstance();
				var player = client.player;
				if (player == null) {
					return;
				}
				var pos = player.position();
				client.getSoundManager().play(new SimpleSoundInstance(entry.sound().get(), SoundSource.MASTER,
						entry.volume(), entry.pitch(), player.getRandom(), pos.x(), pos.y(), pos.z()));
			}
		}
	}
}
