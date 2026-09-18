package com.siegedempires.mixin;

import com.siegedempires.permission.ExplosionProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Safety net: primed TNT that somehow exists in a protected town fizzles instead of detonating.
 */
@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin {

	@Inject(method = "explode", at = @At("HEAD"), cancellable = true)
	private void siegedempires$suppressPeacefulTownDetonation(CallbackInfo ci) {
		PrimedTnt self = (PrimedTnt) (Object) this;
		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (ExplosionProtection.isExplosionSuppressedAt(serverLevel, self.position())) {
			self.discard();
			ci.cancel();
		}
	}
}
