package com.nibblenerds.unitedminecraft.client.speech;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * Binds the C API exported by Prism (https://github.com/ethindp/prism), a
 * cross-platform library that picks the best available screen reader or TTS
 * backend on the current platform, via the Java Foreign Function & Memory API.
 *
 * <p>Prism ships one native library per platform under
 * {@code src/client/resources/prism/<platform>/}. Only Windows is wired up today
 * ({@code windows-x86_64/prism.dll}); adding macOS/Linux support later is just a
 * matter of dropping {@code libprism.dylib}/{@code libprism.so} into a matching
 * directory and adding a case to {@link NativeLibrary#detect()} - the rest of this
 * class (method handles, speak/stop/shutdown) is already portable since Prism's C
 * ABI is identical across platforms.
 *
 * <p>Like the DLL it replaces, the native library can only be loaded from disk, so
 * it's extracted from the classpath to a temp file the first time it's needed.
 */
public final class PrismController {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/prism");

	private static final int PRISM_OK = 0;

	private static final Optional<PrismController> INSTANCE = tryLoad();

	private final Arena arena;
	private final MethodHandle prismShutdown;
	private final MethodHandle registryCreateBest;
	private final MethodHandle backendFree;
	private final MethodHandle backendName;
	private final MethodHandle backendSpeak;
	private final MethodHandle backendStop;
	private final MethodHandle errorString;

	private final MemorySegment context;
	private MemorySegment backend;

	private PrismController(Arena arena, MethodHandle prismShutdown, MethodHandle registryCreateBest,
			MethodHandle backendFree, MethodHandle backendName,
			MethodHandle backendSpeak, MethodHandle backendStop, MethodHandle errorString,
			MemorySegment context, MemorySegment backend) {
		this.arena = arena;
		this.prismShutdown = prismShutdown;
		this.registryCreateBest = registryCreateBest;
		this.backendFree = backendFree;
		this.backendName = backendName;
		this.backendSpeak = backendSpeak;
		this.backendStop = backendStop;
		this.errorString = errorString;
		this.context = context;
		this.backend = backend;
	}

	public static Optional<PrismController> getInstance() {
		return INSTANCE;
	}

	/** Ties Prism's shutdown to client shutdown. Call once, from mod init. */
	public static void register() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> INSTANCE.ifPresent(PrismController::shutdown));
	}

	private static Optional<PrismController> tryLoad() {
		Optional<NativeLibrary> library = NativeLibrary.detect();
		if (library.isEmpty()) {
			LOGGER.info("No Prism native library available for this platform, the default narrator will be used instead");
			return Optional.empty();
		}

		try {
			Path dll = extractLibrary(library.get());
			Arena arena = Arena.ofShared();
			SymbolLookup lookup = SymbolLookup.libraryLookup(dll, arena);
			Linker linker = Linker.nativeLinker();

			MethodHandle prismInit = linker.downcallHandle(
					lookup.find("prism_init").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
			MethodHandle prismShutdown = linker.downcallHandle(
					lookup.find("prism_shutdown").orElseThrow(),
					FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
			MethodHandle registryCreateBest = linker.downcallHandle(
					lookup.find("prism_registry_create_best").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
			MethodHandle backendFree = linker.downcallHandle(
					lookup.find("prism_backend_free").orElseThrow(),
					FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
			MethodHandle backendName = linker.downcallHandle(
					lookup.find("prism_backend_name").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
			MethodHandle backendSpeak = linker.downcallHandle(
					lookup.find("prism_backend_speak").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN));
			MethodHandle backendStop = linker.downcallHandle(
					lookup.find("prism_backend_stop").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
			MethodHandle errorString = linker.downcallHandle(
					lookup.find("prism_error_string").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

			// NULL is a documented valid config: it makes Prism use its defaults
			// (built-in registry, availability polling disabled).
			MemorySegment context = (MemorySegment) prismInit.invoke(MemorySegment.NULL);
			if (context.equals(MemorySegment.NULL)) {
				LOGGER.info("prism_init failed, the default narrator will be used instead");
				return Optional.empty();
			}

			// prism_registry_create_best() returns an already-initialized backend -
			// calling prism_backend_initialize on it would just return
			// PRISM_ERROR_ALREADY_INITIALIZED, so this skips straight to using it.
			MemorySegment backend = (MemorySegment) registryCreateBest.invoke(context);
			if (backend.equals(MemorySegment.NULL)) {
				LOGGER.info("Prism found no usable speech backend, the default narrator will be used instead");
				prismShutdown.invoke(context);
				return Optional.empty();
			}

			String name = readCString((MemorySegment) backendName.invoke(backend));
			LOGGER.info("Loaded Prism speech backend '{}' from {}", name, dll);

			return Optional.of(new PrismController(arena, prismShutdown, registryCreateBest, backendFree, backendName,
					backendSpeak, backendStop, errorString, context, backend));
		} catch (Throwable t) {
			LOGGER.info("Prism unavailable, the default narrator will be used instead: {}", t.toString());
			return Optional.empty();
		}
	}

	private static String describeError(MethodHandle errorString, int code) {
		try {
			return readCString((MemorySegment) errorString.invoke(code));
		} catch (Throwable t) {
			return "error " + code;
		}
	}

	private static String readCString(MemorySegment segment) {
		if (segment.equals(MemorySegment.NULL)) {
			return "<unknown>";
		}
		return segment.reinterpret(Long.MAX_VALUE).getString(0);
	}

	private static Path extractLibrary(NativeLibrary library) throws IOException {
		Path dir = Path.of(System.getProperty("java.io.tmpdir"), "united-minecraft-prism");
		Files.createDirectories(dir);
		Path dest = dir.resolve(library.fileName());
		if (Files.notExists(dest)) {
			try (InputStream in = PrismController.class.getResourceAsStream(library.resourcePath())) {
				if (in == null) {
					throw new UncheckedIOException(new IOException(library.resourcePath() + " was not found on the classpath"));
				}
				Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		return dest;
	}

	/** Speaks {@code text}, interrupting any speech in progress first if {@code interrupt} is set. */
	public synchronized void speak(String text, boolean interrupt) {
		if (backend == null) {
			return;
		}
		try {
			int result = doSpeak(text, interrupt);
			if (result != PRISM_OK) {
				// The backend may have entered an unrecoverable state (e.g. the screen
				// reader it was talking to was closed) - Prism's own docs say backends
				// don't reconnect on their own, so re-acquire the best backend and retry once.
				LOGGER.debug("prism_backend_speak failed ({}), re-acquiring the best backend",
						describeError(errorString, result));
				if (reacquireBackend()) {
					doSpeak(text, interrupt);
				}
			}
		} catch (Throwable t) {
			LOGGER.warn("Failed to speak through Prism", t);
		}
	}

	private int doSpeak(String text, boolean interrupt) throws Throwable {
		try (Arena callArena = Arena.ofConfined()) {
			MemorySegment cText = toCString(callArena, text);
			return (int) backendSpeak.invoke(backend, cText, interrupt);
		}
	}

	public synchronized void stop() {
		if (backend == null) {
			return;
		}
		try {
			int result = (int) backendStop.invoke(backend);
			if (result != PRISM_OK) {
				LOGGER.debug("prism_backend_stop returned {}", describeError(errorString, result));
			}
		} catch (Throwable t) {
			LOGGER.warn("Failed to stop Prism speech", t);
		}
	}

	public synchronized boolean isAvailable() {
		return backend != null;
	}

	private boolean reacquireBackend() {
		try {
			backendFree.invoke(backend);
			// Already initialized, same as in tryLoad() - see the comment there.
			MemorySegment newBackend = (MemorySegment) registryCreateBest.invoke(context);
			if (newBackend.equals(MemorySegment.NULL)) {
				backend = null;
				return false;
			}
			backend = newBackend;
			return true;
		} catch (Throwable t) {
			LOGGER.warn("Failed to re-acquire a Prism backend", t);
			backend = null;
			return false;
		}
	}

	/** Releases the Prism backend and context. Call once, on client shutdown. */
	public synchronized void shutdown() {
		try {
			if (backend != null) {
				backendFree.invoke(backend);
				backend = null;
			}
			prismShutdown.invoke(context);
		} catch (Throwable t) {
			LOGGER.warn("Failed to shut down Prism cleanly", t);
		} finally {
			arena.close();
		}
	}

	private static MemorySegment toCString(Arena arena, String text) {
		byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
		// Arena.allocate zero-initializes the segment, so the trailing byte left
		// after the copy already forms the required null terminator.
		MemorySegment segment = arena.allocate(encoded.length + 1L);
		MemorySegment.copy(encoded, 0, segment, ValueLayout.JAVA_BYTE, 0, encoded.length);
		return segment;
	}

	/** The native Prism library for one platform: where to find it on the classpath, and what to name it on disk. */
	private record NativeLibrary(String resourcePath, String fileName) {
		static Optional<NativeLibrary> detect() {
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

			// Only Windows x86-64 is wired up today. To add another platform, drop its
			// native library under src/client/resources/prism/<dir>/ and add a case here -
			// PrismController itself needs no other changes, since Prism's C ABI is
			// identical across platforms.
			if (os.contains("win") && (arch.equals("amd64") || arch.equals("x86_64"))) {
				return Optional.of(new NativeLibrary("/prism/windows-x86_64/prism.dll", "prism.dll"));
			}

			return Optional.empty();
		}
	}
}
