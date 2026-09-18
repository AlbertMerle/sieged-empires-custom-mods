package com.voxmapsync.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.voxmapsync.server.MapSyncServer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server commands: {@code /mapsync}, {@code /mapsync render}, {@code /mapsync rerender},
 * {@code /mapsync fix}, {@code /mapsync render region}, {@code /mapsync rerender region},
 * {@code /mapsync render webmap}, {@code /mapsync rerender webmap}, {@code /mapsync stop}.
 */
public final class MapsyncCommand {
	private MapsyncCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				register(dispatcher));
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mapsync")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(MapsyncCommand::status)
				.then(Commands.literal("status")
						.executes(MapsyncCommand::status))
				.then(Commands.literal("audit")
						.executes(MapsyncCommand::audit))
				.then(Commands.literal("fix")
						.executes(MapsyncCommand::fix))
				.then(Commands.literal("stop")
						.executes(MapsyncCommand::stop))
				.then(Commands.literal("render")
						.executes(MapsyncCommand::render)
						.then(Commands.literal("region")
								.executes(MapsyncCommand::renderRegion))
						.then(Commands.literal("webmap")
								.executes(MapsyncCommand::renderWebmap)))
				.then(Commands.literal("rerender")
						.executes(MapsyncCommand::rerender)
						.then(Commands.literal("region")
								.executes(MapsyncCommand::rerenderRegion))
						.then(Commands.literal("webmap")
								.executes(MapsyncCommand::rerenderWebmap)))
				.then(Commands.literal("webmap")
						.then(Commands.literal("render")
								.executes(MapsyncCommand::renderWebmap))
						.then(Commands.literal("rerender")
								.executes(MapsyncCommand::rerenderWebmap))));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal(MapSyncServer.statusSummary()), false);
		source.sendSuccess(() -> Component.literal(
				"Usage: /mapsync render [region|webmap] | /mapsync rerender [region|webmap] | /mapsync fix | /mapsync audit | /mapsync stop"),
				false);
		return 1;
	}

	private static int audit(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal(MapSyncServer.auditSummary()), false);
		return 1;
	}

	private static int fix(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		boolean started = MapSyncServer.startFix(msg ->
				source.sendSuccess(() -> Component.literal(msg), true));
		if (started) {
			source.sendSuccess(() -> Component.literal(
					"Started VoxelMapSync border fix on the background worker (low priority, batched). "
							+ "Repairs missing server cache tiles inside the world border — players load them via viewport sync when panning the map. "
							+ "Use /mapsync status for progress; /mapsync stop to cancel."),
					true);
			return 1;
		}
		source.sendFailure(Component.literal(
				"Could not start fix — already running or VoxelMapSync not ready. Try /mapsync status or /mapsync stop."));
		return 0;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MapSyncServer.stopAll(msg -> source.sendSuccess(() -> Component.literal(msg), true));
		return 1;
	}

	private static int render(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		boolean started = MapSyncServer.startFullRender(msg ->
				source.sendSuccess(() -> Component.literal(msg), true));
		if (started) {
			source.sendSuccess(() -> Component.literal(
					"Started full VoxelMapSync render (background). Progress will report here."), true);
			return 1;
		}
		source.sendFailure(Component.literal(
				"Could not start render — already running or VoxelMapSync not ready. Try /mapsync status or /mapsync stop."));
		return 0;
	}

	private static int rerender(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		boolean started = MapSyncServer.startFullRerender(msg ->
				source.sendSuccess(() -> Component.literal(msg), true));
		if (started) {
			source.sendSuccess(() -> Component.literal(
					"Started full VoxelMapSync rerender (force overwrite + resend ≤ bandwidth). Progress will report here."),
					true);
			return 1;
		}
		source.sendFailure(Component.literal(
				"Could not start rerender — already running or VoxelMapSync not ready. Try /mapsync status or /mapsync stop."));
		return 0;
	}

	private static int renderWebmap(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		boolean started = MapSyncServer.startWebmapRender(msg ->
				source.sendSuccess(() -> Component.literal(msg), true));
		if (started) {
			source.sendSuccess(() -> Component.literal(
					"Started Web Map render (background). Progress will report here."), true);
			return 1;
		}
		source.sendFailure(Component.literal(
				"Could not start web map render — already running or VoxelMapSync not ready. Try /mapsync status or /mapsync stop."));
		return 0;
	}

	private static int rerenderWebmap(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		boolean started = MapSyncServer.startWebmapRerender(msg ->
				source.sendSuccess(() -> Component.literal(msg), true));
		if (started) {
			source.sendSuccess(() -> Component.literal(
					"Started Web Map rerender (force overwrite tiles + pyramids). Progress will report here."), true);
			return 1;
		}
		source.sendFailure(Component.literal(
				"Could not start web map rerender — already running or VoxelMapSync not ready. Try /mapsync status or /mapsync stop."));
		return 0;
	}

	private static int renderRegion(CommandContext<CommandSourceStack> ctx) {
		return runPlayerRegionCommand(ctx, false);
	}

	private static int rerenderRegion(CommandContext<CommandSourceStack> ctx) {
		return runPlayerRegionCommand(ctx, true);
	}

	private static int runPlayerRegionCommand(CommandContext<CommandSourceStack> ctx, boolean force) {
		CommandSourceStack source = ctx.getSource();
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("This command must be run by a player in-world."));
			return 0;
		}
		int regionX = (int) Math.floor(player.getX() / 256.0);
		int regionZ = (int) Math.floor(player.getZ() / 256.0);
		boolean started = force
				? MapSyncServer.startPlayerRegionRerender(player, msg ->
						source.sendSuccess(() -> Component.literal(msg), true))
				: MapSyncServer.startPlayerRegionRender(player, msg ->
						source.sendSuccess(() -> Component.literal(msg), true));
		if (started) {
			source.sendSuccess(() -> Component.literal(
					(force ? "Rerendering" : "Rendering") + " your current region "
							+ regionX + "," + regionZ + " (VoxelMap cache + web map). Progress will report here."),
					true);
			return 1;
		}
		source.sendFailure(Component.literal("Could not start region " + (force ? "rerender" : "render")
				+ " — VoxelMapSync not ready."));
		return 0;
	}
}
