package com.siegedempires.client.gui;

import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.network.DiplomacyData;

import java.util.List;

/** Client-side filters for alliance request pickers. */
public final class DiplomacyAllianceFilter {
	private DiplomacyAllianceFilter() {
	}

	public static boolean isExcludedTarget(DiplomacyData diplomacy, String entityType, String entityId) {
		if (diplomacy == null) {
			return false;
		}
		if (entityType.equals(diplomacy.entityType) && entityId.equals(diplomacy.entityId)) {
			return true;
		}
		String ref = DiplomacyRecord.ref(entityType, entityId);
		return containsRef(diplomacy.allies, ref) || containsRef(diplomacy.enemies, ref);
	}

	private static boolean containsRef(List<DiplomacyData.FactionInfo> factions, String ref) {
		if (factions == null) {
			return false;
		}
		for (DiplomacyData.FactionInfo faction : factions) {
			if (ref.equals(DiplomacyRecord.ref(faction.entityType, faction.entityId))) {
				return true;
			}
		}
		return false;
	}
}
