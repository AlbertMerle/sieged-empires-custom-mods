package com.distantnoise.client.mixin.weaponsmodaddon;

import com.distantnoise.network.GunShotReportPayload;
import com.distantnoise.sound.DistantNoiseKind;
import com.distantnoise.sound.GunKindResolver;
import com.weaponsmodaddon.client.gun.GunAimClient;
import com.weaponsmodaddon.network.GunFirePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** C2S gun-shot report alongside WeaponsMod Addon's {@link GunFirePayload}. */
@Mixin(GunAimClient.class)
public class GunAimClientRelayMixin {
	@Inject(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking;send(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
					ordinal = 0
			),
			remap = false
	)
	private static void distantnoise$reportGunShot(Minecraft client, CallbackInfo ci) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		ItemStack aimed = player.getUseItem();
		DistantNoiseKind kind = GunKindResolver.fromStack(aimed);
		if (kind == null) {
			return;
		}
		ClientPlayNetworking.send(new GunShotReportPayload(kind.id()));
	}
}
