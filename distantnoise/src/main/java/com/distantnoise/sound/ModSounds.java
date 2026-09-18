package com.distantnoise.sound;

import com.distantnoise.Distantnoise;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Fixed-range muffled sound tiers (low-pass baked assets). Tier rises with distance.
 */
public final class ModSounds {
	/** Fixed range so quiet far volumes still reach explosion relay max range. */
	public static final float RANGE = 1200.0f;
	/** Extended footstep relay hear distance. */
	public static final float FOOTSTEP_RANGE = 48.0f;
	public static final int TIER_COUNT = 4;

	public static final Holder.Reference<SoundEvent> EXPLOSION = register("explosion");

	public static final Holder.Reference<SoundEvent> EXPLODE_T0 = register("muffled_explode_t0");
	public static final Holder.Reference<SoundEvent> EXPLODE_T1 = register("muffled_explode_t1");
	public static final Holder.Reference<SoundEvent> EXPLODE_T2 = register("muffled_explode_t2");
	public static final Holder.Reference<SoundEvent> EXPLODE_T3 = register("muffled_explode_t3");

	public static final Holder.Reference<SoundEvent> THUNDER_T0 = register("muffled_thunder_t0");
	public static final Holder.Reference<SoundEvent> THUNDER_T1 = register("muffled_thunder_t1");
	public static final Holder.Reference<SoundEvent> THUNDER_T2 = register("muffled_thunder_t2");
	public static final Holder.Reference<SoundEvent> THUNDER_T3 = register("muffled_thunder_t3");

	public static final Holder.Reference<SoundEvent> BEAR_STEP = registerFootstep("bear_step");
	public static final Holder.Reference<SoundEvent> GRASS_WALK = registerFootstep("grass_walk");
	public static final Holder.Reference<SoundEvent> GRASS_RUSSLING = registerGrassRustle("grass_russling");

	public static final Holder.Reference<SoundEvent> HORSE_RUN = registerFootstep("horse_run");
	public static final Holder.Reference<SoundEvent> HORSE_ARMOR_JINGLE = registerFootstep("horse_armor_jingle");
	public static final Holder.Reference<SoundEvent> HORSE_NEIGH = registerFootstep("horse_neigh");
	public static final Holder.Reference<SoundEvent> HORSE_SNORT = registerFootstep("horse_snort");
	public static final Holder.Reference<SoundEvent> DEATH = registerFootstep("death");

	@SuppressWarnings("unchecked")
	private static final Holder.Reference<SoundEvent>[] EXPLODE = new Holder.Reference[] {
			EXPLODE_T0, EXPLODE_T1, EXPLODE_T2, EXPLODE_T3
	};
	@SuppressWarnings("unchecked")
	private static final Holder.Reference<SoundEvent>[] THUNDER = new Holder.Reference[] {
			THUNDER_T0, THUNDER_T1, THUNDER_T2, THUNDER_T3
	};

	private ModSounds() {
	}

	public static void initialize() {
	}

	public static Holder<SoundEvent> explodeTier(int tier) {
		return EXPLODE[clampTier(tier)];
	}

	public static Holder<SoundEvent> thunderTier(int tier) {
		return THUNDER[clampTier(tier)];
	}

	/**
	 * Maps distance from {@code near} to {@code far} (blocks) to muffle tier 0..3.
	 * Tier 0 = clearest (just outside near cutoff); tier 3 = most muffled at far rim.
	 */
	public static int tierForDistance(double dist, double near, double far) {
		if (far <= near) {
			return TIER_COUNT - 1;
		}
		double t = (dist - near) / (far - near);
		t = Math.max(0.0, Math.min(1.0, t));
		int tier = (int) Math.floor(t * TIER_COUNT);
		return clampTier(tier);
	}

	private static int clampTier(int tier) {
		if (tier < 0) {
			return 0;
		}
		if (tier >= TIER_COUNT) {
			return TIER_COUNT - 1;
		}
		return tier;
	}

	private static Holder.Reference<SoundEvent> register(String path) {
		Identifier id = Distantnoise.id(path);
		return Registry.registerForHolder(
				BuiltInRegistries.SOUND_EVENT,
				id,
				SoundEvent.createFixedRangeEvent(id, RANGE)
		);
	}

	private static Holder.Reference<SoundEvent> registerFootstep(String path) {
		Identifier id = Distantnoise.id(path);
		return Registry.registerForHolder(
				BuiltInRegistries.SOUND_EVENT,
				id,
				SoundEvent.createFixedRangeEvent(id, FOOTSTEP_RANGE)
		);
	}

	private static Holder.Reference<SoundEvent> registerGrassRustle(String path) {
		Identifier id = Distantnoise.id(path);
		return Registry.registerForHolder(
				BuiltInRegistries.SOUND_EVENT,
				id,
				SoundEvent.createVariableRangeEvent(id)
		);
	}
}
