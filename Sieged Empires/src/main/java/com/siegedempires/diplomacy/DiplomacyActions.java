package com.siegedempires.diplomacy;

import com.siegedempires.data.DiplomacyDataManager;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.invasion.InvasionManager;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.mail.MailNotifications;
import com.siegedempires.network.DiplomacyBuilder;
import com.siegedempires.network.ModNetworking;
import com.siegedempires.util.ChatAnnouncements;
import com.siegedempires.util.NameValidator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-side diplomacy mutations (ally requests, trade, open borders).
 */
public final class DiplomacyActions {
	public static final String INVITE_ALLY = "ally";
	public static final String INVITE_TRADE = "trade";
	public static final String INVITE_OPEN_BORDERS = "open_borders";
	public static final String INVITE_PEACE = "peace";

	private DiplomacyActions() {
	}

	public record ManagedEntity(String entityType, String entityId, String entityName) {
		public String ref() {
			return DiplomacyRecord.ref(entityType, entityId);
		}
	}

	public static ManagedEntity resolveForPlayer(ServerPlayer player) {
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		if (town == null) {
			return null;
		}
		return resolveForTown(town);
	}

	public static ManagedEntity resolveForTown(TownData town) {
		if (town == null) {
			return null;
		}
		String empireId = town.getEmpireId();
		if (empireId != null && !empireId.isEmpty()) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
			if (empire != null) {
				return new ManagedEntity(DiplomacyRecord.TYPE_EMPIRE, empire.getId(), empire.getName());
			}
		}
		return new ManagedEntity(DiplomacyRecord.TYPE_TOWN, town.getId(), town.getName());
	}

	/**
	 * Whether citizens of {@code visitorTown}'s diplomatic entity may use citizen-level
	 * claim access inside {@code claimTown} under an active Open Borders agreement.
	 * Wartowns never grant open-borders access.
	 */
	public static boolean hasOpenBordersAccess(TownData visitorTown, TownData claimTown) {
		if (visitorTown == null || claimTown == null || claimTown.isWarTown() || visitorTown.isWarTown()) {
			return false;
		}
		ManagedEntity visitor = resolveForTown(visitorTown);
		ManagedEntity claim = resolveForTown(claimTown);
		if (visitor == null || claim == null || visitor.ref().equals(claim.ref())) {
			return false;
		}
		DiplomacyRecord visitorRecord = DiplomacyDataManager.getInstance().get(visitor.entityType(), visitor.entityId());
		if (visitorRecord != null && visitorRecord.getAllyOpenBorders().contains(claim.ref())) {
			return true;
		}
		DiplomacyRecord claimRecord = DiplomacyDataManager.getInstance().get(claim.entityType(), claim.entityId());
		return claimRecord != null && claimRecord.getAllyOpenBorders().contains(visitor.ref());
	}

	public static ManagedEntity resolveTarget(String targetType, String targetName) {
		if (DiplomacyRecord.TYPE_EMPIRE.equals(targetType)) {
			String id = NameValidator.toId(targetName);
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(id);
			if (empire == null) {
				return null;
			}
			return new ManagedEntity(DiplomacyRecord.TYPE_EMPIRE, empire.getId(), empire.getName());
		}
		TownData town = TownDataManager.getInstance().getTownByName(targetName);
		if (town == null) {
			return null;
		}
		String empireId = town.getEmpireId();
		if (empireId != null && !empireId.isEmpty()) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
			if (empire != null) {
				return new ManagedEntity(DiplomacyRecord.TYPE_EMPIRE, empire.getId(), empire.getName());
			}
		}
		return new ManagedEntity(DiplomacyRecord.TYPE_TOWN, town.getId(), town.getName());
	}

	public static String requestAlliance(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity target = resolveTarget(targetType, targetName);
		if (target == null) {
			return "That town or empire does not exist!";
		}
		if (self.ref().equals(target.ref())) {
			return "You cannot ally with yourself!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		DiplomacyRecord targetRecord = DiplomacyDataManager.getInstance().getOrCreate(target.entityType(), target.entityId());

		if (selfRecord.getAllies().contains(target.ref()) || targetRecord.getAllies().contains(self.ref())) {
			return "You are already allies!";
		}
		if (selfRecord.getEnemies().contains(target.ref()) || targetRecord.getEnemies().contains(self.ref())) {
			return "You cannot ally with an enemy!";
		}
		if (targetRecord.getPendingAllyInvites().contains(self.ref())) {
			return "They already have a pending invite from you!";
		}

		targetRecord.getPendingAllyInvites().add(self.ref());
		DiplomacyDataManager.getInstance().save(targetRecord);

		refreshFactionManagers(player, target);
		return null;
	}

	public static String acceptAlliance(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity sender = resolveTarget(targetType, targetName);
		if (sender == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getPendingAllyInvites().contains(sender.ref())) {
			return "You do not have an ally invite from them!";
		}

		addMutualAllies(self, sender);
		selfRecord.getPendingAllyInvites().remove(sender.ref());
		DiplomacyDataManager.getInstance().save(selfRecord);
		refreshFactionManagers(player, self, sender);
		mailOutcome(player, sender, INVITE_ALLY, true);
		return null;
	}

	public static String declineAlliance(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity sender = resolveTarget(targetType, targetName);
		if (sender == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getPendingAllyInvites().remove(sender.ref())) {
			return "You do not have an ally invite from them!";
		}

		DiplomacyDataManager.getInstance().save(selfRecord);
		refreshFactionManagers(player, self);
		mailOutcome(player, sender, INVITE_ALLY, false);
		return null;
	}

	public static String removeAlliance(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity target = resolveTarget(targetType, targetName);
		if (target == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		DiplomacyRecord targetRecord = DiplomacyDataManager.getInstance().getOrCreate(target.entityType(), target.entityId());

		if (!selfRecord.getAllies().contains(target.ref())) {
			return "They are not your ally!";
		}

		removeMutualAlliance(self, target, selfRecord, targetRecord);
		refreshFactionManagers(player, self, target);
		return null;
	}

	public static String requestTrade(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity target = resolveTarget(targetType, targetName);
		if (target == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getAllies().contains(target.ref())) {
			return "You can only request trade with allies!";
		}
		if (selfRecord.getAllyTrade().contains(target.ref())) {
			return "You already have a trade agreement!";
		}

		DiplomacyRecord targetRecord = DiplomacyDataManager.getInstance().getOrCreate(target.entityType(), target.entityId());
		if (targetRecord.getPendingTradeRequests().contains(self.ref())) {
			return "They already have a pending trade request from you!";
		}

		targetRecord.getPendingTradeRequests().add(self.ref());
		DiplomacyDataManager.getInstance().save(targetRecord);
		refreshFactionManagers(player, target);
		return null;
	}

	public static String acceptTrade(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity sender = resolveTarget(targetType, targetName);
		if (sender == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getPendingTradeRequests().remove(sender.ref())) {
			return "You do not have a trade request from them!";
		}

		addMutualTrade(self, sender);
		DiplomacyDataManager.getInstance().save(selfRecord);
		refreshFactionManagers(player, self, sender);
		mailOutcome(player, sender, INVITE_TRADE, true);
		return null;
	}

	public static String declineTrade(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity sender = resolveTarget(targetType, targetName);
		if (sender == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getPendingTradeRequests().remove(sender.ref())) {
			return "You do not have a trade request from them!";
		}

		DiplomacyDataManager.getInstance().save(selfRecord);
		refreshFactionManagers(player, self);
		mailOutcome(player, sender, INVITE_TRADE, false);
		return null;
	}

	public static String requestOpenBorders(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity target = resolveTarget(targetType, targetName);
		if (target == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getAllies().contains(target.ref())) {
			return "You can only request open borders with allies!";
		}
		if (selfRecord.getAllyOpenBorders().contains(target.ref())) {
			return "You already have open borders!";
		}

		DiplomacyRecord targetRecord = DiplomacyDataManager.getInstance().getOrCreate(target.entityType(), target.entityId());
		if (targetRecord.getPendingOpenBorderRequests().contains(self.ref())) {
			return "They already have a pending open borders request from you!";
		}

		targetRecord.getPendingOpenBorderRequests().add(self.ref());
		DiplomacyDataManager.getInstance().save(targetRecord);
		refreshFactionManagers(player, target);
		return null;
	}

	public static String acceptOpenBorders(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity sender = resolveTarget(targetType, targetName);
		if (sender == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getPendingOpenBorderRequests().remove(sender.ref())) {
			return "You do not have an open borders request from them!";
		}

		addMutualOpenBorders(self, sender);
		DiplomacyDataManager.getInstance().save(selfRecord);
		refreshFactionManagers(player, self, sender);
		mailOutcome(player, sender, INVITE_OPEN_BORDERS, true);
		return null;
	}

	public static String declareWar(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity target = resolveTarget(targetType, targetName);
		if (target == null) {
			return "That town or empire does not exist!";
		}
		if (self.ref().equals(target.ref())) {
			return "You cannot declare war on yourself!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		DiplomacyRecord targetRecord = DiplomacyDataManager.getInstance().getOrCreate(target.entityType(), target.entityId());

		if (selfRecord.getEnemies().contains(target.ref()) || targetRecord.getEnemies().contains(self.ref())) {
			return "You are already at war with them!";
		}

		if (selfRecord.getAllies().contains(target.ref())) {
			removeMutualAlliance(self, target, selfRecord, targetRecord);
			selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
			targetRecord = DiplomacyDataManager.getInstance().getOrCreate(target.entityType(), target.entityId());
		}

		addMutualEnemies(self, target, selfRecord, targetRecord);
		ChatAnnouncements.warDeclared(player, self.entityName(), target.entityName());
		MailNotifications.warDeclared(player.level().getServer(), self, target);
		refreshFactionManagers(player, self, target);
		return null;
	}

	public static String requestPeace(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity target = resolveTarget(targetType, targetName);
		if (target == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		DiplomacyRecord targetRecord = DiplomacyDataManager.getInstance().getOrCreate(target.entityType(), target.entityId());

		if (!selfRecord.getEnemies().contains(target.ref())) {
			return "They are not your enemy!";
		}
		if (InvasionManager.hasActiveInvasionBetween(self.ref(), target.ref())) {
			return "You cannot request peace during an active invasion!";
		}
		if (targetRecord.getPendingPeaceRequests().contains(self.ref())) {
			return "They already have a pending peace request from you!";
		}

		targetRecord.getPendingPeaceRequests().add(self.ref());
		DiplomacyDataManager.getInstance().save(targetRecord);
		refreshFactionManagers(player, target);
		return null;
	}

	public static String acceptPeace(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity sender = resolveTarget(targetType, targetName);
		if (sender == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getPendingPeaceRequests().remove(sender.ref())) {
			return "You do not have a peace request from them!";
		}

		DiplomacyRecord senderRecord = DiplomacyDataManager.getInstance().getOrCreate(sender.entityType(), sender.entityId());
		removeMutualEnemies(self, sender, selfRecord, senderRecord);
		DiplomacyDataManager.getInstance().save(selfRecord);
		refreshFactionManagers(player, self, sender);
		mailOutcome(player, sender, INVITE_PEACE, true);
		return null;
	}

	public static String declinePeace(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity sender = resolveTarget(targetType, targetName);
		if (sender == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getPendingPeaceRequests().remove(sender.ref())) {
			return "You do not have a peace request from them!";
		}

		DiplomacyDataManager.getInstance().save(selfRecord);
		refreshFactionManagers(player, self);
		mailOutcome(player, sender, INVITE_PEACE, false);
		return null;
	}

	public static String startInvasion(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity target = resolveTarget(targetType, targetName);
		if (target == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getEnemies().contains(target.ref())) {
			return "You can only invade an enemy!";
		}

		MinecraftServer server = player.level().getServer();
		int online = DiplomacyBuilder.countOnline(target.entityType(), target.entityId(), server);
		int required = com.siegedempires.config.ModSettings.get().invasionMinOnlinePlayers;
		if (online < required) {
			return "At least " + required + " players must be online in " + target.entityName() + " to invade!";
		}

		return com.siegedempires.invasion.InvasionManager.startInvasion(player, self, target);
	}

	public static String declineOpenBorders(ServerPlayer player, String targetType, String targetName) {
		ManagedEntity self = resolveForPlayer(player);
		if (self == null) {
			return "You are not in a town!";
		}
		if (!DiplomacyBuilder.canManageDiplomacy(player, TownDataManager.getInstance().getPlayerTown(player.getUUID()))) {
			return "You cannot manage diplomacy!";
		}

		ManagedEntity sender = resolveTarget(targetType, targetName);
		if (sender == null) {
			return "That town or empire does not exist!";
		}

		DiplomacyRecord selfRecord = DiplomacyDataManager.getInstance().getOrCreate(self.entityType(), self.entityId());
		if (!selfRecord.getPendingOpenBorderRequests().remove(sender.ref())) {
			return "You do not have an open borders request from them!";
		}

		DiplomacyDataManager.getInstance().save(selfRecord);
		refreshFactionManagers(player, self);
		mailOutcome(player, sender, INVITE_OPEN_BORDERS, false);
		return null;
	}

	private static void mailOutcome(ServerPlayer actor, ManagedEntity sender, String kind, boolean accepted) {
		ManagedEntity self = resolveForPlayer(actor);
		if (self != null && sender != null) {
			MailNotifications.diplomacyResponse(actor.level().getServer(), sender, self, kind, accepted);
		}
	}

	private static void addMutualAllies(ManagedEntity a, ManagedEntity b) {
		DiplomacyRecord recordA = DiplomacyDataManager.getInstance().getOrCreate(a.entityType(), a.entityId());
		DiplomacyRecord recordB = DiplomacyDataManager.getInstance().getOrCreate(b.entityType(), b.entityId());
		recordA.getAllies().add(b.ref());
		recordB.getAllies().add(a.ref());
		recordA.getEnemies().remove(b.ref());
		recordB.getEnemies().remove(a.ref());
		DiplomacyDataManager.getInstance().save(recordA);
		DiplomacyDataManager.getInstance().save(recordB);
	}

	private static void removeMutualAlliance(ManagedEntity a, ManagedEntity b,
	                                         DiplomacyRecord recordA, DiplomacyRecord recordB) {
		recordA.getAllies().remove(b.ref());
		recordB.getAllies().remove(a.ref());
		recordA.getAllyTrade().remove(b.ref());
		recordB.getAllyTrade().remove(a.ref());
		recordA.getAllyOpenBorders().remove(b.ref());
		recordB.getAllyOpenBorders().remove(a.ref());
		recordA.getPendingTradeRequests().remove(b.ref());
		recordB.getPendingTradeRequests().remove(a.ref());
		recordA.getPendingOpenBorderRequests().remove(b.ref());
		recordB.getPendingOpenBorderRequests().remove(a.ref());
		DiplomacyDataManager.getInstance().save(recordA);
		DiplomacyDataManager.getInstance().save(recordB);
	}

	private static void addMutualTrade(ManagedEntity a, ManagedEntity b) {
		DiplomacyRecord recordA = DiplomacyDataManager.getInstance().getOrCreate(a.entityType(), a.entityId());
		DiplomacyRecord recordB = DiplomacyDataManager.getInstance().getOrCreate(b.entityType(), b.entityId());
		recordA.getAllyTrade().add(b.ref());
		recordB.getAllyTrade().add(a.ref());
		DiplomacyDataManager.getInstance().save(recordA);
		DiplomacyDataManager.getInstance().save(recordB);
	}

	private static void addMutualEnemies(ManagedEntity a, ManagedEntity b,
	                                   DiplomacyRecord recordA, DiplomacyRecord recordB) {
		recordA.getEnemies().add(b.ref());
		recordB.getEnemies().add(a.ref());
		recordA.getPendingAllyInvites().remove(b.ref());
		recordB.getPendingAllyInvites().remove(a.ref());
		recordA.getPendingTradeRequests().remove(b.ref());
		recordB.getPendingTradeRequests().remove(a.ref());
		recordA.getPendingOpenBorderRequests().remove(b.ref());
		recordB.getPendingOpenBorderRequests().remove(a.ref());
		recordA.getPendingPeaceRequests().remove(b.ref());
		recordB.getPendingPeaceRequests().remove(a.ref());
		DiplomacyDataManager.getInstance().save(recordA);
		DiplomacyDataManager.getInstance().save(recordB);
	}

	private static void removeMutualEnemies(ManagedEntity a, ManagedEntity b,
	                                        DiplomacyRecord recordA, DiplomacyRecord recordB) {
		recordA.getEnemies().remove(b.ref());
		recordB.getEnemies().remove(a.ref());
		recordA.getPendingPeaceRequests().remove(b.ref());
		recordB.getPendingPeaceRequests().remove(a.ref());
		DiplomacyDataManager.getInstance().save(recordA);
		DiplomacyDataManager.getInstance().save(recordB);
	}

	private static void addMutualOpenBorders(ManagedEntity a, ManagedEntity b) {
		DiplomacyRecord recordA = DiplomacyDataManager.getInstance().getOrCreate(a.entityType(), a.entityId());
		DiplomacyRecord recordB = DiplomacyDataManager.getInstance().getOrCreate(b.entityType(), b.entityId());
		recordA.getAllyOpenBorders().add(b.ref());
		recordB.getAllyOpenBorders().add(a.ref());
		recordA.getAllyTrade().add(b.ref());
		recordB.getAllyTrade().add(a.ref());
		DiplomacyDataManager.getInstance().save(recordA);
		DiplomacyDataManager.getInstance().save(recordB);
	}

	private static void refreshFactionManagers(ServerPlayer actor, ManagedEntity... factions) {
		MinecraftServer server = actor.level().getServer();
		if (server == null) {
			return;
		}
		Set<String> refreshed = new HashSet<>();
		for (ManagedEntity faction : factions) {
			if (faction == null) {
				continue;
			}
			String key = faction.entityType() + ":" + faction.entityId();
			if (refreshed.add(key)) {
				ModNetworking.sendDiplomacyToFactionManagers(faction.entityType(), faction.entityId(), server);
			}
		}
	}
}
