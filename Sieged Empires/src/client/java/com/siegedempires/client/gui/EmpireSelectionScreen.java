package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.network.DiplomacyData;
import com.siegedempires.network.EmpireListData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * The unified empire selection screen. Used by EVERY flow where a player
 * picks an empire (currently: Join Empire). Same layout as
 * {@link TownSelectionScreen} (see {@link SelectionListScreen}): search
 * bar on top, scrollable empire list, and each empire's flag directly to
 * the left of its name. Invited empires are listed first with a mail icon.
 */
public class EmpireSelectionScreen extends SelectionListScreen {
	private enum Mode {
		JOIN,
		ALLIANCE_REQUEST,
		DECLARE_WAR
	}

	private final Mode mode;

	private EmpireSelectionScreen(Component title, Screen parent, Mode mode) {
		super(title, parent);
		this.mode = mode;
	}

	/** Join Empire flow: lists every empire, invited empires first. */
	public static EmpireSelectionScreen forJoin(Screen parent) {
		return new EmpireSelectionScreen(
				Component.translatable("gui.siegedempires.join_empire_title"), parent, Mode.JOIN);
	}

	/** Diplomacy: pick an empire to request alliance with. */
	public static EmpireSelectionScreen forAllianceRequest(Screen parent) {
		return new EmpireSelectionScreen(
				Component.translatable("gui.siegedempires.request_alliance_title"), parent, Mode.ALLIANCE_REQUEST);
	}

	/** Diplomacy: pick an empire to declare war on. */
	public static EmpireSelectionScreen forDeclareWar(Screen parent) {
		return new EmpireSelectionScreen(
				Component.translatable("gui.siegedempires.declare_war_title"), parent, Mode.DECLARE_WAR);
	}

	@Override
	protected Component searchLabel() {
		return Component.translatable("gui.siegedempires.search_empire");
	}

	@Override
	protected void requestData() {
		ClientGuiData.setEmpireListListener(this::rebuildList);
		ClientNetworking.requestEmpireList();
		if (mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) {
			ClientGuiData.setDiplomacyListener(this::rebuildList);
			ClientNetworking.requestDiplomacyData();
		}
	}

	@Override
	protected void clearDataListener() {
		ClientGuiData.setEmpireListListener(null);
		if (mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) {
			ClientGuiData.setDiplomacyListener(null);
		}
	}

	@Override
	protected boolean isDataLoaded() {
		if (mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) {
			return ClientGuiData.getEmpireList() != null && ClientGuiData.getDiplomacyData() != null;
		}
		return ClientGuiData.getEmpireList() != null;
	}

	@Override
	protected List<Entry> buildEntries() {
		EmpireListData data = ClientGuiData.getEmpireList();
		if (data == null) {
			return null;
		}

		DiplomacyData diplomacy = ClientGuiData.getDiplomacyData();
		List<Entry> entries = new ArrayList<>();
		for (EmpireListData.EmpireInfo empire : data.empires) {
			if ((mode == Mode.ALLIANCE_REQUEST || mode == Mode.DECLARE_WAR) && diplomacy != null
					&& DiplomacyAllianceFilter.isExcludedTarget(diplomacy, DiplomacyRecord.TYPE_EMPIRE, empire.id)) {
				continue;
			}

			MutableComponent label = Component.literal(empire.name);
			if (mode == Mode.JOIN && empire.invited) {
				label.append(Component.literal(" \u2709"));
			}
			entries.add(new Entry(empire.id, empire.name, label,
					empire.bannerPatterns, empire.bannerBaseColor, empire.bannerPixels,
					() -> onEmpireSelected(empire)));
		}
		return entries;
	}

	private void onEmpireSelected(EmpireListData.EmpireInfo empire) {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		if (mode == Mode.ALLIANCE_REQUEST) {
			minecraft.gui.setScreen(new ConfirmAllyRequestScreen(this,
					DiplomacyRecord.TYPE_EMPIRE, empire.name));
			return;
		}
		if (mode == Mode.DECLARE_WAR) {
			minecraft.gui.setScreen(new ConfirmDeclareWarScreen(this, parent,
					DiplomacyRecord.TYPE_EMPIRE, empire.name));
			return;
		}
		tryJoinEmpire(empire);
	}

	@Override
	protected HeaderAction headerAction() {
		if (mode == Mode.ALLIANCE_REQUEST) {
			return new HeaderAction(
					Component.translatable("gui.siegedempires.find_town"),
					() -> minecraft.gui.setScreen(TownSelectionScreen.forAllianceRequest(this)));
		}
		if (mode == Mode.DECLARE_WAR) {
			return new HeaderAction(
					Component.translatable("gui.siegedempires.find_town"),
					() -> minecraft.gui.setScreen(TownSelectionScreen.forDeclareWar(this)));
		}
		return null;
	}

	private void tryJoinEmpire(EmpireListData.EmpireInfo empire) {
		if (minecraft == null || minecraft.player == null) {
			return;
		}

		if (!empire.empirePublic && !empire.invited) {
			showError(Component.translatable("gui.siegedempires.private_empire").getString());
			return;
		}

		minecraft.gui.setScreen(new ConfirmJoinEmpireScreen(this, empire));
	}
}
