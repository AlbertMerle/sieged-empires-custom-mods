package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.network.DiplomacyData;
import com.siegedempires.network.JoinListData;
import com.siegedempires.network.ManageEmpireData;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The unified town selection screen. Used by EVERY flow where a player
 * picks a town: Join Town/Nation, Invite Town to Empire, and Disannex
 * Town from Empire. Same layout everywhere (see {@link SelectionListScreen}):
 * search bar on top, scrollable town list, and each town's flag directly
 * to the left of its name. Towns inside an empire show the EMPIRE flag.
 */
public class TownSelectionScreen extends SelectionListScreen {
	private enum Mode {
		JOIN,
		INVITE,
		DISANNEX,
		ALLIANCE_REQUEST,
		DECLARE_WAR
	}

	private final Mode mode;

	private TownSelectionScreen(Component title, Screen parent, Mode mode) {
		super(title, parent);
		this.mode = mode;
	}

	/** Join Town/Nation flow: lists every town, invited towns first. */
	public static TownSelectionScreen forJoin(Screen parent) {
		return new TownSelectionScreen(
				Component.translatable("gui.siegedempires.join_town_title"), parent, Mode.JOIN);
	}

	/** Empire management: pick an empire-less town to invite. */
	public static TownSelectionScreen forInvite(Screen parent) {
		return new TownSelectionScreen(
				Component.translatable("gui.siegedempires.invite_town_empire_title"), parent, Mode.INVITE);
	}

	/** Empire management: pick a member town (except the capital) to disannex. */
	public static TownSelectionScreen forDisannex(Screen parent) {
		return new TownSelectionScreen(
				Component.translatable("gui.siegedempires.disannex_town_empire_title"), parent, Mode.DISANNEX);
	}

	/** Diplomacy: pick a town to request alliance with. */
	public static TownSelectionScreen forAllianceRequest(Screen parent) {
		return new TownSelectionScreen(
				Component.translatable("gui.siegedempires.request_alliance_title"), parent, Mode.ALLIANCE_REQUEST);
	}

	/** Diplomacy: pick an independent town/nation to declare war on. */
	public static TownSelectionScreen forDeclareWar(Screen parent) {
		return new TownSelectionScreen(
				Component.translatable("gui.siegedempires.declare_war_title"), parent, Mode.DECLARE_WAR);
	}

	@Override
	protected Component searchLabel() {
		return Component.translatable("gui.siegedempires.search_town");
	}

	@Override
	protected void requestData() {
		if (mode == Mode.JOIN || mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) {
			ClientGuiData.setJoinListListener(this::rebuildList);
			ClientNetworking.requestJoinList();
			if (mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) {
				ClientGuiData.setDiplomacyListener(this::rebuildList);
				ClientNetworking.requestDiplomacyData();
			}
		} else {
			ClientGuiData.setManageEmpireListener(this::rebuildList);
			ClientNetworking.requestManageEmpireData();
		}
	}

	@Override
	protected void clearDataListener() {
		if (mode == Mode.JOIN || mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) {
			ClientGuiData.setJoinListListener(null);
			if (mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) {
				ClientGuiData.setDiplomacyListener(null);
			}
		} else {
			ClientGuiData.setManageEmpireListener(null);
		}
	}

	@Override
	protected boolean isDataLoaded() {
		if (mode == Mode.JOIN || mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) {
			return ClientGuiData.getJoinList() != null
					&& (mode == Mode.JOIN
					|| ClientGuiData.getDiplomacyData() != null);
		}
		return ClientGuiData.getManageEmpireData() != null;
	}

	@Override
	protected List<Entry> buildEntries() {
		if (mode == Mode.JOIN || mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) {
			return buildJoinEntries();
		}
		if (mode == Mode.INVITE) {
			return buildInviteEntries();
		}
		return buildDisannexEntries();
	}

