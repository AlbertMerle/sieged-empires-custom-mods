package com.siegedempires.network;

import java.util.ArrayList;
import java.util.List;

public class JoinListData {
	public List<TownInfo> invitedTowns = new ArrayList<>();
	public List<EmpireGroup> empires = new ArrayList<>();

	public static class TownInfo {
		public String id;
		public String name;
		public boolean nation;
		public boolean townPublic;
		/**
		 * Display flag design for selection lists. Already resolved on the
		 * server: towns inside an empire carry the EMPIRE banner design,
		 * independent towns carry their own design.
		 */
		public List<String> bannerPatterns;
		public String bannerBaseColor;
		public String bannerPixels;
	}

	public static class EmpireGroup {
		public String id;
		public String name;
		public List<String> bannerPatterns;
		public String bannerBaseColor;
		public String bannerPixels;
		public List<TownInfo> towns = new ArrayList<>();

		public EmpireGroup() {
		}

		public EmpireGroup(String id, String name) {
			this.id = id;
			this.name = name;
		}
	}
}
