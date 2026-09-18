package com.siegedempires.client.mixin;

import com.siegedempires.client.performance.PerformanceSettingsState;
import com.siegedempires.client.title.JoinServerStatus;
import com.siegedempires.client.title.JoinSiegedEmpiresButton;
import com.siegedempires.client.title.OtherPlayOptionsScreen;
import com.siegedempires.client.title.TitleFirstTimePerformance;
import com.siegedempires.client.title.TitleMusicPlaylist;
import com.siegedempires.client.title.TitleScreenJoin;
import com.siegedempires.client.title.TitleScreenLayout;
import com.siegedempires.client.title.TitleScreenPerformance;
import com.siegedempires.client.title.TitleScreenSponsor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.Music;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inserts a large "Join Sieged Empires" button above the main menu, moves
 * Singleplayer / Multiplayer / Realms behind "Other Play Options", and keeps
 * sponsor / performance / title-music hooks.
 * First launch with no performance preset shows only panorama + tier picker.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	@Unique
	private static final Tooltip SERVER_DOWN_TOOLTIP = Tooltip.create(
			Component.translatable("menu.siegedempires.server_down").withStyle(ChatFormatting.RED)
	);

	@Unique
	private @Nullable JoinSiegedEmpiresButton siegedempires$joinButton;

	@Unique
	private int siegedempires$menuTop;

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Override
	public Music getBackgroundMusic() {
		return TitleMusicPlaylist.current();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void siegedempires$tickJoinStatus(CallbackInfo ci) {
		if (PerformanceSettingsState.needsFirstTimeChoice()) {
			return;
		}
		JoinServerStatus.get().tick(Minecraft.getInstance());
		this.siegedempires$refreshJoinButton();
	}

	@Inject(method = "removed", at = @At("TAIL"))
	private void siegedempires$shutdownJoinStatus(CallbackInfo ci) {
		JoinServerStatus.get().shutdown();
	}

	@ModifyVariable(
			method = "init",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isDemo()Z", shift = At.Shift.BEFORE),
			ordinal = 0
	)
	private int siegedempires$shiftMenuUpForSponsor(int topPos) {
		if (PerformanceSettingsState.needsFirstTimeChoice()) {
			return topPos;
		}
		return TitleScreenLayout.adjustMenuTop(this.height, this.font, topPos);
	}

	/**
	 * Skip vanilla Singleplayer / Multiplayer / Realms on the title screen and
	 * place the Join button here (cancelling this method would skip a HEAD
	 * {@link ModifyVariable}, which previously dropped the button).
	 */
	@Inject(method = "createNormalMenuOptions", at = @At("HEAD"), cancellable = true)
	private void siegedempires$skipVanillaPlayButtons(int topPos, int spacing, CallbackInfoReturnable<Integer> cir) {
		if (PerformanceSettingsState.needsFirstTimeChoice()) {
			cir.setReturnValue(topPos);
			return;
		}
		int joinTop = topPos + TitleScreenLayout.JOIN_BUTTON_NUDGE;
		this.siegedempires$menuTop = joinTop;
		cir.setReturnValue(siegedempires$addJoinButton(joinTop));
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void siegedempires$addSponsorWidgets(CallbackInfo ci) {
		if (PerformanceSettingsState.needsFirstTimeChoice()) {
			this.clearWidgets();
			this.siegedempires$joinButton = null;
			TitleFirstTimePerformance.addWidgets(this.width, this.height, this.font, this::addRenderableWidget);
			return;
		}
		this.siegedempires$addOtherPlayOptionsButton();
		TitleScreenPerformance.addWidget(this, this::siegedempires$addForegroundWidget, this.width);
		TitleScreenSponsor.addWidgets(
				this,
				this::siegedempires$addForegroundWidget,
				this::siegedempires$addLabelOnly,
				this.font,
				this.width,
				this.height,
				this.siegedempires$menuTop
		);
	}

	/**
	 * First-time picker: panorama + widgets only (no logo, splash, version, Realms toast).
	 */
	@Inject(
			method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void siegedempires$firstTimePanoramaOnly(
			GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!PerformanceSettingsState.needsFirstTimeChoice()) {
			return;
		}
		this.extractPanorama(graphics, a);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		ci.cancel();
	}

	@ModifyVariable(method = "createDemoMenuOptions", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int siegedempires$insertJoinAboveDemo(int topPos) {
		if (PerformanceSettingsState.needsFirstTimeChoice()) {
			return topPos;
		}
		int joinTop = topPos + TitleScreenLayout.JOIN_BUTTON_NUDGE;
		this.siegedempires$menuTop = joinTop;
		return siegedempires$addJoinButton(joinTop);
	}

	@Unique
	private void siegedempires$addOtherPlayOptionsButton() {
		int optionsY = siegedempires$findOptionsButtonY();
		if (optionsY < 0) {
			return;
		}
		TitleScreen self = (TitleScreen) (Object) this;
		int y = optionsY + TitleScreenLayout.MENU_SPACING;
		this.addRenderableWidget(Button.builder(
				Component.translatable("menu.siegedempires.other_play_options"),
				button -> this.minecraft.gui.setScreen(new OtherPlayOptionsScreen(self))
		).bounds(this.width / 2 - 100, y, 200, 20).build());
	}

	@Unique
	private int siegedempires$findOptionsButtonY() {
		for (var child : this.children()) {
			if (!(child instanceof Button button)) {
				continue;
			}
			if (button.getMessage().getContents() instanceof TranslatableContents contents
					&& "menu.options".equals(contents.getKey())) {
				return button.getY();
			}
		}
		return -1;
	}

	@Unique
	private int siegedempires$addJoinButton(int topPos) {
		TitleScreen self = (TitleScreen) (Object) this;
		JoinSiegedEmpiresButton join = new JoinSiegedEmpiresButton(
				this.width / 2 - TitleScreenLayout.JOIN_BUTTON_WIDTH / 2,
				topPos,
				TitleScreenLayout.JOIN_BUTTON_WIDTH,
				TitleScreenLayout.JOIN_BUTTON_HEIGHT,
				button -> TitleScreenJoin.join(self)
		);
		this.siegedempires$joinButton = join;
		this.siegedempires$refreshJoinButton();
		this.addRenderableWidget(join);
		return topPos + TitleScreenLayout.JOIN_BUTTON_HEIGHT + TitleScreenLayout.JOIN_BUTTON_GAP;
	}

	@Unique
	private void siegedempires$refreshJoinButton() {
		JoinSiegedEmpiresButton join = this.siegedempires$joinButton;
		if (join == null) {
			return;
		}
		boolean online = TitleScreenJoin.canJoin();
		join.setServerOnline(online);
		join.setTooltip(online ? null : SERVER_DOWN_TOOLTIP);
	}

	/**
	 * Inserts interactive widgets at the front of the child list so they receive clicks
	 * even when overlapping lower menu buttons (vanilla hit-tests first match only).
	 */
	@Unique
	private <T extends AbstractWidget & net.minecraft.client.gui.components.events.GuiEventListener & NarratableEntry> void siegedempires$addForegroundWidget(T widget) {
		ScreenAccessor accessor = (ScreenAccessor) this;
		accessor.siegedempires$getRenderables().add(widget);
		accessor.siegedempires$getChildren().add(0, widget);
		accessor.siegedempires$getNarratables().add(widget);
	}

	/** Label only — rendered but not in the child list, so it cannot steal mouse clicks. */
	@Unique
	private void siegedempires$addLabelOnly(StringWidget widget) {
		this.addRenderableOnly(widget);
	}
}
