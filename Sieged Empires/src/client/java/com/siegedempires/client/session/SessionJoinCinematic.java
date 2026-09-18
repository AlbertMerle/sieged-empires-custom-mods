package com.siegedempires.client.session;

import com.siegedempires.client.gui.GameMenuHowToPlayScreen;
import com.siegedempires.client.gui.GameMenuScreen;
import com.siegedempires.client.gui.MailScreen;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.client.title.WorldEntryFade;
import com.siegedempires.network.payload.SessionCinematicPayload;
import com.siegedempires.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * Client session-gate flow after world load:
 * mute world audio → reveal Game Menu → Join → location ambient (2s in / 2s out)
 * + location title → release → fade into world (unmute).
 */
public final class SessionJoinCinematic {
	private static final long FADE_MS = 3000L;
	private static final long TITLE_MS = 4000L;
	private static final long AMBIENT_FADE_MS = 2000L;
	private static final float TITLE_SCALE = 3.0F;
	private static final float SUBTITLE_SCALE = 1.35F;
	/** Vanilla {@link net.minecraft.ChatFormatting#GREEN} RGB. */
	private static final int WILDERNESS_RGB = 0x55FF55;
	/** Snowy wilderness title. */
	private static final int WILDERNESS_SNOW_RGB = 0xFFFFFF;
	/** Light blue for Ocean. */
	private static final int OCEAN_RGB = 0x55AAFF;
	/** Vanilla {@link net.minecraft.ChatFormatting#YELLOW} RGB. */
	private static final int CLAIM_RGB = 0xFFFF55;
	/** Vanilla {@link net.minecraft.ChatFormatting#YELLOW} — daytime subtitle. */
	private static final int DAYTIME_RGB = 0xFFFF55;
	/** Vanilla {@link net.minecraft.ChatFormatting#DARK_BLUE} — nighttime subtitle. */
	private static final int NIGHTTIME_RGB = 0x0000AA;
	/** Stormy slate for ocean thunderstorm subtitle. */
	private static final int THUNDERSTORM_RGB = 0xAABBCC;

	/** World categories silenced until the fade-into-world begins. */
	private static final SoundSource[] MUTED_WORLD_SOURCES = {
			SoundSource.WEATHER,
			SoundSource.BLOCKS,
			SoundSource.HOSTILE,
			SoundSource.NEUTRAL,
			SoundSource.PLAYERS,
			SoundSource.AMBIENT,
			SoundSource.VOICE,
			SoundSource.RECORDS
	};

	private enum Phase {
		IDLE,
		FADE_TO_BLACK,
		TITLE,
		WAIT_RELEASE,
		FADE_TO_WORLD
	}

	private static boolean gateActive;
	private static boolean muteWorldAudio;
	private static Phase phase = Phase.IDLE;
	private static long phaseStartMs = -1L;
	private static @Nullable String titleText;
	private static @Nullable String subtitleText;
	private static String titleKind = SessionCinematicPayload.KIND_WILDERNESS;
	private static boolean snowyWilderness;
	private static boolean releaseRequested;
	private static boolean released;

	private enum PendingGameplayUi {
		CHAT,
		INVENTORY
	}

	private static @Nullable PendingGameplayUi pendingGameplayUi;

	private static @Nullable JoinAmbientSoundInstance ambientSound;
	private static long ambientFadeInStartMs = -1L;
	private static long ambientFadeOutStartMs = -1L;
	private static boolean ambientFadeOutStarted;

	private SessionJoinCinematic() {
	}

	public static void onGateStart() {
		gateActive = true;
		muteWorldAudio = true;
		applyWorldMute(Minecraft.getInstance(), true);
	}

	public static boolean isGateActive() {
		return gateActive;
	}

	/**
	 * True while the Game Menu should still offer Join (gate held, cinematic not started).
	 */
	public static boolean shouldShowJoinButton() {
		return gateActive && phase == Phase.IDLE;
	}

