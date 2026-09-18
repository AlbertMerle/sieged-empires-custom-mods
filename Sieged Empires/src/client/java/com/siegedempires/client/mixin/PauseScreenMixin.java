package com.siegedempires.client.mixin;

import com.siegedempires.client.title.SiegedLogoLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brands the in-game Escape / pause menu: small Sieged Empires logo at the top,
 * and a larger bright-red "Leave Game" button in place of Disconnect.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
	@Unique
	private static final int PAUSE_LOGO_PREFERRED_WIDTH = 64;
	@Unique
	private static final int PAUSE_LOGO_MAX_HEIGHT = 36;
	@Unique
	private static final int PAUSE_LOGO_TOP = 6;
	@Unique
	private static final int LEAVE_BUTTON_WIDTH = 220;
	@Unique
	private static final int LEAVE_BUTTON_HEIGHT = 24;
	@Unique
	private static final int LEAVE_BUTTON_Y_NUDGE = 14;

	@Shadow
	@Final
	private boolean showPauseMenu;

	@Shadow
	private @Nullable Button disconnectButton;

	protected PauseScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void siegedempires$brandLeaveButton(CallbackInfo ci) {
		if (!this.showPauseMenu || this.disconnectButton == null) {
			return;
		}

		Button leave = this.disconnectButton;
		leave.setMessage(
				Component.translatable("menu.siegedempires.leave_game")
						.withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
		);

		int centerX = leave.getX() + leave.getWidth() / 2;
		leave.setSize(LEAVE_BUTTON_WIDTH, LEAVE_BUTTON_HEIGHT);
		leave.setX(centerX - LEAVE_BUTTON_WIDTH / 2);
		leave.setY(leave.getY() + LEAVE_BUTTON_Y_NUDGE);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void siegedempires$drawPauseLogo(
			GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!this.showPauseMenu) {
			return;
		}

		SiegedLogoLayout.Size size = SiegedLogoLayout.fit(
				PAUSE_LOGO_PREFERRED_WIDTH,
				Math.max(32, this.width - 32),
				PAUSE_LOGO_MAX_HEIGHT
		);
		int logoX = this.width / 2 - size.width() / 2;
		int logoY = PAUSE_LOGO_TOP;

		graphics.blit(
				RenderPipelines.GUI_TEXTURED,
				SiegedLogoLayout.TEXTURE,
				logoX,
				logoY,
				0.0F,
				0.0F,
				size.width(),
				size.height(),
				SiegedLogoLayout.TEX_WIDTH,
				SiegedLogoLayout.TEX_HEIGHT,
				SiegedLogoLayout.TEX_WIDTH,
				SiegedLogoLayout.TEX_HEIGHT,
				ARGB.white(1.0F)
		);
	}
}
