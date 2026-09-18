package com.siegedempires.client.title;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Oversized title-screen button. Label is light-blue when the server is
 * joinable, dark red when offline (vanilla inactive greying is bypassed so
 * the offline color stays visible).
 */
public class JoinSiegedEmpiresButton extends Button {
	private static final float LABEL_SCALE = 1.35F;
	private static final Component ONLINE_LABEL = Component.translatable("menu.siegedempires.join")
			.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
	private static final Component OFFLINE_LABEL = Component.translatable("menu.siegedempires.join")
			.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);

	private boolean serverOnline;

	public JoinSiegedEmpiresButton(int x, int y, int width, int height, OnPress onPress) {
		super(x, y, width, height, ONLINE_LABEL, onPress, DEFAULT_NARRATION);
	}

	/** Updates enabled state and aqua / dark-red label for live ping status. */
	public void setServerOnline(boolean online) {
		this.serverOnline = online;
		this.active = online;
		this.setMessage(online ? ONLINE_LABEL : OFFLINE_LABEL);
	}

	/**
	 * Always return the aqua/dark-red label. Vanilla {@code WithInactiveMessage}
	 * would otherwise grey out the offline text.
	 */
	@Override
	public Component getMessage() {
		return this.serverOnline ? ONLINE_LABEL : OFFLINE_LABEL;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.extractDefaultSprite(graphics);

		Matrix3x2fStack pose = graphics.pose();
		float centerX = this.getX() + this.getWidth() / 2.0F;
		float centerY = this.getY() + this.getHeight() / 2.0F;
		pose.pushMatrix();
		pose.translate(centerX, centerY);
		pose.scale(LABEL_SCALE, LABEL_SCALE);
		pose.translate(-centerX, -centerY);
		this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
		pose.popMatrix();
	}
}
