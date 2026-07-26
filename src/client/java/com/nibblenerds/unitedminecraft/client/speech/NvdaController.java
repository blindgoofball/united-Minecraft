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

/**
 * Binds the handful of exported functions from NVDA's nvdaControllerClient.dll
 * via the Java Foreign Function & Memory API, so no JNI shim needs to be compiled.
 *
 * <p>Ship the DLL as a classpath resource at
 * {@code src/client/resources/nvda/nvdaControllerClient.dll} (it comes with NVDA
 * under {@code nvda_source/miscDeps/server/x64/nvdaControllerClient.dll} — recent
 * NVDA releases dropped the separate 32/64-bit client DLLs in favor of this single
 * 64-bit one — and is freely redistributable with third-party applications). It is
 * extracted to a temp file the first time it's needed, since Windows can only load
 * DLLs from disk.
 */
public final class NvdaController {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/nvda");
	private static final String RESOURCE_PATH = "/nvda/nvdaControllerClient.dll";

	private static final Optional<NvdaController> INSTANCE = tryLoad();

	private final MethodHandle testIfRunning;
	private final MethodHandle speakText;
	private final MethodHandle cancelSpeech;

	private NvdaController(MethodHandle testIfRunning, MethodHandle speakText, MethodHandle cancelSpeech) {
		this.testIfRunning = testIfRunning;
		this.speakText = speakText;
		this.cancelSpeech = cancelSpeech;
	}

	public static Optional<NvdaController> getInstance() {
		return INSTANCE;
	}

	private static Optional<NvdaController> tryLoad() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			// The controller client is a Windows-only DLL; NVDA itself only runs on Windows.
			return Optional.empty();
		}

		try {
			Path dll = extractDll();
			Arena arena = Arena.ofShared();
			SymbolLookup lookup = SymbolLookup.libraryLookup(dll, arena);
			Linker linker = Linker.nativeLinker();

			MethodHandle testIfRunning = linker.downcallHandle(
					lookup.find("nvdaController_testIfRunning").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT));
			MethodHandle speakText = linker.downcallHandle(
					lookup.find("nvdaController_speakText").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
			MethodHandle cancelSpeech = linker.downcallHandle(
					lookup.find("nvdaController_cancelSpeech").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT));

			LOGGER.info("Loaded NVDA controller client from {}", dll);
			return Optional.of(new NvdaController(testIfRunning, speakText, cancelSpeech));
		} catch (Throwable t) {
			LOGGER.info("NVDA controller client unavailable, the default narrator will be used instead: {}", t.toString());
			return Optional.empty();
		}
	}

	private static Path extractDll() throws IOException {
		Path dir = Path.of(System.getProperty("java.io.tmpdir"), "united-minecraft-nvda");
		Files.createDirectories(dir);
		Path dest = dir.resolve("nvdaControllerClient.dll");
		if (Files.notExists(dest)) {
			try (InputStream in = NvdaController.class.getResourceAsStream(RESOURCE_PATH)) {
				if (in == null) {
					throw new UncheckedIOException(new IOException(RESOURCE_PATH + " was not found on the classpath"));
				}
				Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		return dest;
	}

	/** True if an NVDA instance is currently running and reachable. */
	public boolean isRunning() {
		try {
			int result = (int) testIfRunning.invokeExact();
			return result == 0;
		} catch (Throwable t) {
			return false;
		}
	}

	public void speak(String text) {
		try (Arena callArena = Arena.ofConfined()) {
			MemorySegment wide = toWideString(callArena, text);
			int result = (int) speakText.invokeExact(wide);
			if (result != 0) {
				LOGGER.debug("nvdaController_speakText returned error code {}", result);
			}
		} catch (Throwable t) {
			LOGGER.warn("Failed to send text to NVDA", t);
		}
	}

	public void cancel() {
		try {
			int result = (int) cancelSpeech.invokeExact();
			if (result != 0) {
				LOGGER.debug("nvdaController_cancelSpeech returned error code {}", result);
			}
		} catch (Throwable t) {
			LOGGER.warn("Failed to cancel NVDA speech", t);
		}
	}

	private static MemorySegment toWideString(Arena arena, String text) {
		byte[] encoded = text.getBytes(StandardCharsets.UTF_16LE);
		// Arena.allocate zero-initializes the segment, so the trailing 2 bytes
		// left after the copy already form the required wchar_t null terminator.
		MemorySegment segment = arena.allocate(encoded.length + 2L);
		MemorySegment.copy(encoded, 0, segment, ValueLayout.JAVA_BYTE, 0, encoded.length);
		return segment;
	}
}
