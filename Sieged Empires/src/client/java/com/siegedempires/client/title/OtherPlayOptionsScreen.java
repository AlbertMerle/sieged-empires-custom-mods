package com.siegedempires.client.title;

import com.mojang.authlib.minecraft.BanDetails;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Holds Singleplayer / Multiplayer / Realms after they were removed from the title screen.
 */
public class OtherPlayOptionsScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 24;

	private final Screen parent;

	public OtherPlayOptionsScreen(Screen parent) {
		super(Component.translatable("menu.siegedempires.other_play_options"));
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

		int startY = this.height / 4 + 48;
		int x = this.width / 2 - BUTTON_WIDTH / 2;

		this.addRenderableWidget(Button.builder(
				Component.translatable("menu.singleplayer"),
				button -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))
		).bounds(x, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		Component multiplayerDisabledReason = multiplayerDisabledReason(this.minecraft);
		boolean multiplayerAllowed = multiplayerDisabledReason == null;
		Tooltip multiplayerTooltip = multiplayerDisabledReason != null
				? Tooltip.create(multiplayerDisabledReason)
				: null;

		Button multiplayer = this.addRenderableWidget(Button.builder(
				Component.translatable("menu.multiplayer"),
				button -> {
					Screen screen = this.minecraft.options.skipMultiplayerWarning
							? new JoinMultiplayerScreen(this)
							: new SafetyScreen(this);
					this.minecraft.gui.setScreen(screen);
				}
		).bounds(x, startY + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(multiplayerTooltip)
				.build());
		multiplayer.active = multiplayerAllowed;

		Button realms = this.addRenderableWidget(Button.builder(
				Component.translatable("menu.online"),
				button -> this.minecraft.gui.setScreen(new RealmsMainScreen(this))
		).bounds(x, startY + BUTTON_SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(multiplayerTooltip)
				.build());
		realms.active = multiplayerAllowed;

		this.addRenderableWidget(Button.builder(
				CommonComponents.GUI_BACK,
				button -> this.onClose()
		).bounds(x, startY + BUTTON_SPACING * 3 + 12, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private static @Nullable Component multiplayerDisabledReason(Minecraft minecraft) {
		if (minecraft.allowsMultiplayer()) {
			return null;
		}
		if (minecraft.isNameBanned()) {
			return Component.translatable("title.multiplayer.disabled.banned.name");
		}
		BanDetails multiplayerBan = minecraft.multiplayerBan();
		if (multiplayerBan != null) {
			return multiplayerBan.expires() != null
					? Component.translatable("title.multiplayer.disabled.banned.temporary")
					: Component.translatable("title.multiplayer.disabled.banned.permanent");
		}
		return Component.translatable("title.multiplayer.disabled");
	}
}
