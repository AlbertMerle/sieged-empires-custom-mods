package com.weaponsmodaddon.client.anim;

import ckathode.weaponmod.ReloadHelper;
import ckathode.weaponmod.ReloadHelper.ReloadState;
import ckathode.weaponmod.item.IItemWeapon;
import ckathode.weaponmod.item.RangedComponent;
import com.weaponsmodaddon.ModItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player state machine mapping WeaponMod reload/aim/fire to Blockbench clips.
 * While crawling, long-gun phases use {@code crawlanim.gltf} counterparts at the same speeds.
 */
public final class GunAnimController {
	private static final Map<Integer, PlayerAnimState> STATES = new ConcurrentHashMap<>();

	private GunAnimController() {
	}

	public static void clear() {
		STATES.clear();
	}

	/** Cancel ADS without playing the fire clip. */
	public static void notifyAimCancelled(int entityId) {
		PlayerAnimState state = STATES.get(entityId);
		if (state != null) {
			state.pendingSkipFire = true;
			state.wasReadyWhileUsing = false;
			state.wasUsing = false;
		}
	}

	public static GunAnimClip.BonePose updateAndSample(
			int entityId,
			float ageInTicks,
			ItemStack mainHand,
			boolean isUsingItem,
			HumanoidArm mainArm,
			boolean isCrawling
	) {
		WeaponKind kind = WeaponKind.of(mainHand);
		if (kind == WeaponKind.NONE) {
			STATES.remove(entityId);
			return null;
		}

		PlayerAnimState state = STATES.computeIfAbsent(entityId, id -> new PlayerAnimState());
		state.tick(ageInTicks, mainHand, isUsingItem, kind, isCrawling);

		if (state.clipName == null) {
			return null;
		}
		GunAnimClip clip = GunAnimLibrary.get(state.clipName);
		if (clip == null) {
			return null;
		}

		float speed = playbackSpeed(state, clip);
		float localSeconds = Math.max(0.0F, (ageInTicks - state.clipStartAge) / 20.0F) * speed;
		boolean loop = state.loop;

		if (!loop && localSeconds >= clip.durationSeconds) {
			state.onClipFinished(ageInTicks, kind, isCrawling);
			if (state.clipName == null) {
				return null;
			}
			clip = GunAnimLibrary.get(state.clipName);
			if (clip == null) {
				return null;
			}
			speed = playbackSpeed(state, clip);
			localSeconds = Math.max(0.0F, (ageInTicks - state.clipStartAge) / 20.0F) * speed;
			loop = state.loop;
		}

		GunAnimClip.BonePose pose = clip.sample(localSeconds, loop);
		// Legs stay FA/vanilla. Waist only for crawl fire recoil; strip body elsewhere
		// (standing gunfire JSON also has body keys that must not override FA torso).
		if (!GunAnimLibrary.CRAWL_FIRE.equals(state.clipName)) {
			pose.bones.remove("body");
		}
		if (mainArm == HumanoidArm.LEFT) {
			return mirrorPose(pose);
		}
		return pose;
	}

	/** Drop stale entries occasionally (entity ids reused / players leave). */
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

	private static float playbackSpeed(PlayerAnimState state, GunAnimClip clip) {
		if (state.phase == Phase.RELOAD && state.reloadSpeed > 0.0F) {
			return state.reloadSpeed;
		}
		return GunAnimLibrary.speedFor(state.clipName);
	}

	/**
	 * Stretch/compress reload clip so one play covers WeaponMod reload ticks.
	 * speed = clipDuration / reloadDurationSeconds
	 */
	static float reloadSpeedFor(ItemStack stack, GunAnimClip clip) {
		int ticks = reloadDurationTicks(stack);
		if (ticks <= 0 || clip == null || clip.durationSeconds <= 1.0E-4F) {
			return GunAnimLibrary.DEFAULT_PLAYBACK_SPEED;
		}
		float reloadSeconds = ticks / 20.0F;
		return clip.durationSeconds / reloadSeconds;
	}

	static int reloadDurationTicks(ItemStack stack) {
		if (stack.getItem() instanceof IItemWeapon weapon) {
			RangedComponent ranged = weapon.getRangedComponent();
			if (ranged != null) {
				return Math.max(1, ranged.getReloadDuration(stack));
			}
		}
		return 0;
	}

	private static GunAnimClip.BonePose mirrorPose(GunAnimClip.BonePose src) {
		GunAnimClip.BonePose out = new GunAnimClip.BonePose();
		for (Map.Entry<String, GunAnimClip.BoneSample> e : src.bones.entrySet()) {
			String bone = e.getKey();
			GunAnimClip.BoneSample s = e.getValue();
			String mirroredBone = switch (bone) {
				case "leftArm" -> "rightArm";
				case "rightArm" -> "leftArm";
				default -> bone;
			};
			GunAnimClip.Vec3 rot = s.rotation == null ? null : new GunAnimClip.Vec3(s.rotation.x, -s.rotation.y, -s.rotation.z);
			GunAnimClip.Vec3 pos = s.translation == null ? null : new GunAnimClip.Vec3(-s.translation.x, s.translation.y, s.translation.z);
			out.bones.put(mirroredBone, new GunAnimClip.BoneSample(rot, pos));
		}
		return out;
	}

	static String resolveGunClip(String standingClip, boolean crawling) {
		if (!crawling) {
			return standingClip;
		}
		return GunAnimLibrary.crawlVariant(standingClip);
	}

	enum WeaponKind {
		NONE,
		GUN,
		PISTOL;

