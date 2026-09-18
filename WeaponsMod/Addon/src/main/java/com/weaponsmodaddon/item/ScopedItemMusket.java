package com.weaponsmodaddon.item;

import ckathode.weaponmod.PhysHelper;
import ckathode.weaponmod.ReloadHelper;
import ckathode.weaponmod.item.ItemMusket;
import ckathode.weaponmod.item.MeleeComponent;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ItemMusket clone that falls back to a scoped plain musket when the bayonet breaks.
 */
public class ScopedItemMusket extends ItemMusket {

	@Nullable
	private final Item plainScopedMusket;

	public ScopedItemMusket(
			MeleeComponent meleeComponent,
			@Nullable Item bayonetItem,
			@NotNull Identifier id,
			@Nullable Item plainScopedMusket
	) {
		super(meleeComponent, bayonetItem, id);
		this.plainScopedMusket = plainScopedMusket;
	}

	@Override
	public void bayonetDamage(ItemStack itemstack, LivingEntity entityliving, int damage) {
		if (plainScopedMusket == null) {
			super.bayonetDamage(itemstack, entityliving, damage);
			return;
		}

		if (!itemstack.has(BAYONET_DAMAGE_TYPE)) {
			itemstack.set(BAYONET_DAMAGE_TYPE, (short) 0);
		}
		int bayonetdamage = Objects.requireNonNull(itemstack.get(BAYONET_DAMAGE_TYPE)) + damage;
		int bayonetDurability = getBayonetDurability();
		if (bayonetdamage > bayonetDurability) {
			entityliving.onEquippedItemBroken(this, EquipmentSlot.MAINHAND);
			if (entityliving instanceof Player player) {
				player.awardStat(Stats.ITEM_BROKEN.get(this));
			}
			bayonetdamage = 0;
			ItemStack itemstack2 = new ItemStack(plainScopedMusket, 1);
			itemstack2.setDamageValue(itemstack.getDamageValue());
			entityliving.setItemSlot(EquipmentSlot.MAINHAND, itemstack2);
			if (itemstack.has(ReloadHelper.ReloadState.TYPE)) {
				ReloadHelper.setReloadState(itemstack2, ReloadHelper.getReloadState(itemstack));
			}
		}
		itemstack.set(BAYONET_DAMAGE_TYPE, (short) bayonetdamage);
	}

	private int getBayonetDurability() {
		if (meleeComponent.meleeSpecs != MeleeComponent.MeleeSpecs.NONE && meleeComponent.weaponMaterial != null) {
			return meleeComponent.meleeSpecs.durabilityBase
					+ (int) (meleeComponent.weaponMaterial.durability() * meleeComponent.meleeSpecs.durabilityMult);
		}
		return 0;
	}

	@Override
	public void hurtEnemy(@NotNull ItemStack itemstack, @NotNull LivingEntity entityliving,
			@NotNull LivingEntity attacker) {
		if (hasBayonet()) {
			if (entityliving.invulnerableTime == 20) {
				float kb = meleeComponent.getKnockBack(itemstack, entityliving, attacker);
				PhysHelper.knockBack(entityliving, attacker, kb);
				entityliving.invulnerableTime -= (int) (2.0f / meleeComponent.meleeSpecs.attackDelay);
			}
			if (attacker instanceof Player player && !player.isCreative()) {
				bayonetDamage(itemstack, attacker, 1);
			}
		}
	}
}
