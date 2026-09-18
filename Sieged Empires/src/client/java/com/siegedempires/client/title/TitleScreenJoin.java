package com.siegedempires.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * One-click join from the vanilla title screen "Join Sieged Empires" button.
 * Address comes from {@link JoinAddressResolver} (gist URL, then
 * {@code join-button-ip} fallback). Reachability is polled by
 * {@link JoinServerStatus}; the button stays disabled until the server
 * answers a status ping.
 */
public final class TitleScreenJoin {
	private TitleScreenJoin() {
	}

	/** Valid configured address and multiplayer allowed (ignores live ping). */
	public static boolean hasJoinAddress() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || !minecraft.allowsMultiplayer()) {
			return false;
		}
		String address = JoinAddressResolver.get().currentAddress();
		return !address.isEmpty() && ServerAddress.isValidAddress(address);
	}

	/** Whether the join button should be clickable right now. */
	public static boolean canJoin() {
		return hasJoinAddress() && JoinServerStatus.get().isOnline();
	}

	/** Starts the fade + sound cinematic, then connects. */
	public static void join(Screen parent) {
		if (!canJoin()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		minecraft.gui.setScreen(new JoinFadeScreen(parent));
	}

	/** Opens the vanilla connecting screen (called after the fade finishes). */
	public static void connect(Screen parent) {
		if (!hasJoinAddress()) {
			return;
		}

		WorldEntryFade.arm();
		Minecraft minecraft = Minecraft.getInstance();
		String address = JoinAddressResolver.get().currentAddress();
		ServerData data = new ServerData("Sieged Empires", address, ServerData.Type.OTHER);
		ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(address), data, false, null);
	}
}
