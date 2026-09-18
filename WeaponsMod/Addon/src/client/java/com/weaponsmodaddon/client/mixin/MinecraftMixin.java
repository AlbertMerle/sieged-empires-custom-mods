package com.weaponsmodaddon.client.mixin;

import com.weaponsmodaddon.client.gun.GunAimClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After RMB cancels sticky ADS, keep holding use from immediately re-entering aim.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void weaponsmodaddon$suppressUseAfterAimCancel(CallbackInfo ci) {
		if (GunAimClient.shouldSuppressUseItem()) {
			ci.cancel();
		}
	}
}
