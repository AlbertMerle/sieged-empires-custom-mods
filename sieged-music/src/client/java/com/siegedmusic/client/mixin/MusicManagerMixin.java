package com.siegedmusic.client.mixin;

import com.siegedmusic.client.music.GameMusicPlaylist;
import com.siegedmusic.client.music.MusicFadeController;
import com.siegedmusic.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cross-fades between ambient, cave, and war playlists, fades game music in after menu music,
 * and advances the in-game playlist when a track ends.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private @Nullable SoundInstance currentMusic;

	@Shadow
	public abstract void stopPlaying();

	@Shadow
	public abstract void startPlaying(Music music);

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void siegedmusic$manageFadesAndPlaylist(CallbackInfo ci) {
		if (this.minecraft.player == null || this.minecraft.level == null) {
			return;
		}

		MusicFadeController.tick(this.minecraft, (MusicManager) (Object) this, this.currentMusic);

		if (MusicFadeController.shouldOverrideMusicTick()) {
			ci.cancel();
			return;
		}

		GameMusicPlaylist.updateMode(this.minecraft);

		SoundInstance playing = this.currentMusic;
		if (playing == null) {
			return;
		}
		if (this.minecraft.getSoundManager().isActive(playing)) {
			return;
		}
		Identifier id = playing.getIdentifier();
		if (ModSounds.isGameTrack(id)) {
			GameMusicPlaylist.onTrackEnded(id);
		}
	}
}
