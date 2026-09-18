package com.weaponsmodaddon.damage;

import ckathode.weaponmod.entity.projectile.EntityBlunderShot;
import ckathode.weaponmod.entity.projectile.EntityMortarShell;
import ckathode.weaponmod.entity.projectile.EntityMusketBullet;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.jetbrains.annotations.Nullable;

public final class ProjectileDamage {
	private ProjectileDamage() {
	}

	public static boolean isGunOrArrow(@Nullable DamageSource source) {
		if (source == null) {
			return false;
		}
		if (source.is(ckathode.weaponmod.WMDamageSources.WEAPON)) {
			Entity attacker = source.getEntity();
			if (attacker instanceof net.minecraft.world.entity.player.Player) {
				return true;
			}
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof EntityMusketBullet
				|| direct instanceof EntityBlunderShot
				|| direct instanceof EntityMortarShell) {
			return true;
		}
		return direct instanceof AbstractArrow && !(direct instanceof ThrownTrident);
	}
}
