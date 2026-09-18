package com.siegedempires.client.title;

import com.siegedempires.client.performance.PerformanceSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Top-right "Performance Settings" entry on the vanilla title screen.
 */
public final class TitleScreenPerformance {
	private static final int BUTTON_WIDTH = 140;
	private static final int BUTTON_HEIGHT = 20;
	private static final int RIGHT_MARGIN = 8;
	private static final int TOP_MARGIN = 8;

	private TitleScreenPerformance() {
	}

	public static void addWidget(Screen parent, Consumer<Button> addForegroundWidget, int screenWidth) {
		int x = screenWidth - RIGHT_MARGIN - BUTTON_WIDTH;
		Button button = Button.builder(
						Component.translatable("menu.siegedempires.performance.button"),
						pressed -> Minecraft.getInstance().gui.setScreen(new PerformanceSettingsScreen(parent))
				)
				.bounds(x, TOP_MARGIN, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build();
		addForegroundWidget.accept(button);
	}
}
