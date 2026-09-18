package com.siegedempires.client.network;

import com.siegedempires.client.lock.ClientLockCache;
import com.siegedempires.client.session.SessionJoinCinematic;
import com.siegedempires.network.payload.ConfirmSessionJoinPayload;
import com.siegedempires.network.payload.ConvertInventoryBannersPayload;
import com.siegedempires.network.payload.CreateEmpirePayload;
import com.siegedempires.network.payload.CreateTownPayload;
import com.siegedempires.network.payload.DiplomacyPayload;
import com.siegedempires.network.payload.EmpireListPayload;
import com.siegedempires.network.payload.GuiStatusPayload;
import com.siegedempires.network.payload.JoinListPayload;
import com.siegedempires.network.payload.LockSyncPayload;
import com.siegedempires.network.payload.ManageEmpirePayload;
import com.siegedempires.network.payload.MailActionPayload;
import com.siegedempires.network.payload.MailPayload;
import com.siegedempires.network.payload.ManageTownPayload;
import com.siegedempires.network.payload.RenameTownPayload;
import com.siegedempires.network.payload.RenameTownResultPayload;
import com.siegedempires.network.payload.UpdateTownPayload;
import com.siegedempires.network.payload.UpdateEmpirePayload;
import com.siegedempires.network.payload.UpdateEmpireResultPayload;
import com.siegedempires.network.payload.RequestManageWartownPayload;
import com.siegedempires.network.payload.RequestClearWartownContextPayload;
import com.siegedempires.network.payload.RequestCrownWartownMonarchPayload;
import com.siegedempires.network.payload.PromptDukeDuchessPayload;
import com.siegedempires.network.payload.RequestPendingDukeDuchessPayload;
import com.siegedempires.network.payload.ChooseDukeDuchessPayload;
import com.siegedempires.network.payload.RequestSessionReleasePayload;
import com.siegedempires.network.payload.SessionCinematicPayload;
import com.siegedempires.network.payload.SessionGateStartPayload;
import com.siegedempires.network.payload.SessionReleasedPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworking {
	private static java.util.function.Consumer<RenameTownResultPayload> renameTownResultListener;
	private static java.util.function.Consumer<UpdateEmpireResultPayload> updateEmpireResultListener;
	private static java.util.function.Consumer<PromptDukeDuchessPayload> promptDukeDuchessListener;
	private static PromptDukeDuchessPayload queuedDukeDuchessPrompt;

	private ClientNetworking() {
	}

	public static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(GuiStatusPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientGuiData.setGuiStatus(
					payload.inTown(), payload.isMonarch(), payload.isLord(), payload.isEmperor(),
					payload.canAccessDiplomacy()));
		});

		ClientPlayNetworking.registerGlobalReceiver(JoinListPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientGuiData.setJoinList(payload.json()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ManageTownPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientGuiData.setManageTownData(payload.json()));
		});

		ClientPlayNetworking.registerGlobalReceiver(EmpireListPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientGuiData.setEmpireList(payload.json()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ManageEmpirePayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientGuiData.setManageEmpireData(payload.json()));
		});

		ClientPlayNetworking.registerGlobalReceiver(DiplomacyPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientGuiData.setDiplomacyData(payload.json()));
		});

		ClientPlayNetworking.registerGlobalReceiver(MailPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientGuiData.setMailData(payload.json()));
		});

		// Lock overlay sync
		ClientPlayNetworking.registerGlobalReceiver(LockSyncPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientLockCache.updateFromJson(payload.json()));
		});

		ClientPlayNetworking.registerGlobalReceiver(SessionGateStartPayload.TYPE, (payload, context) -> {
			context.client().execute(SessionJoinCinematic::onGateStart);
		});

		ClientPlayNetworking.registerGlobalReceiver(SessionCinematicPayload.TYPE, (payload, context) -> {
			context.client().execute(() ->
					SessionJoinCinematic.onCinematicInfo(
							payload.title(), payload.kind(), payload.subtitle(), payload.snowy()));
		});

		ClientPlayNetworking.registerGlobalReceiver(SessionReleasedPayload.TYPE, (payload, context) -> {
			context.client().execute(SessionJoinCinematic::onReleased);
		});

		ClientPlayNetworking.registerGlobalReceiver(RenameTownResultPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				if (renameTownResultListener != null) {
					renameTownResultListener.accept(payload);
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(UpdateEmpireResultPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				if (updateEmpireResultListener != null) {
					updateEmpireResultListener.accept(payload);
				}
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(PromptDukeDuchessPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> deliverDukeDuchessPrompt(payload));
		});
	}

	private static void deliverDukeDuchessPrompt(PromptDukeDuchessPayload payload) {
		if (promptDukeDuchessListener != null) {
			promptDukeDuchessListener.accept(payload);
			return;
		}
		if (SessionJoinCinematic.shouldDeferDukeDuchessPrompt()) {
			queuedDukeDuchessPrompt = payload;
			return;
		}
		showDukeDuchessPrompt(payload);
	}

	private static void showDukeDuchessPrompt(PromptDukeDuchessPayload payload) {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (client == null) {
			return;
		}
		client.gui.setScreen(new com.siegedempires.client.gui.DukeDuchessScreen(
				null, payload.wartownId(), payload.wartownName(), payload.emperorName()));
	}

	/** Called when the session join cinematic finishes (or is skipped). */
	public static void onSessionCinematicComplete() {
		if (queuedDukeDuchessPrompt != null) {
			PromptDukeDuchessPayload payload = queuedDukeDuchessPrompt;
			queuedDukeDuchessPrompt = null;
			showDukeDuchessPrompt(payload);
			return;
		}
		ClientPlayNetworking.send(new RequestPendingDukeDuchessPayload());
	}

	public static void requestGuiStatus() {
		ClientPlayNetworking.send(new com.siegedempires.network.payload.RequestGuiStatusPayload());
	}

	public static void requestJoinList() {
		ClientPlayNetworking.send(new com.siegedempires.network.payload.RequestJoinListPayload());
	}

	public static void requestManageTownData() {
		ClientPlayNetworking.send(new com.siegedempires.network.payload.RequestManageTownPayload());
	}

	public static void requestEmpireList() {
		ClientPlayNetworking.send(new com.siegedempires.network.payload.RequestEmpireListPayload());
	}

	public static void requestManageEmpireData() {
		ClientPlayNetworking.send(new com.siegedempires.network.payload.RequestManageEmpirePayload());
	}

	public static void requestDiplomacyData() {
		ClientPlayNetworking.send(new com.siegedempires.network.payload.RequestDiplomacyPayload());
	}

	public static void requestMailData() {
		ClientPlayNetworking.send(new com.siegedempires.network.payload.RequestMailPayload());
	}

	public static void sendMailAction(String action, String mailType, String targetId,
	                                  String entityType, String entityName) {
		ClientPlayNetworking.send(new MailActionPayload(
				action == null ? "" : action,
				mailType == null ? "" : mailType,
				targetId == null ? "" : targetId,
				entityType == null ? "" : entityType,
				entityName == null ? "" : entityName));
	}

	public static void confirmSessionJoin() {
		ClientPlayNetworking.send(new ConfirmSessionJoinPayload());
	}

	public static void requestSessionRelease() {
		ClientPlayNetworking.send(new RequestSessionReleasePayload());
	}

	public static void createEmpire(String name, String description, String title,
			String bannerBaseColor, java.util.List<String> bannerPatterns) {
		createEmpire(name, description, title, bannerBaseColor, bannerPatterns, null);
	}

	public static void createEmpire(String name, String description, String title,
			String bannerBaseColor, java.util.List<String> bannerPatterns, String bannerPixels) {
		String base = bannerBaseColor == null || bannerBaseColor.isEmpty() ? "white" : bannerBaseColor;
		String pixels = com.siegedempires.banner.CustomBannerDesign.isValidEncoded(bannerPixels)
				? bannerPixels
				: com.siegedempires.banner.CustomBannerDesign.solid(base).encode();
		ClientPlayNetworking.send(new CreateEmpirePayload(
				name == null ? "" : name,
				description == null ? "" : description,
				title == null ? "" : title,
				base,
				joinPatterns(bannerPatterns),
				pixels));
	}

	public static void createTown(String name, String description, String monarchTitle,
			String bannerBaseColor, java.util.List<String> bannerPatterns) {
		createTown(name, description, monarchTitle, bannerBaseColor, bannerPatterns, null);
	}

	public static void createTown(String name, String description, String monarchTitle,
			String bannerBaseColor, java.util.List<String> bannerPatterns, String bannerPixels) {
		String base = bannerBaseColor == null || bannerBaseColor.isEmpty() ? "white" : bannerBaseColor;
		String pixels = com.siegedempires.banner.CustomBannerDesign.isValidEncoded(bannerPixels)
				? bannerPixels
				: com.siegedempires.banner.CustomBannerDesign.solid(base).encode();
		ClientPlayNetworking.send(new CreateTownPayload(
				name == null ? "" : name,
				description == null ? "" : description,
				monarchTitle == null ? "" : monarchTitle,
				base,
				joinPatterns(bannerPatterns),
				pixels));
	}

	public static void convertInventoryBanners(String factionKind) {
		ClientPlayNetworking.send(new ConvertInventoryBannersPayload(
				factionKind == null ? ConvertInventoryBannersPayload.KIND_TOWN : factionKind));
	}

	public static void requestManageWartownData(String wartownId) {
		ClientPlayNetworking.send(new RequestManageWartownPayload(wartownId == null ? "" : wartownId));
	}

	public static void clearWartownContext() {
		ClientPlayNetworking.send(new RequestClearWartownContextPayload());
	}

	public static void requestCrownWartownMonarch(String wartownId, String targetUuid) {
		ClientPlayNetworking.send(new RequestCrownWartownMonarchPayload(
				wartownId == null ? "" : wartownId,
				targetUuid == null ? "" : targetUuid));
	}

	public static void chooseDukeDuchess(String wartownId, String monarchTitle) {
		ClientPlayNetworking.send(new ChooseDukeDuchessPayload(
				wartownId == null ? "" : wartownId,
				monarchTitle == null ? "" : monarchTitle));
	}

	public static void setPromptDukeDuchessListener(java.util.function.Consumer<PromptDukeDuchessPayload> listener) {
		promptDukeDuchessListener = listener;
	}

	public static void renameTown(String newName) {
		ClientPlayNetworking.send(new RenameTownPayload(newName == null ? "" : newName));
	}

	public static void updateTown(String name, String bannerBaseColor,
			java.util.List<String> bannerPatterns, String bannerPixels) {
		String base = bannerBaseColor == null || bannerBaseColor.isEmpty() ? "white" : bannerBaseColor;
		String pixels = com.siegedempires.banner.CustomBannerDesign.isValidEncoded(bannerPixels)
				? bannerPixels
				: com.siegedempires.banner.CustomBannerDesign.solid(base).encode();
		ClientPlayNetworking.send(new UpdateTownPayload(
				name == null ? "" : name,
				base,
				joinPatterns(bannerPatterns),
				pixels));
	}

	public static void updateEmpire(String name, String bannerBaseColor,
			java.util.List<String> bannerPatterns, String bannerPixels) {
		String base = bannerBaseColor == null || bannerBaseColor.isEmpty() ? "white" : bannerBaseColor;
		String pixels = com.siegedempires.banner.CustomBannerDesign.isValidEncoded(bannerPixels)
				? bannerPixels
				: com.siegedempires.banner.CustomBannerDesign.solid(base).encode();
		ClientPlayNetworking.send(new UpdateEmpirePayload(
				name == null ? "" : name,
				base,
				joinPatterns(bannerPatterns),
				pixels));
	}

	public static void setRenameTownResultListener(java.util.function.Consumer<RenameTownResultPayload> listener) {
		renameTownResultListener = listener;
	}

	public static void setUpdateEmpireResultListener(java.util.function.Consumer<UpdateEmpireResultPayload> listener) {
		updateEmpireResultListener = listener;
	}

	private static String joinPatterns(java.util.List<String> bannerPatterns) {
		if (bannerPatterns == null || bannerPatterns.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (String pattern : bannerPatterns) {
			if (pattern == null || pattern.isEmpty()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(';');
			}
			sb.append(pattern);
		}
		return sb.toString();
	}
}
