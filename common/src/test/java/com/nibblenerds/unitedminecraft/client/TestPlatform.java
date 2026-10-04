package com.nibblenerds.unitedminecraft.client;

import java.nio.file.Path;

import com.nibblenerds.unitedminecraft.platform.Platform;

/** A {@link Platform} whose directories are a test's own temporary directory. */
final class TestPlatform implements Platform {
	private final Path root;

	private TestPlatform(Path root) {
		this.root = root;
	}

	/** Installs a platform rooted at {@code root} - call from each test's setup. */
	static void install(Path root) {
		Platform.set(new TestPlatform(root));
	}

	@Override
	public Path configDir() {
		return root.resolve("config");
	}

	@Override
	public Path gameDir() {
		return root;
	}
}
