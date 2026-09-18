package com.siegedempires.client;

import com.siegedempires.Siegedempires;
import com.siegedempires.block.ModMenuTypes;
import com.siegedempires.banner.ClaimBannerClientFeedback;
import com.siegedempires.client.banner.CustomBannerClothTextures;
import com.siegedempires.client.banner.ShipBannerRenderer;
import com.siegedempires.client.util.ClientTitleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.siegedempires.client.gold.CoinPileHudOverlay;
import com.siegedempires.client.gui.LocksmithingTableScreen;
import com.siegedempires.client.gui.SiegedEmpiresGuiScreen;
import com.siegedempires.client.lock.LockOverlayRenderer;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.client.performance.PerformancePresetApplier;
import com.siegedempires.client.session.SessionJoinCinematic;
import com.siegedempires.client.title.JoinAddressResolver;
import com.siegedempires.client.title.TitleMusicPlaylist;
import com.siegedempires.client.title.WorldEntryFade;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;

public class SiegedempiresClient implements ClientModInitializer {
	private static final Identifier SESSION_FADE_HUD =
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "session_fade");
	private static final int WINDOW_FOCUS_RETRIES = 30;
	private static int windowFocusRetriesRemaining = WINDOW_FOCUS_RETRIES;

	@Override
	public void onInitializeClient() {
		// Before world enter: Voxy reads this when allocating its geometry buffer.
		PerformancePresetApplier.applyStoredVoxyGeometryBufferOverride();

		ClaimBannerClientFeedback.registerAlreadyClaimed(() -> ClientTitleHelper.showSmallTitle(
				Component.literal(com.siegedempires.banner.BannerManager.CHUNK_ALREADY_CLAIMED_MESSAGE)
						.withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				com.siegedempires.banner.BannerManager.CLAIM_FLASH_TICKS));
		JoinAddressResolver.get().tick();
		ClientNetworking.registerReceivers();
		LockOverlayRenderer.register();
		CoinPileHudOverlay.register();

		// Register GUI screens
		MenuScreens.register(ModMenuTypes.LOCKSMITHING_TABLE, LocksmithingTableScreen::new);

		ModKeyBindings.initialize();
		registerSessionEntry();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			// Re-apply after other mods finish init so the cached tier always beats launcher -D.
			PerformancePresetApplier.applyStoredVoxyGeometryBufferOverride();
			windowFocusRetriesRemaining = WINDOW_FOCUS_RETRIES;
			client.execute(WindowBranding::apply);
		});

		// Launcher may still hold focus briefly after MC starts; retry until it releases or we time out.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (windowFocusRetriesRemaining <= 0) {
				return;
			}
			windowFocusRetriesRemaining--;
			client.execute(() -> WindowBranding.applyWindowPlacement(client));
		});

		ClientTickEvents.END_CLIENT_TICK.register(ShipBannerRenderer::tick);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ModKeyBindings.OPEN_GUI.consumeClick()) {
				if (client.player == null || SessionJoinCinematic.blocksGameplayUi()) {
					continue;
				}

				if (client.gui.screen() instanceof SiegedEmpiresGuiScreen) {
					client.gui.setScreen(null);
				} else if (client.gui.screen() == null) {
					client.gui.setScreen(new SiegedEmpiresGuiScreen());
				}
			}
		});
	}

	private static void registerSessionEntry() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				client.execute(() -> {
					SessionJoinCinematic.onGateStart();
					WorldEntryFade.arm();
				}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			WorldEntryFade.reset();
			SessionJoinCinematic.reset();
			TitleMusicPlaylist.reshuffle();
			CustomBannerClothTextures.clear();
		});

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			WorldEntryFade.tick(client);
			SessionJoinCinematic.tick(client);
		});

		HudElementRegistry.addLast(SESSION_FADE_HUD, (graphics, deltaTracker) ->
				SessionJoinCinematic.extractOverlay(graphics, net.minecraft.client.Minecraft.getInstance().font));
	}
}
