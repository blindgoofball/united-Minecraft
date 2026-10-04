package com.nibblenerds.unitedminecraft.client;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;

/**
 * The two halves of never losing a player's saved data: writing a file so that a crash or power
 * cut part-way through can't leave it half-written, and setting aside a file that fails to load
 * so the next save can't overwrite it. Shared by {@link UnitedMinecraftConfig}, {@link
 * KeybindConfig} and {@link WorldScopedStore}.
 */
final class SafeFiles {
	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

	private SafeFiles() {
	}

	@FunctionalInterface
	interface WriteAction {
		void write(Writer writer) throws IOException;
	}

	/**
	 * Writes {@code file} through a temporary sibling that is then moved over it, so the file on
	 * disk is always either the old contents or the new ones in full - never the truncated,
	 * half-written file an in-place write leaves behind when the game dies mid-save.
	 */
	static void writeAtomically(Path file, WriteAction action) throws IOException {
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				action.write(writer);
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				// Some filesystems (certain network or FUSE mounts) can't rename atomically; a plain
				// replace still never exposes a half-written file, just not as a single step.
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	/**
	 * Renames a file that exists but couldn't be read to {@code <name>.corrupt-<timestamp>}, so the
	 * defaults loaded in its place don't overwrite it on the next save - a player (or a bug report)
	 * can still recover what was in it.
	 */
	static void preserveUnreadable(Path file, Logger logger) {
		Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + LocalDateTime.now().format(BACKUP_STAMP));
		try {
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			logger.warn("Moved unreadable {} aside to {} - starting from defaults", file, backup);
		} catch (IOException e) {
			logger.warn("Could not move unreadable {} aside", file, e);
		}
	}
}
