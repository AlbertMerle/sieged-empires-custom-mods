package com.tertonbiome.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tertonbiome.RegionBiomeFixer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin commands:
 * <ul>
 *   <li>{@code /terrabiome fix region} — caller's 256×256 map region</li>
 *   <li>{@code /terrabiome fix <fromZ> to <toZ>} — full-X strip inside world border</li>
 *   <li>{@code /terrabiome fix status} / {@code stop}</li>
 * </ul>
 */
public final class TerrabiomeCommand {
	private TerrabiomeCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			register(dispatcher));
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("terrabiome")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("fix")
				.then(Commands.literal("region")
					.executes(TerrabiomeCommand::fixRegion))
				.then(Commands.literal("status")
					.executes(TerrabiomeCommand::fixStatus))
				.then(Commands.literal("stop")
					.executes(TerrabiomeCommand::fixStop))
				.then(Commands.argument("fromZ", IntegerArgumentType.integer())
					.then(Commands.literal("to")
						.then(Commands.argument("toZ", IntegerArgumentType.integer())
							.executes(TerrabiomeCommand::fixZStrip))))));
	}

	private static int fixRegion(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Run /terrabiome fix region as a player in the target region."));
			return 0;
		}
		if (RegionBiomeFixer.isRunning()) {
			source.sendFailure(Component.literal("A Terrabiome fix is already running. Use /terrabiome fix status or stop."));
			return 0;
		}

		int regionX = (int) Math.floor(player.getX() / 256.0);
		int regionZ = (int) Math.floor(player.getZ() / 256.0);
		return startFix(source, level -> RegionBiomeFixer.startRegionFix(
			level,
			regionX,
			regionZ,
			msg -> source.sendSuccess(() -> msg, true)
		));
	}

	private static int fixZStrip(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if (RegionBiomeFixer.isRunning()) {
			source.sendFailure(Component.literal("A Terrabiome fix is already running. Use /terrabiome fix status or stop."));
			return 0;
		}

		int fromZ = IntegerArgumentType.getInteger(ctx, "fromZ");
		int toZ = IntegerArgumentType.getInteger(ctx, "toZ");
		return startFix(source, level -> RegionBiomeFixer.startZStripFix(
			level,
			fromZ,
			toZ,
			msg -> source.sendSuccess(() -> msg, true)
		));
	}

	private static int fixStatus(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal(RegionBiomeFixer.statusSummary()), false);
		source.sendSuccess(() -> Component.literal(
			"Usage: /terrabiome fix region | /terrabiome fix <fromZ> to <toZ> | /terrabiome fix stop"
		), false);
		return 1;
	}

	private static int fixStop(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if (!RegionBiomeFixer.stop(msg -> source.sendSuccess(() -> msg, true))) {
			source.sendFailure(Component.literal("No Terrabiome fix is running."));
			return 0;
		}
		return 1;
	}

	private static int startFix(CommandSourceStack source, java.util.function.Function<ServerLevel, Boolean> starter) {
		ServerLevel level = source.getLevel();
		boolean started = starter.apply(level);
		if (!started) {
			source.sendFailure(Component.literal("Could not start Terrabiome fix."));
			return 0;
		}
		return 1;
	}
}
