package com.siegedempires.client.util;

import com.siegedempires.util.TitleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientTitleHelper {
	private ClientTitleHelper() {
	}

	public static void showTitle(Component title, int stayTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui == null || minecraft.gui.hud == null) {
			return;
		}
		// Stop any in-progress title so the new one starts immediately (fresh fade-in).
		minecraft.gui.hud.clearTitles();
		minecraft.gui.hud.setTimes(TitleHelper.FADE_IN, stayTicks, TitleHelper.FADE_OUT);
		minecraft.gui.hud.setTitle(title);
		minecraft.gui.hud.setSubtitle(Component.empty());
	}

	/** Subtitle-scale (2×) centered text — half the usual title size. */
	public static void showSmallTitle(Component text, int stayTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui == null || minecraft.gui.hud == null) {
			return;
		}
		minecraft.gui.hud.clearTitles();
		minecraft.gui.hud.setTimes(TitleHelper.FADE_IN, stayTicks, TitleHelper.FADE_OUT);
		minecraft.gui.hud.setTitle(Component.empty());
		minecraft.gui.hud.setSubtitle(text);
	}
}
