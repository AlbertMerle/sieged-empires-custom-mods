package com.shiftyocean.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.shiftyocean.Shiftyocean;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code config/shiftyocean.json} — player/entity ocean currents.
 * <p>
 * {@code enabled-entities} accepts entity ids ({@code minecraft:oak_boat}),
 * entity-type tags ({@code #minecraft:boat}), and namespace wildcards
 * ({@code shippy-ships:*}), or explicit Shippy vessel ids.
 */
public final class ShiftyoceanConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static ShiftyoceanConfig INSTANCE = new ShiftyoceanConfig();

	public boolean enabled = true;

	/** Blocks/second for swimming players in clear weather. */
	public double clearSpeedBps = 0.0;
	/** Blocks/second for swimming players in rain. */
	public double rainSpeedBps = 0.4;
	/** Blocks/second for swimming players in thunderstorms. */
	public double thunderSpeedBps = 1.0;
	/**
	 * How often the short-interval current re-rolls (ticks; 10 = 500ms).
	 * Each roll uses {@link #generalDirectionBias} toward the 7-minute heading.
	 */
	public int directionChangeTicks = 10;
	/**
	 * How often the general (global) current picks a new heading, in minutes.
	 * Default 7 → 8400 ticks. Heading is any angle on the XZ circle, not NESW-only.
	 */
	public double generalDirectionChangeMinutes = 7.0;
	/**
	 * Chance each short-interval roll uses the general heading (0–1).
	 * Default 0.7 = 70% general, 30% a different random 360° gust.
	 */
	public double generalDirectionBias = 0.7;

	/**
	 * Fraction of player BPS applied to {@link #enabledEntities} (default 0.15 = 15%).
	 */
	public double entitySpeedScale = 0.15;
	/**
	 * Random yaw drift for enabled entities in rain (degrees per second).
	 * Default 8 = 2× the former flat 4°/s.
	 */
	public double entityYawDriftRainDegreesPerSecond = 8.0;
	/**
	 * Random yaw drift for enabled entities in thunderstorms (degrees per second).
	 * Default 12 = 150% of rain default.
	 */
	public double entityYawDriftThunderDegreesPerSecond = 12.0;

	/**
	 * Legacy single yaw key — if present in an old JSON, migrates into rain/thunder once.
	 */
	@Deprecated
	public Double entityYawDriftDegreesPerSecond = null;

	/**
	 * Entity types affected by currents (not players). Players always use full BPS.
	 */
	@SerializedName("enabled-entities")
	public List<String> enabledEntities = defaultEnabledEntities();

	/** Resolved exact ids from {@link #enabledEntities} (no tags/wildcards). */
	private transient Set<Identifier> exactEntityIds = Set.of();
	/** Entity-type tags from {@link #enabledEntities} entries starting with {@code #}. */
	private transient List<TagKey<EntityType<?>>> entityTags = List.of();
	/** Namespaces matched by {@code namespace:*} wildcards. */
	private transient Set<String> entityNamespaces = Set.of();

	private ShiftyoceanConfig() {
	}

	public static ShiftyoceanConfig get() {
		return INSTANCE;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("shiftyocean.json");
		ShiftyoceanConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, ShiftyoceanConfig.class);
			} catch (IOException e) {
				Shiftyocean.LOGGER.warn("Failed to read {}", path, e);
			}
		}

		INSTANCE = loaded != null ? loaded : new ShiftyoceanConfig();
		INSTANCE.sanitize();
		INSTANCE.rebuildEntityMatchers();
		INSTANCE.save(path);

		// Drop legacy properties file if present (migrated to JSON).
		Path legacy = FabricLoader.getInstance().getConfigDir().resolve("shiftyocean.properties");
		if (Files.exists(legacy)) {
			try {
				Files.deleteIfExists(legacy);
				Shiftyocean.LOGGER.info("Removed legacy {}", legacy.getFileName());
			} catch (IOException ignored) {
			}
		}
	}

	private void sanitize() {
		clearSpeedBps = Math.max(0.0, clearSpeedBps);
		rainSpeedBps = Math.max(0.0, rainSpeedBps);
		thunderSpeedBps = Math.max(0.0, thunderSpeedBps);
		directionChangeTicks = Math.max(1, directionChangeTicks);
		generalDirectionChangeMinutes = Math.max(1.0 / 60.0, generalDirectionChangeMinutes);
		generalDirectionBias = Math.min(1.0, Math.max(0.0, generalDirectionBias));
		entitySpeedScale = Math.max(0.0, entitySpeedScale);

		// Old configs had one yaw rate; split into rain (2×) and thunder (1.5× rain).
		if (entityYawDriftDegreesPerSecond != null) {
			double legacy = Math.max(0.0, entityYawDriftDegreesPerSecond);
			entityYawDriftRainDegreesPerSecond = legacy * 2.0;
			entityYawDriftThunderDegreesPerSecond = entityYawDriftRainDegreesPerSecond * 1.5;
			entityYawDriftDegreesPerSecond = null;
		}

		entityYawDriftRainDegreesPerSecond = Math.max(0.0, entityYawDriftRainDegreesPerSecond);
		entityYawDriftThunderDegreesPerSecond = Math.max(0.0, entityYawDriftThunderDegreesPerSecond);
		if (enabledEntities == null) {
			enabledEntities = defaultEnabledEntities();
		}
	}

	private void rebuildEntityMatchers() {
		Set<Identifier> exact = new HashSet<>();
		List<TagKey<EntityType<?>>> tags = new ArrayList<>();
		Set<String> namespaces = new HashSet<>();

		for (String raw : enabledEntities) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String entry = raw.trim();
			if (entry.startsWith("#")) {
				Identifier tagId = Identifier.tryParse(entry.substring(1));
				if (tagId != null) {
					tags.add(TagKey.create(Registries.ENTITY_TYPE, tagId));
				} else {
					Shiftyocean.LOGGER.warn("Invalid enabled-entities tag: {}", entry);
				}
				continue;
			}
			if (entry.endsWith(":*")) {
				String ns = entry.substring(0, entry.length() - 2);
				if (!ns.isBlank() && !ns.contains(":")) {
					namespaces.add(ns);
				} else {
					Shiftyocean.LOGGER.warn("Invalid enabled-entities wildcard: {}", entry);
				}
				continue;
			}
			Identifier id = Identifier.tryParse(entry);
			if (id != null) {
				exact.add(id);
			} else {
				Shiftyocean.LOGGER.warn("Invalid enabled-entities id: {}", entry);
			}
		}

		exactEntityIds = Set.copyOf(exact);
		entityTags = List.copyOf(tags);
		entityNamespaces = Set.copyOf(namespaces);
	}

	/** Ticks between general-heading changes (minutes × 60 × 20). */
	public int generalDirectionChangeTicks() {
		return Math.max(1, (int) Math.round(generalDirectionChangeMinutes * 60.0 * 20.0));
	}

	public boolean isEnabledEntity(EntityType<?> type) {
		Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		if (key != null) {
			if (exactEntityIds.contains(key)) {
				return true;
			}
			if (entityNamespaces.contains(key.getNamespace())) {
				return true;
			}
		}
		for (TagKey<EntityType<?>> tag : entityTags) {
			if (type.builtInRegistryHolder().is(tag)) {
				return true;
			}
		}
		return false;
	}

	private void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
				writer.write('\n');
			}
		} catch (IOException e) {
			Shiftyocean.LOGGER.warn("Failed to write {}", path, e);
		}
	}

	private static List<String> defaultEnabledEntities() {
		List<String> list = new ArrayList<>();
		// All vanilla boats + bamboo raft (not chest boats).
		list.add("#minecraft:boat");
		// Chest boats / chest raft (not in #minecraft:boat).
		list.add("minecraft:oak_chest_boat");
		list.add("minecraft:spruce_chest_boat");
		list.add("minecraft:birch_chest_boat");
		list.add("minecraft:jungle_chest_boat");
		list.add("minecraft:acacia_chest_boat");
		list.add("minecraft:cherry_chest_boat");
		list.add("minecraft:dark_oak_chest_boat");
		list.add("minecraft:pale_oak_chest_boat");
		list.add("minecraft:mangrove_chest_boat");
		list.add("minecraft:bamboo_chest_raft");
		// Shippy Ships — every registered vessel (soft-dep; ignored if mod absent).
		list.addAll(shippyShipsEntities());
		return list;
	}

	/** All Shippy Ships entity ids (sailboat / cog / caravel × wood types). */
	private static List<String> shippyShipsEntities() {
		String[] woods = {
				"oak", "spruce", "birch", "jungle", "acacia",
				"cherry", "dark_oak", "mangrove", "bamboo", "pale_oak"
		};
		String[] kinds = {"sailboat", "cog", "caravel"};
		List<String> list = new ArrayList<>(woods.length * kinds.length);
		for (String wood : woods) {
			for (String kind : kinds) {
				list.add("shippy-ships:" + wood + "_" + kind);
			}
		}
		return list;
	}
}
