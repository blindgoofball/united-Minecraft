package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;

/** When the same on-screen text is - and isn't - narrated again: the action bar and titles. */
class NarrationRepeatTest {
	@Nested
	class ActionBar {
		private final OverlayRepeatFilter filter = new OverlayRepeatFilter();

		@Test
		void newTextIsAlwaysNarrated() {
			assertTrue(filter.shouldNarrate("Mana 100", 0));
			assertTrue(filter.shouldNarrate("Mana 90", 500));
			assertTrue(filter.shouldNarrate("Mana 100", 1000));
		}

		@Test
		void aHudKeptUpByResendsIsReadOnce() {
			assertTrue(filter.shouldNarrate("Mana 100", 0));
			for (long time = 1000; time <= 20_000; time += 1000) {
				assertFalse(filter.shouldNarrate("Mana 100", time), "resend at " + time);
			}
		}

		@Test
		void theSameMessageAfterItFadedIsReadAgain() {
			assertTrue(filter.shouldNarrate("You may not rest now, there are monsters nearby", 0));
			assertTrue(filter.shouldNarrate("You may not rest now, there are monsters nearby",
					OverlayRepeatFilter.OVERLAY_VISIBLE_MILLIS));
		}

		@Test
		void anyResendKeepsTheMessageOnScreen() {
			// Something else shown in between still restarts vanilla's display timer.
			assertTrue(filter.shouldNarrate("A", 0));
			assertTrue(filter.shouldNarrate("B", 2000));
			assertTrue(filter.shouldNarrate("A", 4000), "text differs from the last one");
			assertFalse(filter.shouldNarrate("A", 6000));
		}
	}

	@Nested
	class Titles {
		@BeforeEach
		void reset() {
			TitleNarrationController.reset();
		}

		private static String say(String title, String subtitle) {
			Component message = TitleNarrationController.update(
					title == null ? null : Component.literal(title), subtitle == null ? null : Component.literal(subtitle), true);
			return message == null ? null : message.getString();
		}

		@Test
		void aCountdownIsReadInFull() {
			assertEquals("3", say("3", null));
			assertEquals("2", say("2", null));
			assertEquals("1", say("1", null));
		}

		@Test
		void aTitleHeldOnScreenIsReadOnce() {
			assertEquals("Welcome", say("Welcome", null));
			for (int tick = 0; tick < 100; tick++) {
				assertNull(say("Welcome", null));
			}
		}

		@Test
		void theSameTitleAfterItFadedIsReadAgain() {
			assertEquals("Round over", say("Round over", null));
			assertNull(say(null, null));
			assertEquals("Round over", say("Round over", null));
		}

		@Test
		void aTitleAndItsSubtitleAreReadTogether() {
			assertEquals("Spawn. Safe zone", say("Spawn", "Safe zone"));
		}

		@Test
		void aLaterSubtitleIsReadOnItsOwn() {
			assertEquals("Spawn", say("Spawn", null));
			assertEquals("Safe zone", say("Spawn", "Safe zone"));
		}

		@Test
		void aBlankTitleWithASubtitleReadsJustTheSubtitle() {
			assertEquals("Checkpoint reached", say("", "Checkpoint reached"));
		}

		@Test
		void blankTextIsNotNarrated() {
			assertNull(say("  ", ""));
		}

		@Test
		void switchedOffItStillTracksWhatIsShowing() {
			assertNull(TitleNarrationController.update(Component.literal("Welcome"), null, false));
			assertNull(say("Welcome", null), "turning it on mid-title doesn't read a title already up");
		}
	}
}