		static WeaponKind of(ItemStack stack) {
			if (stack.isEmpty()) {
				return NONE;
			}
			if (stack.is(ModItemTags.TWO_HANDED_GUNS)) {
				return GUN;
			}
			if (stack.is(ModItemTags.PISTOLS)) {
				return PISTOL;
			}
			return NONE;
		}
	}

	enum Phase {
		NONE,
		HOLD,
		AIM_INTRO,
		AIM_HOLD,
		FIRE,
		RELOAD
	}

	static final class PlayerAnimState {
		Phase phase = Phase.NONE;
		String clipName;
		float clipStartAge;
		boolean loop;
		boolean wasUsing;
		boolean wasReadyWhileUsing;
		boolean pendingSkipFire;
		boolean wasCrawling;
		/** Playback multiplier for one-shot reload stretch; 0 = use library default. */
		float reloadSpeed;

		void tick(float ageInTicks, ItemStack stack, boolean isUsing, WeaponKind kind, boolean crawling) {
			ReloadState reload = ReloadHelper.getReloadState(stack);
			boolean ready = reload == ReloadState.STATE_READY;
			boolean reloading = isUsing && (reload == ReloadState.STATE_NONE || reload == ReloadState.STATE_RELOADED);
			boolean crawlGuns = crawling && kind == WeaponKind.GUN;

			if (phase == Phase.FIRE) {
				wasUsing = isUsing;
				if (isUsing) {
					wasReadyWhileUsing = ready;
				}
				// Swap fire clip if crawl toggled mid-shot (rare).
				if (kind == WeaponKind.GUN && crawlGuns != wasCrawling && clipName != null) {
					String next = resolveGunClip(GunAnimLibrary.GUN_FIRE, crawlGuns);
					if (!next.equals(clipName)) {
						clipName = next;
					}
				}
				wasCrawling = crawlGuns;
				return;
			}

			if (reloading) {
				if (phase != Phase.RELOAD || (kind == WeaponKind.GUN && crawlGuns != wasCrawling)) {
					String reloadName = kind == WeaponKind.GUN
							? resolveGunClip(GunAnimLibrary.GUN_RELOAD, crawlGuns)
							: GunAnimLibrary.GUN_RELOAD;
					GunAnimClip reloadClip = GunAnimLibrary.get(reloadName);
					reloadSpeed = reloadSpeedFor(stack, reloadClip);
					setPhase(Phase.RELOAD, reloadName, ageInTicks, false);
				}
			} else if (isUsing && ready) {
				reloadSpeed = 0.0F;
				if (phase != Phase.AIM_INTRO && phase != Phase.AIM_HOLD) {
					String intro = kind == WeaponKind.PISTOL
							? GunAnimLibrary.PISTOL_AIM
							: resolveGunClip(GunAnimLibrary.GUN_AIM, crawlGuns);
					setPhase(Phase.AIM_INTRO, intro, ageInTicks, false);
				} else if (kind == WeaponKind.GUN && crawlGuns != wasCrawling) {
					String standing = phase == Phase.AIM_HOLD ? GunAnimLibrary.GUN_HOLD_AIM : GunAnimLibrary.GUN_AIM;
					setPhase(phase, resolveGunClip(standing, crawlGuns), ageInTicks, phase == Phase.AIM_HOLD);
				}
			} else if (!isUsing && wasUsing && wasReadyWhileUsing) {
				reloadSpeed = 0.0F;
				if (pendingSkipFire) {
					pendingSkipFire = false;
					if (kind == WeaponKind.GUN) {
						setPhase(Phase.HOLD, resolveGunClip(GunAnimLibrary.GUN_HOLDING, crawlGuns), ageInTicks, true);
					} else {
						phase = Phase.NONE;
						clipName = null;
					}
				} else {
					String fire = kind == WeaponKind.PISTOL
							? GunAnimLibrary.PISTOL_FIRE
							: resolveGunClip(GunAnimLibrary.GUN_FIRE, crawlGuns);
					setPhase(Phase.FIRE, fire, ageInTicks, false);
				}
			} else if (!isUsing && kind == WeaponKind.GUN) {
				reloadSpeed = 0.0F;
				String hold = resolveGunClip(GunAnimLibrary.GUN_HOLDING, crawlGuns);
				if (phase != Phase.HOLD || !hold.equals(clipName) || crawlGuns != wasCrawling) {
					setPhase(Phase.HOLD, hold, ageInTicks, true);
				}
			} else if (!isUsing) {
				reloadSpeed = 0.0F;
				phase = Phase.NONE;
				clipName = null;
			}

			wasUsing = isUsing;
			wasCrawling = crawlGuns;
			if (isUsing) {
				wasReadyWhileUsing = ready;
			}
		}

		void onClipFinished(float ageInTicks, WeaponKind kind, boolean crawling) {
			boolean crawlGuns = crawling && kind == WeaponKind.GUN;
			switch (phase) {
				case AIM_INTRO -> {
					String hold = kind == WeaponKind.PISTOL
							? GunAnimLibrary.PISTOL_AIM_HOLD
							: resolveGunClip(GunAnimLibrary.GUN_HOLD_AIM, crawlGuns);
					setPhase(Phase.AIM_HOLD, hold, ageInTicks, true);
				}
				case FIRE -> {
					if (kind == WeaponKind.GUN) {
						setPhase(Phase.HOLD, resolveGunClip(GunAnimLibrary.GUN_HOLDING, crawlGuns), ageInTicks, true);
					} else {
						phase = Phase.NONE;
						clipName = null;
					}
				}
				case RELOAD -> {
					// Hold last reload frame until use ends / state leaves reload.
				}
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
