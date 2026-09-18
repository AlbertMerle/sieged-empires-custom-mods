package com.distantnoise.sound;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

import java.util.Set;

/** Vanilla horse sound ids and armor checks for custom run / vocal layers. */
public final class HorseSounds {
	private static final Identifier HORSE_RUN = Identifier.fromNamespaceAndPath("distantnoise", "horse_run");
	private static final Identifier HORSE_ARMOR_JINGLE = Identifier.fromNamespaceAndPath("distantnoise", "horse_armor_jingle");
	private static final Identifier HORSE_NEIGH = Identifier.fromNamespaceAndPath("distantnoise", "horse_neigh");
	private static final Identifier HORSE_SNORT = Identifier.fromNamespaceAndPath("distantnoise", "horse_snort");

	private static final Set<Identifier> RUN_SOUNDS = Set.of(
			id("entity.horse.gallop"),
			id("entity.horse.step"),
			id("entity.horse.step_wood"),
			id("entity.horse.step_baby"),
			id("entity.skeleton_horse.gallop_water"),
			id("entity.skeleton_horse.step_water"),
			id("entity.horse.armor")
	);

	private static final Set<Identifier> NEIGH_SOUNDS = Set.of(
			id("entity.horse.ambient"),
			id("entity.horse.ambient_baby"),
			id("entity.horse.angry"),
			id("entity.horse.angry_baby"),
			id("entity.donkey.ambient"),
			id("entity.donkey.angry"),
			id("entity.mule.ambient"),
			id("entity.mule.angry"),
			id("entity.skeleton_horse.ambient"),
			id("entity.skeleton_horse.ambient_water"),
			id("entity.zombie_horse.ambient"),
			id("entity.zombie_horse.angry")
	);

	private static final Set<Identifier> SNORT_SOUNDS = Set.of(
			id("entity.horse.breathe"),
			id("entity.horse.breathe_baby")
	);

	private HorseSounds() {
	}

	public static boolean isHorse(LivingEntity entity) {
		return entity instanceof AbstractHorse;
	}

	public static boolean isRunSound(SoundEvent event) {
		return event != null && RUN_SOUNDS.contains(event.location());
	}

	public static boolean isNeighSound(SoundEvent event) {
		return event != null && NEIGH_SOUNDS.contains(event.location());
	}

	public static boolean isSnortSound(SoundEvent event) {
		return event != null && SNORT_SOUNDS.contains(event.location());
	}

	public static boolean hasBodyArmor(LivingEntity entity) {
		return !entity.getItemBySlot(EquipmentSlot.BODY).isEmpty();
	}

	public static Identifier runSoundId() {
		return HORSE_RUN;
	}

	public static Identifier armorJingleSoundId() {
		return HORSE_ARMOR_JINGLE;
	}

	public static Identifier neighSoundId() {
		return HORSE_NEIGH;
	}

	public static Identifier snortSoundId() {
		return HORSE_SNORT;
	}

	private static Identifier id(String path) {
		return Identifier.parse("minecraft:" + path);
	}
}
