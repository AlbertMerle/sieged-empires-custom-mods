package com.siegedempires.boat;

import com.mojang.serialization.Codec;
import com.siegedempires.Siegedempires;
import com.siegedempires.config.ModSettings;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent HP for vanilla boats/rafts so they can be destroyed instead of
 * relying on Minecraft's regenerating damage meter (which rarely reaches break).
 * Also applies Shippy Ships–style thunderstorm damage in deep ocean biomes.
 * Shippy Ships vessels keep their own health system and are skipped.
 */
public final class VanillaBoatHealth {
	public static final AttachmentType<Float> HEALTH = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "vanilla_boat_health"),
			Codec.FLOAT);

	public static final TagKey<Biome> DEEP_WATERS = TagKey.create(
			Registries.BIOME,
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "deep_waters"));

	/** Matches Shippy Ships: first interval ~10s, then 5–20s between storm hits. */
	private static final int INITIAL_STORM_TIMER = 200;
	private static final Map<UUID, Integer> STORM_TIMERS = new ConcurrentHashMap<>();

	private VanillaBoatHealth() {
	}

	public static boolean isVanillaBoat(Entity entity) {
		return entity instanceof AbstractBoat && !isShippyShip(entity);
	}

	public static boolean isShippyShip(Entity entity) {
		return entity.getClass().getName().contains("shippyships");
	}

	public static float maxHealth() {
		return ModSettings.get().vanillaBoatHealth;
	}

	public static float getHealth(AbstractBoat boat) {
		Float stored = boat.getAttached(HEALTH);
		if (stored == null) {
			float max = maxHealth();
			boat.setAttached(HEALTH, max);
			return max;
		}
		return stored;
	}

	public static void setHealth(AbstractBoat boat, float health) {
		boat.setAttached(HEALTH, Math.max(0.0F, health));
	}

	/** Applies damage; returns remaining HP after the hit. */
	public static float damage(AbstractBoat boat, float amount) {
		float hp = getHealth(boat) - amount;
		setHealth(boat, hp);
		boat.setDamage(0.0F);
		return hp;
	}

	public static void clearStormTimer(UUID boatId) {
		if (boatId != null) {
			STORM_TIMERS.remove(boatId);
		}
	}

	/**
	 * Thunderstorm wear in deep water — same rules as Shippy Ships
	 * ({@code isThundering}, in water, {@code #siegedempires:deep_waters}, 1–4 HP per pulse).
	 */
	public static void tickStormDamage(AbstractBoat boat) {
		if (!isVanillaBoat(boat) || boat.level().isClientSide()) {
			return;
		}
		if (!(boat.level() instanceof ServerLevel level) || !level.isThundering()) {
			return;
		}

		UUID id = boat.getUUID();
		int timer = STORM_TIMERS.getOrDefault(id, INITIAL_STORM_TIMER) - 1;
		if (timer > 0) {
			STORM_TIMERS.put(id, timer);
			return;
		}
		STORM_TIMERS.put(id, 100 + boat.getRandom().nextInt(301));

		boolean inDeepWater = level.getBiome(boat.blockPosition()).is(DEEP_WATERS);
		if (!boat.isInWater() || !inDeepWater) {
			return;
		}

		float amount = 1.0F + boat.getRandom().nextFloat() * 3.0F;
		boat.hurtServer(level, boat.damageSources().generic(), amount);
		level.playSound(
				null,
				boat.getX(),
				boat.getY(),
				boat.getZ(),
				SoundEvents.WOOD_HIT,
				boat.getSoundSource(),
				1.0F,
				0.4F + 0.2F * boat.getRandom().nextFloat());
	}
}
