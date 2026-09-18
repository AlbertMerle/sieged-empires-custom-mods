package com.siegedempires.compat.fzmm.mixin;

import com.siegedempires.compat.fzmm.FzmmBannerSession;
import fzmm.zailer.me.builders.BannerBuilder;
import fzmm.zailer.me.client.gui.BaseFzmmScreen;
import fzmm.zailer.me.client.gui.banner_editor.BannerEditorScreen;
import fzmm.zailer.me.client.gui.components.extend.component.EBooleanButton;
import fzmm.zailer.me.client.gui.components.extend.container.EFlowLayout;
import fzmm.zailer.me.utils.history.IClipboardState;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When opened from SE Create Town/Empire: seed the current design, turn
 * Item:Banner/Shield into sticky {@code Finished!} (saves into SE cache),
 * remove Give, and cancel on Back/Escape.
 *
 * <p>FZMM's {@code updatePreview} calls {@code isShieldButton.enabledIgnoreCallback},
 * which rewrites the button label from {@code enabledText}/{@code disabledText}.
 * Both strings are forced to Finished! so clicking patterns / Select banner
 * cannot restore the shield toggle labels.
 *
 * <p>{@code onClose()} lives on {@link BaseFzmmScreen} — do not {@code @Shadow} it here.
 */
@Mixin(BannerEditorScreen.class)
public abstract class BannerEditorScreenMixin {
	@Shadow
	private BannerBuilder bannerBuilder;

	@Shadow
	private EBooleanButton isShieldButton;

	@Shadow
	public abstract void updatePreview(IClipboardState state);

	@Inject(method = "setup", at = @At("RETURN"))
	private void siegedempires$hookSeSession(EFlowLayout root, CallbackInfo ci) {
		if (!FzmmBannerSession.isActive()) {
			return;
		}

		ItemStack initial = FzmmBannerSession.initialBanner();
		BannerBuilder seeded = initial.isEmpty()
				? BannerBuilder.of(new ItemStack(net.minecraft.world.item.Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE)))
				: BannerBuilder.of(initial);
		seeded.isShield(false);
		this.updatePreview(seeded);

		applyFinishedChrome();
		removeGiveButton(root);
	}

	/**
	 * Keep designs as banners even if Select banner loads a shield template.
	 */
	@Inject(method = "updatePreview(Lfzmm/zailer/me/utils/history/IClipboardState;)V", at = @At("HEAD"))
	private void siegedempires$forceBannerMode(IClipboardState state, CallbackInfo ci) {
		if (!FzmmBannerSession.isActive()) {
			return;
		}
		if (state instanceof BannerBuilder builder) {
			builder.isShield(false);
		}
		if (this.bannerBuilder != null) {
			this.bannerBuilder.isShield(false);
		}
	}

	/**
	 * Re-assert Finished! after FZMM syncs the boolean button from builder state.
	 */
	@Inject(method = "updatePreview(Lfzmm/zailer/me/utils/history/IClipboardState;)V", at = @At("RETURN"))
	private void siegedempires$keepFinishedAfterPreview(IClipboardState state, CallbackInfo ci) {
		if (!FzmmBannerSession.isActive()) {
			return;
		}
		applyFinishedChrome();
	}

	@Inject(method = "isShieldButtonExecute", at = @At("HEAD"), cancellable = true)
	private void siegedempires$blockShieldToggle(boolean enabled, CallbackInfo ci) {
		if (!FzmmBannerSession.isActive()) {
			return;
		}
		// Old FZMM press handler — treat as Finished! instead of toggling shield.
		ci.cancel();
		saveAndReturn();
	}

	/**
	 * Rewrite Banner/Shield labels to Finished! and point the press handler at save.
	 * Safe to call repeatedly (after every preview update / screen re-init).
	 */
	private void applyFinishedChrome() {
		if (this.isShieldButton == null) {
			return;
		}
		Component finished = Component.translatable("gui.siegedempires.finished_banner");
		EBooleanButtonAccessor access = (EBooleanButtonAccessor) (Object) this.isShieldButton;
		access.siegedempires$setEnabledText(finished);
		access.siegedempires$setDisabledText(finished);
		this.isShieldButton.enabledIgnoreCallback(false);
		this.isShieldButton.onPress(button -> saveAndReturn());
	}

	private void removeGiveButton(EFlowLayout root) {
		@SuppressWarnings({"rawtypes", "unchecked"})
		ButtonComponent give = (ButtonComponent) root.childByIdOrThrow((Class) ButtonComponent.class, "give-button");
		UIComponent giveUi = asUi(give);
		ParentUIComponent parent = giveUi.parent();
		if (parent instanceof FlowLayout flow) {
			flow.removeChild(giveUi);
			return;
		}
		give.active(false);
		give.setMessage(Component.empty());
	}

	private void saveAndReturn() {
		if (this.bannerBuilder != null) {
			this.bannerBuilder.isShield(false);
			ItemStack result = this.bannerBuilder.get();
			FzmmBannerSession.finish(result);
		} else {
			FzmmBannerSession.cancel();
		}
		((BaseFzmmScreen) (Object) this).onClose();
	}

	/** owo injects {@link UIComponent} onto widgets at runtime; not visible at compile time. */
	private static UIComponent asUi(Object widget) {
		return (UIComponent) widget;
	}
}
