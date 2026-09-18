package com.siegedempires.mixin;

import com.siegedempires.util.PlayerPrefixHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void siegedempires$prependRankPrefix(CallbackInfoReturnable<Component> cir) {
		if (!((Object) this instanceof ServerPlayer self)) {
			return;
		}
		Component prefix = PlayerPrefixHelper.getPrefixComponent(self);
		if (prefix == null) {
			return;
		}
		cir.setReturnValue(Component.empty().append(prefix).append(cir.getReturnValue()));
	}
}
