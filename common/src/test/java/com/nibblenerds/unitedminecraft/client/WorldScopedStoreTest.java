package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

class WorldScopedStoreTest {
	@Test
	void worldsOnOneAddressGetDifferentIds() {
		assertNotEquals(
				WorldScopedStore.serverWorldId("play.example.com", OptionalLong.of(1L)),
				WorldScopedStore.serverWorldId("play.example.com", OptionalLong.of(2L)));
	}

	@Test
	void sameWorldKeepsItsIdAcrossJoins() {
		assertEquals(
				WorldScopedStore.serverWorldId("play.example.com", OptionalLong.of(-5L)),
				WorldScopedStore.serverWorldId("play.example.com", OptionalLong.of(-5L)));
	}

	@Test
	void idWithoutASeedIsTheBareAddress() {
		assertEquals("mp_play.example.com", WorldScopedStore.serverWorldId("play.example.com", OptionalLong.empty()));
	}
}
