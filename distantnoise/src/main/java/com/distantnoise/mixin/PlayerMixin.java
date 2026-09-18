package com.distantnoise.mixin;

import com.distantnoise.config.DistantNoiseConfig;
import com.distantnoise.sound.DeathSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "getDeathSound", at = @At("RETURN"), cancellable = true)
	private void distantnoise$customDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
		if (DeathSounds.enabled(DistantNoiseConfig.get())) {
			cir.setReturnValue(DeathSounds.deathEvent());
		}
	}
}
