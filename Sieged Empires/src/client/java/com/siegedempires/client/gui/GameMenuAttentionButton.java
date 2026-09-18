package com.siegedempires.client.gui;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.util.function.BooleanSupplier;

/**
 * Game Menu button that can bounce and stay highlighted to draw attention.
 */
public class GameMenuAttentionButton extends Button {
	/** Peak offset from center in any direction (smaller = subtler). */
	private static final float BOUNCE_PX = 1.5F;
	/** 10% of the original attention bounce rate. */
	private static final float BOUNCE_HZ = 0.24F;
	private static final float PULSE_SCALE = 0.05F;

	private final float labelScale;
	private final BooleanSupplier attention;

	public GameMenuAttentionButton(
			int x, int y, int width, int height,
			Component message, float labelScale,
			BooleanSupplier attention, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.labelScale = labelScale;
		this.attention = attention;
		this.setOverrideRenderHighlightedSprite(() ->
				this.attention.getAsBoolean() || this.isHoveredOrFocused());
	}

	public void setPosition(int x, int y) {
		this.setX(x);
		this.setY(y);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean attract = this.attention.getAsBoolean() && this.active;
		float bounceX = 0.0F;
		float bounceY = 0.0F;
		float scale = 1.0F;
		if (attract) {
			// Circular wobble around the button center (signed sin/cos, not one-sided).
			double t = Util.getMillis() / 1000.0 * Math.PI * 2.0 * BOUNCE_HZ;
			bounceX = (float) (Math.sin(t) * BOUNCE_PX);
			bounceY = (float) (Math.cos(t) * BOUNCE_PX);
			scale = 1.0F + (float) (Math.abs(Math.sin(t)) * PULSE_SCALE);
		}

		Matrix3x2fStack pose = graphics.pose();
		float centerX = this.getX() + this.getWidth() / 2.0F;
		float centerY = this.getY() + this.getHeight() / 2.0F;
		pose.pushMatrix();
		pose.translate(centerX + bounceX, centerY + bounceY);
		pose.scale(scale, scale);
		pose.translate(-centerX, -centerY);

		this.extractDefaultSprite(graphics);

		float textScale = this.labelScale;
		if (textScale != 1.0F) {
			pose.translate(centerX, centerY);
			pose.scale(textScale, textScale);
			pose.translate(-centerX, -centerY);
		}
		this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
		pose.popMatrix();
	}
}
