package com.siegedempires.client.performance;

import com.siegedempires.Siegedempires;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copies bundled preset files into the game directory and merges performance-related {@code options.txt} keys.
 * Also sets {@code voxy.geometryBufferSizeOverrideMB} in the running JVM so CurseForge/Modrinth/Prism
 * clients get the cap without relying on launcher JVM args.
 */
public final class PerformancePresetApplier {
	private static final String PRESET_ROOT = "/data/siegedempires/performance_presets/";
	private static final String VOXY_GEOMETRY_BUFFER_PROPERTY = "voxy.geometryBufferSizeOverrideMB";
	private static final Path SOUND_PHYSICS_FILE =
			Path.of("config", "sound_physics_remastered", "soundphysics.properties");

	private static final List<PresetFile> SHARED_FILES = List.of(
			new PresetFile("options_performance.txt", null, true),
			new PresetFile("iris.properties", "config/iris.properties", false),
			new PresetFile("voxy-config.json", "config/voxy-config.json", false),
			new PresetFile("voxyserver-client.json", "config/voxyserver-client.json", false),
			new PresetFile("voxyserver.json", "config/voxyserver.json", false),
			/** SE launcher reads on next Play; mod also sets the property in-process below. */
			new PresetFile("user_jvm_args.txt", "user_jvm_args.txt", false)
	);

	private static final PresetFile BSL_SETTINGS =
			new PresetFile("shaderpacks/BSL_v10.1.3.zip.txt", "shaderpacks/BSL_v10.1.3.zip.txt", false);

	/** Potato: no BSL profile copy (shaders disabled in iris.properties). */
	private static final List<PresetFile> POTATO_FILES = SHARED_FILES;
	private static final List<PresetFile> LOW_FILES = concat(SHARED_FILES, BSL_SETTINGS);
	private static final List<PresetFile> MEDIUM_FILES = concat(SHARED_FILES, BSL_SETTINGS);
	private static final List<PresetFile> HIGH_FILES = concat(SHARED_FILES, BSL_SETTINGS);
	private static final List<PresetFile> ULTRA_FILES = concat(SHARED_FILES, BSL_SETTINGS);

	private PerformancePresetApplier() {
	}

	/**
	 * Re-applies Voxy geometry-buffer size from the last cached performance tier.
	 * Always wins over whatever {@code -Dvoxy.geometryBufferSizeOverrideMB} the launcher passed
	 * (e.g. Medium → 512 even if JVM args say 0 or 2000). No cached tier → pack default 328.
	 */
	public static void applyStoredVoxyGeometryBufferOverride() {
		PerformanceLevel level = PerformanceSettingsState.get().selectedLevelOrNull();
		int mb = level != null ? level.voxyGeometryBufferMb() : PerformanceLevel.DEFAULT_VOXY_GEOMETRY_BUFFER_MB;
		String source = level != null ? "cached performance setting '" + level.id() + "'" : "pack default (no tier chosen yet)";
		applyVoxyGeometryBufferOverride(mb, source);
	}

	/**
	 * Sets {@code voxy.geometryBufferSizeOverrideMB} for this JVM process, replacing any prior value
	 * (including launcher {@code -D} args). Voxy reads it when allocating the GL geometry buffer.
	 */
	public static void applyVoxyGeometryBufferOverride(PerformanceLevel level) {
		applyVoxyGeometryBufferOverride(level.voxyGeometryBufferMb(), "performance preset '" + level.id() + "'");
	}

	private static void applyVoxyGeometryBufferOverride(int megabytes, String source) {
		String previous = System.getProperty(VOXY_GEOMETRY_BUFFER_PROPERTY);
		String value = Integer.toString(megabytes);
		System.setProperty(VOXY_GEOMETRY_BUFFER_PROPERTY, value);
		if (previous == null) {
			Siegedempires.LOGGER.info("Set {}={} from {} (no prior JVM value)", VOXY_GEOMETRY_BUFFER_PROPERTY, value, source);
		} else if (previous.equals(value)) {
			Siegedempires.LOGGER.info("Kept {}={} from {}", VOXY_GEOMETRY_BUFFER_PROPERTY, value, source);
		} else {
			Siegedempires.LOGGER.info(
					"Overrode {} from JVM/prior {} → {} ({})",
					VOXY_GEOMETRY_BUFFER_PROPERTY,
					previous,
					value,
					source);
		}
	}

