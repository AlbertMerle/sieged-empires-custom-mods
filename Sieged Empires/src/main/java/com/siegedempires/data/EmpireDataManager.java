package com.siegedempires.data;

import com.siegedempires.Siegedempires;
import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.BannerManager;
import com.siegedempires.banner.CustomBannerDesign;
import com.siegedempires.invasion.InvasionManager;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.util.NameValidator;
import com.siegedempires.util.PlayerPrefixManager;

import java.util.*;

public class EmpireDataManager {
	private static EmpireDataManager INSTANCE;

	private final Map<String, EmpireData> empiresById;

	private EmpireDataManager() {
		this.empiresById = new HashMap<>();
	}

	public static EmpireDataManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new EmpireDataManager();
		}
		return INSTANCE;
	}

	public void loadAllEmpires() {
		empiresById.clear();

		for (String id : DataStorage.getAllEmpireIds()) {
			EmpireData empire = DataStorage.loadEmpire(id);
			if (empire != null) {
				empiresById.put(id, empire);
				Siegedempires.LOGGER.info("Loaded empire: " + empire.getName());
			}
		}

		Siegedempires.LOGGER.info("Loaded " + empiresById.size() + " empires");
	}

	public EmpireData getEmpire(String empireId) {
		return empiresById.get(empireId);
	}

	public Collection<EmpireData> getAllEmpires() {
		return empiresById.values();
	}

	public void saveEmpire(EmpireData empire) {
		if (empire != null) {
			DataStorage.saveEmpire(empire);
			empiresById.put(empire.getId(), empire);
		}
	}

	public EmpireData createEmpire(String name, String description, UUID emperorUuid, String emperorName,
									String emperorTitle, String capitalTownId,
									List<String> bannerPatterns, String bannerBaseColor,
									String bannerPixels) {
		String empireId = NameValidator.toId(name);

		if (empiresById.containsKey(empireId) || DataStorage.empireExists(empireId)) {
			return null;
		}

		EmpireData empire = new EmpireData(empireId, name);
		empire.setDescription(description);
		empire.setEmperorUuid(emperorUuid);
		empire.setEmperorName(emperorName);
		empire.setEmperorTitle(emperorTitle);
		empire.setCapitalTownId(capitalTownId);
		empire.setBannerPatterns(new ArrayList<>(bannerPatterns));
		empire.setBannerBaseColor(bannerBaseColor);
		if (CustomBannerDesign.isValidEncoded(bannerPixels)) {
			empire.setBannerPixels(bannerPixels);
		} else {
			BannerHelper.ensureBannerPixels(empire);
		}
		empire.getMemberTownIds().add(capitalTownId);

		empiresById.put(empireId, empire);
		DataStorage.saveEmpire(empire);

		Siegedempires.LOGGER.info("Created empire: " + name + " by " + emperorName + " (" + emperorTitle + ")");
		return empire;
	}

	public boolean empireNameExists(String name) {
		String id = NameValidator.toId(name);
		return empiresById.containsKey(id) || DataStorage.empireExists(id);
	}

	public String joinEmpire(String empireId, TownData town) {
		EmpireData empire = empiresById.get(empireId);
		if (empire == null) {
			return "That empire does not exist!";
		}

		if (town.getEmpireId() != null && !town.getEmpireId().isEmpty()) {
			return "Your town is already in an empire!";
		}

		if (empire.getMemberTownIds().contains(town.getId())) {
			return "Your town is already in that empire!";
		}

		if (!empire.isEmpirePublic() && !empire.isInvited(town.getId())) {
			return "Can't Join Empire. Empire has Private Membership.";
		}

		empire.getMemberTownIds().add(town.getId());
		empire.getInvitedTowns().remove(town.getId());
		town.setEmpireId(empireId);

		String previousPixels = town.getBannerPixels();
		BannerHelper.preserveOwnBannerAndAdoptEmpire(town, empire);

		saveEmpire(empire);
		TownDataManager.getInstance().saveTown(town);

		BannerManager.syncBannersForTown(BannerManager.getServer(), town, previousPixels);
		PlayerPrefixManager.refreshForTown(town);

		com.siegedempires.mail.MailNotifications.empireInviteResponse(
				BannerManager.getServer(), empire.getEmperorUuid(),
				town.getName(), empire.getName(), true);
		com.siegedempires.data.PlayerMailManager.refreshOnlinePlayer(
				BannerManager.getServer(), town.getMonarchUuid());

		Siegedempires.LOGGER.info("Town " + town.getName() + " joined empire " + empire.getName());
		return null;
	}

	public String inviteTown(String empireId, String townId) {
		EmpireData empire = empiresById.get(empireId);
		if (empire == null) {
			return "That empire does not exist!";
		}

		TownData town = TownDataManager.getInstance().getTown(townId);
		if (town == null) {
			return "That town does not exist!";
		}

		if (town.getEmpireId() != null && !town.getEmpireId().isEmpty()) {
			return "That town is already in an empire!";
		}

		if (empire.getMemberTownIds().contains(townId)) {
			return "That town is already in your empire!";
		}

		if (empire.getInvitedTowns().contains(townId)) {
			return "That town has already been invited!";
		}

		empire.getInvitedTowns().add(townId);
		saveEmpire(empire);
		com.siegedempires.data.PlayerMailManager.refreshOnlinePlayer(
				com.siegedempires.banner.BannerManager.getServer(), town.getMonarchUuid());
		return null;
	}

	public String disannexTown(UUID emperorUuid, String townId) {
		EmpireData empire = getEmpireByEmperor(emperorUuid);
		if (empire == null) {
			return "Only the Emperor or Empress can disannex towns!";
		}

		if (townId.equals(empire.getCapitalTownId())) {
			return "You cannot disannex the capital town!";
		}

		if (!empire.getMemberTownIds().contains(townId)) {
			return "That town is not in your empire!";
		}

		TownData town = TownDataManager.getInstance().getTown(townId);
		if (town == null) {
			return "That town could not be found!";
		}

		empire.getMemberTownIds().remove(townId);
		empire.getInvitedTowns().remove(townId);
		town.setEmpireId(null);

		String previousPixels = BannerHelper.restoreOwnBannerAfterLeavingEmpire(town);

		saveEmpire(empire);
		TownDataManager.getInstance().saveTown(town);

		if (previousPixels != null) {
			BannerManager.syncBannersForTown(BannerManager.getServer(), town, previousPixels);
		}
		PlayerPrefixManager.refreshForTown(town);

		Siegedempires.LOGGER.info("Town " + town.getName() + " disannexed from empire " + empire.getName());
		return null;
	}

	public String setEmpirePublic(UUID emperorUuid, boolean empirePublic) {
		EmpireData empire = getEmpireByEmperor(emperorUuid);
		if (empire == null) {
			return "Only the Emperor or Empress can change empire settings!";
		}

		empire.setEmpirePublic(empirePublic);
		saveEmpire(empire);
		return null;
	}

	public String toggleEmpirePublic(UUID emperorUuid) {
		EmpireData empire = getEmpireByEmperor(emperorUuid);
		if (empire == null) {
			return "Only the Emperor or Empress can change empire settings!";
		}

		empire.setEmpirePublic(!empire.isEmpirePublic());
		saveEmpire(empire);
		return null;
	}

	public String inviteTownByEmperor(UUID emperorUuid, String townName) {
		EmpireData empire = getEmpireByEmperor(emperorUuid);
		if (empire == null) {
			return "Only the Emperor or Empress can invite towns!";
		}

		TownData town = TownDataManager.getInstance().getTownByName(townName);
		if (town == null) {
			return "That town does not exist!";
		}

		return inviteTown(empire.getId(), town.getId());
	}

	public EmpireData getEmpireForTown(String townId) {
		TownData town = TownDataManager.getInstance().getTown(townId);
		if (town != null && town.getEmpireId() != null && !town.getEmpireId().isEmpty()) {
			EmpireData byId = empiresById.get(town.getEmpireId());
			if (byId != null) {
				return byId;
			}
		}
		for (EmpireData empire : empiresById.values()) {
			if (empire.getMemberTownIds().contains(townId)) {
				return empire;
			}
		}
		return null;
	}

	public boolean isEmperor(UUID playerUuid) {
		for (EmpireData empire : empiresById.values()) {
			if (empire.getEmperorUuid().equals(playerUuid)) {
				return true;
			}
		}
		return false;
	}

	public EmpireData getEmpireByEmperor(UUID playerUuid) {
		for (EmpireData empire : empiresById.values()) {
			if (empire.getEmperorUuid().equals(playerUuid)) {
				return empire;
			}
		}
		return null;
	}

	/**
	 * Emperor updates empire name and/or banner. Member towns adopt the new flag.
	 * Claims, members, and ranks are unchanged.
	 * @return error message, or {@code null} on success
	 */
	public String updateEmpireNameAndBanner(UUID emperorUuid, String newName,
											String bannerBaseColor, List<String> bannerPatterns,
											String bannerPixels) {
		EmpireData empire = getEmpireByEmperor(emperorUuid);
		if (empire == null) {
			return "Only the Emperor or Empress can edit the empire!";
		}

		String trimmed = newName == null ? "" : newName.trim();
		String validationError = NameValidator.getValidationError(trimmed);
		if (validationError != null) {
			return validationError;
		}

		if (InvasionManager.hasActiveInvasionFor(DiplomacyRecord.TYPE_EMPIRE, empire.getId())) {
			return "You cannot edit your empire during an active invasion or siege!";
		}

		boolean nameChanged = !trimmed.equals(empire.getName());
		String oldPixels = empire.getBannerPixels();
		String newBase = bannerBaseColor == null || bannerBaseColor.isEmpty() ? "white" : bannerBaseColor;
		List<String> newPatterns = bannerPatterns != null ? new ArrayList<>(bannerPatterns) : new ArrayList<>();
		String newPixels = CustomBannerDesign.isValidEncoded(bannerPixels)
				? bannerPixels
				: CustomBannerDesign.solid(newBase).encode();

		boolean bannerChanged = !Objects.equals(oldPixels, newPixels)
				|| !newBase.equals(empire.getBannerBaseColor() != null ? empire.getBannerBaseColor() : "white")
				|| !newPatterns.equals(empire.getBannerPatterns() != null ? empire.getBannerPatterns() : List.of());

		if (!nameChanged && !bannerChanged) {
			return "Nothing to update!";
		}

		if (nameChanged) {
			String renameError = renameEmpire(emperorUuid, trimmed);
			if (renameError != null) {
				return renameError;
			}
			empire = getEmpireByEmperor(emperorUuid);
			if (empire == null) {
				return "Failed to update empire!";
			}
		}

		empire.setBannerBaseColor(newBase);
		empire.setBannerPatterns(newPatterns);
		empire.setBannerPixels(newPixels);
		saveEmpire(empire);

		for (String townId : empire.getMemberTownIds()) {
			TownData town = TownDataManager.getInstance().getTown(townId);
			if (town == null) {
				continue;
			}
			BannerHelper.adoptEmpireBanner(town, empire);
			TownDataManager.getInstance().saveTown(town);
			PlayerPrefixManager.refreshForTown(town);
		}

		com.siegedempires.banner.BannerManager.syncBannersForEmpire(
				com.siegedempires.banner.BannerManager.getServer(), empire, oldPixels);
		return null;
	}

	/**
	 * Renames an empire (display name and id when the slug changes).
	 * @return error message, or {@code null} on success
	 */
	public String renameEmpire(UUID emperorUuid, String newName) {
		EmpireData empire = getEmpireByEmperor(emperorUuid);
		if (empire == null) {
			return "Only the Emperor or Empress can rename the empire!";
		}

		String trimmed = newName == null ? "" : newName.trim();
		String validationError = NameValidator.getValidationError(trimmed);
		if (validationError != null) {
			return validationError;
		}

		if (trimmed.equals(empire.getName())) {
			return "That is already your empire name!";
		}

		String oldId = empire.getId();
		String newId = NameValidator.toId(trimmed);
		if (!newId.equals(oldId) && empireNameExists(trimmed)) {
			return "An empire with that name already exists!";
		}

		if (InvasionManager.hasActiveInvasionFor(DiplomacyRecord.TYPE_EMPIRE, oldId)) {
			return "You cannot rename your empire during an active invasion or siege!";
		}

		if (newId.equals(oldId)) {
			empire.setName(trimmed);
			saveEmpire(empire);
			return null;
		}

		empiresById.remove(oldId);
		empire.setId(newId);
		empire.setName(trimmed);
		DataStorage.deleteEmpire(oldId);
		saveEmpire(empire);
		empiresById.put(newId, empire);

		DiplomacyDataManager.getInstance().renameEntity(DiplomacyRecord.TYPE_EMPIRE, oldId, newId);
		updateTownEmpireIdReferences(oldId, newId);

		Siegedempires.LOGGER.info("Renamed empire {} -> {} ({})", oldId, newId, trimmed);
		return null;
	}

	private void updateTownEmpireIdReferences(String oldId, String newId) {
		for (TownData town : TownDataManager.getInstance().getAllTowns()) {
			if (oldId.equals(town.getEmpireId())) {
				town.setEmpireId(newId);
				TownDataManager.getInstance().saveTown(town);
			}
		}
	}

	/**
	 * Transfers empire leadership to another member town's Monarch.
	 * Updates capital to the successor's town and syncs wartown monarch fields.
	 */
	public String transferEmpire(UUID emperorUuid, UUID successorUuid) {
		EmpireData empire = getEmpireByEmperor(emperorUuid);
		if (empire == null) {
			return "Only the Emperor or Empress can step down!";
		}

		if (successorUuid.equals(emperorUuid)) {
			return "You cannot give the crown to yourself!";
		}

		TownData successorTown = TownDataManager.getInstance().getMonarchTown(successorUuid);
		if (successorTown == null) {
			return "That player is not a Monarch!";
		}

		if (!empire.getMemberTownIds().contains(successorTown.getId())) {
			return "That Monarch's town is not in your empire!";
		}

		String oldEmperorName = empire.getEmperorName();
		String oldCapitalId = empire.getCapitalTownId();
		String successorName = successorTown.getMonarchName();
		if (successorName == null || successorName.isEmpty()) {
			successorName = successorTown.getMemberName(successorUuid);
		}

		empire.setEmperorUuid(successorUuid);
		empire.setEmperorName(successorName);
		// Derive Emperor/Empress from the successor's King/Queen title
		String monarchTitle = successorTown.getMonarchTitle();
		empire.setEmperorTitle("Queen".equals(monarchTitle) ? "Empress" : "Emperor");
		empire.setCapitalTownId(successorTown.getId());

		saveEmpire(empire);

		TownData warTown = TownDataManager.getInstance().getWarTownForEmpire(empire.getId());
		if (warTown != null) {
			warTown.setMonarchUuid(successorUuid);
			warTown.setMonarchName(successorName);
			TownDataManager.getInstance().saveTown(warTown);
		}

		PlayerPrefixManager.refreshPlayer(emperorUuid);
		PlayerPrefixManager.refreshPlayer(successorUuid);
		PlayerPrefixManager.refreshForTown(successorTown);
		TownData oldEmperorTown = TownDataManager.getInstance().getPlayerTown(emperorUuid);
		if (oldEmperorTown != null
				&& !oldEmperorTown.getId().equals(successorTown.getId())) {
			PlayerPrefixManager.refreshForTown(oldEmperorTown);
		}
		if (oldCapitalId != null && !oldCapitalId.equals(successorTown.getId())
				&& (oldEmperorTown == null || !oldCapitalId.equals(oldEmperorTown.getId()))) {
			TownData oldCapital = TownDataManager.getInstance().getTown(oldCapitalId);
			if (oldCapital != null) {
				PlayerPrefixManager.refreshForTown(oldCapital);
			}
		}

		Siegedempires.LOGGER.info("Empire " + empire.getName() + " leadership transferred from "
				+ oldEmperorName + " to " + successorName);
		return null;
	}

	/**
	 * Dissolves an empire: every member town (and any town still pointing at this
	 * empire id) is detached so they are independent again — default neutral to
	 * each other. Also removes wartown, diplomacy, and active invasions.
	 */
	public String deleteEmpire(UUID emperorUuid) {
		EmpireData empire = getEmpireByEmperor(emperorUuid);
		if (empire == null) {
			return "Only the Emperor or Empress can delete the empire!";
		}

		String empireId = empire.getId();
		String empireName = empire.getName();
		String empireRef = DiplomacyRecord.ref(DiplomacyRecord.TYPE_EMPIRE, empireId);

		Set<String> townIdsToDetach = new HashSet<>(empire.getMemberTownIds());
		for (TownData town : TownDataManager.getInstance().getAllTowns()) {
			if (town == null || town.isWarTown()) {
				continue;
			}
			if (empireId.equals(town.getEmpireId())) {
				townIdsToDetach.add(town.getId());
			}
		}

		for (String townId : townIdsToDetach) {
			TownData town = TownDataManager.getInstance().getTown(townId);
			if (town == null || town.isWarTown()) {
				continue;
			}
			town.setEmpireId(null);
			String previousPixels = BannerHelper.restoreOwnBannerAfterLeavingEmpire(town);
			TownDataManager.getInstance().saveTown(town);
			if (previousPixels != null) {
				BannerManager.syncBannersForTown(BannerManager.getServer(), town, previousPixels);
			}
			PlayerPrefixManager.refreshForTown(town);
		}

		TownDataManager.getInstance().deleteWarTownForEmpire(empireId);
		InvasionManager.cancelInvasionsInvolving(empireRef);
		DiplomacyDataManager.getInstance().removeEntity(DiplomacyRecord.TYPE_EMPIRE, empireId);

		empiresById.remove(empireId);
		DataStorage.deleteEmpire(empireId);

		Siegedempires.LOGGER.info("Deleted empire: " + empireName
				+ " — detached " + townIdsToDetach.size() + " town(s)");
		return null;
	}
}
