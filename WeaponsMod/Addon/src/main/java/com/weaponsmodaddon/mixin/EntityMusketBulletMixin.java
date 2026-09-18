package com.weaponsmodaddon.mixin;

import ckathode.weaponmod.entity.projectile.EntityMusketBullet;
import com.weaponsmodaddon.config.AddonConfig;
import com.weaponsmodaddon.gun.PreciseGunAim;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sticky-ADS fire path sets {@link PreciseGunAim} so musket/flintlock cone spread is zero —
 * shot direction matches the aim point after SS look-follow / scoped sway bake-in.
 * <p>
 * Damage uses {@code config/weaponsmodaddon.json} multipliers on the stock 20 base.
 * Flintlock's hardcoded {@code extraDamage - 10} is undone so the config multiplier owns the ratio.
 */
@Mixin(EntityMusketBullet.class)
public class EntityMusketBulletMixin {

	private static final float BASE_BULLET_DAMAGE = 20.0f;
	/** WeaponMod flintlock subtracts this from {@code extraDamage}; we reverse it. */
	private static final float FLINTLOCK_STOCK_PENALTY = 10.0f;

	@ModifyVariable(method = "shootFromRotation", at = @At("HEAD"), argsOnly = true, ordinal = 4)
	private float weaponsmodaddon$preciseAdsSpread(float uncertainty) {
		return PreciseGunAim.isActive() ? 0.0F : uncertainty;
	}

	@Inject(method = "getDamage", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$configDamage(Entity entity, CallbackInfoReturnable<Float> cir) {
		EntityMusketBullet self = (EntityMusketBullet) (Object) this;
		ItemStack weapon = self.getWeaponItem();
		AddonConfig config = AddonConfig.get();
		float mult = config.damageMultiplierFor(weapon);
		float extra = self.extraDamage;
		if (config.isFlintlock(weapon)) {
			extra += FLINTLOCK_STOCK_PENALTY;
		}
		cir.setReturnValue(BASE_BULLET_DAMAGE * mult + extra);
	}
}
