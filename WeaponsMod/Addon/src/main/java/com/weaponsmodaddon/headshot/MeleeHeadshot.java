package com.weaponsmodaddon.headshot;

import ckathode.weaponmod.WMDamageSources;
import ckathode.weaponmod.entity.projectile.EntityProjectile;
import com.weaponsmodaddon.config.AddonConfig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Melee head hits on players: 2× damage when the victim has no helmet and the attacker's
 * look ray intersects the top hitbox band. Helmeted victims keep base damage.
 */
public final class MeleeHeadshot {
	private MeleeHeadshot() {
	}

	public static float apply(float damage, LivingEntity victim, DamageSource source) {
		if (!(victim instanceof Player player) || victim.level().isClientSide()) {
			return damage;
		}
		AddonConfig cfg = AddonConfig.get();
		if (!cfg.playerHeadshot || HeadshotContext.hasHelmet(player) || !isMeleeDamage(source)) {
			return damage;
		}
		Entity attackerEntity = source.getEntity();
		if (!(attackerEntity instanceof LivingEntity attacker)) {
			return damage;
		}
		if (!isMeleeHeadHit(attacker, victim, cfg.headshot.topHitboxFraction)) {
			return damage;
		}
		return damage * (float) cfg.headshot.damageMultiplier;
	}

	static boolean isMeleeDamage(DamageSource source) {
		if (source.is(DamageTypes.PLAYER_ATTACK)
				|| source.is(DamageTypes.MOB_ATTACK)
				|| source.is(WMDamageSources.BATTLEAXE)) {
			return true;
		}
		if (source.is(WMDamageSources.WEAPON)) {
			return false;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof AbstractArrow || direct instanceof ThrownTrident || direct instanceof EntityProjectile) {
			return false;
		}
		Entity attacker = source.getEntity();
		return attacker instanceof LivingEntity && (direct == null || direct == attacker);
	}

	static boolean isMeleeHeadHit(LivingEntity attacker, LivingEntity victim, double topFraction) {
		Vec3 eye = attacker.getEyePosition(1.0F);
		double reach = attacker.distanceTo(victim) + 2.0;
		Vec3 end = eye.add(attacker.getViewVector(1.0F).scale(reach));
		AABB box = victim.getBoundingBox();
		return box.clip(eye, end)
				.filter(pos -> HeadshotContext.isTopBandHit(victim, pos, topFraction))
				.isPresent();
	}
}
