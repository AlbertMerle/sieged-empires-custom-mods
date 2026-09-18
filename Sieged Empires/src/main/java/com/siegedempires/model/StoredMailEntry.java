package com.siegedempires.model;

/**
 * Informational mail stored on disk (response notifications, war declarations, etc.).
 * Actionable invites are derived live from town/empire/diplomacy state.
 */
public class StoredMailEntry {
	public String id;
	/** MailBuilder type: response, war, etc. Null/empty treated as response. */
	public String type;
	public String messageKey;
	public String arg1;
	public String arg2;
	public String arg3;
	/** Optional sender faction for flag + name display. */
	public String entityType;
	public String entityId;
	public String senderName;
	public long createdAt;

	public StoredMailEntry() {
	}

	public StoredMailEntry(String id, String messageKey, String arg1, String arg2, String arg3) {
		this(id, "response", messageKey, arg1, arg2, arg3, null, null, null);
	}

	public StoredMailEntry(String id, String type, String messageKey,
	                       String arg1, String arg2, String arg3,
	                       String entityType, String entityId, String senderName) {
		this.id = id;
		this.type = type == null || type.isEmpty() ? "response" : type;
		this.messageKey = messageKey;
		this.arg1 = arg1 == null ? "" : arg1;
		this.arg2 = arg2 == null ? "" : arg2;
		this.arg3 = arg3 == null ? "" : arg3;
		this.entityType = entityType == null ? "" : entityType;
		this.entityId = entityId == null ? "" : entityId;
		this.senderName = senderName == null ? "" : senderName;
		this.createdAt = System.currentTimeMillis();
	}
}
