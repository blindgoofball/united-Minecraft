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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nibblenerds.unitedminecraft.platform.Platform;

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

	private static final long BACKEND_SUPPORTS_SPEAK_TO_MEMORY = 1L << 3;
	private static final long BACKEND_SUPPORTS_BRAILLE = 1L << 4;
	private static final int PRISM_ERROR_ALREADY_INITIALIZED = 15;
	private static final long BACKEND_SUPPORTS_OUTPUT = 1L << 5;
	/** How often a fallen-back controller checks whether the backend it lost has come back - see {@link #probeIfDue}. */
	private static final long PROBE_INTERVAL_MILLIS = 10_000;

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
	/** The backend in use before a failure forced a fallback; null while nothing has failed. */
	private String lostBackendName;
	private long nextProbeMillis;
	private boolean shutDown;

	/** Null when this Prism build lacks the calls {@link #renderToMemory} needs. */
	private final MemoryApi memoryApi;
	/** Guards everything below - separate from {@code this} so rendering never holds up screen reader speech. */
	private final Object memoryLock = new Object();
	private boolean memoryBackendSearched;
	private MemorySegment memoryBackend;
	private MemorySegment audioCallbackStub;
	private volatile AudioCollector collector;

	private PrismController(Arena arena, MethodHandle prismShutdown, MethodHandle registryCreateBest,
			MethodHandle backendFree, MethodHandle backendName, MethodHandle backendGetFeatures,
			MethodHandle backendSpeak, MethodHandle backendBraille, MethodHandle backendOutput,
			MethodHandle backendStop, MethodHandle errorString,
			MemorySegment context, MemorySegment backend, MemoryApi memoryApi) {
		this.arena = arena;
		this.memoryApi = memoryApi;
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

	/**
	 * No-op call that forces the static initializer above to run, so Prism loads (or is
	 * found absent and fallen back on) at mod init rather than lazily at whatever the
	 * player first happens to narrate. Call once, from mod init.
	 */
	public static void init() {
	}

	/** Shuts Prism down if it loaded at all. Call once, when the client is stopping. */
	public static void shutdownIfLoaded() {
		INSTANCE.ifPresent(PrismController::shutdown);
	}

	private static Optional<PrismController> tryLoad() {
		Optional<NativeLibrary> library = NativeLibrary.detect();
		if (library.isEmpty()) {
			LOGGER.info("No Prism native library available for this platform, the default narrator will be used instead");
			return Optional.empty();
		}

		// The arena owns the loaded native library: every failure path below has to close it, or
		// the library stays mapped into the process for good with nothing left holding a handle
		// to it. handedOff tracks whether a PrismController took ownership instead.
		Arena arena = null;
		boolean handedOff = false;
		try {
			Path dll = extractLibrary(library.get());
			arena = Arena.ofShared();
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

			MemoryApi memoryApi = MemoryApi.find(lookup, linker);

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

			PrismController controller = new PrismController(arena, prismShutdown, registryCreateBest, backendFree,
					backendName, backendGetFeatures, backendSpeak, backendBraille, backendOutput, backendStop,
					errorString, context, backend, memoryApi);
			handedOff = true;
			return Optional.of(controller);
		} catch (Throwable t) {
			// Logged at WARN with the full stack trace (not just t.toString()) because a
			// bare message loses the cause chain that usually explains *why* the native
			// library failed to load or link (e.g. an UnsatisfiedLinkError wrapping a
			// dlopen failure) - that detail is the difference between "it just doesn't
			// work" reports and an actionable diagnosis.
			LOGGER.warn("Prism unavailable, the default narrator will be used instead", t);
			return Optional.empty();
		} finally {
			if (arena != null && !handedOff) {
				try {
					arena.close();
				} catch (Throwable t) {
					// Already on a failure path with the fallback narrator selected - nothing
					// useful left to do about a close that also fails.
					LOGGER.debug("Failed to release the Prism arena after a failed load", t);
				}
			}
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
		Path dir = Platform.get().gameDir().resolve("united_minecraft").resolve("prism-native");
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
		} catch (IOException e) {
			// Windows locks a DLL for as long as any process has it loaded, so a second game
			// instance running alongside this one makes the overwrite above fail outright with
			// AccessDeniedException - which tryLoad's catch-all would swallow, silently dropping
			// that instance to the vanilla narrator. An existing copy is overwhelmingly likely to
			// be the very library that's holding the lock, so use it rather than give up; the
			// re-extract is a safeguard against a stale file, not a correctness requirement.
			if (!Files.exists(dest)) {
				throw e;
			}
			LOGGER.info("Could not refresh {} ({}), using the copy already there", dest, e.toString());
		}
		return dest;
	}

	/**
	 * Outputs {@code text} through every modality the backend supports (speech and,
	 * where available, a connected braille display), interrupting any speech in
	 * progress first if {@code interrupt} is set.
	 *
	 * @return false if Prism couldn't say it, so the caller should fall back to another narrator
	 */
	public synchronized boolean speak(String text, boolean interrupt) {
		probeIfDue();
		if (backend == null) {
			return false;
		}
		try {
			int result = doOutput(text, interrupt);
			if (result == PRISM_OK) {
				return true;
			}
			// The backend may have entered an unrecoverable state (e.g. the screen
			// reader it was talking to was closed) - Prism's own docs say backends
			// don't reconnect on their own, so re-acquire the best backend and retry once.
			LOGGER.debug("Prism output failed ({}), re-acquiring the best backend",
					describeError(errorString, result));
			String failedName = backendNameOrNull(backend);
			if (reacquireBackend()) {
				String newName = backendNameOrNull(backend);
				if (failedName != null && !failedName.equals(newName)) {
					// Fell back to a different backend - typically the screen reader was closed and
					// SAPI or OneCore took over. Remember what was lost so probeIfDue can switch
					// back once it's running again, instead of staying on the fallback voice until
					// the game restarts.
					lostBackendName = failedName;
					nextProbeMillis = System.currentTimeMillis() + PROBE_INTERVAL_MILLIS;
					LOGGER.info("Prism fell back from '{}' to '{}'", failedName, newName);
				}
				return doOutput(text, interrupt) == PRISM_OK;
			}
			if (failedName != null) {
				lostBackendName = failedName;
			}
			nextProbeMillis = System.currentTimeMillis() + PROBE_INTERVAL_MILLIS;
			return false;
		} catch (Throwable t) {
			LOGGER.warn("Failed to speak through Prism", t);
			return false;
		}
	}

	/**
	 * After a fallback (see {@link #speak}), periodically asks Prism for its best backend again
	 * and switches to it if it's the one that was lost (the screen reader has been restarted), or
	 * if there was no backend at all and now there is one. Only runs while fallen back, so a
	 * healthy backend is never disturbed.
	 */
	private void probeIfDue() {
		if (shutDown || (backend != null && lostBackendName == null)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextProbeMillis) {
			return;
		}
		nextProbeMillis = now + PROBE_INTERVAL_MILLIS;
		try {
			MemorySegment candidate = (MemorySegment) registryCreateBest.invoke(context);
			if (candidate.equals(MemorySegment.NULL)) {
				return;
			}
			String candidateName = backendNameOrNull(candidate);
			boolean recovered = backend == null || (candidateName != null && candidateName.equals(lostBackendName));
			if (!recovered) {
				backendFree.invoke(candidate);
				return;
			}
			if (backend != null) {
				backendFree.invoke(backend);
			}
			backend = candidate;
			lostBackendName = null;
			updateSupportedFeatures();
			LOGGER.info("Prism switched back to backend '{}'", candidateName);
		} catch (Throwable t) {
			LOGGER.debug("Failed to probe for a better Prism backend", t);
		}
	}

	private String backendNameOrNull(MemorySegment candidate) {
		try {
			return readCString((MemorySegment) backendName.invoke(candidate));
		} catch (Throwable t) {
			return null;
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
		shutDown = true;
		try {
			synchronized (memoryLock) {
				if (memoryBackend != null) {
					backendFree.invoke(memoryBackend);
					memoryBackend = null;
				}
				// Stops a later renderToMemory from searching again against a shut-down context.
				memoryBackendSearched = true;
			}
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

	/**
	 * Synthesizes {@code text} to audio samples instead of speaking it, for sounds the game plays
	 * itself (positioned in 3D, unlike screen reader speech) - see {@code StructureVoiceAudio}.
	 * Screen reader backends can't do this, so it uses a backend of its own: the highest-priority
	 * one that can render to memory, which is OneCore or SAPI on Windows and AVSpeech on macOS.
	 * Linux's Speech Dispatcher can't, so this is always empty there.
	 *
	 * <p>Blocking - the backends render the whole utterance before returning - so call it off the
	 * client thread.
	 */
	public Optional<RenderedSpeech> renderToMemory(String text) {
		synchronized (memoryLock) {
			if (!memoryBackendSearched) {
				memoryBackendSearched = true;
				memoryBackend = findMemoryBackend();
			}
			if (memoryBackend == null) {
				return Optional.empty();
			}
			AudioCollector target = new AudioCollector();
			collector = target;
			try (Arena callArena = Arena.ofConfined()) {
				int result = (int) memoryApi.speakToMemory().invoke(memoryBackend, toCString(callArena, text),
						audioCallbackStub, MemorySegment.NULL);
				if (result != PRISM_OK) {
					LOGGER.debug("prism_backend_speak_to_memory failed for '{}' ({})", text, describeError(errorString, result));
					return Optional.empty();
				}
				return target.result();
			} catch (Throwable t) {
				LOGGER.warn("Failed to render speech to memory through Prism", t);
				return Optional.empty();
			} finally {
				collector = null;
			}
		}
	}

	/** Called with {@link #memoryLock} held. */
	private MemorySegment findMemoryBackend() {
		if (memoryApi == null) {
			LOGGER.info("This Prism build can't render speech to memory");
			return null;
		}
		try {
			audioCallbackStub = Linker.nativeLinker().upcallStub(
					MethodHandles.lookup().findVirtual(PrismController.class, "onAudio",
							MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class,
									long.class, long.class, long.class)).bindTo(this),
					FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
							ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG),
					arena);
			long count = (long) memoryApi.registryCount().invoke(context);
			// The registry lists backends highest priority first.
			for (long i = 0; i < count; i++) {
				long id = (long) memoryApi.registryIdAt().invoke(context, i);
				MemorySegment candidate = (MemorySegment) memoryApi.registryCreate().invoke(context, id);
				if (candidate.equals(MemorySegment.NULL)) {
					continue;
				}
				// Features are known before initializing, so screen reader backends are skipped
				// without ever connecting to the screen reader.
				long features = (long) backendGetFeatures.invoke(candidate);
				if ((features & BACKEND_SUPPORTS_SPEAK_TO_MEMORY) != 0) {
					int result = (int) memoryApi.backendInitialize().invoke(candidate);
					if (result == PRISM_OK || result == PRISM_ERROR_ALREADY_INITIALIZED) {
						LOGGER.info("Rendering speech to memory with Prism backend '{}'",
								readCString((MemorySegment) backendName.invoke(candidate)));
						return candidate;
					}
				}
				backendFree.invoke(candidate);
			}
			LOGGER.info("No Prism backend on this system can render speech to memory");
		} catch (Throwable t) {
			LOGGER.warn("Failed to find a Prism backend that renders speech to memory", t);
		}
		return null;
	}

	/** Prism's {@code PrismAudioCallback}: {@code count} interleaved float samples. May be called more than once per utterance. */
	@SuppressWarnings("unused")
	private void onAudio(MemorySegment userdata, MemorySegment samples, long count, long channels, long sampleRate) {
		AudioCollector target = collector;
		if (target != null && count > 0 && channels > 0 && sampleRate > 0) {
			target.add(samples.reinterpret(count * Float.BYTES).toArray(ValueLayout.JAVA_FLOAT),
					(int) channels, (int) sampleRate);
		}
	}

	/**
	 * Speech rendered by {@link #renderToMemory}.
	 *
	 * @param samples interleaved, {@code channels} per frame, nominally -1..1
	 */
	public record RenderedSpeech(float[] samples, int channels, int sampleRate) {
	}

	private static final class AudioCollector {
		private float[] samples = new float[0];
		private int channels;
		private int sampleRate;

		synchronized void add(float[] more, int channels, int sampleRate) {
			float[] joined = Arrays.copyOf(samples, samples.length + more.length);
			System.arraycopy(more, 0, joined, samples.length, more.length);
			this.samples = joined;
			this.channels = channels;
			this.sampleRate = sampleRate;
		}

		synchronized Optional<RenderedSpeech> result() {
			return samples.length == 0 ? Optional.empty() : Optional.of(new RenderedSpeech(samples, channels, sampleRate));
		}
	}

	/** The registry and speak-to-memory calls - looked up separately so a Prism build without them still speaks. */
	private record MemoryApi(MethodHandle registryCount, MethodHandle registryIdAt, MethodHandle registryCreate,
			MethodHandle backendInitialize, MethodHandle speakToMemory) {
		static MemoryApi find(SymbolLookup lookup, Linker linker) {
			Optional<MemorySegment> count = lookup.find("prism_registry_count");
			Optional<MemorySegment> idAt = lookup.find("prism_registry_id_at");
			Optional<MemorySegment> create = lookup.find("prism_registry_create");
			Optional<MemorySegment> initialize = lookup.find("prism_backend_initialize");
			Optional<MemorySegment> speakToMemory = lookup.find("prism_backend_speak_to_memory");
			if (count.isEmpty() || idAt.isEmpty() || create.isEmpty() || initialize.isEmpty() || speakToMemory.isEmpty()) {
				return null;
			}
			// size_t and PrismBackendId (uint64_t) are both 64-bit on every platform Prism ships for.
			return new MemoryApi(
					linker.downcallHandle(count.get(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)),
					linker.downcallHandle(idAt.get(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)),
					linker.downcallHandle(create.get(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)),
					linker.downcallHandle(initialize.get(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
					linker.downcallHandle(speakToMemory.get(), FunctionDescriptor.of(ValueLayout.JAVA_INT,
							ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)));
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
