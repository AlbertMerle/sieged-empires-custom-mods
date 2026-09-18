package com.siegedempires.network;

import java.util.ArrayList;
import java.util.List;

public class ManageEmpireData {
	public boolean empirePublic;
	public String empireId;
	public String empireName;
	public String capitalTownId;
	/** Sum of member-town claimed chunks for Edit Empire Name/Banner cost. */
	public int claimedChunks;
	public List<String> bannerPatterns = new ArrayList<>();
	public String bannerBaseColor;
	public String bannerPixels;
	public List<TownInfo> memberTowns = new ArrayList<>();
	public List<TownInfo> inviteableTowns = new ArrayList<>();
	public List<WarTownInfo> warTowns = new ArrayList<>();

	public static class WarTownInfo {
		public String id;
		public String name;
		public List<String> bannerPatterns;
		public String bannerBaseColor;
		public String bannerPixels;
	}

	public static class TownInfo {
		public String id;
		public String name;
		public boolean nation;
		/** Monarch display name for step-down / leadership pickers. */
		public String monarchName;
		public String monarchUuid;
		/**
		 * Display flag design for selection lists. Already resolved on the
		 * server: member towns carry the EMPIRE banner design (towns in an
		 * empire adopt the empire flag), inviteable towns carry their own.
		 */
		public List<String> bannerPatterns;
		public String bannerBaseColor;
		public String bannerPixels;
	}
}
