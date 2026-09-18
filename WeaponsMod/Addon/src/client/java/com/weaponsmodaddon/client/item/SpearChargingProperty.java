package com.weaponsmodaddon.client.item;

import com.mojang.serialization.MapCodec;
import com.weaponsmodaddon.client.anim.SpearAnimController;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * True while charging a spear or during aim/aim-hold/throw clips — selects the +90° tip model.
 */
public record SpearChargingProperty() implements ConditionalItemModelProperty {
	public static final MapCodec<SpearChargingProperty> MAP_CODEC = MapCodec.unit(new SpearChargingProperty());

	@Override
	public boolean get(
			ItemStack itemStack,
			@Nullable ClientLevel level,
			@Nullable LivingEntity owner,
			int seed,
			ItemDisplayContext displayContext
	) {
		if (owner == null) {
			return false;
		}
		if (owner.isUsingItem() && owner.getUseItem() == itemStack) {
			return true;
		}
		return SpearAnimController.shouldFlipItem(owner.getId());
	}

	@Override
	public MapCodec<SpearChargingProperty> type() {
		return MAP_CODEC;
	}
}
