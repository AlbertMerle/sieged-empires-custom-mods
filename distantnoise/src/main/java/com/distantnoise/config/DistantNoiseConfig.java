package com.distantnoise.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.distantnoise.Distantnoise;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config/distantnoise.json} — distant musket / TNT relay settings.
 */
public final class DistantNoiseConfig {
	private static class StompingEntitiesAdapter implements JsonDeserializer<Map<String, Double>> {
		@Override
		public Map<String, Double> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			Map<String, Double> result = new LinkedHashMap<>();
			if (json != null && json.isJsonObject()) {
				JsonObject obj = json.getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
					double dist = 50.0;
					if (entry.getValue() != null && entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
						dist = entry.getValue().getAsDouble();
					}
					result.put(entry.getKey(), dist);
				}
			} else if (json != null && json.isJsonArray()) {
				JsonArray array = json.getAsJsonArray();
				Map<String, Double> defaults = defaultStompingEntities();
				for (JsonElement el : array) {
					if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
						String key = el.getAsString();
						Double defaultDist = defaults.get(key);
						result.put(key, defaultDist != null ? defaultDist : 50.0);
					}
				}
			}
			return result;
		}
	}

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.registerTypeAdapter(new TypeToken<Map<String, Double>>() {}.getType(), new StompingEntitiesAdapter())
			.create();
	private static DistantNoiseConfig INSTANCE = new DistantNoiseConfig();

	public boolean enabled = true;

	/** Max hear distance (blocks) for distant relays. */
	@SerializedName("radius")
	public double radius = 800.0;

	/**
	 * Legacy cutoff for non-explosion relays that still use tier muffling.
	 * Explosions use {@link #explosion} and relay from 0 blocks.
	 */
	@SerializedName("near-cutoff")
	public double nearCutoff = 40.0;

	/** Gun relay: full boosted volume, ear ring, and distance falloff. */
	@SerializedName("gun")
	public GunRelaySettings gun = GunRelaySettings.defaults();

	/** TNT / explosion relay — custom boom with gun-like curve out to {@link #radius}. */
	@SerializedName("explosion")
	public ExplosionRelaySettings explosion = ExplosionRelaySettings.defaults();

	/** Packet volume at {@link #nearCutoff} — full strength at the edge of the distant band. */
	@SerializedName("volume-near")
	public float volumeNear = 1.0f;

	/** Packet volume at {@link #radius} — barely audible whisper at max range. */
	@SerializedName("volume-far")
	public float volumeFar = 0.03f;

	/** Extra pitch mul applied on the client (slightly duller overall). */
	@SerializedName("pitch")
	public float pitch = 0.9f;

	/**
	 * Gun relays beyond server view distance (chunks × 16) are delayed by this many ticks
	 * so far shots feel like they arrive after the visible muzzle flash. {@code 40} = 2 s.
	 * Set to {@code 0} to play immediately at all distances.
	 */
	@SerializedName("gun-delay-beyond-view-ticks")
	public int gunDelayBeyondViewTicks = 40;

	/** Relay listed entity stomps with distance-scaled volume. */
	@SerializedName("footsteps-enabled")
	public boolean footstepsEnabled = true;

	/** Entity type ids to hear distance (e.g. {@code "minecraft:iron_golem": 65.0}) that relay stomp sounds. */
	@SerializedName("stomping-entities")
	public Map<String, Double> stompingEntities = defaultStompingEntities();

	@SerializedName("stomp")
	public RelayRangeSettings stomp = RelayRangeSettings.stompDefaults();

	/** Relay running / fleeing mob steps with extended hear distance (excludes stomping-entities). */
	@SerializedName("running-footsteps")
	public boolean runningFootstepsEnabled = true;

	/** Min horizontal speed (blocks per second) to count as running. */
	@SerializedName("running-min-speed-bps")
	public double runningMinSpeedBps = 4.0;

	@SerializedName("running")
	public RelayRangeSettings running = RelayRangeSettings.runningDefaults();

	/** Relay animal vocal sounds with distance-scaled volume and muffling. */
	@SerializedName("vocal-animals")
	public boolean vocalAnimals = true;

	@SerializedName("vocal")
	public RelayRangeSettings vocal = RelayRangeSettings.vocalDefaults();

	/** Loop grass rustle while animals walk through short/tall grass plants. */
	@SerializedName("grass-russling")
	public boolean grassRussling = true;

	/** Walking through plants — default max 18 blocks with smooth falloff. */
	@SerializedName("russling-walk")
	public RelayRangeSettings russlingWalk = RelayRangeSettings.russlingWalkDefaults();

	/** Fleeing / running through plants — default max 48 blocks with smooth falloff. */
	@SerializedName("russling-flee")
	public RelayRangeSettings russlingFlee = RelayRangeSettings.russlingFleeDefaults();

	/** Relay player walk/sprint/fall echoes while enclosed in a cave (SPR-like enclosure). */
	@SerializedName("cave-echoes")
	public boolean caveEchoesEnabled = true;

	/** Walking footsteps in caves — default max 30 blocks. */
	@SerializedName("cave-walk")
	public RelayRangeSettings caveWalk = RelayRangeSettings.caveWalkDefaults();

	/** Sprinting footsteps in caves — default max 50 blocks. */
	@SerializedName("cave-sprint")
	public RelayRangeSettings caveSprint = RelayRangeSettings.caveSprintDefaults();

	/** Fall-damage impacts in caves — default max 90 blocks (near-silent at the rim). */
	@SerializedName("cave-fall")
	public RelayRangeSettings caveFall = RelayRangeSettings.caveFallDefaults();

	/** Relay player sprint footsteps above ground (not in cave-echo spaces). */
	@SerializedName("sprint-footsteps")
	public boolean sprintFootstepsEnabled = true;

	/** Surface sprint steps — default max 28 blocks, whisper-quiet at the rim. */
	@SerializedName("sprint")
	public RelayRangeSettings sprint = RelayRangeSettings.surfaceSprintDefaults();

	/** Player grass-walk clips while moving through non-collidable plants / crops. */
	@SerializedName("plant-grass-walk")
	public boolean plantGrassWalkEnabled = true;

	/** Walking through plants — vanilla-ish hear distance (~16). */
	@SerializedName("plant-walk")
	public RelayRangeSettings plantWalk = RelayRangeSettings.plantWalkDefaults();

	/** Sprinting through plants — default max 28 blocks. */
	@SerializedName("plant-sprint")
	public RelayRangeSettings plantSprint = RelayRangeSettings.plantSprintDefaults();

	/** Sneaking/crawling through plants — default max 6 blocks (volume scaled in profile). */
	@SerializedName("plant-sneak")
	public RelayRangeSettings plantSneak = RelayRangeSettings.plantSneakDefaults();

	/** Play spaced-out grass walking sounds when snakes (rattlesnake, anaconda) move. */
	@SerializedName("snake-movement")
	public boolean snakeMovementEnabled = true;

	/** Entity type ids that play grass walking sounds when moving. */
	@SerializedName("snake-entities")
	public java.util.List<String> snakeEntities = defaultSnakeEntities();

	/** Hear distance settings for snake movement — default max 15 blocks. */
	@SerializedName("snake")
	public RelayRangeSettings snake = RelayRangeSettings.snakeDefaults();

	/** Custom horse run / armor jingle sounds (replaces vanilla gallop/step). */
	@SerializedName("horse-sounds")
	public boolean horseSoundsEnabled = true;

	/** Horse gallop + neigh hear distance — default max 40 blocks. */
	@SerializedName("horse")
	public RelayRangeSettings horse = RelayRangeSettings.horseDefaults();

	/** Horse body-armor jingle — separate shorter range (default max 12 blocks). */
	@SerializedName("horse-armor")
	public RelayRangeSettings horseArmor = RelayRangeSettings.horseArmorDefaults();

	/** Replace player + mob death sounds with {@code distantnoise:death}. */
	@SerializedName("death-sounds")
	public boolean deathSoundsEnabled = true;

	/** Death clip hear distance — default max 40 blocks. */
	@SerializedName("death")
	public RelayRangeSettings death = RelayRangeSettings.deathDefaults();

	// Legacy flat keys — read once on load, omitted after rewrite.
	@SerializedName("russling-hear-distance")
	private Double legacyRusslingHearDistance;
	@SerializedName("footstep-radius")
	private Double legacyFootstepRadius;
	@SerializedName("footstep-near-cutoff")
	private Double legacyFootstepNearCutoff;
	@SerializedName("footstep-volume-near")
	private Float legacyFootstepVolumeNear;
	@SerializedName("footstep-volume-far")
	private Float legacyFootstepVolumeFar;
	@SerializedName("footstep-pitch")
	private Float legacyFootstepPitch;
	@SerializedName("vocal-radius")
	private Double legacyVocalRadius;
	@SerializedName("vocal-near-cutoff")
	private Double legacyVocalNearCutoff;
	@SerializedName("vocal-volume-near")
	private Float legacyVocalVolumeNear;
	@SerializedName("vocal-volume-far")
	private Float legacyVocalVolumeFar;
	@SerializedName("vocal-pitch")
	private Float legacyVocalPitch;

	private DistantNoiseConfig() {
	}

	/** Distance-scaled relay tuning shared by stomp and vocal sections. */
	public static final class RelayRangeSettings {
		/** Max blocks a player can hear the sound; silent beyond this. */
		@SerializedName("max-volume-range")
		public double maxVolumeRange = 40.0;

		/** Within this distance the sound plays at full {@link #volumeNear}; fades out toward {@link #maxVolumeRange}. */
		@SerializedName("full-volume-closeup-range")
		public double fullVolumeCloseupRange = 8.0;

		@SerializedName("volume-near")
		public float volumeNear = 1.0f;

		@SerializedName("volume-far")
		public float volumeFar = 0.18f;

		@SerializedName("pitch")
		public float pitch = 1.0f;

		static RelayRangeSettings stompDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 48.0;
			s.fullVolumeCloseupRange = 8.0;
			return s;
		}

		static RelayRangeSettings runningDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 30.0;
			s.fullVolumeCloseupRange = 6.0;
			s.volumeNear = 1.35f;
			s.volumeFar = 0.16f;
			return s;
		}

		static RelayRangeSettings vocalDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 48.0;
			s.fullVolumeCloseupRange = 6.0;
			return s;
		}

		static RelayRangeSettings deathDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 40.0;
			s.fullVolumeCloseupRange = 6.0;
			return s;
		}

		static RelayRangeSettings caveWalkDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 30.0;
			s.fullVolumeCloseupRange = 4.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.05f;
			return s;
		}

		static RelayRangeSettings caveSprintDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 50.0;
			s.fullVolumeCloseupRange = 6.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.05f;
			return s;
		}

		static RelayRangeSettings caveFallDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 90.0;
			s.fullVolumeCloseupRange = 8.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.04f;
			return s;
		}

		static RelayRangeSettings surfaceSprintDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 28.0;
			s.fullVolumeCloseupRange = 5.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.04f;
			return s;
		}

		static RelayRangeSettings plantWalkDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			// Matches typical vanilla step hear distance (volume×16 style).
			s.maxVolumeRange = 16.0;
			s.fullVolumeCloseupRange = 3.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.12f;
			return s;
		}

		static RelayRangeSettings plantSprintDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 28.0;
			s.fullVolumeCloseupRange = 5.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.04f;
			return s;
		}

		static RelayRangeSettings plantSneakDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 6.0;
			s.fullVolumeCloseupRange = 2.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.25f;
			return s;
		}

		static RelayRangeSettings snakeDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 15.0;
			s.fullVolumeCloseupRange = 3.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.15f;
			return s;
		}

		static RelayRangeSettings horseDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 40.0;
			s.fullVolumeCloseupRange = 6.0;
			s.volumeNear = 1.35f;
			s.volumeFar = 0.16f;
			return s;
		}

		static RelayRangeSettings horseArmorDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 12.0;
			s.fullVolumeCloseupRange = 3.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.18f;
			return s;
		}

		static RelayRangeSettings russlingWalkDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 18.0;
			s.fullVolumeCloseupRange = 4.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.15f;
			return s;
		}

		static RelayRangeSettings russlingFleeDefaults() {
			RelayRangeSettings s = new RelayRangeSettings();
			s.maxVolumeRange = 48.0;
			s.fullVolumeCloseupRange = 6.0;
			s.volumeNear = 1.0f;
			s.volumeFar = 0.15f;
			return s;
		}

		void normalize(double minMaxRange) {
			maxVolumeRange = Math.max(minMaxRange, maxVolumeRange);
			fullVolumeCloseupRange = Math.max(0.0, Math.min(fullVolumeCloseupRange, maxVolumeRange - 1.0));
			volumeNear = clampVolumeMul(volumeNear);
			volumeFar = clampVolumeMul(volumeFar);
			if (volumeFar > volumeNear) {
				float tmp = volumeFar;
				volumeFar = volumeNear;
				volumeNear = tmp;
			}
			pitch = Math.max(0.1f, Math.min(2.0f, pitch));
		}

		private static float clampVolumeMul(float v) {
			return Math.max(0.01f, Math.min(2.0f, v));
		}

		void applyLegacy(Double maxRange, Double closeupRange, Float volNear, Float volFar, Float pitchMul) {
			if (maxRange != null) {
				maxVolumeRange = maxRange;
			}
			if (closeupRange != null) {
				fullVolumeCloseupRange = closeupRange;
			}
			if (volNear != null) {
				volumeNear = volNear;
			}
			if (volFar != null) {
				volumeFar = volFar;
			}
			if (pitchMul != null) {
				pitch = pitchMul;
			}
		}
	}

	/** Gun shot relay — full loud bang to {@link #fullVolumeRange}, fade + muffle to pack radius. */
	public static final class GunRelaySettings {
		/** Full boosted volume (WeaponsMod Addon client loudness) out to this distance. */
		@SerializedName("full-volume-range")
		public double fullVolumeRange = 48.0;

		/** Ear ring + shock world muffling within this distance (matches addon shooter feel). */
		@SerializedName("ear-ring-range")
		public double earRingRange = 8.0;

		@SerializedName("volume-near")
		public float volumeNear = 1.0f;

		@SerializedName("volume-far")
		public float volumeFar = 0.03f;

		@SerializedName("pitch")
		public float pitch = 1.0f;

		static GunRelaySettings defaults() {
			return new GunRelaySettings();
		}

		void normalize(double maxRadius) {
			fullVolumeRange = Math.max(1.0, Math.min(fullVolumeRange, maxRadius - 1.0));
			earRingRange = Math.max(0.0, Math.min(earRingRange, fullVolumeRange));
			volumeNear = clampVolumeMul(volumeNear);
			volumeFar = clampVolumeMul(volumeFar);
			if (volumeFar > volumeNear) {
				float tmp = volumeFar;
				volumeFar = volumeNear;
				volumeNear = tmp;
			}
			pitch = Math.max(0.1f, Math.min(2.0f, pitch));
		}

		private static float clampVolumeMul(float v) {
			return Math.max(0.01f, Math.min(2.0f, v));
		}
	}

	/** Explosion relay — loud close boom, muffled + bassy echo to {@link #maxVolumeRange}. */
	public static final class ExplosionRelaySettings {
		/** Max hear distance for TNT / explosions (default 1200 blocks). */
		@SerializedName("max-volume-range")
		public double maxVolumeRange = 1200.0;

		/** Full boosted volume out to this distance (matches gun default 48 blocks). */
		@SerializedName("full-volume-range")
		public double fullVolumeRange = 48.0;

		/** Ear ring + shock world muffling within this distance. */
		@SerializedName("ear-ring-range")
		public double earRingRange = 8.0;

		@SerializedName("volume-near")
		public float volumeNear = 1.0f;

		@SerializedName("volume-far")
		public float volumeFar = 0.03f;

		@SerializedName("pitch")
		public float pitch = 1.0f;

		/** Pitch at max range — lower = bassier distant echo (default 0.52). */
		@SerializedName("pitch-far")
		public float pitchFar = 0.52f;

		static ExplosionRelaySettings defaults() {
			return new ExplosionRelaySettings();
		}

		void normalize() {
			maxVolumeRange = Math.max(32.0, maxVolumeRange);
			fullVolumeRange = Math.max(1.0, Math.min(fullVolumeRange, maxVolumeRange - 1.0));
			earRingRange = Math.max(0.0, Math.min(earRingRange, fullVolumeRange));
			volumeNear = clampVolumeMul(volumeNear);
			volumeFar = clampVolumeMul(volumeFar);
			if (volumeFar > volumeNear) {
				float tmp = volumeFar;
				volumeFar = volumeNear;
				volumeNear = tmp;
			}
			pitch = Math.max(0.1f, Math.min(2.0f, pitch));
			pitchFar = Math.max(0.1f, Math.min(pitch, pitchFar));
		}

		private static float clampVolumeMul(float v) {
			return Math.max(0.01f, Math.min(2.0f, v));
		}
	}

	private static Map<String, Double> defaultStompingEntities() {
		Map<String, Double> map = new LinkedHashMap<>();
		// 65 blocks: iron golem, ravager, warden
		map.put("minecraft:iron_golem", 65.0);
		map.put("minecraft:ravager", 65.0);
		map.put("minecraft:warden", 65.0);

		// 50 blocks: all bears, gorilla, bison, elephant
		map.put("alexsmobs:grizzly_bear", 50.0);
		map.put("minecraft:polar_bear", 50.0);
		map.put("minecraft:panda", 50.0);
		map.put("alexsmobs:dropbear", 50.0);
		map.put("alexsmobs:sea_bear", 50.0);
		map.put("alexsmobs:gorilla", 50.0);
		map.put("alexsmobs:bison", 50.0);
		map.put("alexsmobs:elephant", 50.0);

		// 35 blocks: camel, moose, crocodile, caiman, tiger, snow leopard, tusklin
		map.put("minecraft:camel", 35.0);
		map.put("minecraft:camel_husk", 35.0);
		map.put("alexsmobs:moose", 35.0);
		map.put("alexsmobs:crocodile", 35.0);
		map.put("alexsmobs:caiman", 35.0);
		map.put("alexsmobs:tiger", 35.0);
		map.put("alexsmobs:snow_leopard", 35.0);
		map.put("alexsmobs:tusklin", 35.0);

		// 20 blocks: Tasmanian devil
		map.put("alexsmobs:tasmanian_devil", 20.0);

		return map;
	}

	private static java.util.List<String> defaultSnakeEntities() {
		return java.util.List.of(
				"alexsmobs:rattlesnake",
				"alexsmobs:anaconda"
		);
	}

	private transient Map<Identifier, Double> stompingEntityDistances = Map.of();
	private transient java.util.Set<Identifier> snakeEntityTypes = java.util.Set.of();

	public static DistantNoiseConfig get() {
		return INSTANCE;
	}

	public boolean isStompingEntity(Identifier entityTypeId) {
		return stompingEntityDistances.containsKey(entityTypeId);
	}

	public Double getStompingDistance(Identifier entityTypeId) {
		return stompingEntityDistances.get(entityTypeId);
	}

	public boolean isSnakeEntity(Identifier entityTypeId) {
		return snakeEntityTypes.contains(entityTypeId);
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("distantnoise.json");
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				DistantNoiseConfig loaded = GSON.fromJson(reader, DistantNoiseConfig.class);
				if (loaded != null) {
					INSTANCE = loaded;
				}
			} catch (IOException e) {
				Distantnoise.LOGGER.warn("Failed to read {}; using defaults", path, e);
			}
		}
		INSTANCE.normalize();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(INSTANCE, writer);
			}
		} catch (IOException e) {
			Distantnoise.LOGGER.warn("Failed to write {}", path, e);
		}
		Distantnoise.LOGGER.info(
				"Distantnoise config: enabled={} radius={} nearCutoff={} "
						+ "footsteps={} stompClose={} footEntities={} "
						+ "running={} runningMax={} runningClose={} runningMinBps={} "
						+ "vocal={} vocalMax={} vocalClose={} grassRussling={} russlingWalkMax={} russlingFleeMax={} "
						+ "caveEchoes={} caveWalkMax={} caveSprintMax={} caveFallMax={} "
						+ "sprintFootsteps={} sprintMax={} sprintClose={} "
						+ "plantGrass={} plantWalkMax={} plantSprintMax={} plantSneakMax={} "
						+ "snakeMovement={} snakeMax={} snakeClose={} snakeEntities={} "
						+ "horseSounds={} horseMax={} horseClose={} horseArmorMax={} "
						+ "deathSounds={} deathMax={} deathClose={}",
				INSTANCE.enabled,
				INSTANCE.radius,
				INSTANCE.nearCutoff,
				INSTANCE.footstepsEnabled,
				INSTANCE.stomp.fullVolumeCloseupRange,
				INSTANCE.stompingEntityDistances.size(),
				INSTANCE.runningFootstepsEnabled,
				INSTANCE.running.maxVolumeRange,
				INSTANCE.running.fullVolumeCloseupRange,
				INSTANCE.runningMinSpeedBps,
				INSTANCE.vocalAnimals,
				INSTANCE.vocal.maxVolumeRange,
				INSTANCE.vocal.fullVolumeCloseupRange,
				INSTANCE.grassRussling,
				INSTANCE.russlingWalk.maxVolumeRange,
				INSTANCE.russlingFlee.maxVolumeRange,
				INSTANCE.caveEchoesEnabled,
				INSTANCE.caveWalk.maxVolumeRange,
				INSTANCE.caveSprint.maxVolumeRange,
				INSTANCE.caveFall.maxVolumeRange,
				INSTANCE.sprintFootstepsEnabled,
				INSTANCE.sprint.maxVolumeRange,
				INSTANCE.sprint.fullVolumeCloseupRange,
				INSTANCE.plantGrassWalkEnabled,
				INSTANCE.plantWalk.maxVolumeRange,
				INSTANCE.plantSprint.maxVolumeRange,
				INSTANCE.plantSneak.maxVolumeRange,
				INSTANCE.snakeMovementEnabled,
				INSTANCE.snake.maxVolumeRange,
				INSTANCE.snake.fullVolumeCloseupRange,
				INSTANCE.snakeEntityTypes.size(),
				INSTANCE.horseSoundsEnabled,
				INSTANCE.horse.maxVolumeRange,
				INSTANCE.horse.fullVolumeCloseupRange,
				INSTANCE.horseArmor.maxVolumeRange,
				INSTANCE.deathSoundsEnabled,
				INSTANCE.death.maxVolumeRange,
				INSTANCE.death.fullVolumeCloseupRange);
	}

	private void normalize() {
		radius = Math.max(32.0, radius);
		nearCutoff = Math.max(0.0, Math.min(nearCutoff, radius - 1.0));
		volumeNear = clamp01(volumeNear);
		volumeFar = clamp01(volumeFar);
		if (volumeFar > volumeNear) {
			float tmp = volumeFar;
			volumeFar = volumeNear;
			volumeNear = tmp;
		}
		pitch = Math.max(0.1f, Math.min(2.0f, pitch));
		gunDelayBeyondViewTicks = Math.max(0, Math.min(200, gunDelayBeyondViewTicks));

		if (gun == null) {
			gun = GunRelaySettings.defaults();
		}
		gun.normalize(radius);

		if (explosion == null) {
			explosion = ExplosionRelaySettings.defaults();
		}
		explosion.normalize();

		if (stomp == null) {
			stomp = RelayRangeSettings.stompDefaults();
		}
		if (running == null) {
			running = RelayRangeSettings.runningDefaults();
		}
		if (vocal == null) {
			vocal = RelayRangeSettings.vocalDefaults();
		}
		if (caveWalk == null) {
			caveWalk = RelayRangeSettings.caveWalkDefaults();
		}
		if (caveSprint == null) {
			caveSprint = RelayRangeSettings.caveSprintDefaults();
		}
		if (caveFall == null) {
			caveFall = RelayRangeSettings.caveFallDefaults();
		}
		if (sprint == null) {
			sprint = RelayRangeSettings.surfaceSprintDefaults();
		}
		if (plantWalk == null) {
			plantWalk = RelayRangeSettings.plantWalkDefaults();
		}
		if (plantSprint == null) {
			plantSprint = RelayRangeSettings.plantSprintDefaults();
		}
		if (plantSneak == null) {
			plantSneak = RelayRangeSettings.plantSneakDefaults();
		}
		if (snake == null) {
			snake = RelayRangeSettings.snakeDefaults();
		}
		if (horseArmor == null) {
			horseArmor = RelayRangeSettings.horseArmorDefaults();
		}
		if (death == null) {
			death = RelayRangeSettings.deathDefaults();
		}
		if (horse == null) {
			horse = RelayRangeSettings.horseDefaults();
		}
		if (russlingWalk == null) {
			russlingWalk = RelayRangeSettings.russlingWalkDefaults();
		}
		if (russlingFlee == null) {
			russlingFlee = RelayRangeSettings.russlingFleeDefaults();
		}
		stomp.applyLegacy(
				legacyFootstepRadius,
				legacyFootstepNearCutoff,
				legacyFootstepVolumeNear,
				legacyFootstepVolumeFar,
				legacyFootstepPitch
		);
		vocal.applyLegacy(
				legacyVocalRadius,
				legacyVocalNearCutoff,
				legacyVocalVolumeNear,
				legacyVocalVolumeFar,
				legacyVocalPitch
		);
		russlingWalk.applyLegacy(legacyRusslingHearDistance, null, null, null, null);
		stomp.normalize(8.0);
		running.normalize(4.0);
		vocal.normalize(4.0);
		caveWalk.normalize(8.0);
		caveSprint.normalize(8.0);
		caveFall.normalize(16.0);
		sprint.normalize(8.0);
		plantWalk.normalize(4.0);
		plantSprint.normalize(8.0);
		plantSneak.normalize(2.0);
		snake.normalize(2.0);
		horse.normalize(4.0);
		horseArmor.normalize(2.0);
		death.normalize(4.0);
		russlingWalk.normalize(4.0);
		russlingFlee.normalize(8.0);
		runningMinSpeedBps = Math.max(0.5, runningMinSpeedBps);

		if (snakeEntities == null || snakeEntities.isEmpty()) {
			snakeEntities = new java.util.ArrayList<>(defaultSnakeEntities());
		}
		java.util.Set<Identifier> snakeIds = new java.util.HashSet<>();
		for (String raw : snakeEntities) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			try {
				snakeIds.add(Identifier.parse(raw.trim()));
			} catch (Exception e) {
				Distantnoise.LOGGER.warn("Ignoring invalid snake-entities entry: {}", raw);
			}
		}
		if (snakeIds.isEmpty()) {
			for (String s : defaultSnakeEntities()) {
				snakeIds.add(Identifier.parse(s));
			}
		}
		snakeEntityTypes = java.util.Set.copyOf(snakeIds);

		if (stompingEntities == null || stompingEntities.isEmpty()) {
			stompingEntities = new LinkedHashMap<>(defaultStompingEntities());
		}
		Map<Identifier, Double> distances = new HashMap<>();
		for (Map.Entry<String, Double> entry : stompingEntities.entrySet()) {
			String raw = entry.getKey();
			if (raw == null || raw.isBlank()) {
				continue;
			}
			try {
				Identifier id = Identifier.parse(raw.trim());
				double dist = entry.getValue() != null ? Math.max(1.0, entry.getValue()) : stomp.maxVolumeRange;
				distances.put(id, dist);
			} catch (Exception e) {
				Distantnoise.LOGGER.warn("Ignoring invalid stomping-entities entry: {}", raw);
			}
		}
		if (distances.isEmpty()) {
			for (Map.Entry<String, Double> entry : defaultStompingEntities().entrySet()) {
				distances.put(Identifier.parse(entry.getKey()), entry.getValue());
			}
		}
		stompingEntityDistances = Map.copyOf(distances);
	}

	private static float clamp01(float v) {
		return Math.max(0.01f, Math.min(1.0f, v));
	}
}
