package com.siegedempires.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * When connection is lost / kicked, vanilla falls back to the multiplayer
 * server list as the DisconnectedScreen parent. Prefer the title screen.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerImplMixin {
	@WrapOperation(
			method = "createDisconnectScreen",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/Objects;requireNonNullElseGet(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;"
			)
	)
	private Object siegedempires$preferTitleDisconnectParent(
			Object screen,
			Supplier<?> supplier,
			Operation<Object> original
	) {
		Object result = original.call(screen, supplier);
		if (result instanceof JoinMultiplayerScreen) {
			return new TitleScreen();
		}
		return result;
	}
}