	public static boolean blocksGameplayUi() {
		return gateActive || phase != Phase.IDLE;
	}

	/** True while the session gate or enter-world cinematic still owns the player. */
	public static boolean isSessionGateBlockingInput() {
		return gateActive || phase != Phase.IDLE;
	}

	/** Defer Duke/Duchess prompts until the join cinematic completes. */
	public static boolean shouldDeferDukeDuchessPrompt() {
		return isSessionGateBlockingInput();
	}

	/**
	 * Chat / inventory while the Game Menu (or How to Play) is open — vanilla only
	 * routes keybinds when {@code screen == null}, so screens call this from
	 * {@code keyPressed}.
	 */
	public static boolean tryBreakGateOnKey(Minecraft minecraft, KeyEvent event) {
		if (!isSessionGateBlockingInput()) {
			return false;
		}
		PendingGameplayUi action = resolveGameplayKey(minecraft, event);
		if (action == null) {
			return false;
		}
		breakGateForGameplayUi(action);
		return true;
	}

	/** Chat / inventory during HUD-only cinematic phases (no screen open). */
	public static void tryBreakGateOnKeybinds(Minecraft minecraft) {
		if (!isSessionGateBlockingInput() || minecraft.gui.screen() != null) {
			return;
		}
		if (minecraft.options.keyChat.consumeClick()) {
			breakGateForGameplayUi(PendingGameplayUi.CHAT);
		} else if (minecraft.options.keyInventory.consumeClick()) {
			breakGateForGameplayUi(PendingGameplayUi.INVENTORY);
		}
	}

	/** Opens chat/inventory once the server has switched the player to survival. */
	public static void tickPendingGameplayOpen(Minecraft minecraft) {
		if (pendingGameplayUi == null || minecraft.player == null) {
			return;
		}
		tryOpenPendingGameplayUi(minecraft);
	}

	public static void startJoin() {
		if (!gateActive || phase != Phase.IDLE) {
			return;
		}
		ClientNetworking.confirmSessionJoin();
		phase = Phase.FADE_TO_BLACK;
		phaseStartMs = Util.getMillis();
		titleText = null;
		subtitleText = null;
		titleKind = SessionCinematicPayload.KIND_WILDERNESS;
		snowyWilderness = false;
		releaseRequested = false;
		released = false;
		ambientFadeInStartMs = -1L;
		ambientFadeOutStartMs = -1L;
		ambientFadeOutStarted = false;
	}

	public static void onCinematicInfo(String title, String kind, String subtitle, boolean snowy) {
		titleText = title == null || title.isEmpty() ? "Wilderness" : title;
		titleKind = kind == null || kind.isEmpty() ? SessionCinematicPayload.KIND_WILDERNESS : kind;
		subtitleText = subtitle == null ? "" : subtitle;
		snowyWilderness = snowy;
		startAmbientIfNeeded();
	}

	public static void onReleased() {
		released = true;
		gateActive = false;
		tryOpenPendingGameplayUi(Minecraft.getInstance());
		if (pendingGameplayUi != null) {
			return;
		}
		if (phase == Phase.WAIT_RELEASE) {
			beginWorldReveal();
		} else if (phase == Phase.TITLE && titleFinished()) {
			beginWorldReveal();
		}
	}

	public static float screenBlackAlpha() {
		return switch (phase) {
			case FADE_TO_BLACK -> fadeProgress();
			case TITLE, WAIT_RELEASE -> 1.0F;
			case FADE_TO_WORLD -> 1.0F - fadeProgress();
			case IDLE -> 0.0F;
		};
	}

