package com.weaponsmodaddon.client.sound;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.openal.AL10.AL_NO_ERROR;
import static org.lwjgl.openal.AL10.alGetError;
import static org.lwjgl.openal.AL10.alSourcei;
import static org.lwjgl.openal.EXTEfx.AL_DIRECT_FILTER;
import static org.lwjgl.openal.EXTEfx.AL_FILTER_LOWPASS;
import static org.lwjgl.openal.EXTEfx.AL_FILTER_NULL;
import static org.lwjgl.openal.EXTEfx.AL_FILTER_TYPE;
import static org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAIN;
import static org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAINHF;
import static org.lwjgl.openal.EXTEfx.alFilterf;
import static org.lwjgl.openal.EXTEfx.alFilteri;
import static org.lwjgl.openal.EXTEfx.alGenFilters;

/**
 * OpenAL Soft low-pass on world sources while ear-ring is active.
 * Relative sources (the tinnitus itself) are exempt.
 * <p>
 * When Sound Physics Remastered is loaded we never touch {@code AL_DIRECT_FILTER}:
 * SPR owns that slot for reverb/occlusion aux routing, and clearing it kills gunshot echo.
 */
public final class GunEarRingLowpass {
	private static final Logger LOGGER = LoggerFactory.getLogger("weaponsmodaddon/ear-ring");
	/**
	 * HF shelf gain at full muffling. EFX low-pass has no Hz cutoff; Soft’s
	 * reference is ~5 kHz, so this gain approximates a ~200 Hz muffled band
	 * (audible rumble/noise, not near-silent).
	 */
	private static final float MIN_GAIN_HF = 0.42f;
	/** Keep overall dry gain high so muffled audio stays hearable. */
	private static final float MIN_GAIN = 0.92f;

	private static final Set<Integer> RELATIVE_SOURCES = ConcurrentHashMap.newKeySet();
	private static final Set<Integer> KNOWN_SOURCES = ConcurrentHashMap.newKeySet();

	private static boolean efxChecked;
	private static boolean efxAvailable;
	private static boolean deferToSoundPhysics;
	private static int filterId;
	private static float lastAmount = -1.0f;

	private GunEarRingLowpass() {
	}

	public static void markRelative(int source, boolean relative) {
		KNOWN_SOURCES.add(source);
		if (relative) {
			RELATIVE_SOURCES.add(source);
			clearFilter(source);
		} else {
			RELATIVE_SOURCES.remove(source);
			applyToSource(source);
		}
	}

	public static void onSourceUsed(int source) {
		KNOWN_SOURCES.add(source);
		applyToSource(source);
	}

	public static void forgetSource(int source) {
		KNOWN_SOURCES.remove(source);
		RELATIVE_SOURCES.remove(source);
	}

	/** Update shared filter params from 0..1 effect amount; refresh attached sources. */
	public static void setEffectAmount(float amount) {
		if (!ensureEfx()) {
			return;
		}
		float t = Mth.clamp(amount, 0.0f, 1.0f);
		if (Math.abs(t - lastAmount) < 0.002f && t > 0.0f && t < 1.0f) {
			return;
		}
		lastAmount = t;

		if (t <= 0.001f) {
			clearAllFilters();
			return;
		}

		float gainHf = Mth.lerp(t, 1.0f, MIN_GAIN_HF);
		float gain = Mth.lerp(t, 1.0f, MIN_GAIN);
		alFilteri(filterId, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
		alFilterf(filterId, AL_LOWPASS_GAIN, gain);
		alFilterf(filterId, AL_LOWPASS_GAINHF, gainHf);
		drainError();

		for (int source : KNOWN_SOURCES) {
			applyToSource(source);
		}
	}

	public static void clearAllFilters() {
		lastAmount = 0.0f;
		if (!efxAvailable || filterId == 0 || deferToSoundPhysics) {
			return;
		}
		for (int source : KNOWN_SOURCES) {
			clearFilter(source);
		}
	}

	private static void applyToSource(int source) {
		if (!ensureEfx() || filterId == 0 || deferToSoundPhysics) {
			return;
		}
		if (RELATIVE_SOURCES.contains(source) || GunEarRingClient.effectAmount() <= 0.001f) {
			clearFilter(source);
			return;
		}
		alSourcei(source, AL_DIRECT_FILTER, filterId);
		drainError();
	}

	private static void clearFilter(int source) {
		if (!efxAvailable || deferToSoundPhysics) {
			return;
		}
		alSourcei(source, AL_DIRECT_FILTER, AL_FILTER_NULL);
		drainError();
	}

	private static boolean ensureEfx() {
		if (efxChecked) {
			return efxAvailable;
		}
		efxChecked = true;
		if (FabricLoader.getInstance().isModLoaded("sound_physics_remastered")) {
			// SPR drives AL_DIRECT_FILTER + aux reverb sends; fighting it kills echo.
			deferToSoundPhysics = true;
			efxAvailable = false;
			LOGGER.info("Sound Physics Remastered present — ear-ring uses volume duck only (no EFX lowpass)");
			return false;
		}
		try {
			var alcCaps = ALC.getCapabilities();
			var alCaps = AL.getCapabilities();
			if (alcCaps == null || alCaps == null || !alcCaps.ALC_EXT_EFX || alCaps.alGenFilters == 0L) {
				LOGGER.info("OpenAL EFX unavailable; ear-ring will volume-duck only");
				efxAvailable = false;
				return false;
			}
			filterId = alGenFilters();
			if (filterId == 0 || alGetError() != AL_NO_ERROR) {
				LOGGER.info("Failed to create OpenAL lowpass filter; ear-ring will volume-duck only");
				efxAvailable = false;
				filterId = 0;
				return false;
			}
			alFilteri(filterId, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
			alFilterf(filterId, AL_LOWPASS_GAIN, 1.0f);
			alFilterf(filterId, AL_LOWPASS_GAINHF, 1.0f);
			efxAvailable = true;
			LOGGER.info("Ear-ring OpenAL lowpass ready (filter {})", filterId);
			return true;
		} catch (Throwable t) {
			LOGGER.info("OpenAL EFX init failed; ear-ring will volume-duck only: {}", t.toString());
			efxAvailable = false;
			return false;
		}
	}

	private static void drainError() {
		alGetError();
	}
}
