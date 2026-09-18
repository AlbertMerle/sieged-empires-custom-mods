package com.siegedempires.client.performance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.siegedempires.Siegedempires;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tracks the player's chosen performance preset under {@code config/SiegedEmpires/performance_settings.json}.
 * No tier is selected until the player picks one (first-launch title prompt or Performance Settings).
 */
public final class PerformanceSettingsState {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final Path FILE = Path.of("config", "SiegedEmpires", "performance_settings.json");
	private static PerformanceSettingsState INSTANCE;

	@SerializedName("selected_level")
	public @Nullable String selectedLevel = null;

	private PerformanceSettingsState() {
	}

	public static PerformanceSettingsState get() {
		if (INSTANCE == null) {
			load();
		}
		return INSTANCE;
	}

	/** True until the player has applied a performance preset at least once. */
	public static boolean needsFirstTimeChoice() {
		return !get().hasSelected();
	}

	public boolean hasSelected() {
		return this.selectedLevel != null && !this.selectedLevel.isBlank();
	}

	/** Selected tier, or {@code null} when none has been chosen yet. */
	public @Nullable PerformanceLevel selectedLevelOrNull() {
		if (!hasSelected()) {
			return null;
		}
		return PerformanceLevel.byId(this.selectedLevel);
	}

	/** Falls back to Medium only for invalid stored ids; use {@link #selectedLevelOrNull()} when none may be set. */
	public PerformanceLevel selectedLevel() {
		PerformanceLevel level = selectedLevelOrNull();
		return level != null ? level : PerformanceLevel.MEDIUM;
	}

	public void setSelectedLevel(PerformanceLevel level) {
		this.selectedLevel = level.id();
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				INSTANCE = GSON.fromJson(reader, PerformanceSettingsState.class);
				if (INSTANCE == null) {
					INSTANCE = new PerformanceSettingsState();
				}
			} catch (IOException e) {
				Siegedempires.LOGGER.error("Failed to load performance settings state", e);
				INSTANCE = new PerformanceSettingsState();
			}
		} else {
			INSTANCE = new PerformanceSettingsState();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(get(), writer);
			}
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to save performance settings state", e);
		}
	}
}
