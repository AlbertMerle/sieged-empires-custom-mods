package com.siegedempires.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class GuiNotifications {
	private GuiNotifications() {
	}

	public static void showCantAfford(Minecraft minecraft) {
		if (minecraft == null) {
			return;
		}

		playErrorSound(minecraft);

		minecraft.gui.toastManager().addToast(new CantAffordToast(
				minecraft.font,
				Component.translatable("gui.siegedempires.cant_afford")
		));
	}

	/** Copper golem statue fall — used for afford checks and invalid form input. */
	public static void playErrorSound(Minecraft minecraft) {
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.playSound(SoundEvents.COPPER_GOLEM_STATUE_FALL, 1.0F, 1.0F);
		}
	}

	public static void playXpSound(Minecraft minecraft) {
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.75F, 1.0F);
		}
	}
}
