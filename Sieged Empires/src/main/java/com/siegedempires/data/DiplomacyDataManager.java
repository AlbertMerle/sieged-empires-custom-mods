package com.siegedempires.data;

import com.siegedempires.Siegedempires;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DiplomacyDataManager {
	private static DiplomacyDataManager INSTANCE;

	private final Map<String, DiplomacyRecord> recordsByKey = new HashMap<>();

	private DiplomacyDataManager() {
	}

	public static DiplomacyDataManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new DiplomacyDataManager();
		}
		return INSTANCE;
	}

	public static String key(String entityType, String entityId) {
		return entityType + ":" + entityId;
	}

	public void loadAll() {
		recordsByKey.clear();
		for (String fileKey : DataStorage.getAllDiplomacyKeys()) {
			DiplomacyRecord record = DataStorage.loadDiplomacy(fileKey);
			if (record != null && record.getEntityType() != null && record.getEntityId() != null) {
				recordsByKey.put(key(record.getEntityType(), record.getEntityId()), record);
			}
		}
		migrateLegacyTownAndEmpireData();
		Siegedempires.LOGGER.info("Loaded " + recordsByKey.size() + " diplomacy records");
	}

	private void migrateLegacyTownAndEmpireData() {
		for (TownData town : TownDataManager.getInstance().getAllTowns()) {
			if (town.isWarTown()) {
				continue;
			}
			String recordKey = key(DiplomacyRecord.TYPE_TOWN, town.getId());
			if (recordsByKey.containsKey(recordKey)) {
				continue;
			}
			if (isEmpty(town.getAllies()) && isEmpty(town.getEnemies())) {
				continue;
			}
			DiplomacyRecord record = new DiplomacyRecord(DiplomacyRecord.TYPE_TOWN, town.getId());
			record.setAllies(normalizeRefs(town.getAllies()));
			record.setEnemies(normalizeRefs(town.getEnemies()));
			save(record);
		}

		for (EmpireData empire : EmpireDataManager.getInstance().getAllEmpires()) {
			String recordKey = key(DiplomacyRecord.TYPE_EMPIRE, empire.getId());
			if (recordsByKey.containsKey(recordKey)) {
				continue;
			}
			if (isEmpty(empire.getAllies()) && isEmpty(empire.getEnemies())) {
				continue;
			}
			DiplomacyRecord record = new DiplomacyRecord(DiplomacyRecord.TYPE_EMPIRE, empire.getId());
			record.setAllies(normalizeRefs(empire.getAllies()));
			record.setEnemies(normalizeRefs(empire.getEnemies()));
			save(record);
		}
	}

	private static boolean isEmpty(Set<String> set) {
		return set == null || set.isEmpty();
	}

	private static Set<String> normalizeRefs(Set<String> refs) {
		Set<String> normalized = new HashSet<>();
		if (refs == null) {
			return normalized;
		}
		for (String ref : refs) {
			if (ref == null || ref.isEmpty()) {
				continue;
			}
			if (ref.contains(":")) {
				normalized.add(ref);
			} else {
				normalized.add(DiplomacyRecord.ref(DiplomacyRecord.TYPE_TOWN, ref));
			}
		}
		return normalized;
	}

	public DiplomacyRecord getOrCreate(String entityType, String entityId) {
		String recordKey = key(entityType, entityId);
		DiplomacyRecord record = recordsByKey.get(recordKey);
		if (record == null) {
			record = new DiplomacyRecord(entityType, entityId);
			recordsByKey.put(recordKey, record);
			DataStorage.saveDiplomacy(record);
		}
		return record;
	}

	public DiplomacyRecord get(String entityType, String entityId) {
		return recordsByKey.get(key(entityType, entityId));
	}

	public void save(DiplomacyRecord record) {
		if (record == null || record.getEntityType() == null || record.getEntityId() == null) {
			return;
		}
		recordsByKey.put(key(record.getEntityType(), record.getEntityId()), record);
		DataStorage.saveDiplomacy(record);
	}

	public void removeEntity(String entityType, String entityId) {
		if (entityType == null || entityId == null || entityId.isEmpty()) {
			return;
		}
		String recordKey = key(entityType, entityId);
		recordsByKey.remove(recordKey);
		DataStorage.deleteDiplomacy(entityType, entityId);

		String ref = DiplomacyRecord.ref(entityType, entityId);
		for (DiplomacyRecord record : new HashSet<>(recordsByKey.values())) {
			boolean changed = false;
			changed |= record.getAllies().remove(ref);
			changed |= record.getEnemies().remove(ref);
			changed |= record.getPendingAllyInvites().remove(ref);
			changed |= record.getPendingTradeRequests().remove(ref);
			changed |= record.getPendingOpenBorderRequests().remove(ref);
			changed |= record.getPendingPeaceRequests().remove(ref);
			changed |= record.getAllyTrade().remove(ref);
			changed |= record.getAllyOpenBorders().remove(ref);
			if (changed) {
				save(record);
			}
		}
	}

	/**
	 * Moves a town or empire diplomacy record to a new id and rewrites every
	 * {@code type:id} reference that pointed at the old id.
	 */
	public void renameEntity(String entityType, String oldId, String newId) {
		if (entityType == null || oldId == null || newId == null
				|| oldId.isEmpty() || newId.isEmpty() || oldId.equals(newId)) {
			return;
		}

		String oldRef = DiplomacyRecord.ref(entityType, oldId);
		String newRef = DiplomacyRecord.ref(entityType, newId);

		DiplomacyRecord record = get(entityType, oldId);
		if (record != null) {
			recordsByKey.remove(key(entityType, oldId));
			DataStorage.deleteDiplomacy(entityType, oldId);
			record.setEntityId(newId);
			save(record);
		}

		for (DiplomacyRecord other : new HashSet<>(recordsByKey.values())) {
			if (replaceRef(other, oldRef, newRef)) {
				save(other);
			}
		}
	}

	private static boolean replaceRef(DiplomacyRecord record, String oldRef, String newRef) {
		boolean changed = false;
		changed |= replaceInSet(record.getAllies(), oldRef, newRef);
		changed |= replaceInSet(record.getEnemies(), oldRef, newRef);
		changed |= replaceInSet(record.getPendingAllyInvites(), oldRef, newRef);
		changed |= replaceInSet(record.getPendingTradeRequests(), oldRef, newRef);
		changed |= replaceInSet(record.getPendingOpenBorderRequests(), oldRef, newRef);
		changed |= replaceInSet(record.getPendingPeaceRequests(), oldRef, newRef);
		changed |= replaceInSet(record.getAllyTrade(), oldRef, newRef);
		changed |= replaceInSet(record.getAllyOpenBorders(), oldRef, newRef);
		return changed;
	}

	private static boolean replaceInSet(Set<String> set, String oldRef, String newRef) {
		if (set == null || !set.remove(oldRef)) {
			return false;
		}
		set.add(newRef);
		return true;
	}
}
