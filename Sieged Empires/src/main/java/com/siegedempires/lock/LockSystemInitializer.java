package com.siegedempires.lock;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;

public final class LockSystemInitializer {

    public static void register() {
        LockpickSessionManager.register();

        // Interact with locked blocks / place locks / use keys
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            InteractionResult r = LockInteractionHandler.onUseBlock(player, level, hand, hit);
            if (r != InteractionResult.PASS) return r;
            return InteractionResult.PASS;
        });
    }
}