package com.siegedempires.client.gui;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.client.network.ClientNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Yes/No confirm before converting all plain inventory banners to the
 * town or empire flag design (free).
 */
public class ConfirmConvertBannersScreen extends ConfirmYesNoScreen {
	private final String factionKind;

	public ConfirmConvertBannersScreen(Screen parent, String factionKind) {
		super(parent,
				Component.translatable("gui.siegedempires.confirm_convert_banners_title"),
				Component.translatable("gui.siegedempires.confirm_convert_banners"));
		this.factionKind = factionKind;
	}

	@Override
	protected void onConfirm() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		if (!playerHasConvertibleBanners(this.minecraft.player.getInventory())) {
			GuiNotifications.playErrorSound(this.minecraft);
			this.minecraft.player.sendOverlayMessage(
					Component.translatable("gui.siegedempires.convert_banners_none"));
			this.minecraft.gui.setScreen(this.parent);
			return;
		}
		ClientNetworking.convertInventoryBanners(this.factionKind);
		this.minecraft.gui.setScreen(this.parent);
	}

	private static boolean playerHasConvertibleBanners(Inventory inventory) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (BannerHelper.isConvertibleBanner(stack)) {
				return true;
			}
		}
		return false;
	}
}
