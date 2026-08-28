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
import net.fabricmc.loader.api.FabricLoader;

/**
 * Binds the C API exported by Prism (https://github.com/ethindp/prism), a
 * cross-platform library that picks the best available screen reader or TTS
 * backend on the current platform, via the Java Foreign Function & Memory API.
 *
 * <p>Prism ships one native library per platform/architecture under
 * {@code src/client/resources/prism/<platform>/} - Windows (x86-64, arm64), macOS
 * (a single universal x86-64 + arm64 dylib), and Linux (x86-64, arm64) are all
 * wired up. Adding another platform/architecture later is just a matter of
 * dropping its native library into a matching directory and adding a case to
 * {@link NativeLibrary#detect()} - the rest of this class (method handles,
 * speak/stop/shutdown) is already portable since Prism's C ABI is identical
 * across platforms.
 *
 * <p>Like the DLL it replaces, the native library can only be loaded from disk, so
 * it's extracted from the classpath to a temp file the first time it's needed.
 */
public final class PrismController {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/prism");

	private static final int PRISM_OK = 0;

	private static final Optional<PrismController> INSTANCE = tryLoad();

	private static final long BACKEND_SUPPORTS_BRAILLE = 1L << 4;
	private static final long BACKEND_SUPPORTS_OUTPUT = 1L << 5;

	private final Arena arena;
	private final MethodHandle prismShutdown;
	private final MethodHandle registryCreateBest;
	private final MethodHandle backendFree;
	private final MethodHandle backendName;
	private final MethodHandle backendGetFeatures;
	private final MethodHandle backendSpeak;
	private final MethodHandle backendBraille;
	private final MethodHandle backendOutput;
	private final MethodHandle backendStop;
	private final MethodHandle errorString;

	private final MemorySegment context;
	private MemorySegment backend;
	private boolean brailleSupported;
	private boolean outputSupported;

	private PrismController(Arena arena, MethodHandle prismShutdown, MethodHandle registryCreateBest,
			MethodHandle backendFree, MethodHandle backendName, MethodHandle backendGetFeatures,
			MethodHandle backendSpeak, MethodHandle backendBraille, MethodHandle backendOutput,
			MethodHandle backendStop, MethodHandle errorString,
			MemorySegment context, MemorySegment backend) {
		this.arena = arena;
		this.prismShutdown = prismShutdown;
		this.registryCreateBest = registryCreateBest;
		this.backendFree = backendFree;
		this.backendName = backendName;
		this.backendGetFeatures = backendGetFeatures;
		this.backendSpeak = backendSpeak;
		this.backendBraille = backendBraille;
		this.backendOutput = backendOutput;
		this.backendStop = backendStop;
		this.errorString = errorString;
		this.context = context;
		this.backend = backend;
		updateSupportedFeatures();
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
			MethodHandle backendGetFeatures = linker.downcallHandle(
					lookup.find("prism_backend_get_features").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
			MethodHandle backendSpeak = linker.downcallHandle(
					lookup.find("prism_backend_speak").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN));
			MethodHandle backendBraille = linker.downcallHandle(
					lookup.find("prism_backend_braille").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
			MethodHandle backendOutput = linker.downcallHandle(
					lookup.find("prism_backend_output").orElseThrow(),
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
					backendGetFeatures, backendSpeak, backendBraille, backendOutput, backendStop, errorString,
					context, backend));
		} catch (Throwable t) {
			// Logged at WARN with the full stack trace (not just t.toString()) because a
			// bare message loses the cause chain that usually explains *why* the native
			// library failed to load or link (e.g. an UnsatisfiedLinkError wrapping a
			// dlopen failure) - that detail is the difference between "it just doesn't
			// work" reports and an actionable diagnosis.
			LOGGER.warn("Prism unavailable, the default narrator will be used instead", t);
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
		// Deliberately not System.getProperty("java.io.tmpdir"): on Linux that's
		// usually /tmp, which many distros (and containers/Flatpak) mount `noexec`.
		// The copy itself succeeds either way, but dlopen()/mmap(PROT_EXEC) on the
		// extracted .so then fails - silently, from Java's point of view, since it
		// just surfaces as a generic link failure with no mention of the real cause.
		// The game directory is never mounted noexec, so extract there instead.
		Path dir = FabricLoader.getInstance().getGameDir().resolve("united_minecraft").resolve("prism-native");
		Files.createDirectories(dir);
		Path dest = dir.resolve(library.fileName());
		// Always re-extract rather than reusing a file left over from a previous run:
		// a prior interrupted copy, a mod update that shipped a fixed library under
		// the same file name, or third-party interference (AV quarantine, etc.) could
		// otherwise leave a stale or corrupt copy in place indefinitely.
		try (InputStream in = PrismController.class.getResourceAsStream(library.resourcePath())) {
			if (in == null) {
				throw new UncheckedIOException(new IOException(library.resourcePath() + " was not found on the classpath"));
			}
			Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
		}
		return dest;
	}

