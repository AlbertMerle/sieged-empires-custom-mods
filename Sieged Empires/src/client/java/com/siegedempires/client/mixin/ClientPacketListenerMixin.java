package com.siegedempires.client.mixin;

import com.siegedempires.client.gui.GuideBookScreen;
import com.siegedempires.guide.GuideBook;
import com.siegedempires.item.ModItems;
import com.siegedempires.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens the larger custom guide GUI instead of vanilla {@code BookViewScreen},
 * which clips most of the Sieged Empire Guide text.
 * <p>
 * Must run <em>after</em> {@code PacketUtils.ensureRunningOnSameThread} — a HEAD
 * inject would open the screen on the Netty IO thread and crash
 * ({@code Rendersystem called from wrong thread}).
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Shadow
	private ClientLevel level;

	@Redirect(
			method = "handleTakeItemEntity",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/sounds/SoundEvents;ITEM_PICKUP:Lnet/minecraft/sounds/SoundEvent;",
					opcode = Opcodes.GETSTATIC
			)
	)
	private SoundEvent siegedempires$goldCoinPickupSound(ClientboundTakeItemEntityPacket packet) {
		Entity from = this.level.getEntity(packet.getItemId());
		if (from instanceof ItemEntity itemEntity && itemEntity.getItem().is(ModItems.GOLD_COIN)) {
			return ModSounds.COIN_CRAFT.value();
		}
		return SoundEvents.ITEM_PICKUP;
	}

	@Inject(
			method = "handleOpenBook",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER
			),
			cancellable = true
	)
	private void siegedempires$openGuideBook(ClientboundOpenBookPacket packet, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		ItemStack stack = player.getItemInHand(packet.getHand());
		if (!GuideBook.isGuideBook(stack)) {
			return;
		}
		minecraft.gui.setScreen(new GuideBookScreen(stack));
		ci.cancel();
	}
}
