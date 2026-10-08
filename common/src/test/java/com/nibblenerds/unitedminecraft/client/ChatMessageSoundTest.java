package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatMessageSoundTest {
	@Test
	void ownMessagesAreRecognisedByName() {
		assertTrue(ChatMessageSound.isOwnMessage("Steve", "Steve"));
		assertFalse(ChatMessageSound.isOwnMessage("Alex", "Steve"));
	}
}