	/**
	 * Outputs {@code text} through every modality the backend supports (speech and,
	 * where available, a connected braille display), interrupting any speech in
	 * progress first if {@code interrupt} is set.
	 */
	public synchronized void speak(String text, boolean interrupt) {
		if (backend == null) {
			return;
		}
		try {
			int result = doOutput(text, interrupt);
			if (result != PRISM_OK) {
				// The backend may have entered an unrecoverable state (e.g. the screen
				// reader it was talking to was closed) - Prism's own docs say backends
				// don't reconnect on their own, so re-acquire the best backend and retry once.
				LOGGER.debug("Prism output failed ({}), re-acquiring the best backend",
						describeError(errorString, result));
				if (reacquireBackend()) {
					doOutput(text, interrupt);
				}
			}
		} catch (Throwable t) {
			LOGGER.warn("Failed to speak through Prism", t);
		}
	}

	/**
	 * Sends {@code text} through {@code prism_backend_output} (speech + braille together)
	 * when the backend supports it; otherwise falls back to speaking, plus a separate
	 * braille call if the backend supports braille but not the combined output call.
	 */
	private int doOutput(String text, boolean interrupt) throws Throwable {
		if (outputSupported) {
			try (Arena callArena = Arena.ofConfined()) {
				MemorySegment cText = toCString(callArena, text);
				return (int) backendOutput.invoke(backend, cText, interrupt);
			}
		}
		int result = doSpeak(text, interrupt);
		if (result == PRISM_OK && brailleSupported) {
			int brailleResult = doBraille(text);
			if (brailleResult != PRISM_OK) {
				LOGGER.debug("prism_backend_braille failed ({})", describeError(errorString, brailleResult));
			}
		}
		return result;
	}

	private int doSpeak(String text, boolean interrupt) throws Throwable {
		try (Arena callArena = Arena.ofConfined()) {
			MemorySegment cText = toCString(callArena, text);
			return (int) backendSpeak.invoke(backend, cText, interrupt);
		}
	}

	private int doBraille(String text) throws Throwable {
		try (Arena callArena = Arena.ofConfined()) {
			MemorySegment cText = toCString(callArena, text);
			return (int) backendBraille.invoke(backend, cText);
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
			updateSupportedFeatures();
			return true;
		} catch (Throwable t) {
			LOGGER.warn("Failed to re-acquire a Prism backend", t);
			backend = null;
			return false;
		}
	}

	/** Refreshes {@link #brailleSupported}/{@link #outputSupported} for the current {@link #backend}. */
	private void updateSupportedFeatures() {
		try {
			long features = (long) backendGetFeatures.invoke(backend);
			brailleSupported = (features & BACKEND_SUPPORTS_BRAILLE) != 0;
			outputSupported = (features & BACKEND_SUPPORTS_OUTPUT) != 0;
		} catch (Throwable t) {
			LOGGER.debug("Failed to query Prism backend features, assuming speech-only", t);
			brailleSupported = false;
			outputSupported = false;
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
			boolean isArm64 = arch.equals("aarch64") || arch.equals("arm64");
			boolean isX86_64 = arch.equals("amd64") || arch.equals("x86_64");

			// To add another platform/architecture, drop its native library under
			// src/client/resources/prism/<dir>/ and add a case here - PrismController
			// itself needs no other changes, since Prism's C ABI is identical across
			// platforms.
			if (os.contains("win")) {
				if (isX86_64) {
					return Optional.of(new NativeLibrary("/prism/windows-x86_64/prism.dll", "prism.dll"));
				}
				if (isArm64) {
					return Optional.of(new NativeLibrary("/prism/windows-aarch64/prism.dll", "prism.dll"));
				}
			} else if (os.contains("mac") || os.contains("darwin")) {
				// One universal (x86_64 + arm64) dylib covers both Intel and Apple Silicon.
				if (isX86_64 || isArm64) {
					return Optional.of(new NativeLibrary("/prism/macos-universal/libprism.dylib", "libprism.dylib"));
				}
			} else if (os.contains("nux")) {
				if (isX86_64) {
					return Optional.of(new NativeLibrary("/prism/linux-x86_64/libprism.so", "libprism.so"));
				}
				if (isArm64) {
					return Optional.of(new NativeLibrary("/prism/linux-aarch64/libprism.so", "libprism.so"));
				}
			}

			return Optional.empty();
		}
	}
}