	public static void tick(Minecraft minecraft) {
		if (muteWorldAudio) {
			applyWorldMute(minecraft, true);
		}

		if (phase == Phase.IDLE) {
			return;
		}

		tickAmbientEnvelope();

		tryBreakGateOnKeybinds(minecraft);
		tickPendingGameplayOpen(minecraft);

		// Chat / inventory / settings during the enter-world fades: drop the overlay
		// immediately and leave their screen open (do not force-close it).
		if (playerOpenedGameplayScreen(minecraft)) {
			abortFadeForGameplayUi();
			return;
		}

		long now = Util.getMillis();
		switch (phase) {
			case FADE_TO_BLACK -> {
				// Stay fully black once the menu fade finishes; wait for title info if needed.
				if (now - phaseStartMs >= FADE_MS && titleText != null) {
					closeGameMenu(minecraft);
					phase = Phase.TITLE;
					phaseStartMs = now;
					ambientFadeOutStarted = false;
				}
			}
			case TITLE -> {
				maybeStartAmbientFadeOut(now);
				// Overlap release RTT with the title fade-out so world reveal can start immediately.
				if (!releaseRequested && now - phaseStartMs >= TITLE_MS - 1000L) {
					releaseRequested = true;
					ClientNetworking.requestSessionRelease();
				}
				if (now - phaseStartMs >= TITLE_MS) {
					if (released) {
						beginWorldReveal();
					} else {
						phase = Phase.WAIT_RELEASE;
						phaseStartMs = now;
						forceAmbientSilent();
						if (!releaseRequested) {
							releaseRequested = true;
							ClientNetworking.requestSessionRelease();
						}
					}
				}
			}
			case WAIT_RELEASE -> {
				forceAmbientSilent();
				if (released) {
					beginWorldReveal();
				}
			}
			case FADE_TO_WORLD -> {
				if (fadeProgress() >= 1.0F) {
					reset();
				}
			}
			default -> {
			}
		}
	}

