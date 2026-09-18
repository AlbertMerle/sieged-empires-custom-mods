package com.weaponsmodaddon.client.mixin;

import com.weaponsmodaddon.client.anim.GunAnimClip;
import com.weaponsmodaddon.client.anim.GunAnimController;
import com.weaponsmodaddon.client.anim.SpearAnimController;
import com.weaponsmodaddon.ModItemTags;
import com.weaponsmodaddon.gun.GunAimState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Blockbench gun/pistol and spear/javelin clips after vanilla {@code setupAnim}.
 * Head + arm rotations (crawl absolute; standing head additive). FA compat jem clamps
 * pivots; while crawl/swim + gun it freezes FA torso wave and attach angles.
 * {@code crawling_firegun} also adds waist rotation. Standing ADS: waist follows look.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends HumanoidRenderState> {
	@Shadow
	@Final
	public ModelPart head;

	@Shadow
	@Final
	public ModelPart body;

	@Shadow
	@Final
	public ModelPart rightArm;

	@Shadow
	@Final
	public ModelPart leftArm;

	@Inject(
			method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
			at = @At("RETURN"),
			order = 10000
	)
	private void weaponsmodaddon$applyPlayerAnims(T state, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState avatar)) {
			return;
		}

		ItemStack main = state.getMainHandItemStack();
		boolean crawling = state.isVisuallySwimming;
		boolean crawlGunLock = crawling && main.is(ModItemTags.TWO_HANDED_GUNS);
		GunAnimClip.BonePose pose = GunAnimController.updateAndSample(
				avatar.id,
				state.ageInTicks,
				main,
				state.isUsingItem,
				state.mainArm,
				crawling
		);
		if (pose == null) {
			pose = SpearAnimController.updateAndSample(
					avatar.id,
					state.ageInTicks,
					main,
					state.isUsingItem,
					state.attackTime,
					state.mainArm,
					crawling
			);
		}
		if (pose == null) {
			return;
		}

		float lookYaw = this.head.yRot;

		GunAnimClip.BoneSample headSample = pose.bones.get("head");
		if (crawlGunLock) {
			// Absolute crawl head (do not stack on vanilla/FA swim pitch).
			if (headSample != null && headSample.rotation != null) {
				this.head.xRot = headSample.rotation.x;
				this.head.yRot = lookYaw + headSample.rotation.y;
				this.head.zRot = headSample.rotation.z;
			}
		} else if (headSample != null && headSample.rotation != null) {
			this.head.xRot += headSample.rotation.x;
			this.head.yRot += headSample.rotation.y;
			this.head.zRot += headSample.rotation.z;
		}

		applyRotation(pose, "rightArm", this.rightArm, false);
		applyRotation(pose, "leftArm", this.leftArm, false);
		if (pose.bones.containsKey("body")) {
			applyRotation(pose, "body", this.body, true);
		}

		if (crawlGunLock) {
			// Shoulders follow body (FA may shift body.t*); then add clip translation.
			// FA crawl_lock jem reads these into var.wm_*arm_t* (no FA swim pivot orbit).
			attachArmsToBody(state.ageScale);
			this.head.x = this.body.x;
			this.head.y = this.body.y;
			this.head.z = this.body.z;
			applyTranslation(pose, "rightArm", this.rightArm);
			applyTranslation(pose, "leftArm", this.leftArm);
			applyTranslation(pose, "head", this.head);
			return;
		}

		boolean aiming = state.isUsingItem
				&& (GunAimState.isReadyGun(main)
						|| main.is(ModItemTags.SPEARS)
						|| main.is(ModItemTags.JAVELINS));
		if (aiming) {
			this.body.yRot += lookYaw;
			this.rightArm.yRot += lookYaw;
			this.leftArm.yRot += lookYaw;
			orbitArmsAroundBody(state.ageScale);
		}
	}

	private void attachArmsToBody(float ageScale) {
		float reach = 5.0F * ageScale;
		float y = 2.0F * ageScale;
		this.rightArm.x = this.body.x - reach;
		this.rightArm.y = this.body.y + y;
		this.rightArm.z = this.body.z;
		this.leftArm.x = this.body.x + reach;
		this.leftArm.y = this.body.y + y;
		this.leftArm.z = this.body.z;
	}

	private void orbitArmsAroundBody(float ageScale) {
		float by = this.body.yRot;
		float reach = 5.0F * ageScale;
		this.rightArm.z = Mth.sin(by) * reach;
		this.rightArm.x = -Mth.cos(by) * reach;
		this.leftArm.z = -Mth.sin(by) * reach;
		this.leftArm.x = Mth.cos(by) * reach;
	}

	private static void applyRotation(
			GunAnimClip.BonePose pose, String name, ModelPart part, boolean additive
	) {
		GunAnimClip.BoneSample sample = pose.bones.get(name);
		if (sample == null || sample.rotation == null) {
			return;
		}
		if (additive) {
			part.xRot += sample.rotation.x;
			part.yRot += sample.rotation.y;
			part.zRot += sample.rotation.z;
		} else {
			part.xRot = sample.rotation.x;
			part.yRot = sample.rotation.y;
			part.zRot = sample.rotation.z;
		}
	}

	private static void applyTranslation(GunAnimClip.BonePose pose, String name, ModelPart part) {
		GunAnimClip.BoneSample sample = pose.bones.get(name);
		if (sample == null || sample.translation == null) {
			return;
		}
		part.x += sample.translation.x;
		part.y += sample.translation.y;
		part.z += sample.translation.z;
	}
}
