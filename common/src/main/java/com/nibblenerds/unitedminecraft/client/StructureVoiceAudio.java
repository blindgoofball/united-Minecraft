package com.nibblenerds.unitedminecraft.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.nibblenerds.unitedminecraft.client.speech.PrismController;
import com.nibblenerds.unitedminecraft.platform.Platform;

import net.minecraft.resources.Identifier;

/**
 * Spoken structure names for {@link StructureVoiceController}, synthesized once each by the
 * system's own text-to-speech and then played by the game, panned and pitched to say where the
 * structure is. Screen reader speech can't be panned, which is why this renders audio rather
 * than speaking through the screen reader:
 * <ul>
 * <li>Windows: Prism's OneCore or SAPI backend (the same voices Narrator uses);</li>
 * <li>macOS: Prism's AVSpeech backend, falling back to the {@code say} command;</li>
 * <li>Linux: {@code espeak-ng} (or {@code espeak}), which Speech Dispatcher itself usually
 * speaks through - Prism has no Linux backend that can render to memory.</li>
 * </ul>
 * If none of those work, {@link #request} completes empty and the controller narrates the
 * structure through the screen reader instead.
 *
 * <p>Direction is plain stereo panning rather than 3D positioning: each name is kept as one mono
 * recording, and {@link #variant} bakes a stereo copy for a given left/right balance, with
 * "behind" as a low-pass muffle (panning alone can't tell front from back). {@code
 * StructureVoiceChannelMixin} then plays those copies straight to the left and right channels,
 * bypassing HRTF, so the balance is exactly what was baked and nothing moves while it plays.
 *
 * <p>Like the wall tones there are no sound files: {@code SoundBufferLibraryMixin} answers
 * requests for {@link #PATH_PREFIX} paths from {@link #create}.
 */
public final class StructureVoiceAudio {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/structure_voices");

	public static final String NAMESPACE = "united_minecraft";
	public static final String PATH_PREFIX = "sounds/structure_voice/";
	private static final float PEAK = 0.9f;
	/** Balance is baked in steps of 1/PAN_STEPS, and "behind" in steps of 1/BEHIND_STEPS, so a handful of copies cover every direction. */
	private static final int PAN_STEPS = 10;
	private static final int BEHIND_STEPS = 4;
	/** The muffle for something directly behind: a low-pass this low. Nothing behind at all is unfiltered. */
	private static final double BEHIND_CUTOFF_HZ = 1200.0;
	private static final double OPEN_CUTOFF_HZ = 12000.0;
	private static final long EXTERNAL_TIMEOUT_SECONDS = 10;

