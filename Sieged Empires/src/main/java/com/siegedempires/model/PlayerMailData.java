package com.siegedempires.model;

import java.util.ArrayList;
import java.util.List;

public class PlayerMailData {
	private List<StoredMailEntry> entries = new ArrayList<>();

	public List<StoredMailEntry> getEntries() {
		if (entries == null) {
			entries = new ArrayList<>();
		}
		return entries;
	}

	public void setEntries(List<StoredMailEntry> entries) {
		this.entries = entries;
	}
}
