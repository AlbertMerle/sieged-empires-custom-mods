package com.siegedempires.compat.fzmm.mixin;

import com.siegedempires.compat.fzmm.FzmmBannerSession;
import fzmm.zailer.me.client.gui.BaseFzmmScreen;
import fzmm.zailer.me.client.gui.banner_editor.BannerEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels an SE create-menu FZMM session on Back/Escape.
 * {@code onClose()} is declared on {@link BaseFzmmScreen}, not {@link BannerEditorScreen}.
 */
@Mixin(BaseFzmmScreen.class)
public abstract class BaseFzmmScreenMixin {
	@Inject(method = "onClose", at = @At("HEAD"))
	private void siegedempires$cancelSeBannerSession(CallbackInfo ci) {
		if (!((Object) this instanceof BannerEditorScreen)) {
			return;
		}
		// finish() already cleared the session; Back/Escape leaves it active → cancel.
		if (FzmmBannerSession.isActive()) {
			FzmmBannerSession.cancel();
		}
	}
}
