package com.siegedempires.data;

import com.siegedempires.banner.BannerManager;
import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.Siegedempires;
import com.siegedempires.model.ChunkPosition;
import com.siegedempires.model.TownData;
import com.siegedempires.network.ManageTownData;
import com.siegedempires.util.NameValidator;
import com.siegedempires.util.PlayerPrefixManager;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class TownDataManager {
    private static TownDataManager INSTANCE;
    
    private final Map<String, TownData> townsById;
    private final Map<UUID, TownData> townsByPlayer;
    private final Map<ChunkPosition, String> townIdByChunk;
    
    private TownDataManager() {
        this.townsById = new HashMap<>();
        this.townsByPlayer = new HashMap<>();
        this.townIdByChunk = new HashMap<>();
    }
    
    public static TownDataManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TownDataManager();
        }
        return INSTANCE;
    }
    
    public void loadAllTowns() {
        townsById.clear();
        townsByPlayer.clear();
        
        String[] townIds = DataStorage.getAllTownIds();
        for (String id : townIds) {
            TownData town = DataStorage.loadTown(id);
            if (town != null) {
                townsById.put(id, town);
                if (!town.isWarTown()) {
                    townsByPlayer.put(town.getMonarchUuid(), town);
                    for (var entry : town.getMembers().entrySet()) {
                        if ("Lord".equals(entry.getValue())) {
                            town.getLords().add(entry.getKey());
                        }
                        townsByPlayer.put(entry.getKey(), town);
                    }
                }
                Siegedempires.LOGGER.info("Loaded town: " + town.getName()
                        + (town.isWarTown() ? " (WarTown)" : " (Nation: " + town.isNation() + ")"));
            }
        }
        
        rebuildChunkIndex();
        Siegedempires.LOGGER.info("Loaded " + townsById.size() + " towns");
    }
    
    public TownData createTown(String name, UUID monarchUuid, String monarchName) {
        String townId = NameValidator.toId(name);
        
        if (DataStorage.townExists(townId)) {
            return null;
        }
        
        TownData town = new TownData(townId, name, monarchUuid, monarchName);
        town.setDescription("");
        
        townsById.put(townId, town);
        townsByPlayer.put(monarchUuid, town);
        
        DataStorage.saveTown(town);
        
        Siegedempires.LOGGER.info("Created town: " + name + " for " + monarchName);
        return town;
    }
    
    public TownData getTown(String townId) {
        return townsById.get(townId);
    }
    
    public TownData getTownByName(String name) {
        String id = NameValidator.toId(name);
        return townsById.get(id);
    }
    
    public TownData getPlayerTown(UUID playerUuid) {
        return townsByPlayer.get(playerUuid);
    }
    
    public boolean hasTown(UUID playerUuid) {
        return townsByPlayer.containsKey(playerUuid);
    }
    
    public Collection<TownData> getAllTowns() {
        return townsById.values();
    }

    /** Visible towns for GUIs — excludes empire wartowns. */
    public Collection<TownData> getVisibleTowns() {
        List<TownData> visible = new ArrayList<>();
        for (TownData town : townsById.values()) {
            if (!town.isWarTown()) {
                visible.add(town);
            }
        }
        return visible;
    }

    public static String warTownIdForEmpire(String empireId) {
        return "wartown_" + empireId;
    }

    public static String warTownIdForCapture(String empireId, String sourceTownId) {
        return "wartown_" + empireId + "_" + sourceTownId;
    }

    public TownData getWarTownForEmpire(String empireId) {
        if (empireId == null || empireId.isEmpty()) {
            return null;
        }
        return townsById.get(warTownIdForEmpire(empireId));
    }

    /** All wartowns owned by an empire that currently hold at least one chunk. */
    public List<TownData> getWarTownsForEmpire(String empireId) {
        List<TownData> result = new ArrayList<>();
        if (empireId == null || empireId.isEmpty()) {
            return result;
        }
        for (TownData town : townsById.values()) {
            if (town.isWarTown()
                    && empireId.equals(town.getEmpireId())
                    && !town.getClaimedChunks().isEmpty()) {
                result.add(town);
            }
        }
        result.sort(java.util.Comparator.comparing(t -> t.getName().toLowerCase()));
        return result;
    }

    /**
     * Returns the empire's hidden wartown for a specific captured source town,
     * creating it if needed. Display name is {@code New <source town name>}.
     */
    public TownData getOrCreateWarTown(com.siegedempires.model.EmpireData empire, TownData sourceTown) {
        if (empire == null || sourceTown == null || sourceTown.isWarTown()) {
            return getOrCreateWarTown(empire);
        }

        String id = warTownIdForCapture(empire.getId(), sourceTown.getId());
        String displayName = "New " + sourceTown.getName();
        TownData existing = townsById.get(id);
        if (existing != null) {
            existing.setName(displayName);
            existing.setEmpireId(empire.getId());
            existing.setCapturedFromTownId(sourceTown.getId());
            if (existing.getMonarchUuid() == null) {
                existing.setMonarchUuid(empire.getEmperorUuid());
                existing.setMonarchName(empire.getEmperorName());
            }
            if (existing.getMonarchTitle() == null || existing.getMonarchTitle().isEmpty()) {
                existing.setMonarchTitle("King");
            }
            saveTown(existing);
            return existing;
        }

        TownData warTown = new TownData();
        warTown.setId(id);
        warTown.setName(displayName);
        warTown.setWarTown(true);
        warTown.setEmpireId(empire.getId());
        warTown.setCapturedFromTownId(sourceTown.getId());
        warTown.setMonarchUuid(empire.getEmperorUuid());
        warTown.setMonarchName(empire.getEmperorName());
        warTown.setMonarchTitle("King");
        warTown.setTownPublic(false);
        warTown.setDescription("");
        townsById.put(id, warTown);
        DataStorage.saveTown(warTown);
        rebuildChunkIndex();
        Siegedempires.LOGGER.info("Created wartown " + displayName + " for empire: " + empire.getName());
        return warTown;
    }

    /**
     * Returns the empire's hidden wartown, creating it if needed.
     * Wartowns are never registered in {@code townsByPlayer}.
     */
    public TownData getOrCreateWarTown(com.siegedempires.model.EmpireData empire) {
        if (empire == null) {
            return null;
        }
        TownData existing = getWarTownForEmpire(empire.getId());
        if (existing != null) {
            existing.setName(empire.getName());
            existing.setEmpireId(empire.getId());
            existing.setMonarchUuid(empire.getEmperorUuid());
            existing.setMonarchName(empire.getEmperorName());
            return existing;
        }

        String id = warTownIdForEmpire(empire.getId());
        TownData warTown = new TownData();
        warTown.setId(id);
        warTown.setName(empire.getName());
        warTown.setWarTown(true);
        warTown.setEmpireId(empire.getId());
        warTown.setMonarchUuid(empire.getEmperorUuid());
        warTown.setMonarchName(empire.getEmperorName());
        warTown.setTownPublic(false);
        warTown.setDescription("");
        townsById.put(id, warTown);
        DataStorage.saveTown(warTown);
        rebuildChunkIndex();
        Siegedempires.LOGGER.info("Created wartown for empire: " + empire.getName());
        return warTown;
    }

    /**
     * Transfers {@code chunk} from its current owner to {@code newOwner}, clearing
     * citizen grants on the old town. Connectivity is not required.
     */
    public void transferClaimChunk(ChunkPosition chunk, TownData newOwner, ServerLevel level) {
        if (chunk == null || newOwner == null) {
            return;
        }
        TownData previous = getTownAtChunk(chunk.getX(), chunk.getZ(), chunk.getDimension());
        if (previous != null && previous.getId().equals(newOwner.getId())) {
            return;
        }
        if (previous != null) {
            previous.getClaimedChunks().remove(chunk);
            previous.getRestrictedChunks().remove(chunk);
            for (var owned : previous.getCitizenOwnedChunks().values()) {
                if (owned != null) {
                    owned.remove(chunk);
                }
            }
            previous.getCitizenOwnedChunks().entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());
            for (var owned : previous.getCitizenChunks().values()) {
                if (owned != null) {
                    owned.remove(chunk);
                }
            }
            previous.checkNationStatus();
            saveTown(previous);
        }
        newOwner.getClaimedChunks().add(chunk);
        newOwner.checkNationStatus();
        saveTown(newOwner);
        if (level != null) {
            com.siegedempires.claim.FlanClaimBridge.claimChunk(newOwner, chunk, level);
        }
    }

	public void saveTown(TownData town) {
		if (town != null) {
			DataStorage.saveTown(town);
			rebuildChunkIndex();
			BannerManager.syncBannersForTown(BannerManager.getServer(), town);
			notifySquaremapTownChanged(town);
		}
	}

    public TownData getTownAtChunk(int chunkX, int chunkZ, String dimension) {
        String townId = townIdByChunk.get(new ChunkPosition(chunkX, chunkZ, dimension));
        return townId != null ? townsById.get(townId) : null;
    }

    private void rebuildChunkIndex() {
        townIdByChunk.clear();
        for (TownData town : townsById.values()) {
            for (ChunkPosition chunk : town.getClaimedChunks()) {
                townIdByChunk.put(chunk, town.getId());
            }
        }
    }
    
    public boolean townNameExists(String name) {
        String id = NameValidator.toId(name);
        return townsById.containsKey(id);
    }

    /**
     * Renames a monarch's town (updates display name and, when needed, the town id).
     * @return error message, or {@code null} on success
     */
    public String renameTown(ServerPlayer monarch, String newName) {
        TownData town = getMonarchTown(monarch.getUUID());
        if (town == null) {
            return "Only the Monarch can rename the town!";
        }
        if (town.isWarTown()) {
            return "This town cannot be renamed!";
        }

        String trimmed = newName == null ? "" : newName.trim();
        String validationError = NameValidator.getValidationError(trimmed);
        if (validationError != null) {
            return validationError;
        }

        if (trimmed.equals(town.getName())) {
            return "That is already your town name!";
        }

        String oldId = town.getId();
        String newId = NameValidator.toId(trimmed);
        if (!newId.equals(oldId) && townNameExists(trimmed)) {
            return "A town with that name already exists!";
        }

        if (com.siegedempires.invasion.InvasionManager.hasActiveInvasionFor(
                com.siegedempires.model.DiplomacyRecord.TYPE_TOWN, oldId)) {
            return "You cannot rename your town during an active invasion or siege!";
        }

        if (newId.equals(oldId)) {
            town.setName(trimmed);
            saveTown(town);
            refreshTownMembers(town);
            return null;
        }

        townsById.remove(oldId);
        town.setId(newId);
        town.setName(trimmed);
        DataStorage.deleteTown(oldId);
        saveTown(town);
        townsById.put(newId, town);
        rebuildChunkIndex();

        DiplomacyDataManager.getInstance().renameEntity(
                com.siegedempires.model.DiplomacyRecord.TYPE_TOWN, oldId, newId);
        updateEmpireTownIdReferences(oldId, newId);
        BannerManager.migrateTownId(BannerManager.getServer(), oldId, newId);

        refreshTownMembers(town);
        Siegedempires.LOGGER.info("Renamed town {} -> {} ({})", oldId, newId, trimmed);
        return null;
    }

    /**
     * Update monarch town display name and/or banner design. Claims, members, and ranks
     * are left untouched. When the name's id slug changes, reuses {@link #renameTown}.
     * @return error message, or {@code null} on success
     */
    public String updateTownNameAndBanner(ServerPlayer monarch, String newName,
                                          String bannerBaseColor, List<String> bannerPatterns,
                                          String bannerPixels) {
        TownData town = getMonarchTown(monarch.getUUID());
        if (town == null) {
            return "Only the Monarch can edit the town!";
        }
        if (town.isWarTown()) {
            return "This town cannot be edited!";
        }

        String trimmed = newName == null ? "" : newName.trim();
        String validationError = NameValidator.getValidationError(trimmed);
        if (validationError != null) {
            return validationError;
        }

        boolean nameChanged = !trimmed.equals(town.getName());
        String oldPixels = town.getBannerPixels();
        String newBase = bannerBaseColor == null || bannerBaseColor.isEmpty() ? "white" : bannerBaseColor;
        List<String> newPatterns = bannerPatterns != null ? new ArrayList<>(bannerPatterns) : new ArrayList<>();
        String newPixels = CustomBannerDesign.isValidEncoded(bannerPixels)
                ? bannerPixels
                : CustomBannerDesign.solid(newBase).encode();

        boolean bannerChanged = !Objects.equals(oldPixels, newPixels)
                || !newBase.equals(town.getBannerBaseColor() != null ? town.getBannerBaseColor() : "white")
                || !newPatterns.equals(town.getBannerPatterns() != null ? town.getBannerPatterns() : List.of());

        boolean inEmpire = town.getEmpireId() != null && !town.getEmpireId().isEmpty();
        if (inEmpire && bannerChanged) {
            return "Your town uses the empire flag while in an empire!";
        }

        if (!nameChanged && !bannerChanged) {
            return "Nothing to update!";
        }

        if (nameChanged) {
            String renameError = renameTown(monarch, trimmed);
            if (renameError != null) {
                return renameError;
            }
            town = getMonarchTown(monarch.getUUID());
            if (town == null) {
                return "Failed to update town!";
            }
        }

        town.setBannerBaseColor(newBase);
        town.setBannerPatterns(newPatterns);
        town.setBannerPixels(newPixels);
        saveTown(town);

        BannerManager.syncBannersForTown(BannerManager.getServer(), town, oldPixels);
        refreshTownMembers(town);
        return null;
    }

    private static void refreshTownMembers(TownData town) {
        for (UUID memberUuid : town.getMembers().keySet()) {
            PlayerPrefixManager.refreshPlayer(memberUuid);
        }
    }

    private static void updateEmpireTownIdReferences(String oldId, String newId) {
        for (com.siegedempires.model.EmpireData empire : EmpireDataManager.getInstance().getAllEmpires()) {
            boolean changed = false;
            if (oldId.equals(empire.getCapitalTownId())) {
                empire.setCapitalTownId(newId);
                changed = true;
            }
            java.util.List<String> members = empire.getMemberTownIds();
            for (int i = 0; i < members.size(); i++) {
                if (oldId.equals(members.get(i))) {
                    members.set(i, newId);
                    changed = true;
                }
            }
            if (empire.getInvitedTowns().remove(oldId)) {
                empire.getInvitedTowns().add(newId);
                changed = true;
            }
            if (changed) {
                EmpireDataManager.getInstance().saveEmpire(empire);
            }
        }
    }

    public boolean joinTown(ServerPlayer player, String townName) {
        UUID playerUuid = player.getUUID();

        if (hasTown(playerUuid)) {
            return false;
        }

        TownData town = getTownByName(townName);
        if (town == null || town.isWarTown()) {
            return false;
        }

        if (!town.isTownPublic() && !town.isInvited(playerUuid)) {
            return false;
        }

        boolean wasInvited = town.isInvited(playerUuid);
        UUID monarchUuid = town.getMonarchUuid();
        String resolvedTownName = town.getName();

        town.getMembers().put(playerUuid, "Citizen");
        town.setMemberName(playerUuid, player.getName().getString());
        town.getInvitedPlayers().remove(playerUuid);
        townsByPlayer.put(playerUuid, town);
        saveTown(town);
        PlayerPrefixManager.refresh(player);
        if (wasInvited) {
            com.siegedempires.mail.MailNotifications.townInviteResponse(
                    player.level().getServer(), monarchUuid, player.getName().getString(), resolvedTownName, true);
            com.siegedempires.network.ModNetworking.sendMail(player);
        }
        return true;
    }

    public boolean leaveTown(UUID playerUuid) {
        TownData town = getPlayerTown(playerUuid);
        if (town == null) {
            return false;
        }

        if (town.getMonarchUuid().equals(playerUuid)) {
            return false;
        }

        town.getMembers().remove(playerUuid);
        town.getMemberNames().remove(playerUuid);
        townsByPlayer.remove(playerUuid);
        saveTown(town);
        PlayerPrefixManager.refreshPlayer(playerUuid);
        return true;
    }

    public boolean canJoinTown(UUID playerUuid, TownData town) {
        return town != null && !town.isWarTown() && !hasTown(playerUuid)
                && (town.isTownPublic() || town.isInvited(playerUuid));
    }

    public String getJoinFailureMessage(UUID playerUuid, TownData town) {
        if (town == null) {
            return "Town not found!";
        }
        if (hasTown(playerUuid)) {
            return "You are already in a town!";
        }
        if (!town.isTownPublic() && !town.isInvited(playerUuid)) {
            return "Can't Join Town. Town has Private Citizenship.";
        }
        return "Unable to join town!";
    }

    public TownData getMonarchTown(UUID playerUuid) {
        TownData context = getContextWartown(playerUuid);
        if (context != null) {
            com.siegedempires.model.EmpireData empire =
                    EmpireDataManager.getInstance().getEmpire(context.getEmpireId());
            if (empire != null && empire.getEmperorUuid().equals(playerUuid)) {
                return context;
            }
            if (context.getMonarchUuid().equals(playerUuid)) {
                return context;
            }
        }
        TownData town = getPlayerTown(playerUuid);
        if (town != null && town.getMonarchUuid().equals(playerUuid)) {
            return town;
        }
        return null;
    }

    public boolean isMonarch(UUID playerUuid) {
        TownData town = getPlayerTown(playerUuid);
        return town != null && town.getMonarchUuid().equals(playerUuid);
    }

    public boolean isLord(UUID playerUuid) {
        TownData town = getPlayerTown(playerUuid);
        return town != null && town.isLord(playerUuid);
    }

    public boolean canManageTown(UUID playerUuid) {
        return isMonarch(playerUuid) || isLord(playerUuid);
    }

    public TownData getManageableTown(UUID playerUuid) {
        TownData context = getContextWartown(playerUuid);
        if (context != null) {
            return context;
        }
        TownData town = getPlayerTown(playerUuid);
        if (town == null) {
            return null;
        }
        if (town.getMonarchUuid().equals(playerUuid) || town.isLord(playerUuid)) {
            return town;
        }
        return null;
    }

    private TownData getContextWartown(UUID playerUuid) {
        String wartownId = WartownManagementContext.get(playerUuid);
        if (wartownId == null || wartownId.isEmpty()) {
            return null;
        }
        TownData wartown = townsById.get(wartownId);
        if (wartown == null || !wartown.isWarTown()) {
            return null;
        }
        com.siegedempires.model.EmpireData empire =
                EmpireDataManager.getInstance().getEmpire(wartown.getEmpireId());
        if (empire != null && empire.getEmperorUuid().equals(playerUuid)) {
            return wartown;
        }
        if (wartown.getMonarchUuid().equals(playerUuid) || wartown.isLord(playerUuid)) {
            return wartown;
        }
        return null;
    }

    public String invitePlayer(ServerPlayer manager, String targetName) {
        TownData town = getManageableTown(manager.getUUID());
        if (town == null) {
            return "Only the Monarch or a Lord can invite players!";
        }

        if (BannerManager.getServer() == null) {
            return "Server not ready!";
        }

        ServerPlayer target = BannerManager.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            return "Player not found or not online!";
        }

        UUID targetUuid = target.getUUID();
        if (town.getMembers().containsKey(targetUuid)) {
            return "That player is already in your town!";
        }
        if (hasTown(targetUuid)) {
            return "That player is already in another town!";
        }
        if (town.isInvited(targetUuid)) {
            return "That player has already been invited!";
        }

        town.getInvitedPlayers().add(targetUuid);
        saveTown(town);
        com.siegedempires.network.ModNetworking.sendMail(target);
        return null;
    }

    public String evictPlayer(ServerPlayer monarch, UUID targetUuid) {
        TownData town = getMonarchTown(monarch.getUUID());
        if (town == null) {
            return "Only the Monarch can evict players!";
        }

        if (targetUuid.equals(monarch.getUUID())) {
            return "You cannot evict yourself!";
        }

        if (!town.getMembers().containsKey(targetUuid)) {
            return "That player is not in your town!";
        }

        if ("Monarch".equals(town.getMembers().get(targetUuid))) {
            return "You cannot evict the Monarch!";
        }

        town.getMembers().remove(targetUuid);
        town.getMemberNames().remove(targetUuid);
        town.getInvitedPlayers().remove(targetUuid);
        townsByPlayer.remove(targetUuid);
        saveTown(town);
        PlayerPrefixManager.refreshPlayer(targetUuid);
        return null;
    }

    public String setTownPublic(ServerPlayer monarch, boolean townPublic) {
        TownData town = getMonarchTown(monarch.getUUID());
        if (town == null) {
            return "Only the Monarch can change town settings!";
        }

        town.setTownPublic(townPublic);
        saveTown(town);
        return null;
    }

    public String toggleTownPublic(ServerPlayer monarch) {
        TownData town = getMonarchTown(monarch.getUUID());
        if (town == null) {
            return "Only the Monarch can change town settings!";
        }

        town.setTownPublic(!town.isTownPublic());
        saveTown(town);
        return null;
    }

    public String deleteTown(ServerPlayer monarch) {
        TownData town = getMonarchTown(monarch.getUUID());
        if (town == null) {
            return "Only the Monarch can delete the town!";
        }
        if (town.isWarTown()) {
            return "Wartowns cannot be deleted!";
        }

		String townId = town.getId();
		java.util.Set<UUID> memberIds = new java.util.HashSet<>(town.getMembers().keySet());
		for (UUID memberUuid : memberIds) {
			townsByPlayer.remove(memberUuid);
		}

		townsById.remove(townId);
		DataStorage.deleteTown(townId);
		rebuildChunkIndex();
		BannerManager.resetBannersForDeletedTown(BannerManager.getServer(), townId);
		notifySquaremapTownRemoved(townId);

		for (UUID memberUuid : memberIds) {
			PlayerPrefixManager.refreshPlayer(memberUuid);
		}

        Siegedempires.LOGGER.info("Deleted town: " + town.getName());
        return null;
    }

    /**
     * Deletes all wartowns belonging to an empire. Used when an empire is dissolved.
     */
    public void deleteWarTownForEmpire(String empireId) {
        if (empireId == null || empireId.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (TownData town : townsById.values()) {
            if (town.isWarTown() && empireId.equals(town.getEmpireId())) {
                ids.add(town.getId());
            }
        }
        for (String townId : ids) {
            townsById.remove(townId);
            DataStorage.deleteTown(townId);
            BannerManager.resetBannersForDeletedTown(BannerManager.getServer(), townId);
            notifySquaremapTownRemoved(townId);
        }
        if (!ids.isEmpty()) {
            rebuildChunkIndex();
            Siegedempires.LOGGER.info("Deleted " + ids.size() + " wartown(s) for dissolved empire: " + empireId);
        }
    }

    /**
     * Assigns a wartown monarch without a Duke/Duchess title (offline crowning).
     */
    public String assignWartownMonarchWithoutTitle(String wartownId, UUID targetUuid) {
        return applyWartownMonarch(wartownId, targetUuid, null);
    }

    /**
     * Crowns a player as Duke/Duchess (stored as King/Queen) of a wartown after they accept.
     */
    public String crownWartownMonarch(String wartownId, UUID targetUuid, String monarchTitle) {
        if (!"King".equals(monarchTitle) && !"Queen".equals(monarchTitle)) {
            return "Monarch title must be 'King' or 'Queen'!";
        }
        return applyWartownMonarch(wartownId, targetUuid, monarchTitle);
    }

    /**
     * Sets Duke/Duchess title for a wartown monarch who was crowned while offline.
     */
    public String completeWartownMonarchTitle(String wartownId, UUID targetUuid, String monarchTitle) {
        if (!"King".equals(monarchTitle) && !"Queen".equals(monarchTitle)) {
            return "Monarch title must be 'King' or 'Queen'!";
        }
        TownData wartown = townsById.get(wartownId);
        if (wartown == null || !wartown.isWarTown()) {
            return "Wartown not found!";
        }
        if (!targetUuid.equals(wartown.getMonarchUuid())) {
            return "You are not the monarch of that wartown!";
        }
        if (wartown.getMonarchTitle() != null && !wartown.getMonarchTitle().isEmpty()) {
            return "You have already chosen your title!";
        }
        wartown.setMonarchTitle(monarchTitle);
        saveTown(wartown);
        PlayerPrefixManager.refreshPlayer(targetUuid);
        Siegedempires.LOGGER.info("Set wartown title " + monarchTitle + " for " + wartown.getMonarchName()
                + " of " + wartown.getName());
        return null;
    }

    private String applyWartownMonarch(String wartownId, UUID targetUuid, String monarchTitle) {
        TownData wartown = townsById.get(wartownId);
        if (wartown == null || !wartown.isWarTown()) {
            return "Wartown not found!";
        }
        if (!isPlayerInEmpire(targetUuid, wartown.getEmpireId())) {
            return "That player is not in your empire!";
        }

        TownData currentTown = getPlayerTown(targetUuid);
        if (currentTown != null) {
            if ("Monarch".equals(currentTown.getMembers().get(targetUuid))) {
                return "That player is a Monarch and cannot be crowned!";
            }
            currentTown.getMembers().remove(targetUuid);
            currentTown.getMemberNames().remove(targetUuid);
            currentTown.getLords().remove(targetUuid);
            currentTown.getInvitedPlayers().remove(targetUuid);
            townsByPlayer.remove(targetUuid);
            saveTown(currentTown);
        }

        UUID previousMonarch = wartown.getMonarchUuid();
        String targetName = resolveEmpirePlayerName(targetUuid, wartown);
        if (targetName == null || targetName.isEmpty()) {
            return "Could not resolve player name!";
        }

        wartown.setMonarchUuid(targetUuid);
        wartown.setMonarchName(targetName);
        if (monarchTitle != null) {
            wartown.setMonarchTitle(monarchTitle);
        } else {
            wartown.setMonarchTitle(null);
        }
        wartown.getMembers().put(targetUuid, "Monarch");
        wartown.setMemberName(targetUuid, targetName);
        wartown.getLords().remove(targetUuid);
        townsByPlayer.put(targetUuid, wartown);
        saveTown(wartown);

        if (previousMonarch != null && !previousMonarch.equals(targetUuid)) {
            wartown.getMembers().remove(previousMonarch);
            wartown.getMemberNames().remove(previousMonarch);
            TownData prevAssigned = townsByPlayer.get(previousMonarch);
            if (prevAssigned != null && wartown.getId().equals(prevAssigned.getId())) {
                townsByPlayer.remove(previousMonarch);
            }
            PlayerPrefixManager.refreshPlayer(previousMonarch);
        }
        PlayerPrefixManager.refreshPlayer(targetUuid);
        if (monarchTitle != null) {
            Siegedempires.LOGGER.info("Crowned " + targetName + " as " + monarchTitle + " of wartown " + wartown.getName());
        } else {
            Siegedempires.LOGGER.info("Assigned " + targetName + " as wartown monarch of " + wartown.getName()
                    + " (title pending)");
        }
        return null;
    }

    public String resolveEmpirePlayerNameForMessage(UUID targetUuid, TownData wartown) {
        return resolveEmpirePlayerName(targetUuid, wartown);
    }

    private String resolveEmpirePlayerName(UUID targetUuid, TownData wartown) {
        String targetName = wartown.getMemberName(targetUuid);
        if (targetName != null && !targetName.isEmpty()) {
            return targetName;
        }
        for (TownData memberTown : getVisibleTowns()) {
            if (wartown.getEmpireId().equals(memberTown.getEmpireId())
                    && memberTown.getMemberNames().containsKey(targetUuid)) {
                return memberTown.getMemberName(targetUuid);
            }
        }
        return null;
    }

    public boolean isPlayerInEmpire(UUID playerUuid, String empireId) {
        if (empireId == null || empireId.isEmpty()) {
            return false;
        }
        com.siegedempires.model.EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
        if (empire == null) {
            return false;
        }
        if (empire.getEmperorUuid().equals(playerUuid)) {
            return true;
        }
        TownData town = getPlayerTown(playerUuid);
        return town != null && empireId.equals(town.getEmpireId());
    }

    /** Collect unique players across all member towns of an empire. */
    public List<ManageTownData.MemberInfo> collectEmpirePlayers(String empireId) {
        List<ManageTownData.MemberInfo> players = new ArrayList<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        com.siegedempires.model.EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
        if (empire == null) {
            return players;
        }
        if (empire.getEmperorUuid() != null && seen.add(empire.getEmperorUuid())) {
            ManageTownData.MemberInfo emperor = new ManageTownData.MemberInfo();
            emperor.uuid = empire.getEmperorUuid().toString();
            emperor.name = empire.getEmperorName() != null ? empire.getEmperorName() : "Emperor";
            emperor.role = "Emperor";
            players.add(emperor);
        }
        for (String townId : empire.getMemberTownIds()) {
            TownData town = getTown(townId);
            if (town == null || town.isWarTown()) {
                continue;
            }
            for (var entry : town.getMembers().entrySet()) {
                if (seen.add(entry.getKey())) {
                    ManageTownData.MemberInfo info = new ManageTownData.MemberInfo();
                    info.uuid = entry.getKey().toString();
                    info.name = town.getMemberName(entry.getKey());
                    info.role = entry.getValue();
                    players.add(info);
                }
            }
        }
        players.sort(java.util.Comparator.comparing(m -> m.name.toLowerCase()));
        return players;
    }

    /**
     * Resolve the UUID of a town member by display name. Matches case-insensitively
     * against the cached {@code memberNames} map. Used by the Give Citizen Land
     * flow so offline members can be picked.
     *
     * @return the member's UUID, or {@code null} if no member matches.
     */
    public UUID findMemberByName(TownData town, String playerName) {
        if (town == null || playerName == null || playerName.isEmpty()) {
            return null;
        }
        for (var entry : town.getMemberNames().entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(playerName)) {
                return entry.getKey();
            }
        }
        // Fallback: try the live player name on the members map's UUID.
        for (var entry : town.getMembers().entrySet()) {
            String cached = town.getMemberName(entry.getKey());
            if (cached != null && cached.equalsIgnoreCase(playerName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Build the CitizenGiveLand banner item for {@code monarchOrLord} to give
     * the chunk to {@code targetUuid}. The caller is responsible for actually
     * inserting the stack into the player's inventory (the GUI flow does this
     * via {@code PlayerInventory#add}).
     *
     * @return the constructed ItemStack, or {@code null} when the inputs are
     *         invalid (e.g. target is not a member of the player's town).
     */
    public ItemStack buildCitizenGiveLandBanner(ServerPlayer monarchOrLord, UUID targetUuid) {
        if (monarchOrLord == null || targetUuid == null) {
            return null;
        }
        TownData town = getPlayerTown(monarchOrLord.getUUID());
        if (town == null) {
            return null;
        }
        if (!town.getMonarchUuid().equals(monarchOrLord.getUUID())
            && !town.isLord(monarchOrLord.getUUID())) {
            return null;
        }
        if (!town.getMembers().containsKey(targetUuid)) {
            return null;
        }
        if (targetUuid.equals(monarchOrLord.getUUID())) {
            return null;
        }
        return com.siegedempires.banner.BannerHelper.createCitizenGiveLandBannerItem(
            town, targetUuid, town.getMemberName(targetUuid), monarchOrLord.getUUID(),
            monarchOrLord.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
    }

    /**
     * Convenience helper: build the banner and give it to the player. Returns
     * {@code null} on success, or a user-facing error message on failure.
     */
    public String giveCitizenGiveLandBanner(ServerPlayer monarchOrLord, String targetName) {
        if (monarchOrLord == null) {
            return "Only players can give citizen land!";
        }
        TownData town = getPlayerTown(monarchOrLord.getUUID());
        if (town == null) {
            return "You are not in a town!";
        }
        if (!town.getMonarchUuid().equals(monarchOrLord.getUUID())
            && !town.isLord(monarchOrLord.getUUID())) {
            return "Only the Monarch or a Lord can give citizen land!";
        }
        if (targetName == null || targetName.isBlank()) {
            return "Enter a citizen name!";
        }
        UUID targetUuid = findMemberByName(town, targetName);
        if (targetUuid == null) {
            // Try resolving as a live player name so online players who
            // have not yet cached their member name still work.
            net.minecraft.server.MinecraftServer srv = monarchOrLord.level().getServer();
            for (ServerPlayer online : srv.getPlayerList().getPlayers()) {
                if (online.getName().getString().equalsIgnoreCase(targetName)) {
                    targetUuid = online.getUUID();
                    break;
                }
            }
        }
        if (targetUuid == null || !town.getMembers().containsKey(targetUuid)) {
            return "That player is not in your town!";
        }
        if (targetUuid.equals(monarchOrLord.getUUID())) {
            return "You cannot give land to yourself!";
        }
        ItemStack stack = buildCitizenGiveLandBanner(monarchOrLord, targetUuid);
        if (stack == null) {
            return "Could not create the banner!";
        }
        if (!monarchOrLord.getInventory().add(stack)) {
            return "Your inventory is full!";
        }
        return null;
    }

    /**
     * Revoke every chunk grant owned by {@code targetUuid} in the caller's
     * town. Persists the change to the town's JSON config file.
     *
     * @return {@code null} on success or a user-facing error string.
     */
    public String revokeAllLandForCitizen(ServerPlayer monarchOrLord, UUID targetUuid) {
        if (monarchOrLord == null) return "Only players can revoke land!";
        if (targetUuid == null) return "Missing citizen!";
        TownData town = getPlayerTown(monarchOrLord.getUUID());
        if (town == null) return "You are not in a town!";
        if (!town.getMonarchUuid().equals(monarchOrLord.getUUID())
            && !town.isLord(monarchOrLord.getUUID())) {
            return "Only the Monarch or a Lord can revoke land!";
        }
        if (!town.getMembers().containsKey(targetUuid)) {
            return "That player is not in your town!";
        }
        java.util.Set<ChunkPosition> chunks = town.getCitizenOwnedChunks().remove(targetUuid);
        if (chunks != null && !chunks.isEmpty()) {
            saveTown(town);
        }
        return null;
    }

    /**
     * Give the caller a Land Revoke Banner. The placer's UUID is stored on
     * the item so {@link com.siegedempires.banner.BannerManager} can DM the
     * placer with the result of each placement.
     */
    public String giveLandRevokeBanner(ServerPlayer monarchOrLord) {
        if (monarchOrLord == null) return "Only players can create a Land Revoke Banner!";
        TownData town = getPlayerTown(monarchOrLord.getUUID());
        if (town == null) return "You are not in a town!";
        if (!town.getMonarchUuid().equals(monarchOrLord.getUUID())
            && !town.isLord(monarchOrLord.getUUID())) {
            return "Only the Monarch or a Lord can create a Land Revoke Banner!";
        }
        ItemStack stack = com.siegedempires.banner.BannerHelper.createLandRevokeBannerItem(
            town, monarchOrLord.getUUID(),
            monarchOrLord.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
        if (stack == null) return "Could not create the Land Revoke Banner!";
        if (!monarchOrLord.getInventory().add(stack)) {
            return "Your inventory is full!";
        }
        return null;
    }

    public String trustCitizen(ServerPlayer monarch, UUID targetUuid) {
        TownData town = getMonarchTown(monarch.getUUID());
        if (town == null) {
            return "Only the Monarch can trust citizens!";
        }

        if (targetUuid.equals(monarch.getUUID())) {
            return "You cannot change your own role!";
        }

        if (!town.getMembers().containsKey(targetUuid)) {
            return "That player is not in your town!";
        }

        String role = town.getMembers().get(targetUuid);
        if ("Monarch".equals(role)) {
            return "You cannot change the Monarch's role!";
        }
        if ("Lord".equals(role)) {
            return "That player is already a Lord!";
        }
        if ("Trusted Citizen".equals(role)) {
            return "That player is already a Trusted Citizen!";
        }

        town.getMembers().put(targetUuid, "Trusted Citizen");
        saveTown(town);
        PlayerPrefixManager.refreshPlayer(targetUuid);
        return null;
    }

    public String makeLord(ServerPlayer monarch, UUID targetUuid) {
        TownData town = getMonarchTown(monarch.getUUID());
        if (town == null) {
            return "Only the Monarch can make a citizen a Lord!";
        }

        if (targetUuid.equals(monarch.getUUID())) {
            return "You cannot make yourself a Lord!";
        }

        if (!town.getMembers().containsKey(targetUuid)) {
            return "That player is not in your town!";
        }

        String role = town.getMembers().get(targetUuid);
        if ("Monarch".equals(role)) {
            return "The Monarch cannot be made a Lord!";
        }
        if ("Lord".equals(role)) {
            return "That player is already a Lord!";
        }

        town.getMembers().put(targetUuid, "Lord");
        town.getLords().add(targetUuid);
        saveTown(town);
        PlayerPrefixManager.refreshPlayer(targetUuid);
        return null;
    }

    /**
     * Transfers town monarchy from the current Monarch to a Lord.
     * The former Monarch becomes a Lord. Emperors must step down as Emperor first.
     */
    public String transferMonarchy(ServerPlayer monarch, UUID successorUuid) {
        TownData town = getMonarchTown(monarch.getUUID());
        if (town == null) {
            return "Only the Monarch can step down!";
        }

        if (EmpireDataManager.getInstance().isEmperor(monarch.getUUID())) {
            return "You must Step Down as Emperor before transferring the crown!";
        }

        if (successorUuid.equals(monarch.getUUID())) {
            return "You cannot give the crown to yourself!";
        }

        if (!town.getMembers().containsKey(successorUuid)) {
            return "That player is not in your town!";
        }

        if (!town.isLord(successorUuid) && !"Lord".equals(town.getMembers().get(successorUuid))) {
            return "You can only give the crown to a Lord!";
        }

        UUID oldMonarchUuid = monarch.getUUID();
        String successorName = town.getMemberName(successorUuid);
        String oldMonarchName = town.getMemberName(oldMonarchUuid);

        // Demote former monarch to Lord
        town.getMembers().put(oldMonarchUuid, "Lord");
        town.getLords().add(oldMonarchUuid);

        // Promote successor; remove from lords set (role is Monarch)
        town.getMembers().put(successorUuid, "Monarch");
        town.getLords().remove(successorUuid);
        town.setMonarchUuid(successorUuid);
        town.setMonarchName(successorName);
        // Keep existing King/Queen title on the town (chosen at founding)

        saveTown(town);
        PlayerPrefixManager.refreshPlayer(oldMonarchUuid);
        PlayerPrefixManager.refreshPlayer(successorUuid);

		Siegedempires.LOGGER.info("Town " + town.getName() + " monarchy transferred from "
				+ oldMonarchName + " to " + successorName);
		return null;
	}

	private static void notifySquaremapTownChanged(TownData town) {
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("squaremap")) {
			com.siegedempires.compat.squaremap.SquaremapCompat.onTownChanged(town);
		}
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voxelmapsync")) {
			com.siegedempires.compat.voxelmapsync.VoxelMapSyncCompat.onTownChanged(town);
		}
	}

	private static void notifySquaremapTownRemoved(String townId) {
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("squaremap")) {
			com.siegedempires.compat.squaremap.SquaremapCompat.onTownRemoved(townId);
		}
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voxelmapsync")) {
			com.siegedempires.compat.voxelmapsync.VoxelMapSyncCompat.onTownRemoved(townId);
		}
	}
}