	@Override
	protected void addExtraButtons(int centerX) {
		if (mode != Mode.INVITE) {
			return;
		}

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.siegedempires.invite"),
				button -> inviteSearchedTown()
		).bounds(centerX - BUTTON_WIDTH / 2, actionButtonY(), BUTTON_WIDTH, ROW_HEIGHT).build());
	}

	@Override
	protected boolean hasExtraButtons() {
		return mode == Mode.INVITE;
	}

	// ------------------------------------------------------------------
	// JOIN: every town in one flat list (invited first), each row shows
	// the empire flag when the town is in an empire, else its own flag.
	// ------------------------------------------------------------------

	private List<Entry> buildJoinEntries() {
		JoinListData data = ClientGuiData.getJoinList();
		if (data == null) {
			return null;
		}

		DiplomacyData diplomacy = ClientGuiData.getDiplomacyData();
		List<Entry> entries = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		if (mode == Mode.JOIN) {
			List<JoinListData.TownInfo> invited = new ArrayList<>(data.invitedTowns);
			invited.sort(Comparator.comparing(town -> town.name.toLowerCase(java.util.Locale.ROOT)));
			for (JoinListData.TownInfo town : invited) {
				entries.add(joinEntry(town, true));
				seen.add(town.id);
			}
		}

		List<JoinListData.TownInfo> rest = new ArrayList<>();
		for (JoinListData.EmpireGroup group : data.empires) {
			if ((mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) && !"none".equals(group.id)) {
				continue;
			}
			for (JoinListData.TownInfo town : group.towns) {
				if (seen.add(town.id)) {
					rest.add(town);
				}
			}
		}
		rest.sort(Comparator.comparing(town -> town.name.toLowerCase(java.util.Locale.ROOT)));
		for (JoinListData.TownInfo town : rest) {
			if ((mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) && diplomacy != null
					&& DiplomacyAllianceFilter.isExcludedTarget(diplomacy, DiplomacyRecord.TYPE_TOWN, town.id)) {
				continue;
			}
			entries.add(joinEntry(town, false));
		}

		return entries;
	}

	private Entry joinEntry(JoinListData.TownInfo town, boolean invited) {
		MutableComponent label = formatTownName(town.nation, town.name);
		if (invited) {
			label.append(Component.literal(" \u2709"));
		}
		return new Entry(town.id, town.name, label,
				town.bannerPatterns, town.bannerBaseColor, town.bannerPixels,
				() -> onTownSelected(town, invited));
	}

	private void onTownSelected(JoinListData.TownInfo town, boolean invited) {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		if (mode == Mode.ALLIANCE_REQUEST) {
			minecraft.gui.setScreen(new ConfirmAllyRequestScreen(this,
					DiplomacyRecord.TYPE_TOWN, town.name));
			return;
		}
		if (mode == Mode.DECLARE_WAR) {
			minecraft.gui.setScreen(new ConfirmDeclareWarScreen(this, parent,
					DiplomacyRecord.TYPE_TOWN, town.name));
			return;
		}
		tryJoinTown(town, invited);
	}

	@Override
	protected HeaderAction headerAction() {
		if (mode == Mode.ALLIANCE_REQUEST) {
			return new HeaderAction(
					Component.translatable("gui.siegedempires.find_empire"),
					() -> minecraft.gui.setScreen(
							EmpireSelectionScreen.forAllianceRequest(diplomacyListParent())));
		}
		if (mode == Mode.DECLARE_WAR) {
			return new HeaderAction(
					Component.translatable("gui.siegedempires.find_empire"),
					() -> minecraft.gui.setScreen(
							EmpireSelectionScreen.forDeclareWar(diplomacyListParent())));
		}
		return null;
	}

	private Screen diplomacyListParent() {
		if (parent instanceof EmpireSelectionScreen empireScreen) {
			return empireScreen.parent;
		}
		return parent;
	}

	private void tryJoinTown(JoinListData.TownInfo town, boolean invited) {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		if (!town.townPublic && !invited) {
			showError(Component.translatable("gui.siegedempires.private_town").getString());
			return;
		}

		minecraft.player.connection.sendCommand("town join " + town.name.replace(" ", "_"));
		minecraft.gui.setScreen(null);
	}

	// ------------------------------------------------------------------
	// INVITE: empire-less towns only; they show their own flag.
	// ------------------------------------------------------------------

	private List<Entry> buildInviteEntries() {
		ManageEmpireData data = ClientGuiData.getManageEmpireData();
		if (data == null || data.inviteableTowns == null) {
			return null;
		}

		List<Entry> entries = new ArrayList<>();
		for (ManageEmpireData.TownInfo town : data.inviteableTowns) {
			entries.add(new Entry(town.id, town.name,
					formatTownName(town.nation, town.name),
					town.bannerPatterns, town.bannerBaseColor, town.bannerPixels,
					() -> inviteTown(town.name)));
		}
		return entries;
	}

	private void inviteSearchedTown() {
		String townName = searchBox != null ? searchBox.getValue().trim() : "";
		if (townName.isEmpty()) {
			showError(Component.translatable("gui.siegedempires.town_name_required").getString());
			return;
		}
		inviteTown(townName);
	}

	private void inviteTown(String townName) {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		minecraft.player.connection.sendCommand("empire invite " + townName.replace(" ", "_"));
		showMessage(Component.translatable("gui.siegedempires.invite_town_sent", townName).getString(), true);
		ClientNetworking.requestManageEmpireData();
	}

	// ------------------------------------------------------------------
	// DISANNEX: member towns except the capital; member towns are inside
	// the empire, so the server already sends the empire flag for them.
	// ------------------------------------------------------------------

	private List<Entry> buildDisannexEntries() {
		ManageEmpireData data = ClientGuiData.getManageEmpireData();
		if (data == null || data.memberTowns == null) {
			return null;
		}

		List<Entry> entries = new ArrayList<>();
		for (ManageEmpireData.TownInfo town : data.memberTowns) {
			if (town.id != null && town.id.equals(data.capitalTownId)) {
				continue;
			}
			entries.add(new Entry(town.id, town.name,
					formatTownName(town.nation, town.name),
					town.bannerPatterns, town.bannerBaseColor, town.bannerPixels,
					() -> minecraft.gui.setScreen(new ConfirmDisannexTownScreen(this, town))));
		}
		return entries;
	}

	private static MutableComponent formatTownName(boolean nation, String name) {
		return Component.translatable(
				nation ? "gui.siegedempires.nation_of" : "gui.siegedempires.town_of", name);
	}
}