	private static final Map<String, CompletableFuture<Optional<Clip>>> clips = new ConcurrentHashMap<>();
	/** Every buffer {@link #create} has made - how {@code StructureVoiceChannelMixin} recognizes them. */
	private static final Set<SoundBuffer> voiceBuffers = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
	private static final ExecutorService renderer = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "United Minecraft structure voice");
		thread.setDaemon(true);
		return thread;
	});

	private StructureVoiceAudio() {
	}

	/** A rendered voice: 16-bit mono, peak-normalized. */
	public record Clip(String slug, short[] samples, int sampleRate) {
		/** At normal pitch - a lower pitch plays for longer. */
		public int durationTicks() {
			return (int) Math.ceil(samples.length * 20.0 / sampleRate);
		}
	}

	/**
	 * The sound id for {@code clip} baked at a direction.
	 *
	 * @param pan -1 fully left, 0 centred, +1 fully right
	 * @param behind 0 in front of you (unfiltered) to 1 directly behind (most muffled)
	 */
	public static Identifier variant(Clip clip, float pan, float behind) {
		int panStep = Math.round((Math.clamp(pan, -1.0f, 1.0f) + 1.0f) * PAN_STEPS);
		int behindStep = Math.round(Math.clamp(behind, 0.0f, 1.0f) * BEHIND_STEPS);
		return Identifier.fromNamespaceAndPath(NAMESPACE, "structure_voice/" + clip.slug() + "/p" + panStep + "_b" + behindStep);
	}

	/** Whether {@code buffer} is a structure voice - see {@code StructureVoiceChannelMixin}. */
	public static boolean isVoiceBuffer(SoundBuffer buffer) {
		return voiceBuffers.contains(buffer);
	}

	/**
	 * The clip for {@code text}, rendering it on a background thread the first time. Completes
	 * empty when no text-to-speech engine can render audio on this system.
	 */
	public static CompletableFuture<Optional<Clip>> request(String text) {
		String slug = slug(text);
		return clips.computeIfAbsent(slug, key -> CompletableFuture.supplyAsync(() -> render(text, key), renderer));
	}

	public static boolean handles(Identifier path) {
		return parse(path) != null;
	}

	/** Must only be called for a path {@link #handles} accepts. Bakes the stereo copy. */
	public static SoundBuffer create(Identifier path) {
		Request request = parse(path);
		short[] mono = request.clip().samples();
		float pan = request.panStep() / (float) PAN_STEPS - 1.0f;
		float behind = request.behindStep() / (float) BEHIND_STEPS;
		// Equal-power panning: the voice is as loud at the sides as in the middle, and fully in
		// one ear at -1 or +1.
		double angle = (pan + 1.0) * Math.PI / 4.0;
		double left = Math.cos(angle);
		double right = Math.sin(angle);
		double smoothing = 1.0;
		if (behind > 0.0f) {
			double cutoff = OPEN_CUTOFF_HZ * Math.pow(BEHIND_CUTOFF_HZ / OPEN_CUTOFF_HZ, behind);
			smoothing = 1.0 - Math.exp(-2.0 * Math.PI * cutoff / request.clip().sampleRate());
		}
		ByteBuffer data = ByteBuffer.allocateDirect(mono.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		double filtered = 0.0;
		for (short sample : mono) {
			// One-pole low-pass - a muffle, like hearing something round the back of your head.
			filtered += smoothing * (sample - filtered);
			data.putShort((short) Math.round(filtered * left));
			data.putShort((short) Math.round(filtered * right));
		}
		data.flip();
		SoundBuffer buffer = new SoundBuffer(data, new AudioFormat(request.clip().sampleRate(), 16, 2, true, false));
		voiceBuffers.add(buffer);
		return buffer;
	}

	private record Request(Clip clip, int panStep, int behindStep) {
	}

	/** The finished clip and direction a path (".../structure_voice/village_1a2b/p13_b0.ogg") refers to, or null. */
	private static Request parse(Identifier path) {
		if (!path.getNamespace().equals(NAMESPACE) || !path.getPath().startsWith(PATH_PREFIX)) {
			return null;
		}
		String rest = path.getPath().substring(PATH_PREFIX.length());
		if (rest.endsWith(".ogg")) {
			rest = rest.substring(0, rest.length() - ".ogg".length());
		}
		int slash = rest.indexOf('/');
		int underscore = rest.lastIndexOf("_b");
		if (slash < 0 || underscore < slash || !rest.startsWith("p", slash + 1)) {
			return null;
		}
		CompletableFuture<Optional<Clip>> future = clips.get(rest.substring(0, slash));
		Clip clip = future != null && future.isDone() && !future.isCompletedExceptionally()
				? future.join().orElse(null) : null;
		try {
			int panStep = Integer.parseInt(rest.substring(slash + 2, underscore));
			int behindStep = Integer.parseInt(rest.substring(underscore + 2));
			if (clip == null || panStep < 0 || panStep > 2 * PAN_STEPS || behindStep < 0 || behindStep > BEHIND_STEPS) {
				return null;
			}
			return new Request(clip, panStep, behindStep);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String slug(String text) {
		String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		// Keeps non-Latin names (a translated or modded structure) distinct and path-safe.
		return slug.isEmpty() ? "u" + Integer.toHexString(text.hashCode()) : slug + "_" + Integer.toHexString(text.hashCode());
	}

	private static Optional<Clip> render(String text, String slug) {
		Optional<Pcm> pcm = PrismController.getInstance()
				.flatMap(prism -> prism.renderToMemory(text))
				.map(speech -> new Pcm(speech.samples(), speech.channels(), speech.sampleRate()));
		if (pcm.isEmpty()) {
			pcm = renderExternally(text);
		}
		if (pcm.isEmpty()) {
			LOGGER.info("No text-to-speech engine could render '{}' to audio; structures will be narrated instead", text);
			return Optional.empty();
		}
		return Optional.of(toClip(pcm.get(), slug));
	}

	/** Downmixes to mono and normalizes the peak, so every name comes out equally loud whatever the voice. */
	private static Clip toClip(Pcm pcm, String slug) {
		int channels = Math.max(1, pcm.channels());
		int frames = pcm.samples().length / channels;
		float[] mono = new float[frames];
		float peak = 0.0f;
		for (int frame = 0; frame < frames; frame++) {
			float sum = 0.0f;
			for (int channel = 0; channel < channels; channel++) {
				sum += pcm.samples()[frame * channels + channel];
			}
			mono[frame] = sum / channels;
			peak = Math.max(peak, Math.abs(mono[frame]));
		}
		float gain = peak > 0.0001f ? PEAK / peak : 1.0f;
		short[] samples = new short[frames];
		for (int frame = 0; frame < frames; frame++) {
			samples[frame] = (short) Math.round(Math.max(-1.0f, Math.min(1.0f, mono[frame] * gain)) * Short.MAX_VALUE);
		}
		return new Clip(slug, samples, pcm.sampleRate());
	}

	private record Pcm(float[] samples, int channels, int sampleRate) {
	}

	/** Command-line fallbacks for systems where Prism can't render to memory. */
	private static Optional<Pcm> renderExternally(String text) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("mac") || os.contains("darwin")) {
			return renderWithSay(text);
		}
		if (os.contains("win")) {
			return Optional.empty();
		}
		for (String command : List.of("espeak-ng", "espeak")) {
			Optional<Pcm> pcm = runForWav(new ProcessBuilder(command, "--stdout", text), null);
			if (pcm.isPresent()) {
				return pcm;
			}
		}
		return Optional.empty();
	}

	private static Optional<Pcm> renderWithSay(String text) {
		Path file = null;
		try {
			Path dir = Platform.get().gameDir().resolve("united_minecraft");
			Files.createDirectories(dir);
			file = Files.createTempFile(dir, "structure_voice", ".wav");
			return runForWav(new ProcessBuilder("say", "--file-format=WAVE", "--data-format=LEI16@22050",
					"-o", file.toString(), text), file);
		} catch (IOException e) {
			LOGGER.debug("Could not render '{}' with say", text, e);
			return Optional.empty();
		} finally {
			if (file != null) {
				try {
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
					// A leftover temp file in the game directory is harmless.
				}
			}
		}
	}

	/** Runs {@code process} and reads a WAV from {@code output}, or from its stdout when that's null. */
	private static Optional<Pcm> runForWav(ProcessBuilder process, Path output) {
		try {
			Process running = process.redirectErrorStream(false).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			byte[] stdout;
			try (InputStream in = running.getInputStream()) {
				stdout = readAll(in);
			}
			if (!running.waitFor(EXTERNAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				running.destroyForcibly();
				return Optional.empty();
			}
			if (running.exitValue() != 0) {
				return Optional.empty();
			}
			return parseWav(output == null ? stdout : Files.readAllBytes(output));
		} catch (IOException e) {
			// Not installed - expected on most systems for at least one of the candidates.
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		in.transferTo(out);
		return out.toByteArray();
	}

	/**
	 * Reads 16-bit PCM from a RIFF/WAVE file. Tolerates the bogus chunk sizes espeak writes when
	 * streaming to stdout (it can't seek back to fill them in) by reading to the end instead.
	 */
	static Optional<Pcm> parseWav(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		if (bytes.length < 12 || buffer.getInt(0) != 0x46464952 /* RIFF */ || buffer.getInt(8) != 0x45564157 /* WAVE */) {
			return Optional.empty();
		}
		int channels = 0;
		int sampleRate = 0;
		int bits = 0;
		int position = 12;
		while (position + 8 <= bytes.length) {
			int id = buffer.getInt(position);
			long size = Integer.toUnsignedLong(buffer.getInt(position + 4));
			int body = position + 8;
			if (id == 0x20746d66 /* "fmt " */ && body + 16 <= bytes.length) {
				int format = buffer.getShort(body) & 0xffff;
				channels = buffer.getShort(body + 2) & 0xffff;
				sampleRate = buffer.getInt(body + 4);
				bits = buffer.getShort(body + 14) & 0xffff;
				if (format != 1 && format != 0xfffe) {
					return Optional.empty();
				}
			} else if (id == 0x61746164 /* "data" */) {
				if (channels == 0 || sampleRate <= 0 || bits != 16) {
					return Optional.empty();
				}
				int end = (int) Math.min(bytes.length, body + size);
				float[] samples = new float[(end - body) / 2];
				for (int i = 0; i < samples.length; i++) {
					samples[i] = buffer.getShort(body + i * 2) / 32768.0f;
				}
				return samples.length == 0 ? Optional.empty() : Optional.of(new Pcm(samples, channels, sampleRate));
			}
			position = (int) Math.min(Integer.MAX_VALUE, body + size + (size & 1));
		}
		return Optional.empty();
	}
}
