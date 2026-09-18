package com.weaponsmodaddon.client.anim;

import com.weaponsmodaddon.ModItemTags;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Per-player state machine for WeaponMod spear / javelin Blockbench clips
 * ({@code spearanim.gltf}): hold → aim → aimhold → throw; melee uses {@code spear_attack}.
 */
public final class SpearAnimController {
	private static final Map<Integer, PlayerAnimState> STATES = new ConcurrentHashMap<>();

	private SpearAnimController() {
	}

	public static void clear() {
		STATES.clear();
	}

	/** True while aim / aim-hold / throw — javelin item should be flipped 180° in-hand. */
	public static boolean shouldFlipItem(int entityId) {
		PlayerAnimState state = STATES.get(entityId);
		if (state == null) {
			return false;
		}
		return state.phase == Phase.AIM_INTRO
				|| state.phase == Phase.AIM_HOLD
				|| state.phase == Phase.THROW;
	}

	private static boolean usesSpearAnims(ItemStack stack) {
		// Modded WeaponMod spears only (+ javelin from earlier wiring).
		return stack.is(ModItemTags.SPEARS) || stack.is(ModItemTags.JAVELINS);
	}

	public static GunAnimClip.BonePose updateAndSample(
			int entityId,
			float ageInTicks,
			ItemStack mainHand,
			boolean isUsingItem,
			float attackTime,
			HumanoidArm mainArm,
			boolean isCrawling
	) {
		PlayerAnimState existing = STATES.get(entityId);
		boolean throwInProgress = existing != null && existing.phase == Phase.THROW;
		boolean attackInProgress = existing != null && existing.phase == Phase.ATTACK;
		// Crawl/swim: leave FA/vanilla alone — crawl Blockbench clips are gun-only.
		// Finish an in-progress throw that started while standing.
		if (isCrawling && !throwInProgress) {
			STATES.remove(entityId);
			return null;
		}
		// Spear is removed from the hand at throw-clip halfway; keep playing the rest of THROW.
		if (!throwInProgress && !attackInProgress && (mainHand.isEmpty() || !usesSpearAnims(mainHand))) {
			STATES.remove(entityId);
			return null;
		}

		PlayerAnimState state = STATES.computeIfAbsent(entityId, id -> new PlayerAnimState());
		state.tick(ageInTicks, isUsingItem, attackTime);

		if (state.clipName == null) {
			return null;
		}
		GunAnimClip clip = SpearAnimLibrary.get(state.clipName);
		if (clip == null) {
			return null;
		}

		float speed = SpearAnimLibrary.speedFor(state.clipName);
		float localSeconds = Math.max(0.0F, (ageInTicks - state.clipStartAge) / 20.0F) * speed;
		boolean loop = state.loop;

		if (!loop && localSeconds >= clip.durationSeconds) {
			state.onClipFinished(ageInTicks);
			if (state.clipName == null) {
				return null;
			}
			clip = SpearAnimLibrary.get(state.clipName);
			if (clip == null) {
				return null;
			}
			speed = SpearAnimLibrary.speedFor(state.clipName);
			localSeconds = Math.max(0.0F, (ageInTicks - state.clipStartAge) / 20.0F) * speed;
			loop = state.loop;
		}

		GunAnimClip.BonePose pose = clip.sample(localSeconds, loop);
		// Attack clip body keys fight vanilla crouch/waist; arms + head only.
		if (state.phase == Phase.ATTACK) {
			pose.bones.remove("body");
		}
		if (mainArm == HumanoidArm.LEFT) {
			return mirrorPose(pose);
		}
		return pose;
	}

	public static void prune(int keepLastN) {
		if (STATES.size() <= keepLastN) {
			return;
		}
		Iterator<Map.Entry<Integer, PlayerAnimState>> it = STATES.entrySet().iterator();
		while (STATES.size() > keepLastN && it.hasNext()) {
			it.next();
			it.remove();
		}
	}

	private static GunAnimClip.BonePose mirrorPose(GunAnimClip.BonePose src) {
		GunAnimClip.BonePose out = new GunAnimClip.BonePose();
		for (Map.Entry<String, GunAnimClip.BoneSample> e : src.bones.entrySet()) {
			String bone = e.getKey();
			GunAnimClip.BoneSample s = e.getValue();
			String mirroredBone = switch (bone) {
				case "leftArm" -> "rightArm";
				case "rightArm" -> "leftArm";
				case "leftLeg" -> "rightLeg";
				case "rightLeg" -> "leftLeg";
				default -> bone;
			};
			GunAnimClip.Vec3 rot = s.rotation == null ? null : new GunAnimClip.Vec3(s.rotation.x, -s.rotation.y, -s.rotation.z);
			GunAnimClip.Vec3 pos = s.translation == null ? null : new GunAnimClip.Vec3(-s.translation.x, s.translation.y, s.translation.z);
			out.bones.put(mirroredBone, new GunAnimClip.BoneSample(rot, pos));
		}
		return out;
	}

	enum Phase {
		NONE,
		HOLD,
		AIM_INTRO,
		AIM_HOLD,
		THROW,
		ATTACK
	}

	static final class PlayerAnimState {
		Phase phase = Phase.NONE;
		String clipName;
		float clipStartAge;
		boolean loop;
		boolean wasUsing;
		boolean wasSwinging;

		void tick(float ageInTicks, boolean isUsing, float attackTime) {
			boolean swinging = attackTime > 0.0F;
			boolean swingStarted = swinging && !wasSwinging;

			if (phase == Phase.THROW) {
				wasUsing = isUsing;
				wasSwinging = swinging;
				return;
			}

			if (phase == Phase.ATTACK) {
				// New click mid-swing restarts the attack clip.
				if (swingStarted) {
					setPhase(Phase.ATTACK, SpearAnimLibrary.ATTACK, ageInTicks, false);
				}
				wasUsing = isUsing;
				wasSwinging = swinging;
				return;
			}

			if (isUsing) {
				if (phase != Phase.AIM_INTRO && phase != Phase.AIM_HOLD) {
					setPhase(Phase.AIM_INTRO, SpearAnimLibrary.AIM, ageInTicks, false);
				}
			} else if (wasUsing) {
				setPhase(Phase.THROW, SpearAnimLibrary.THROW, ageInTicks, false);
			} else if (swingStarted) {
				setPhase(Phase.ATTACK, SpearAnimLibrary.ATTACK, ageInTicks, false);
			} else {
				setPhase(Phase.HOLD, SpearAnimLibrary.HOLDING, ageInTicks, true);
			}

			wasUsing = isUsing;
			wasSwinging = swinging;
		}

		void onClipFinished(float ageInTicks) {
			switch (phase) {
				case AIM_INTRO -> setPhase(Phase.AIM_HOLD, SpearAnimLibrary.AIM_HOLD, ageInTicks, true);
				case THROW -> {
					// Hand may already be empty (projectile left at halfway); drop state.
					phase = Phase.NONE;
					clipName = null;
					loop = false;
				}
				case ATTACK -> setPhase(Phase.HOLD, SpearAnimLibrary.HOLDING, ageInTicks, true);
				default -> {
				}
			}
		}

		void setPhase(Phase next, String clip, float ageInTicks, boolean loop) {
			if (phase == next && clip.equals(clipName)) {
				return;
			}
			this.phase = next;
			this.clipName = clip;
			this.clipStartAge = ageInTicks;
			this.loop = loop;
		}
	}
}
