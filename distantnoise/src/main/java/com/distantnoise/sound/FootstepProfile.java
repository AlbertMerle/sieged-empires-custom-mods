package com.distantnoise.sound;

import net.minecraft.resources.Identifier;

/** Source sound + playback tuning for a distant footstep relay. */
public record FootstepProfile(Identifier soundId, float baseVolume, float pitch) {
}
