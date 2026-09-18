package com.siegedempires.network;

import java.util.ArrayList;
import java.util.List;

public class DiplomacyData {
	public String entityType;
	public String entityId;
	public String entityName;
	/** Player's town name (always set when in a town). */
	public String townName;
	/** Empire name when the town belongs to an empire; otherwise null/empty. */
	public String empireName;
	public List<String> bannerPatterns = new ArrayList<>();
	public String bannerBaseColor;
	/** Custom 20×40 dye-pixel hex encoding; preferred when valid. */
	public String bannerPixels;
	public boolean canManage;
	public boolean hasActiveInvasion;
	public int invasionMinOnlinePlayers = 3;
	public List<FactionInfo> allies = new ArrayList<>();
	public List<FactionInfo> enemies = new ArrayList<>();
	public List<PendingNotification> notifications = new ArrayList<>();

	public static class FactionInfo {
		public String entityType;
		public String entityId;
		public String name;
		public List<String> bannerPatterns = new ArrayList<>();
		public String bannerBaseColor;
		public String bannerPixels;
		public int onlineCount;
		public boolean hasTrade;
		public boolean hasOpenBorders;
	}

	public static class PendingNotification {
		public String inviteType;
		public String entityType;
		public String entityId;
		public String name;
		public List<String> bannerPatterns = new ArrayList<>();
		public String bannerBaseColor;
		public String bannerPixels;
	}
}
