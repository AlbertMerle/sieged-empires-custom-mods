package com.siegedempires.client.title;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Brief cinematic between the title-screen join button and the connect screen:
 * plays the player level-up sound, fades the title UI to black over 3 seconds,
 * then starts connecting. Title music keeps playing (Minecraft-style) until
 * {@link WorldEntryFade} fades it out after the world loads.
 */
public class JoinFadeScreen extends Screen {
	private static final long FADE_DURATION_MS = 3000L;

	private static @Nullable JoinFadeScreen active;

	private final Screen parent;
	private long fadeStartMs = -1L;
	private boolean startedConnecting;

	public JoinFadeScreen(Screen parent) {
		super(Component.empty());
		this.parent = parent;
	}

	public static boolean isFading() {
		return active != null;
	}

	@Override
	protected void init() {
		super.init();
		active = this;
		this.minecraft.getSoundManager().play(
				SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F)
		);
		this.fadeStartMs = Util.getMillis();
	}

	@Override
	public void tick() {
		if (this.startedConnecting || this.fadeStartMs < 0L) {
			return;
		}
		if (Util.getMillis() - this.fadeStartMs >= FADE_DURATION_MS) {
			this.startedConnecting = true;
			TitleScreenJoin.connect(this.parent);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Title screen draws its own panorama inside extractRenderState.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.parent.extractRenderState(graphics, mouseX, mouseY, a);

		float progress = this.fadeProgress();
		int alpha = Mth.ceil(progress * 255.0F);
		if (alpha <= 0) {
			return;
		}

		graphics.nextStratum();
		graphics.fill(0, 0, this.width, this.height, ARGB.color(alpha, 0, 0, 0));
	}

	private float fadeProgress() {
		if (this.fadeStartMs < 0L) {
			return 0.0F;
		}
		return Mth.clamp((float) (Util.getMillis() - this.fadeStartMs) / (float) FADE_DURATION_MS, 0.0F, 1.0F);
	}

	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		this.parent.resize(width, height);
	}

	@Override
	public void removed() {
		if (active == this) {
			active = null;
		}
		super.removed();
	}

	@Override
	public Music getBackgroundMusic() {
		// Keep the playlist's current track so MusicManager does not swap mid-fade.
		return TitleMusicPlaylist.current();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
