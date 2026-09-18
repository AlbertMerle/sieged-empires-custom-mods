package com.siegedempires.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.siegedempires.client.nametag.PlantStealthHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Player nametag tweaks:
 * <ul>
 *   <li>Force {@code seeThrough=false} so gamertags respect block occlusion
 *       (vanilla draws a SEE_THROUGH pass when not sneaking).</li>
 *   <li>Clear the nametag when the player is crawling (anywhere) or
 *       sneaking inside a 2-block-tall plant.</li>
 * </ul>
 */
@Mixin(EntityRenderer.class)
public class EntityRendererNametagMixin {
	@Inject(
			method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
			at = @At("TAIL")
	)
	private void siegedempires$hideNametagInPlantCover(
			Entity entity,
			EntityRenderState state,
			float partialTicks,
			double nameTagDistance,
			double belowNameDistance,
			CallbackInfo ci) {
		if (entity.getType() != EntityTypes.PLAYER) {
			return;
		}
		if (PlantStealthHelper.shouldHideNametag(entity)) {
			state.nameTag = null;
			state.scoreText = null;
		}
	}

	@ModifyArgs(
			method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
			)
	)
	private void siegedempires$noPlayerNametagThroughBlocks(
			Args args,
			EntityRenderState state,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			CameraRenderState camera,
			int offset) {
		if (state.entityType == EntityTypes.PLAYER) {
			args.set(4, false);
		}
	}
}
