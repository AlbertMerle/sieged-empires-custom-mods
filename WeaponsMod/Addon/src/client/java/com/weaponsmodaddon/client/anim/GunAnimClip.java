package com.weaponsmodaddon.client.anim;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One named clip from {@code player_gun_anims.json}.
 */
public final class GunAnimClip {
	public final String name;
	public final float durationSeconds;
	private final Map<String, BoneChannels> bones;

	GunAnimClip(String name, float durationSeconds, Map<String, BoneChannels> bones) {
		this.name = name;
		this.durationSeconds = durationSeconds;
		this.bones = bones;
	}

	public BonePose sample(float timeSeconds, boolean loop) {
		float t = timeSeconds;
		if (loop && durationSeconds > 1.0E-4F) {
			t = t % durationSeconds;
			if (t < 0.0F) {
				t += durationSeconds;
			}
		} else if (t > durationSeconds) {
			t = durationSeconds;
		}

		BonePose pose = new BonePose();
		for (Map.Entry<String, BoneChannels> e : bones.entrySet()) {
			BoneChannels ch = e.getValue();
			Vec3 rot = ch.rotation.isEmpty() ? null : sampleVec(ch.rotation, t, true);
			Vec3 pos = ch.translation.isEmpty() ? null : sampleVec(ch.translation, t, false);
			pose.bones.put(e.getKey(), new BoneSample(rot, pos));
		}
		return pose;
	}

	private static Vec3 sampleVec(List<Keyframe> keys, float t, boolean angular) {
		if (keys.isEmpty()) {
			return Vec3.ZERO;
		}
		if (keys.size() == 1 || t <= keys.getFirst().t) {
			Keyframe k = keys.getFirst();
			return new Vec3(k.x, k.y, k.z);
		}
		Keyframe last = keys.getLast();
		if (t >= last.t) {
			return new Vec3(last.x, last.y, last.z);
		}
		for (int i = 0; i < keys.size() - 1; i++) {
			Keyframe a = keys.get(i);
			Keyframe b = keys.get(i + 1);
			if (t <= b.t) {
				float span = b.t - a.t;
				float alpha = span < 1.0E-6F ? 1.0F : (t - a.t) / span;
				if (angular) {
					return new Vec3(
							lerpAngle(a.x, b.x, alpha),
							lerpAngle(a.y, b.y, alpha),
							lerpAngle(a.z, b.z, alpha)
					);
				}
				return new Vec3(
						lerp(a.x, b.x, alpha),
						lerp(a.y, b.y, alpha),
						lerp(a.z, b.z, alpha)
				);
			}
		}
		return new Vec3(last.x, last.y, last.z);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Shortest-arc lerp in radians (matches glTF/Blockbench quaternion interpolation). */
	private static float lerpAngle(float from, float to, float t) {
		float delta = to - from;
		if (delta > (float) Math.PI) {
			delta -= (float) (Math.PI * 2.0);
		} else if (delta < (float) -Math.PI) {
			delta += (float) (Math.PI * 2.0);
		}
		return from + delta * t;
	}

	static final class BoneChannels {
		final List<Keyframe> rotation;
		final List<Keyframe> translation;

		BoneChannels(List<Keyframe> rotation, List<Keyframe> translation) {
			this.rotation = rotation;
			this.translation = translation;
		}
	}

	static final class Keyframe {
		final float t;
		final float x;
		final float y;
		final float z;

		Keyframe(float t, float x, float y, float z) {
			this.t = t;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	public static final class BonePose {
		public final Map<String, BoneSample> bones = new HashMap<>();
	}

	public static final class BoneSample {
		public final Vec3 rotation;
		public final Vec3 translation;

		BoneSample(Vec3 rotation, Vec3 translation) {
			this.rotation = rotation;
			this.translation = translation;
		}
	}

	public static final class Vec3 {
		public static final Vec3 ZERO = new Vec3(0.0F, 0.0F, 0.0F);
		public final float x;
		public final float y;
		public final float z;

		public Vec3(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	static Map<String, BoneChannels> unmodifiable(Map<String, BoneChannels> in) {
		return Collections.unmodifiableMap(in);
	}
}
