package com.siegedempires.client.mixin;

import com.siegedempires.client.title.TitleMusicPlaylist;
import com.siegedempires.client.title.WorldEntryFade;
import com.siegedempires.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During world-entry fade, bypass normal music scheduling and fade the MUSIC
 * category gain 1→0. Also advances the title playlist when a title track ends.
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

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void siegedempires$fadeTitleMusicOnWorldEntry(CallbackInfo ci) {
		if (!WorldEntryFade.isFadingMusic()) {
			return;
		}

		float volume = WorldEntryFade.musicVolume();
		this.minecraft.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, volume);
		if (volume <= 0.0F) {
			this.stopPlaying();
		}
		ci.cancel();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void siegedempires$advanceTitlePlaylistWhenTrackEnds(CallbackInfo ci) {
		if (WorldEntryFade.isFadingMusic()) {
			return;
		}
		SoundInstance playing = this.currentMusic;
		if (playing == null) {
			return;
		}
		if (this.minecraft.getSoundManager().isActive(playing)) {
			return;
		}
		Identifier id = playing.getIdentifier();
		if (ModSounds.isTitleTrack(id)) {
			TitleMusicPlaylist.onTitleTrackEnded(id);
		}
	}
}
