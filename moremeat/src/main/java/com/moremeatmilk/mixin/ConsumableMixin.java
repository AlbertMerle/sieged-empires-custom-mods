package com.moremeatmilk.mixin;

import com.moremeatmilk.food.RawMeatEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Consumable.class)
public class ConsumableMixin {

	@Inject(method = "onConsume", at = @At("RETURN"))
	private void moremeatmilk$afterConsume(
		Level level,
		LivingEntity entity,
		ItemStack stack,
		CallbackInfoReturnable<ItemStack> cir
	) {
		RawMeatEffects.onConsumed(level, entity, stack);
	}

}
