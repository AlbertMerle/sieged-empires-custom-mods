package com.siegedempires.client.mixin;

import com.siegedempires.client.title.TitleMusicPlaylist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Uses Sieged Empires title music for all pre-world menu situations that would
 * otherwise play vanilla {@link Musics#MENU}, so clicking Options / Multiplayer
 * / etc. keeps the same track instead of cutting to a different menu song.
 * Never remaps once a world is loaded — in-game must not restart title music.
 *
 * Also sends players leaving a multiplayer world back to the title screen
 * instead of the vanilla server list.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "getSituationalMusic", at = @At("RETURN"), cancellable = true)
	private void siegedempires$preferTitleMenuMusic(CallbackInfoReturnable<Music> cir) {
		Minecraft self = (Minecraft) (Object) this;
		if (self.level != null) {
			return;
		}
		if (cir.getReturnValue() == Musics.MENU) {
			cir.setReturnValue(TitleMusicPlaylist.current());
		}
	}

	@Inject(method = "disconnectFromWorld", at = @At("TAIL"))
	private void siegedempires$disconnectToTitle(Component message, CallbackInfo ci) {
		Minecraft self = (Minecraft) (Object) this;
		if (self.gui.screen() instanceof JoinMultiplayerScreen) {
			self.gui.setScreen(new TitleScreen());
		}
	}
}
