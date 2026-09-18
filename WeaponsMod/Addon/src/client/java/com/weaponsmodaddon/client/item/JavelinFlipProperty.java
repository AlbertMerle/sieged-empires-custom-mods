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
 * True while charging a javelin or during aim/aim-hold/throw clips — selects the 180°-flipped model.
 */
public record JavelinFlipProperty() implements ConditionalItemModelProperty {
	public static final MapCodec<JavelinFlipProperty> MAP_CODEC = MapCodec.unit(new JavelinFlipProperty());

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
	public MapCodec<JavelinFlipProperty> type() {
		return MAP_CODEC;
	}
}
