package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.OptionalLong;
import java.util.UUID;

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
	void aWorldTheServerNamedIsIdentifiedByThatNameAlone() {
		UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
		assertEquals("mp_world_123e4567-e89b-12d3-a456-426614174000", WorldScopedStore.serverWorldId(id));
		assertNotEquals(WorldScopedStore.serverWorldId(id), WorldScopedStore.serverWorldId(UUID.randomUUID()));
	}

	@Test
	void idWithoutASeedIsTheBareAddress() {
		assertEquals("mp_play.example.com", WorldScopedStore.serverWorldId("play.example.com", OptionalLong.empty()));
	}
}
