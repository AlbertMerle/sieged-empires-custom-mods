package com.siegedempires.client.gold;

import com.siegedempires.Siegedempires;
import com.siegedempires.block.GoldCoinPileBlock;
import com.siegedempires.block.ModBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class CoinPileHudOverlay {
	private static final Identifier HUD_ID =
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "coin_pile_hint");
	/** Vanilla XP bar top is {@code guiHeight - 29} (margin 24 + height 5). */
	private static final int XP_BAR_TOP_OFFSET = 29;
	private static final int ABOVE_XP_BAR = 60;
	private static final int TEXT_COLOR = ARGB.color(255, 255, 255, 255);

	private CoinPileHudOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.INFO_BAR,
				HUD_ID,
				CoinPileHudOverlay::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null
				|| minecraft.level == null
				|| minecraft.gui.screen() != null
				|| minecraft.gui.hud.isHidden()) {
			return;
		}

		HitResult hitResult = minecraft.hitResult;
		if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
		if (!state.is(ModBlocks.GOLD_COIN_PILE)) {
			return;
		}

		int count = state.getValue(GoldCoinPileBlock.COUNT);
		Component text = Component.literal("Coin Pile " + count + " / " + GoldCoinPileBlock.MAX_COUNT);
		int centerX = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() - XP_BAR_TOP_OFFSET - ABOVE_XP_BAR - minecraft.font.lineHeight;
		graphics.nextStratum();
		graphics.centeredText(minecraft.font, text, centerX, y, TEXT_COLOR);
	}
}
