package com.siegedempires.mixin;

import com.siegedempires.util.PlayerPrefixHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void siegedempires$tabListPrefix(CallbackInfoReturnable<Component> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		Component prefix = PlayerPrefixHelper.getPrefixComponent(self);
		if (prefix == null) {
			return;
		}
		cir.setReturnValue(Component.empty().append(prefix).append(self.getName()));
	}
}