	public static boolean apply(Minecraft client, PerformanceLevel level) {
		if (!level.isAvailable()) {
			return false;
		}

		Path gameDir = client.gameDirectory.toPath();
		try {
			for (PresetFile file : filesFor(level)) {
				applyPresetFile(gameDir, level, file);
			}
			applySoundPhysicsEnabled(gameDir, level != PerformanceLevel.POTATO);
			applyVoxyGeometryBufferOverride(level);

			client.options.load();
			client.options.save();
			PerformanceSettingsState.get().setSelectedLevel(level);
			PerformanceSettingsState.save();
			Siegedempires.LOGGER.info("Applied {} performance preset", level.id());
			return true;
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to apply {} performance preset", level.id(), e);
			return false;
		}
	}

	private static List<PresetFile> filesFor(PerformanceLevel level) {
		return switch (level) {
			case POTATO -> POTATO_FILES;
			case LOW -> LOW_FILES;
			case MEDIUM -> MEDIUM_FILES;
			case HIGH -> HIGH_FILES;
			case ULTRA -> ULTRA_FILES;
		};
	}

	private static List<PresetFile> concat(List<PresetFile> base, PresetFile... extra) {
		List<PresetFile> combined = new ArrayList<>(base);
		combined.addAll(List.of(extra));
		return List.copyOf(combined);
	}

	private static void applyPresetFile(Path gameDir, PerformanceLevel level, PresetFile file) throws IOException {
		String resourcePath = PRESET_ROOT + level.id() + "/" + file.resourceName();
		if (file.mergeOptions()) {
			Map<String, String> overrides = readOptionOverrides(resourcePath);
			mergePerformanceOptions(gameDir.resolve("options.txt"), overrides);
			return;
		}

		Path target = gameDir.resolve(file.targetPath());
		copyResource(resourcePath, target);
	}

	/**
	 * Potato disables Sound Physics Remastered; every higher tier turns it back on so
	 * switching upward restores audio processing.
	 */
	private static void applySoundPhysicsEnabled(Path gameDir, boolean enabled) throws IOException {
		Path target = gameDir.resolve(SOUND_PHYSICS_FILE);
		Files.createDirectories(target.getParent());
		List<String> lines = Files.exists(target)
				? Files.readAllLines(target, StandardCharsets.UTF_8)
				: List.of("# Enables/Disables all sound effects");
		boolean wrote = false;
		List<String> result = new ArrayList<>(lines.size() + 1);
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.startsWith("enabled=") || trimmed.startsWith("enabled =")) {
				result.add("enabled=" + enabled);
				wrote = true;
			} else {
				result.add(line);
			}
		}
		if (!wrote) {
			result.add(0, "enabled=" + enabled);
		}
		Files.write(target, result, StandardCharsets.UTF_8);
	}

	private static Map<String, String> readOptionOverrides(String resourcePath) throws IOException {
		Map<String, String> overrides = new LinkedHashMap<>();
		try (InputStream input = PerformancePresetApplier.class.getResourceAsStream(resourcePath)) {
			if (input == null) {
				throw new IOException("Missing preset resource: " + resourcePath);
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					int colon = line.indexOf(':');
					if (colon <= 0) {
						continue;
					}
					overrides.put(line.substring(0, colon), line.substring(colon + 1));
				}
			}
		}
		return overrides;
	}

	private static void mergePerformanceOptions(Path optionsFile, Map<String, String> overrides) throws IOException {
		List<String> lines = Files.exists(optionsFile) ? Files.readAllLines(optionsFile, StandardCharsets.UTF_8) : List.of();
		Set<String> merged = new HashSet<>();
		List<String> result = new ArrayList<>();

		for (String line : lines) {
			int colon = line.indexOf(':');
			if (colon > 0) {
				String key = line.substring(0, colon);
				if (overrides.containsKey(key)) {
					result.add(key + ":" + overrides.get(key));
					merged.add(key);
					continue;
				}
			}
			result.add(line);
		}

		for (Map.Entry<String, String> entry : overrides.entrySet()) {
			if (!merged.contains(entry.getKey())) {
				result.add(entry.getKey() + ":" + entry.getValue());
			}
		}

		Files.createDirectories(optionsFile.getParent());
		Files.write(optionsFile, result, StandardCharsets.UTF_8);
	}

	private static void copyResource(String resourcePath, Path target) throws IOException {
		try (InputStream input = PerformancePresetApplier.class.getResourceAsStream(resourcePath)) {
			if (input == null) {
				throw new IOException("Missing preset resource: " + resourcePath);
			}
			Files.createDirectories(target.getParent());
			Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private record PresetFile(String resourceName, String targetPath, boolean mergeOptions) {
	}
}