	private static void startAmbientIfNeeded() {
		if (ambientSound != null || titleText == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Holder.Reference<SoundEvent> event = ModSounds.joinAmbientFor(titleKind, subtitleText, snowyWilderness);
		ambientSound = new JoinAmbientSoundInstance(event.value(), SoundInstance.createUnseededRandom());
		ambientSound.setEnvelope(0.0F);
		minecraft.getSoundManager().play(ambientSound);
		ambientFadeInStartMs = Util.getMillis();
		ambientFadeOutStartMs = -1L;
		ambientFadeOutStarted = false;
	}

	private static void maybeStartAmbientFadeOut(long now) {
		if (ambientFadeOutStarted || ambientSound == null) {
			return;
		}
		// Fade ambient out for 2s before the screen starts fading into the world.
		if (now - phaseStartMs >= TITLE_MS - AMBIENT_FADE_MS) {
			ambientFadeOutStarted = true;
			ambientFadeOutStartMs = now;
		}
	}

	private static void tickAmbientEnvelope() {
		if (ambientSound == null) {
			return;
		}
		long now = Util.getMillis();
		float env;
		if (ambientFadeOutStartMs >= 0L) {
			env = 1.0F - Mth.clamp((float) (now - ambientFadeOutStartMs) / (float) AMBIENT_FADE_MS, 0.0F, 1.0F);
			if (env <= 0.0F) {
				forceAmbientSilent();
				return;
			}
		} else if (ambientFadeInStartMs >= 0L) {
			env = Mth.clamp((float) (now - ambientFadeInStartMs) / (float) AMBIENT_FADE_MS, 0.0F, 1.0F);
		} else {
			env = 0.0F;
		}
		ambientSound.setEnvelope(env);
	}

	private static void forceAmbientSilent() {
		if (ambientSound != null) {
			ambientSound.setEnvelope(0.0F);
			ambientSound.finish();
			ambientSound = null;
		}
		ambientFadeOutStartMs = -1L;
	}

	private static void applyWorldMute(Minecraft minecraft, boolean muted) {
		if (minecraft.getSoundManager() == null) {
			return;
		}
		float vol = muted ? 0.0F : 1.0F;
		for (SoundSource source : MUTED_WORLD_SOURCES) {
			minecraft.getSoundManager().updateCategoryVolume(source, vol);
		}
	}

	/**
	 * True when the player opened a real gameplay UI (chat, inventory, pause/settings, …)
	 * during the post-Game-Menu cinematic. Ignores our own session-menu screens
	 * (Game Menu, How to Play, Mail).
	 */
	private static boolean playerOpenedGameplayScreen(Minecraft minecraft) {
		Screen screen = minecraft.gui.screen();
		return screen != null && !isSessionMenuScreen(screen);
	}

	/** Screens that belong to the session gate / Game Menu flow. */
	public static boolean isSessionMenuScreen(@Nullable Screen screen) {
		return screen instanceof GameMenuScreen
				|| screen instanceof GameMenuHowToPlayScreen
				|| screen instanceof MailScreen;
	}

	/**
	 * Clears the black overlay immediately, keeps the player's screen, and still
	 * requests session release so they are not left gated in spectator.
	 */
	private static void abortFadeForGameplayUi() {
		breakGateForGameplayUi(null);
	}

	/**
	 * End the session gate / cinematic, request survival on the server, clear overlays,
	 * and optionally queue chat or inventory once survival is confirmed.
	 */
	private static void breakGateForGameplayUi(@Nullable PendingGameplayUi action) {
		if (action != null) {
			pendingGameplayUi = action;
		}
		if (!releaseRequested) {
			releaseRequested = true;
			ClientNetworking.requestSessionRelease();
		}
		WorldEntryFade.reset();
		forceAmbientSilent();
		unmuteWorld();
		gateActive = false;
		phase = Phase.IDLE;
		phaseStartMs = -1L;
		titleText = null;
		subtitleText = null;
		closeSessionScreens(Minecraft.getInstance());
		tryOpenPendingGameplayUi(Minecraft.getInstance());
		ClientNetworking.onSessionCinematicComplete();
	}

	private static @Nullable PendingGameplayUi resolveGameplayKey(Minecraft minecraft, KeyEvent event) {
		if (minecraft.options.keyChat.matches(event)) {
			return PendingGameplayUi.CHAT;
		}
		if (minecraft.options.keyInventory.matches(event)) {
			return PendingGameplayUi.INVENTORY;
		}
		return null;
	}

	private static void tryOpenPendingGameplayUi(Minecraft minecraft) {
		if (pendingGameplayUi == null || minecraft.player == null) {
			return;
		}
		if (minecraft.player.isSpectator()) {
			return;
		}
		switch (pendingGameplayUi) {
			case CHAT -> minecraft.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
			case INVENTORY -> minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
		}
		pendingGameplayUi = null;
	}

	private static void closeSessionScreens(Minecraft minecraft) {
		if (isSessionMenuScreen(minecraft.gui.screen())) {
			minecraft.gui.setScreen(null);
		}
	}

	public static void extractOverlay(GuiGraphicsExtractor graphics, Font font) {
		float black = Math.max(WorldEntryFade.screenBlackAlpha(), screenBlackAlpha());
		if (black > 0.0F) {
			int a = Mth.ceil(black * 255.0F);
			graphics.nextStratum();
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color(a, 0, 0, 0));
		}

		if (phase != Phase.TITLE || titleText == null) {
			return;
		}

		float titleAlpha = titleAlpha();
		if (titleAlpha <= 0.0F) {
			return;
		}

		int rgb = titleRgb(titleKind, snowyWilderness);
		int color = withAlpha(rgb, titleAlpha);
		Component text = Component.literal(titleText);

		int centerX = graphics.guiWidth() / 2;
		int centerY = graphics.guiHeight() / 2;
		boolean hasSubtitle = subtitleText != null && !subtitleText.isEmpty();
		int titleDrawY = hasSubtitle
				? centerY - Math.round(font.lineHeight * TITLE_SCALE * 0.55F)
				: centerY - font.lineHeight / 2;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, titleDrawY + font.lineHeight / 2.0F);
		pose.scale(TITLE_SCALE, TITLE_SCALE);
		pose.translate(-centerX, -(titleDrawY + font.lineHeight / 2.0F));
		graphics.nextStratum();
		graphics.centeredText(font, text, centerX, titleDrawY, color);
		pose.popMatrix();

