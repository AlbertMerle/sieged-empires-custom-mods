package com.siegedempires.network;

import java.util.ArrayList;
import java.util.List;

public class ManageTownData {
	public boolean townPublic;
	public boolean isMonarch;
	public boolean isLord;
	public String empireId;
	public String empireName;
	public String townName;
	public boolean isEmperor;
	/** Set when the emperor is remotely managing a wartown. */
	public String wartownId;
	/** Claimed chunk count for Edit Town Name/Banner cost. */
	public int claimedChunks;
	public List<String> bannerPatterns = new ArrayList<>();
	public String bannerBaseColor;
	public String bannerPixels;
	public List<MemberInfo> members = new ArrayList<>();
	public List<String> onlinePlayers = new ArrayList<>();
	/** All players in the empire (for Crown Duke/Duchess picker). */
	public List<MemberInfo> empirePlayers = new ArrayList<>();

	public static class MemberInfo {
		public String uuid;
		public String name;
		public String role;
	}
}
