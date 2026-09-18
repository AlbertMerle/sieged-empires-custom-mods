package com.siegedempires.client.gui;

import com.siegedempires.client.network.ClientGuiData;
import com.siegedempires.client.network.ClientNetworking;
import com.siegedempires.network.ManageEmpireData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Lists captured wartowns for the emperor to manage. */
public class ManageWartownsListScreen extends SelectionListScreen {
	private final Screen parent;

	public ManageWartownsListScreen(Screen parent) {
		super(Component.translatable("gui.siegedempires.manage_wartowns_title"), parent);
		this.parent = parent;
	}

	@Override
	protected Component searchLabel() {
		return Component.translatable("gui.siegedempires.search_wartown");
	}

	@Override
	protected void requestData() {
		ClientGuiData.setManageEmpireListener(this::rebuildList);
		ClientNetworking.requestManageEmpireData();
	}

	@Override
	protected void clearDataListener() {
		ClientGuiData.setManageEmpireListener(null);
	}

	@Override
	protected boolean isDataLoaded() {
		return ClientGuiData.getManageEmpireData() != null;
	}

	@Override
	protected List<Entry> buildEntries() {
		ManageEmpireData data = ClientGuiData.getManageEmpireData();
		if (data == null || data.warTowns == null) {
			return List.of();
		}
		List<Entry> entries = new ArrayList<>();
		for (ManageEmpireData.WarTownInfo warTown : data.warTowns) {
			entries.add(new Entry(
					warTown.id,
					warTown.name,
					Component.literal(warTown.name),
					warTown.bannerPatterns,
					warTown.bannerBaseColor,
					warTown.bannerPixels,
					() -> minecraft.gui.setScreen(new ManageTownScreen(this, warTown.id))
			));
		}
		return entries;
	}

	@Override
	public void onClose() {
		clearDataListener();
		minecraft.gui.setScreen(parent);
	}
}
