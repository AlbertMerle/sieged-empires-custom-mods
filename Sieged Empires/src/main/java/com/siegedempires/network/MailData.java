package com.siegedempires.network;

import java.util.ArrayList;
import java.util.List;

public class MailData {
	public List<MailEntry> entries = new ArrayList<>();

	public static class MailEntry {
		public String id;
		/** town_invite, empire_invite, ally, trade, open_borders, peace, war, crown, response */
		public String type;
		public boolean actionable;
		public String messageKey;
		public String arg1;
		public String arg2;
		public String arg3;
		/** Display name of the sending town/empire (shown next to the flag). */
		public String senderName;
		/** town / empire — used for copy and diplomacy actions */
		public String entityType;
		public String entityName;
		/** For town/empire/crown actions, or stored mail id for responses */
		public String targetId;
		public List<String> bannerPatterns = new ArrayList<>();
		public String bannerBaseColor;
		public String bannerPixels;
	}
}
