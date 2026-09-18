package com.siegedempires.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.siegedempires.diplomacy.DiplomacyActions;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.network.ModNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DiplomacyCommand {
	private DiplomacyCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("diplomacy")
				.then(Commands.literal("allyrequest")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> allyRequest(ctx, true)))))
				.then(Commands.literal("allyaccept")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> allyAccept(ctx, true)))))
				.then(Commands.literal("allydecline")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> allyDecline(ctx, true)))))
				.then(Commands.literal("allyremove")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> allyRemove(ctx, true)))))
				.then(Commands.literal("traderequest")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> tradeRequest(ctx, true)))))
				.then(Commands.literal("tradeaccept")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> tradeAccept(ctx, true)))))
				.then(Commands.literal("tradedecline")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> tradeDecline(ctx, true)))))
				.then(Commands.literal("bordersrequest")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> bordersRequest(ctx, true)))))
				.then(Commands.literal("bordersaccept")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> bordersAccept(ctx, true)))))
				.then(Commands.literal("bordersdecline")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> bordersDecline(ctx, true)))))
				.then(Commands.literal("declarewar")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> declareWar(ctx, true)))))
				.then(Commands.literal("peacerequest")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> peaceRequest(ctx, true)))))
				.then(Commands.literal("peaceaccept")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> peaceAccept(ctx, true)))))
				.then(Commands.literal("peacedecline")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> peaceDecline(ctx, true)))))
				.then(Commands.literal("invasionstart")
						.then(Commands.argument("type", StringArgumentType.word())
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> invasionStart(ctx, true))))));
	}

	private static int allyRequest(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::requestAlliance);
	}

	private static int allyAccept(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::acceptAlliance);
	}

	private static int allyDecline(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::declineAlliance);
	}

	private static int allyRemove(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::removeAlliance);
	}

	private static int tradeRequest(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::requestTrade);
	}

	private static int tradeAccept(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::acceptTrade);
	}

	private static int tradeDecline(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::declineTrade);
	}

	private static int bordersRequest(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::requestOpenBorders);
	}

	private static int bordersAccept(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::acceptOpenBorders);
	}

	private static int bordersDecline(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::declineOpenBorders);
	}

	private static int declareWar(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::declareWar);
	}

	private static int peaceRequest(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::requestPeace);
	}

	private static int peaceAccept(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::acceptPeace);
	}

	private static int peaceDecline(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::declinePeace);
	}

	private static int invasionStart(CommandContext<CommandSourceStack> ctx, boolean refresh) {
		return run(ctx, refresh, DiplomacyActions::startInvasion);
	}

	@FunctionalInterface
	private interface Action {
		String run(ServerPlayer player, String type, String name);
	}

	private static int run(CommandContext<CommandSourceStack> ctx, boolean refresh, Action action) {
		CommandSourceStack source = ctx.getSource();
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can manage diplomacy!"));
			return 0;
		}

		String type = normalizeType(StringArgumentType.getString(ctx, "type"));
		String name = StringArgumentType.getString(ctx, "name").trim().replace("_", " ");
		if (type == null) {
			source.sendFailure(Component.literal("Type must be 'town' or 'empire'!"));
			return 0;
		}

		String error = action.run(player, type, name);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}

		if (refresh) {
			ModNetworking.sendDiplomacy(player);
		}
		return 1;
	}

	private static String normalizeType(String raw) {
		if (DiplomacyRecord.TYPE_TOWN.equalsIgnoreCase(raw)) {
			return DiplomacyRecord.TYPE_TOWN;
		}
		if (DiplomacyRecord.TYPE_EMPIRE.equalsIgnoreCase(raw)) {
			return DiplomacyRecord.TYPE_EMPIRE;
		}
		return null;
	}
}
