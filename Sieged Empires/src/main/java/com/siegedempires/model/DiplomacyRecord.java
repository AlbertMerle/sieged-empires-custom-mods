package com.siegedempires.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Diplomatic relations for one town or empire, stored under
 * {@code config/SiegedEmpires/Diplomacy/}.
 *
 * <p>Each ally/enemy entry uses {@code town:<id>} or {@code empire:<id>}.
 */
public class DiplomacyRecord {
	public static final String TYPE_TOWN = "town";
	public static final String TYPE_EMPIRE = "empire";

	private String entityType;
	private String entityId;
	private Set<String> allies = new HashSet<>();
	private Set<String> enemies = new HashSet<>();
	/** Incoming ally requests from other towns/empires ({@code town:<id>} / {@code empire:<id>}). */
	private Set<String> pendingAllyInvites = new HashSet<>();
	/** Incoming trade requests from existing allies. */
	private Set<String> pendingTradeRequests = new HashSet<>();
	/** Incoming open-borders requests from existing allies. */
	private Set<String> pendingOpenBorderRequests = new HashSet<>();
	/** Incoming peace requests from current enemies. */
	private Set<String> pendingPeaceRequests = new HashSet<>();
	/** Allies with an active trade agreement. */
	private Set<String> allyTrade = new HashSet<>();
	/** Allies with open borders (implies trade as well). */
	private Set<String> allyOpenBorders = new HashSet<>();

	public DiplomacyRecord() {
	}

	public DiplomacyRecord(String entityType, String entityId) {
		this.entityType = entityType;
		this.entityId = entityId;
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public String getEntityId() {
		return entityId;
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	public Set<String> getAllies() {
		return allies;
	}

	public void setAllies(Set<String> allies) {
		this.allies = allies != null ? allies : new HashSet<>();
	}

	public Set<String> getEnemies() {
		return enemies;
	}

	public void setEnemies(Set<String> enemies) {
		this.enemies = enemies != null ? enemies : new HashSet<>();
	}

	public Set<String> getPendingAllyInvites() {
		if (pendingAllyInvites == null) {
			pendingAllyInvites = new HashSet<>();
		}
		return pendingAllyInvites;
	}

	public void setPendingAllyInvites(Set<String> pendingAllyInvites) {
		this.pendingAllyInvites = pendingAllyInvites != null ? pendingAllyInvites : new HashSet<>();
	}

	public Set<String> getPendingTradeRequests() {
		if (pendingTradeRequests == null) {
			pendingTradeRequests = new HashSet<>();
		}
		return pendingTradeRequests;
	}

	public void setPendingTradeRequests(Set<String> pendingTradeRequests) {
		this.pendingTradeRequests = pendingTradeRequests != null ? pendingTradeRequests : new HashSet<>();
	}

	public Set<String> getPendingOpenBorderRequests() {
		if (pendingOpenBorderRequests == null) {
			pendingOpenBorderRequests = new HashSet<>();
		}
		return pendingOpenBorderRequests;
	}

	public void setPendingOpenBorderRequests(Set<String> pendingOpenBorderRequests) {
		this.pendingOpenBorderRequests = pendingOpenBorderRequests != null ? pendingOpenBorderRequests : new HashSet<>();
	}

	public Set<String> getPendingPeaceRequests() {
		if (pendingPeaceRequests == null) {
			pendingPeaceRequests = new HashSet<>();
		}
		return pendingPeaceRequests;
	}

	public void setPendingPeaceRequests(Set<String> pendingPeaceRequests) {
		this.pendingPeaceRequests = pendingPeaceRequests != null ? pendingPeaceRequests : new HashSet<>();
	}

	public Set<String> getAllyTrade() {
		if (allyTrade == null) {
			allyTrade = new HashSet<>();
		}
		return allyTrade;
	}

	public void setAllyTrade(Set<String> allyTrade) {
		this.allyTrade = allyTrade != null ? allyTrade : new HashSet<>();
	}

	public Set<String> getAllyOpenBorders() {
		if (allyOpenBorders == null) {
			allyOpenBorders = new HashSet<>();
		}
		return allyOpenBorders;
	}

	public void setAllyOpenBorders(Set<String> allyOpenBorders) {
		this.allyOpenBorders = allyOpenBorders != null ? allyOpenBorders : new HashSet<>();
	}

	public static String ref(String entityType, String entityId) {
		return entityType + ":" + entityId;
	}

	public static String[] parseRef(String ref) {
		if (ref == null || ref.isEmpty()) {
			return null;
		}
		int sep = ref.indexOf(':');
		if (sep <= 0 || sep >= ref.length() - 1) {
			return new String[]{TYPE_TOWN, ref};
		}
		return new String[]{ref.substring(0, sep), ref.substring(sep + 1)};
	}
}
