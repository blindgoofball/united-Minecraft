package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ServerWorldFingerprintTest {
	@Test
	void differentSpawnsGiveDifferentFingerprints() {
		assertNotEquals(
				ServerWorldFingerprint.fingerprint("minecraft:overworld", 10, 70, -4),
				ServerWorldFingerprint.fingerprint("minecraft:overworld", 11, 70, -4));
		assertNotEquals(
				ServerWorldFingerprint.fingerprint("minecraft:overworld", 10, 70, -4),
				ServerWorldFingerprint.fingerprint("minecraft:the_nether", 10, 70, -4));
	}

	@Test
	void sameSpawnKeepsItsFingerprint() {
		assertEquals(
				ServerWorldFingerprint.fingerprint("minecraft:overworld", 0, 64, 0),
				ServerWorldFingerprint.fingerprint("minecraft:overworld", 0, 64, 0));
	}

	@Test
	void nothingIsKnownBeforeASpawnArrives() {
		ServerWorldFingerprint.reset();
		assertEquals(java.util.OptionalLong.empty(), ServerWorldFingerprint.current());
	}
}
