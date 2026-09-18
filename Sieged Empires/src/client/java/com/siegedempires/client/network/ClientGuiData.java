package com.siegedempires.client.network;

import com.siegedempires.network.EmpireListBuilder;
import com.siegedempires.network.EmpireListData;
import com.siegedempires.network.JoinListBuilder;
import com.siegedempires.network.JoinListData;
import com.siegedempires.network.DiplomacyBuilder;
import com.siegedempires.network.DiplomacyData;
import com.siegedempires.network.ManageEmpireBuilder;
import com.siegedempires.network.ManageEmpireData;
import com.siegedempires.network.MailBuilder;
import com.siegedempires.network.MailData;
import com.siegedempires.network.ManageTownBuilder;
import com.siegedempires.network.ManageTownData;

public final class ClientGuiData {
	private static boolean inTown;
	private static boolean isMonarch;
	private static boolean isLord;
	private static boolean isEmperor;
	private static boolean canAccessDiplomacy;
	private static JoinListData joinList;
	private static ManageTownData manageTownData;
	private static ManageEmpireData manageEmpireData;
	private static DiplomacyData diplomacyData;
	private static MailData mailData;
	private static Runnable mailListener;
	private static EmpireListData empireList;
	private static Runnable guiStatusListener;
	private static Runnable joinListListener;
	private static Runnable manageTownListener;
	private static Runnable manageEmpireListener;
	private static Runnable diplomacyListener;
	private static Runnable empireListListener;

	private ClientGuiData() {
	}

	public static boolean isInTown() {
		return inTown;
	}

	public static boolean isMonarch() {
		return isMonarch;
	}

	public static boolean isLord() {
		return isLord;
	}

	public static boolean isEmperor() {
		return isEmperor;
	}

	/** Emperors, or independent-town monarchs — not vassal monarchs under another empire. */
	public static boolean canAccessDiplomacy() {
		return canAccessDiplomacy;
	}

	public static boolean canManageTown() {
		return isMonarch || isLord;
	}

	public static JoinListData getJoinList() {
		return joinList;
	}

	public static ManageTownData getManageTownData() {
		return manageTownData;
	}

	public static ManageEmpireData getManageEmpireData() {
		return manageEmpireData;
	}

	public static DiplomacyData getDiplomacyData() {
		return diplomacyData;
	}

	public static MailData getMailData() {
		return mailData;
	}

	public static void setGuiStatus(boolean inTown, boolean isMonarch, boolean isLord, boolean isEmperor,
	                                boolean canAccessDiplomacy) {
		ClientGuiData.inTown = inTown;
		ClientGuiData.isMonarch = isMonarch;
		ClientGuiData.isLord = isLord;
		ClientGuiData.isEmperor = isEmperor;
		ClientGuiData.canAccessDiplomacy = canAccessDiplomacy;
		if (guiStatusListener != null) {
			guiStatusListener.run();
		}
	}

	public static void setJoinList(String json) {
		joinList = JoinListBuilder.fromJson(json);
		if (joinListListener != null) {
			joinListListener.run();
		}
	}

	public static void setManageTownData(String json) {
		manageTownData = ManageTownBuilder.fromJson(json);
		if (manageTownListener != null) {
			manageTownListener.run();
		}
	}

	public static EmpireListData getEmpireList() {
		return empireList;
	}

	public static void setEmpireList(String json) {
		empireList = EmpireListBuilder.fromJson(json);
		if (empireListListener != null) {
			empireListListener.run();
		}
	}

	public static void setManageEmpireData(String json) {
		manageEmpireData = ManageEmpireBuilder.fromJson(json);
		if (manageEmpireListener != null) {
			manageEmpireListener.run();
		}
	}

	public static void setDiplomacyData(String json) {
		diplomacyData = DiplomacyBuilder.fromJson(json);
		if (diplomacyListener != null) {
			diplomacyListener.run();
		}
	}

	public static void setMailData(String json) {
		mailData = MailBuilder.fromJson(json);
		if (mailListener != null) {
			mailListener.run();
		}
	}

	public static void setMailListener(Runnable listener) {
		mailListener = listener;
	}

	public static void clearMailListener() {
		mailListener = null;
	}

	public static boolean hasMail() {
		return mailData != null && mailData.entries != null && !mailData.entries.isEmpty();
	}

	public static void setEmpireListListener(Runnable listener) {
		empireListListener = listener;
	}

	public static void setGuiStatusListener(Runnable listener) {
		guiStatusListener = listener;
	}

	public static void setJoinListListener(Runnable listener) {
		joinListListener = listener;
	}

	public static void setManageTownListener(Runnable listener) {
		manageTownListener = listener;
	}

	public static void setManageEmpireListener(Runnable listener) {
		manageEmpireListener = listener;
	}

	public static void setDiplomacyListener(Runnable listener) {
		diplomacyListener = listener;
	}

	public static void clearListeners() {
		guiStatusListener = null;
		joinListListener = null;
		manageTownListener = null;
		manageEmpireListener = null;
		diplomacyListener = null;
		empireListListener = null;
		mailListener = null;
	}
}
