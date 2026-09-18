package com.siegedempires.banner;

/**
 * Client hook for claim-banner UI feedback. Registered from {@code SiegedempiresClient}.
 * Needed because a client-side {@code InteractionResult.FAIL} does not send a use packet
 * to the server (MC 26.2), so deny titles must render on the client in dev / SP.
 */
public final class ClaimBannerClientFeedback {
	private static Runnable alreadyClaimedHandler;

	private ClaimBannerClientFeedback() {
	}

	public static void registerAlreadyClaimed(Runnable handler) {
		alreadyClaimedHandler = handler;
	}

	public static void showAlreadyClaimed() {
		if (alreadyClaimedHandler != null) {
			alreadyClaimedHandler.run();
		}
	}
}
