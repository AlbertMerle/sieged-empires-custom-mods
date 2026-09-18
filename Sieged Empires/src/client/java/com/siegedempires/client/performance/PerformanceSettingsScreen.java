package com.siegedempires.client.performance;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Preset picker opened from the title screen.
 */
public class PerformanceSettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 8;
	private static final Component COMING_SOON = Component.translatable("menu.siegedempires.performance.coming_soon");

	private final Screen parent;
	private Component statusMessage = CommonComponents.EMPTY;

	public PerformanceSettingsScreen(Screen parent) {
		super(Component.translatable("menu.siegedempires.performance.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int titleWidth = this.font.width(this.title);
		this.addRenderableWidget(new StringWidget(
				this.width / 2 - titleWidth / 2,
				40,
				titleWidth,
				this.font.lineHeight,
				this.title,
				this.font
		));

		if (!this.statusMessage.getString().isEmpty()) {
			int statusWidth = this.font.width(this.statusMessage);
			this.addRenderableWidget(new StringWidget(
					this.width / 2 - statusWidth / 2,
					56,
					statusWidth,
					this.font.lineHeight,
					this.statusMessage,
					this.font
			));
		}

		PerformanceLevel selected = PerformanceSettingsState.get().selectedLevelOrNull();
		int totalHeight = PerformanceLevel.values().length * BUTTON_HEIGHT + (PerformanceLevel.values().length - 1) * BUTTON_GAP;
		int startY = this.height / 2 - totalHeight / 2;

		for (int index = 0; index < PerformanceLevel.values().length; index++) {
			PerformanceLevel level = PerformanceLevel.values()[index];
			int y = startY + index * (BUTTON_HEIGHT + BUTTON_GAP);
			Button button = this.createLevelButton(level, selected, y);
			this.addRenderableWidget(button);
		}

		this.addRenderableWidget(
				Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
						.bounds(this.width / 2 - 100, this.height - 52, 200, 20)
						.build()
		);
	}

	private Button createLevelButton(PerformanceLevel level, @org.jspecify.annotations.Nullable PerformanceLevel selected, int y) {
		Component label = level.label();
		if (selected != null && level == selected) {
			label = label.copy().append(Component.literal(" ✓").withStyle(ChatFormatting.GREEN));
		}

		Button button = Button.builder(label, pressed -> this.selectLevel(level))
				.bounds(this.width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build();
		button.active = level.isAvailable();
		if (!level.isAvailable()) {
			button.setTooltip(Tooltip.create(COMING_SOON));
		}
		return button;
	}

	private void selectLevel(PerformanceLevel level) {
		if (!level.isAvailable()) {
			return;
		}

		Minecraft client = this.minecraft;
		if (client == null) {
			return;
		}

		if (PerformancePresetApplier.apply(client, level)) {
			this.statusMessage = Component.translatable("menu.siegedempires.performance.applied", level.label())
					.withStyle(ChatFormatting.GREEN);
		} else {
			this.statusMessage = Component.translatable("menu.siegedempires.performance.apply_failed")
					.withStyle(ChatFormatting.RED);
		}
		this.rebuildWidgets();
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
