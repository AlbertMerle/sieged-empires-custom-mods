package com.siegedempires.network;

import com.siegedempires.banner.BannerManager;
import com.siegedempires.banner.InventoryBannerConverter;
import com.siegedempires.command.EmpireCommand;
import com.siegedempires.command.TownCommand;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.data.PendingCrownManager;
import com.siegedempires.data.PlayerMailManager;
import com.siegedempires.data.WartownManagementContext;
import com.siegedempires.lock.LockDataManager;
import com.siegedempires.model.TownData;
import com.siegedempires.network.payload.ConvertInventoryBannersPayload;
import com.siegedempires.network.payload.CreateEmpirePayload;
import com.siegedempires.network.payload.CreateTownPayload;
import com.siegedempires.network.payload.EmpireListPayload;
import com.siegedempires.network.payload.GuiStatusPayload;
import com.siegedempires.network.payload.JoinListPayload;
import com.siegedempires.block.LocksmithingTableMenu;
import com.siegedempires.network.payload.LockSyncPayload;
import com.siegedempires.network.payload.LocksmithingUpdatePayload;
import com.siegedempires.network.payload.ManageEmpirePayload;
import com.siegedempires.network.payload.ManageTownPayload;
import com.siegedempires.network.payload.RequestEmpireListPayload;
import com.siegedempires.network.payload.RequestGuiStatusPayload;
import com.siegedempires.network.payload.RequestJoinListPayload;
import com.siegedempires.network.payload.DiplomacyPayload;
import com.siegedempires.network.payload.RequestDiplomacyPayload;
import com.siegedempires.network.payload.RequestMailPayload;
import com.siegedempires.network.payload.MailPayload;
import com.siegedempires.network.payload.MailActionPayload;
import com.siegedempires.network.payload.RequestManageEmpirePayload;
import com.siegedempires.network.payload.RequestManageTownPayload;
import com.siegedempires.network.payload.RenameTownPayload;
import com.siegedempires.network.payload.RenameTownResultPayload;
import com.siegedempires.network.payload.UpdateTownPayload;
import com.siegedempires.network.payload.UpdateEmpirePayload;
import com.siegedempires.network.payload.UpdateEmpireResultPayload;
import com.siegedempires.network.payload.RequestManageWartownPayload;
import com.siegedempires.network.payload.RequestClearWartownContextPayload;
import com.siegedempires.network.payload.RequestCrownWartownMonarchPayload;
import com.siegedempires.network.payload.PromptDukeDuchessPayload;
import com.siegedempires.network.payload.ChooseDukeDuchessPayload;
import com.siegedempires.network.payload.RequestPendingDukeDuchessPayload;
import com.siegedempires.network.payload.ConfirmSessionJoinPayload;
import com.siegedempires.network.payload.RequestSessionReleasePayload;
import com.siegedempires.network.payload.SessionCinematicPayload;
import com.siegedempires.network.payload.SessionGateStartPayload;
import com.siegedempires.network.payload.SessionReleasedPayload;
import com.siegedempires.mail.MailActions;
import com.siegedempires.mail.MailNotifications;
import com.siegedempires.network.MailBuilder;
import com.siegedempires.session.SessionGateManager;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class ModNetworking {
	private static final Gson GSON = new Gson();

	private ModNetworking() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.serverboundPlay().register(RequestGuiStatusPayload.TYPE, RequestGuiStatusPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestJoinListPayload.TYPE, RequestJoinListPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GuiStatusPayload.TYPE, GuiStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(JoinListPayload.TYPE, JoinListPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestManageTownPayload.TYPE, RequestManageTownPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ManageTownPayload.TYPE, ManageTownPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestEmpireListPayload.TYPE, RequestEmpireListPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EmpireListPayload.TYPE, EmpireListPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestManageEmpirePayload.TYPE, RequestManageEmpirePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ManageEmpirePayload.TYPE, ManageEmpirePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestDiplomacyPayload.TYPE, RequestDiplomacyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DiplomacyPayload.TYPE, DiplomacyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LockSyncPayload.TYPE, LockSyncPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LocksmithingUpdatePayload.TYPE, LocksmithingUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SessionGateStartPayload.TYPE, SessionGateStartPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ConfirmSessionJoinPayload.TYPE, ConfirmSessionJoinPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SessionCinematicPayload.TYPE, SessionCinematicPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestSessionReleasePayload.TYPE, RequestSessionReleasePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SessionReleasedPayload.TYPE, SessionReleasedPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CreateEmpirePayload.TYPE, CreateEmpirePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CreateTownPayload.TYPE, CreateTownPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ConvertInventoryBannersPayload.TYPE, ConvertInventoryBannersPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RenameTownPayload.TYPE, RenameTownPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RenameTownResultPayload.TYPE, RenameTownResultPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateTownPayload.TYPE, UpdateTownPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateEmpirePayload.TYPE, UpdateEmpirePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UpdateEmpireResultPayload.TYPE, UpdateEmpireResultPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestManageWartownPayload.TYPE, RequestManageWartownPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestClearWartownContextPayload.TYPE, RequestClearWartownContextPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestCrownWartownMonarchPayload.TYPE, RequestCrownWartownMonarchPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PromptDukeDuchessPayload.TYPE, PromptDukeDuchessPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ChooseDukeDuchessPayload.TYPE, ChooseDukeDuchessPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestPendingDukeDuchessPayload.TYPE,
				RequestPendingDukeDuchessPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestMailPayload.TYPE, RequestMailPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MailPayload.TYPE, MailPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MailActionPayload.TYPE, MailActionPayload.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(RequestGuiStatusPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendGuiStatus(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestJoinListPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendJoinList(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestManageTownPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendManageTown(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestEmpireListPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendEmpireList(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestManageEmpirePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendManageEmpire(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestDiplomacyPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendDiplomacy(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestMailPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendMail(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(MailActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				String error = switch (payload.action()) {
					case MailActionPayload.ACTION_ACCEPT -> MailActions.accept(
							player, payload.mailType(), payload.targetId(),
							payload.entityType(), payload.entityName());
					case MailActionPayload.ACTION_DECLINE -> MailActions.decline(
							player, payload.mailType(), payload.targetId(),
							payload.entityType(), payload.entityName());
					case MailActionPayload.ACTION_DISMISS -> MailActions.dismiss(player, payload.targetId());
					default -> "Unknown mail action!";
				};
				if (error != null) {
					player.sendSystemMessage(Component.literal(error));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(LocksmithingUpdatePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				if (player.containerMenu instanceof LocksmithingTableMenu menu) {
					menu.applyClientFields(payload.rename(), payload.password());
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(ConfirmSessionJoinPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> SessionGateManager.beginCinematic(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestSessionReleasePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> SessionGateManager.release(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(CreateEmpirePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				String error = EmpireCommand.tryCreateEmpire(
						player,
						payload.name(),
						payload.description(),
						payload.title(),
						payload.bannerBaseColor(),
						splitPatterns(payload.bannerPatternsJoined()),
						payload.bannerPixels());
				if (error != null) {
					player.sendSystemMessage(Component.literal(error));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(CreateTownPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				String error = TownCommand.tryCreateTown(
						player,
						payload.name(),
						payload.description(),
						payload.monarchTitle(),
						payload.bannerBaseColor(),
						splitPatterns(payload.bannerPatternsJoined()),
						payload.bannerPixels());
				if (error != null) {
					player.sendSystemMessage(Component.literal(error));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(ConvertInventoryBannersPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				String error = InventoryBannerConverter.convert(player, payload.factionKind());
				if (error != null) {
					player.sendSystemMessage(Component.literal(error));
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RenameTownPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				String error = TownCommand.tryRenameTown(player, payload.newName());
				if (error != null) {
					ServerPlayNetworking.send(player, new RenameTownResultPayload(false, error));
					return;
				}
				ServerPlayNetworking.send(player, new RenameTownResultPayload(true, ""));
				sendManageTown(player);
				sendDiplomacy(player);
				sendGuiStatus(player);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(UpdateTownPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				String error = TownCommand.tryUpdateTown(
						player,
						payload.name(),
						payload.bannerBaseColor(),
						splitPatterns(payload.bannerPatternsJoined()),
						payload.bannerPixels(),
						false);
				if (error != null) {
					ServerPlayNetworking.send(player, new RenameTownResultPayload(false, error));
					return;
				}
				ServerPlayNetworking.send(player, new RenameTownResultPayload(true, ""));
				sendManageTown(player);
				sendDiplomacy(player);
				sendGuiStatus(player);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(UpdateEmpirePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				String error = EmpireCommand.tryUpdateEmpire(
						player,
						payload.name(),
						payload.bannerBaseColor(),
						splitPatterns(payload.bannerPatternsJoined()),
						payload.bannerPixels());
				if (error != null) {
					ServerPlayNetworking.send(player, new UpdateEmpireResultPayload(false, error));
					return;
				}
				ServerPlayNetworking.send(player, new UpdateEmpireResultPayload(true, ""));
				sendManageEmpire(player);
				sendDiplomacy(player);
				sendGuiStatus(player);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestManageWartownPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendManageWartown(player, payload.wartownId()));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestClearWartownContextPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> WartownManagementContext.clear(player.getUUID()));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestCrownWartownMonarchPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> handleCrownRequest(player, payload.wartownId(), payload.targetUuid()));
		});

		ServerPlayNetworking.registerGlobalReceiver(ChooseDukeDuchessPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> handleDukeDuchessChoice(player, payload.wartownId(), payload.monarchTitle()));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestPendingDukeDuchessPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendPendingDukeDuchessPrompt(player));
		});
	}

	private static List<String> splitPatterns(String joined) {
		List<String> patterns = new ArrayList<>();
		if (joined == null || joined.isEmpty()) {
			return patterns;
		}
		for (String part : joined.split(";", -1)) {
			if (!part.isEmpty()) {
				patterns.add(part);
			}
		}
		return patterns;
	}

	public static void sendGuiStatus(ServerPlayer player) {
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		boolean inTown = town != null;
		boolean isMonarch = inTown && town.getMonarchUuid().equals(player.getUUID());
		boolean isLord = inTown && town.isLord(player.getUUID());
		boolean isEmperor = EmpireDataManager.getInstance().isEmperor(player.getUUID());
		boolean canAccessDiplomacy = DiplomacyBuilder.canManageDiplomacy(player, town);
		ServerPlayNetworking.send(player, new GuiStatusPayload(
				inTown, isMonarch, isLord, isEmperor, canAccessDiplomacy));
	}

	public static void sendJoinList(ServerPlayer player) {
		String json = JoinListBuilder.toJson(JoinListBuilder.build(player.getUUID()));
		ServerPlayNetworking.send(player, new JoinListPayload(json));
	}

	public static void sendManageTown(ServerPlayer player) {
		String json = ManageTownBuilder.toJson(ManageTownBuilder.build(player, BannerManager.getServer()));
		ServerPlayNetworking.send(player, new ManageTownPayload(json));
	}

	public static void sendEmpireList(ServerPlayer player) {
		String json = EmpireListBuilder.toJson(EmpireListBuilder.build(player.getUUID()));
		ServerPlayNetworking.send(player, new EmpireListPayload(json));
	}

	public static void sendManageEmpire(ServerPlayer player) {
		String json = ManageEmpireBuilder.toJson(ManageEmpireBuilder.build(player));
		ServerPlayNetworking.send(player, new ManageEmpirePayload(json));
	}

	public static void sendManageWartown(ServerPlayer player, String wartownId) {
		TownData wartown = TownDataManager.getInstance().getTown(wartownId);
		if (wartown == null || !wartown.isWarTown()) {
			WartownManagementContext.clear(player.getUUID());
			return;
		}
		var empire = EmpireDataManager.getInstance().getEmpire(wartown.getEmpireId());
		if (empire == null || !empire.getEmperorUuid().equals(player.getUUID())) {
			WartownManagementContext.clear(player.getUUID());
			return;
		}
		WartownManagementContext.set(player.getUUID(), wartownId);
		String json = ManageTownBuilder.toJson(
				ManageTownBuilder.buildForWartown(player, BannerManager.getServer(), wartownId));
		ServerPlayNetworking.send(player, new ManageTownPayload(json));
	}

	private static void handleCrownRequest(ServerPlayer emperor, String wartownId, String targetUuidStr) {
		TownData wartown = TownDataManager.getInstance().getTown(wartownId);
		if (wartown == null || !wartown.isWarTown()) {
			emperor.sendSystemMessage(Component.literal("Wartown not found!"));
			return;
		}
		var empire = EmpireDataManager.getInstance().getEmpire(wartown.getEmpireId());
		if (empire == null || !empire.getEmperorUuid().equals(emperor.getUUID())) {
			emperor.sendSystemMessage(Component.literal("Only the Emperor can crown a Duke/Duchess!"));
			return;
		}
		java.util.UUID targetUuid;
		try {
			targetUuid = java.util.UUID.fromString(targetUuidStr);
		} catch (IllegalArgumentException e) {
			emperor.sendSystemMessage(Component.literal("Invalid player!"));
			return;
		}
		if (!TownDataManager.getInstance().isPlayerInEmpire(targetUuid, empire.getId())) {
			emperor.sendSystemMessage(Component.literal("That player is not in your empire!"));
			return;
		}
		ServerPlayer target = emperor.level().getServer().getPlayerList().getPlayer(targetUuid);
		if (target == null) {
			String error = TownDataManager.getInstance().assignWartownMonarchWithoutTitle(wartownId, targetUuid);
			if (error != null) {
				emperor.sendSystemMessage(Component.literal(error));
				return;
			}
			String targetName = TownDataManager.getInstance().resolveEmpirePlayerNameForMessage(targetUuid, wartown);
			PendingCrownManager.set(targetUuid, new PendingCrownManager.CrownPrompt(
					wartownId, wartown.getName(), emperor.getName().getString(), emperor.getUUID(), true));
			emperor.sendSystemMessage(Component.literal(
					(targetName != null ? targetName : "That player")
							+ " is now monarch of " + wartown.getName()
							+ ". They will choose Duke or Duchess when they log in."));
			sendManageWartown(emperor, wartownId);
			return;
		}
		ServerPlayNetworking.send(target, new PromptDukeDuchessPayload(
				wartownId, wartown.getName(), emperor.getName().getString()));
		PendingCrownManager.set(targetUuid, new PendingCrownManager.CrownPrompt(
				wartownId, wartown.getName(), emperor.getName().getString(), emperor.getUUID()));
		sendMail(target);
		emperor.sendSystemMessage(Component.literal(
				"Asked " + target.getName().getString() + " to accept the crown of " + wartown.getName() + "."));
	}

	private static void handleDukeDuchessChoice(ServerPlayer target, String wartownId, String monarchTitle) {
		PendingCrownManager.CrownPrompt prompt = PendingCrownManager.get(target.getUUID());
		String error;
		TownData wartown = TownDataManager.getInstance().getTown(wartownId);
		boolean titleOnly = prompt != null && prompt.monarchAssigned()
				|| (wartown != null && target.getUUID().equals(wartown.getMonarchUuid())
				&& (wartown.getMonarchTitle() == null || wartown.getMonarchTitle().isEmpty()));
		if (titleOnly) {
			error = TownDataManager.getInstance().completeWartownMonarchTitle(wartownId, target.getUUID(), monarchTitle);
		} else {
			error = TownDataManager.getInstance().crownWartownMonarch(wartownId, target.getUUID(), monarchTitle);
		}
		if (error != null) {
			target.sendSystemMessage(Component.literal(error));
			return;
		}
		PendingCrownManager.clear(target.getUUID());
		if (prompt != null) {
			MailNotifications.crownResponse(target.level().getServer(), prompt.emperorUuid(),
					target.getName().getString(), prompt.wartownName(), true);
		}
		sendMail(target);
		wartown = TownDataManager.getInstance().getTown(wartownId);
		String displayTitle = "Queen".equals(monarchTitle) ? "Duchess" : "Duke";
		target.sendSystemMessage(Component.literal(
				"You are now " + displayTitle + " of " + (wartown != null ? wartown.getName() : "the wartown") + "!"));
		if (wartown != null) {
			var empire = EmpireDataManager.getInstance().getEmpire(wartown.getEmpireId());
			if (empire != null) {
				ServerPlayer emperor = target.level().getServer().getPlayerList().getPlayer(empire.getEmperorUuid());
				if (emperor != null) {
					emperor.sendSystemMessage(Component.literal(
							target.getName().getString() + " is now " + displayTitle + " of " + wartown.getName() + "!"));
					sendManageWartown(emperor, wartownId);
				}
			}
		}
	}

	public static void sendPendingDukeDuchessPrompt(ServerPlayer player) {
		PendingCrownManager.CrownPrompt prompt = PendingCrownManager.resolveForPlayer(player.getUUID());
		if (prompt == null) {
			return;
		}
		ServerPlayNetworking.send(player, new PromptDukeDuchessPayload(
				prompt.wartownId(), prompt.wartownName(), prompt.emperorName()));
	}

	public static void sendDiplomacy(ServerPlayer player) {
		String json = DiplomacyBuilder.toJson(DiplomacyBuilder.build(player));
		ServerPlayNetworking.send(player, new DiplomacyPayload(json));
	}

	public static void sendMail(ServerPlayer player) {
		String json = MailBuilder.toJson(MailBuilder.build(player));
		ServerPlayNetworking.send(player, new MailPayload(json));
	}

	/** Push fresh diplomacy data to online monarchs/emperors managing a faction. */
	public static void sendDiplomacyToFactionManagers(String entityType, String entityId,
	                                                  net.minecraft.server.MinecraftServer server) {
		if (server == null || entityType == null || entityId == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
			if (town == null) {
				continue;
			}
			com.siegedempires.diplomacy.DiplomacyActions.ManagedEntity managed =
					com.siegedempires.diplomacy.DiplomacyActions.resolveForTown(town);
			if (managed != null
					&& entityType.equals(managed.entityType())
					&& entityId.equals(managed.entityId())
					&& DiplomacyBuilder.canManageDiplomacy(player, town)) {
				sendDiplomacy(player);
				sendMail(player);
			}
		}
	}

	/**
	 * Send lock positions to a player so the client can render lock overlays.
	 */
	public static void syncLocksToPlayer(ServerPlayer player) {
		var locksByDim = LockDataManager.getAllLockedPositions();
		String json = GSON.toJson(locksByDim);
		ServerPlayNetworking.send(player, new LockSyncPayload(json));
	}

	/**
	 * Broadcast full lock state to all players in a level.
	 * Always sends every dimension so the client cache is not wiped to a single dim.
	 */
	public static void broadcastLockSync(ServerLevel level) {
		String json = GSON.toJson(LockDataManager.getAllLockedPositions());
		var payload = new LockSyncPayload(json);
		for (ServerPlayer p : level.players()) {
			ServerPlayNetworking.send(p, payload);
		}
	}
}
