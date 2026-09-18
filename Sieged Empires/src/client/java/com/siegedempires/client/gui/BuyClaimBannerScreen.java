package com.siegedempires.client.gui;

import com.siegedempires.util.InventoryHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * Quantity picker for buying claim banners (1–16) at 1 gold bar each.
 */
public class BuyClaimBannerScreen extends Screen {
	/** Gold bars (or coin equivalent) charged per claim banner. */
	public static final int COST_PER_BANNER = 1;
	public static final int MIN_COUNT = 1;
	public static final int MAX_COUNT = 16;

	private static final int SLIDER_WIDTH = 200;
	private static final int SLIDER_HEIGHT = 20;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 12;
	/** Slightly larger than body text so the chosen quantity is easy to read. */
	private static final float COUNT_SCALE = 1.75F;

	/** Cached quantity from the slider; always defaults to 1 when opening fresh. */
	private static int cachedCount = MIN_COUNT;

	private final Screen parent;
	private int selectedCount = MIN_COUNT;

	public BuyClaimBannerScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.buy_claim_banner_quantity_title"));
		this.parent = parent;
		cachedCount = MIN_COUNT;
		this.selectedCount = MIN_COUNT;
	}

	public static int getCachedCount() {
		return Mth.clamp(cachedCount, MIN_COUNT, MAX_COUNT);
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		selectedCount = MIN_COUNT;
		cachedCount = MIN_COUNT;

		int centerX = this.width / 2;
		int centerY = this.height / 2;
		int sliderY = centerY - 8;

		double initialValue = countToValue(selectedCount);
		this.addRenderableWidget(new AbstractSliderButton(
				centerX - SLIDER_WIDTH / 2,
				sliderY,
				SLIDER_WIDTH,
				SLIDER_HEIGHT,
				Component.empty(),
				initialValue
		) {
			@Override
			protected void updateMessage() {
				// Count is drawn above the slider; keep the handle label empty.
			}

			@Override
			protected void applyValue() {
				selectedCount = valueToCount(this.value);
				cachedCount = selectedCount;
			}
		});

		int footerY = centerY + 40;
		int totalFooterWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
		int footerLeft = centerX - totalFooterWidth / 2;

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onBack()
		).bounds(footerLeft, footerY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.purchase").withStyle(ChatFormatting.GREEN),
				button -> onPurchase()
		).bounds(footerLeft + BUTTON_WIDTH + BUTTON_GAP, footerY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private void onBack() {
		cachedCount = MIN_COUNT;
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	private void onPurchase() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}

		int count = getCachedCount();
		int totalCost = count * COST_PER_BANNER;
		cachedCount = count;

		if (!InventoryHelper.hasEnoughGold(this.minecraft.player, totalCost)) {
			GuiNotifications.showCantAfford(this.minecraft);
			return;
		}

		this.minecraft.player.connection.sendCommand("town buyclaimbanner " + count);
		cachedCount = MIN_COUNT;
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		int centerY = this.height / 2;
		int sliderY = centerY - 8;

		int countBaselineY = sliderY - Math.round(this.font.lineHeight * COUNT_SCALE) - 8;
		int titleY = countBaselineY - this.font.lineHeight - 10;

		graphics.nextStratum();
		graphics.centeredText(this.font, this.title, centerX, titleY, 0xFFFFFFFF);

		Component countLabel = Component.literal(String.valueOf(selectedCount));
		Matrix3x2fStack pose = graphics.pose();
		float countCenterY = countBaselineY + this.font.lineHeight / 2.0F;
		pose.pushMatrix();
		pose.translate(centerX, countCenterY);
		pose.scale(COUNT_SCALE, COUNT_SCALE);
		pose.translate(-centerX, -countCenterY);
		graphics.nextStratum();
		graphics.centeredText(this.font, countLabel, centerX, countBaselineY, 0xFFFFFFFF);
		pose.popMatrix();
	}

	@Override
	public void onClose() {
		cachedCount = MIN_COUNT;
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static double countToValue(int count) {
		int clamped = Mth.clamp(count, MIN_COUNT, MAX_COUNT);
		return (clamped - MIN_COUNT) / (double) (MAX_COUNT - MIN_COUNT);
	}

	private static int valueToCount(double value) {
		int steps = MAX_COUNT - MIN_COUNT;
		return MIN_COUNT + Mth.clamp((int) Math.round(value * steps), 0, steps);
	}
}
