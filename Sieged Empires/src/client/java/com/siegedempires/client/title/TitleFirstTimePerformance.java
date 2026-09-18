package com.siegedempires.client.title;

import com.siegedempires.client.performance.PerformanceLevel;
import com.siegedempires.client.performance.PerformancePresetApplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * First-launch title overlay: panorama only, prompt text, and the four performance presets.
 * Menu music continues via {@link TitleScreen}'s background-music hook.
 */
public final class TitleFirstTimePerformance {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 8;
	private static final int TITLE_GAP = 16;
	private static final Component COMING_SOON = Component.translatable("menu.siegedempires.performance.coming_soon");
	private static final Component PROMPT = Component.translatable("menu.siegedempires.performance.choose");

	private TitleFirstTimePerformance() {
	}

	public static void addWidgets(int screenWidth, int screenHeight, Font font, Consumer<AbstractWidget> addWidget) {
		int totalHeight = PerformanceLevel.values().length * BUTTON_HEIGHT
				+ (PerformanceLevel.values().length - 1) * BUTTON_GAP;
		int startY = screenHeight / 2 - totalHeight / 2;
		int promptWidth = font.width(PROMPT);
		int promptY = Math.max(12, startY - TITLE_GAP - font.lineHeight);

		addWidget.accept(new StringWidget(
				screenWidth / 2 - promptWidth / 2,
				promptY,
				promptWidth,
				font.lineHeight,
				PROMPT,
				font
		));

		for (int index = 0; index < PerformanceLevel.values().length; index++) {
			PerformanceLevel level = PerformanceLevel.values()[index];
			int y = startY + index * (BUTTON_HEIGHT + BUTTON_GAP);
			Button button = Button.builder(level.label(), pressed -> selectLevel(level))
					.bounds(screenWidth / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			button.active = level.isAvailable();
			if (!level.isAvailable()) {
				button.setTooltip(Tooltip.create(COMING_SOON));
			}
			addWidget.accept(button);
		}
	}

	private static void selectLevel(PerformanceLevel level) {
		if (!level.isAvailable()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (PerformancePresetApplier.apply(client, level)) {
			client.gui.setScreen(new TitleScreen());
		}
	}
}
