package com.weaponsmodaddon.client.mixin;

import com.weaponsmodaddon.client.scope.ScopedMusketAimClient;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse vanilla spyglass vignette while aiming a scoped musket. */
@Mixin(Player.class)
public class PlayerIsScopingMixin {

	@Inject(method = "isScoping", at = @At("RETURN"), cancellable = true)
	private void weaponsmodaddon$scopedMusketScoping(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		Player self = (Player) (Object) this;
		if (ScopedMusketAimClient.isAimingScoped(self)) {
			cir.setReturnValue(true);
		}
	}
}
