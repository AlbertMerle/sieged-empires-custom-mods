package com.weaponsmodaddon.gun;

import com.weaponsmodaddon.config.AddonConfig;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Config-driven musket / flintlock bullet damage (mirrors {@code EntityMusketBulletMixin}). */
public final class GunDamage {
	private static final float BASE_BULLET_DAMAGE = 20.0f;
	private static final float FLINTLOCK_STOCK_PENALTY = 10.0f;

	private GunDamage() {
	}

	public static float compute(@Nullable ItemStack weapon) {
		AddonConfig config = AddonConfig.get();
		float mult = config.damageMultiplierFor(weapon);
		float extra = 0.0f;
		if (config.isFlintlock(weapon)) {
			extra -= FLINTLOCK_STOCK_PENALTY;
		}
		return BASE_BULLET_DAMAGE * mult + extra;
	}
}
