package com.weaponsmodaddon.headshot;

import com.weaponsmodaddon.config.AddonConfig;
import com.weaponsmodaddon.config.EntityTagMatcher;
import java.util.UUID;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Player head hits (top AABB band) and mob front hits (forward AABB band).
 * Gun hits without head protection are lethal where configured.
 */
public final class HeadshotContext {
	public enum Kind {
		GUN,
		ARROW,
		SPEAR
	}

	private enum HitType {
		PLAYER_HEAD,
		ANIMAL_FRONT,
		ANIMAL_HEAD,
		ONE_SHOT
	}

	private record Pending(UUID targetId, Kind kind, HitType hitType, boolean hasHelmet) {
	}

	private static final ThreadLocal<@Nullable Pending> PENDING = new ThreadLocal<>();

	private HeadshotContext() {
	}

	public static void clear() {
		PENDING.remove();
	}

	public static void prepare(@Nullable EntityHitResult hit, Kind kind) {
		clear();
		if (hit == null) {
			return;
		}
		Entity entity = hit.getEntity();
		if (!(entity instanceof LivingEntity living) || living.level().isClientSide()) {
			return;
		}

		AddonConfig cfg = AddonConfig.get();
		Vec3 hitPos = hit.getLocation();

		if (living instanceof Player player) {
			if (!cfg.playerHeadshot) {
				return;
			}
			if (!isTopBandHit(player, hitPos, cfg.headshot.topHitboxFraction)) {
				return;
			}
			PENDING.set(new Pending(player.getUUID(), kind, HitType.PLAYER_HEAD, hasHelmet(player)));
			return;
		}

		if (kind == Kind.GUN && cfg.oneShotAnimals && cfg.oneShotAnimalsEnabled.matches(living, false)) {
			PENDING.set(new Pending(living.getUUID(), kind, HitType.ONE_SHOT, false));
			return;
		}

		if (kind == Kind.SPEAR) {
			if (cfg.animalHeadshotFront
					&& cfg.animalHeadshotFrontEnabled.matches(living, true)
					&& isTopBandHit(living, hitPos, cfg.headshot.topHitboxFraction)) {
				PENDING.set(new Pending(living.getUUID(), kind, HitType.ANIMAL_HEAD, false));
			}
			return;
		}

		if (cfg.animalHeadshotFront
				&& cfg.animalHeadshotFrontEnabled.matches(living, true)
				&& isFrontBandHit(living, hitPos, cfg.animalHeadshotFrontFraction)) {
			PENDING.set(new Pending(living.getUUID(), kind, HitType.ANIMAL_FRONT, false));
		}
	}

	public static boolean isPendingFor(LivingEntity entity) {
		Pending pending = PENDING.get();
		return pending != null && pending.targetId.equals(entity.getUUID());
	}

	public static float applyAndModifyDamage(LivingEntity entity, float damage) {
		Pending pending = PENDING.get();
		if (pending == null || !pending.targetId.equals(entity.getUUID())) {
			return damage;
		}

		AddonConfig cfg = AddonConfig.get();
		AddonConfig.HeadshotTuning tuning = cfg.headshot;
		int blindnessTicks = Math.max(1, (int) Math.round(tuning.blindnessSeconds * 20.0));

		return switch (pending.hitType) {
			case ONE_SHOT -> Math.max(damage, 10000.0f);
			case PLAYER_HEAD -> {
				if (pending.kind == Kind.SPEAR) {
					if (!pending.hasHelmet && tuning.gunNoHelmetLethal) {
						yield Math.max(damage, 10000.0f);
					}
					yield damage;
				}
				entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindnessTicks, 0));
				if (pending.kind == Kind.GUN && !pending.hasHelmet && tuning.gunNoHelmetLethal) {
					yield Math.max(damage, 10000.0f);
				}
				yield damage * (float) tuning.damageMultiplier;
			}
			case ANIMAL_HEAD -> Math.max(damage, 10000.0f);
			case ANIMAL_FRONT -> {
				entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindnessTicks, 0));
				if (pending.kind == Kind.GUN) {
					yield Math.max(damage, 10000.0f);
				}
				yield damage * (float) tuning.damageMultiplier;
			}
		};
	}

	/** Top fraction of the axis-aligned hitbox (players). */
	public static boolean isTopBandHit(Entity entity, Vec3 hitPos, double topFraction) {
		AABB box = entity.getBoundingBox();
		double height = box.getYsize();
		if (height <= 1.0e-4) {
			return false;
		}
		double fraction = Math.min(1.0, Math.max(0.05, topFraction));
		double headMinY = box.maxY - height * fraction;
		return hitPos.y >= headMinY;
	}

	/** Front fraction along the mob's horizontal facing (animals). */
	public static boolean isFrontBandHit(LivingEntity entity, Vec3 hitPos, double frontFraction) {
		AABB box = entity.getBoundingBox();
		Vec3 center = box.getCenter();
		Vec3 forward = entity.getViewVector(1.0F);
		forward = new Vec3(forward.x, 0.0, forward.z);
		if (forward.lengthSqr() < 1.0e-6) {
			forward = Vec3.directionFromRotation(0.0F, entity.getYRot());
		} else {
			forward = forward.normalize();
		}

		double minForward = Double.POSITIVE_INFINITY;
		double maxForward = Double.NEGATIVE_INFINITY;
		double minX = box.minX;
		double minY = box.minY;
		double minZ = box.minZ;
		double maxX = box.maxX;
		double maxY = box.maxY;
		double maxZ = box.maxZ;
		for (double x : new double[] {minX, maxX}) {
			for (double y : new double[] {minY, maxY}) {
				for (double z : new double[] {minZ, maxZ}) {
					double projection = new Vec3(x, y, z).subtract(center).dot(forward);
					minForward = Math.min(minForward, projection);
					maxForward = Math.max(maxForward, projection);
				}
			}
		}

		double depth = maxForward - minForward;
		if (depth <= 1.0e-4) {
			return false;
		}
		double fraction = Math.min(1.0, Math.max(0.05, frontFraction));
		double hitForward = hitPos.subtract(center).dot(forward);
		return hitForward >= maxForward - depth * fraction;
	}

	public static boolean hasHelmet(Player player) {
		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
		return !head.isEmpty() && head.is(ItemTags.HEAD_ARMOR);
	}
}
