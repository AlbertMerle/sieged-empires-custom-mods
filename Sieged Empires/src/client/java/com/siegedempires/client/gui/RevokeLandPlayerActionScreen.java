package com.siegedempires.client.gui;

import com.siegedempires.network.ManageTownData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shows the two actions available for the chosen town member:
 * <ul>
 *   <li>Revoke All Land (clears every chunk grant for the citizen)</li>
 *   <li>Create Land Revoke Banner (gives the caller a purple banner that
 *       revokes whichever citizen owns the chunk where it is placed)</li>
 * </ul>
 */
public class RevokeLandPlayerActionScreen extends Screen {
	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;

	private final Screen parent;
	private final ManageTownData.MemberInfo member;

	public RevokeLandPlayerActionScreen(Screen parent, ManageTownData.MemberInfo member) {
		super(Component.translatable("gui.siegedempires.revoke_land_player_title", member.name));
		this.parent = parent;
		this.member = member;
	}

	@Override
	protected void init() {
		super.init();
		clearWidgets();

		int centerX = this.width / 2;
		Component name = Component.literal(member.name != null ? member.name : "?");
		this.addRenderableWidget(Button.builder(
				name,
				button -> {}
		).bounds(centerX - 100, this.height / 2 - 60, 200, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.revoke_all_land").withStyle(net.minecraft.ChatFormatting.RED),
				button -> minecraft.gui.setScreen(new ConfirmRevokeAllLandScreen(this, member))
		).bounds(centerX - BUTTON_WIDTH / 2, this.height / 2 - 30, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.create_land_revoke_banner"),
				button -> minecraft.gui.setScreen(new ConfirmCreateLandRevokeBannerScreen(this))
		).bounds(centerX - BUTTON_WIDTH / 2, this.height / 2 - 5, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.back"),
				button -> onClose()
		).bounds(centerX - 100, this.height - 28, 200, BUTTON_HEIGHT).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int centerX = this.width / 2;
		graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2, 8, 0xFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
