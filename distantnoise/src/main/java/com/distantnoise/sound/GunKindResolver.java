package com.distantnoise.sound;

import ckathode.weaponmod.item.IItemWeapon;
import ckathode.weaponmod.item.RangedCompBlunderbuss;
import ckathode.weaponmod.item.RangedCompFlintlock;
import ckathode.weaponmod.item.RangedCompMortar;
import ckathode.weaponmod.item.RangedCompMusket;
import ckathode.weaponmod.item.RangedComponent;
import net.minecraft.world.item.ItemStack;

/** Maps WeaponMod ranged components / held stacks to distant relay kinds. */
public final class GunKindResolver {
	private GunKindResolver() {
	}

	public static DistantNoiseKind from(RangedComponent component) {
		if (component instanceof RangedCompFlintlock) {
			return DistantNoiseKind.GUNSHOT;
		}
		if (component instanceof RangedCompMusket
				|| component instanceof RangedCompBlunderbuss
				|| component instanceof RangedCompMortar) {
			return DistantNoiseKind.MUSKET;
		}
		return null;
	}

	public static DistantNoiseKind fromStack(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof IItemWeapon weapon)) {
			return null;
		}
		RangedComponent ranged = weapon.getRangedComponent();
		return ranged == null ? null : from(ranged);
	}
}
