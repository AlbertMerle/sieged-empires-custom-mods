package com.siegedmusic.client.mixin;

import com.siegedmusic.client.music.GameMusicPlaylist;
import com.siegedmusic.sound.VanillaGameMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla in-world situational music with Sieged Music tracks.
 * Menu / title music is untouched (handled by Sieged Empires on the title screen).
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "getSituationalMusic", at = @At("RETURN"), cancellable = true)
	private void siegedmusic$replaceVanillaGameMusic(CallbackInfoReturnable<Music> cir) {
		Minecraft self = (Minecraft) (Object) this;
		if (self.player == null || self.level == null) {
			return;
		}
		Music vanilla = cir.getReturnValue();
		if (!VanillaGameMusic.isReplaceable(vanilla)) {
			return;
		}
		cir.setReturnValue(GameMusicPlaylist.current(self));
	}
}
