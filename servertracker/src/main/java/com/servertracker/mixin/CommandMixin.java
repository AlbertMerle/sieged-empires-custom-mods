package com.servertracker.mixin;

import com.mojang.brigadier.ParseResults;
import com.servertracker.Servertracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandMixin {
	@Inject(method = "performCommand", at = @At("HEAD"))
	private void servertracker$logPlayerCommand(
			ParseResults<CommandSourceStack> parseResults,
			String command,
			CallbackInfo callbackInfo
	) {
		if (parseResults.getContext().getSource().getEntity() instanceof ServerPlayer player) {
			Servertracker.logCommand(player, command);
		}
	}
}