		if (hasSubtitle) {
			int subRgb = subtitleRgb(subtitleText);
			int subColor = withAlpha(subRgb, titleAlpha);
			int subtitleY = titleDrawY + Math.round(font.lineHeight * TITLE_SCALE) + 6;
			pose.pushMatrix();
			pose.translate(centerX, subtitleY + font.lineHeight / 2.0F);
			pose.scale(SUBTITLE_SCALE, SUBTITLE_SCALE);
			pose.translate(-centerX, -(subtitleY + font.lineHeight / 2.0F));
			graphics.nextStratum();
			graphics.centeredText(font, Component.literal(subtitleText), centerX, subtitleY, subColor);
			pose.popMatrix();
		}
	}

	private static int titleRgb(String kind, boolean snowy) {
		if (SessionCinematicPayload.KIND_OCEAN.equals(kind)) {
			return OCEAN_RGB;
		}
		if (SessionCinematicPayload.KIND_CLAIM.equals(kind)) {
			return CLAIM_RGB;
		}
		return snowy ? WILDERNESS_SNOW_RGB : WILDERNESS_RGB;
	}

	private static int subtitleRgb(String subtitle) {
		if ("(Thunderstorm)".equals(subtitle)) {
			return THUNDERSTORM_RGB;
		}
		if ("(Nighttime)".equals(subtitle)) {
			return NIGHTTIME_RGB;
		}
		return DAYTIME_RGB;
	}

	private static int withAlpha(int rgb, float alpha) {
		return ARGB.color(Mth.ceil(alpha * 255.0F), (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
	}

	private static void beginWorldReveal() {
		closeGameMenu(Minecraft.getInstance());
		forceAmbientSilent();
		unmuteWorld();
		phase = Phase.FADE_TO_WORLD;
		phaseStartMs = Util.getMillis();
	}

	private static void unmuteWorld() {
		muteWorldAudio = false;
		applyWorldMute(Minecraft.getInstance(), false);
	}

	private static void closeGameMenu(Minecraft minecraft) {
		closeSessionScreens(minecraft);
	}

	private static boolean titleFinished() {
		return phaseStartMs >= 0L && Util.getMillis() - phaseStartMs >= TITLE_MS;
	}

	private static float fadeProgress() {
		if (phaseStartMs < 0L) {
			return 0.0F;
		}
		return Mth.clamp((float) (Util.getMillis() - phaseStartMs) / (float) FADE_MS, 0.0F, 1.0F);
	}

	/** Fade in 1s, hold 2s, fade out 1s across the 4s title window. */
	private static float titleAlpha() {
		float t = Mth.clamp((float) (Util.getMillis() - phaseStartMs) / (float) TITLE_MS, 0.0F, 1.0F);
		float ms = t * TITLE_MS;
		if (ms < 1000.0F) {
			return ms / 1000.0F;
		}
		if (ms < 3000.0F) {
			return 1.0F;
		}
		return 1.0F - (ms - 3000.0F) / 1000.0F;
	}

	public static void reset() {
		boolean completedCinematic = phase == Phase.FADE_TO_WORLD;
		forceAmbientSilent();
		unmuteWorld();
		gateActive = false;
		phase = Phase.IDLE;
		phaseStartMs = -1L;
		titleText = null;
		subtitleText = null;
		titleKind = SessionCinematicPayload.KIND_WILDERNESS;
		snowyWilderness = false;
		releaseRequested = false;
		released = false;
		pendingGameplayUi = null;
		ambientFadeInStartMs = -1L;
		ambientFadeOutStartMs = -1L;
		ambientFadeOutStarted = false;
		if (completedCinematic) {
			ClientNetworking.onSessionCinematicComplete();
		}
	}
}
