package com.siegedempires.client.title;

import com.siegedempires.Siegedempires;
import com.siegedempires.config.ModSettings;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches the title-screen Join IP from {@code join-button-ip-url} (GitHub gist
 * raw text) on a background thread. Falls back to {@code join-button-ip} until
 * a fetch succeeds, and keeps the last good address if a later fetch fails.
 */
public final class JoinAddressResolver {
	private static final JoinAddressResolver INSTANCE = new JoinAddressResolver();
	private static final long REFRESH_MS = 5L * 60L * 1000L;
	private static final long RETRY_FAIL_MS = 60L * 1000L;
	private static final int TIMEOUT_MS = 5000;
	private static final Pattern GIST_HTML = Pattern.compile(
			"^https?://gist\\.github\\.com/([^/]+)/([0-9a-fA-F]+)(?:/.*)?(?:#.*)?$",
			Pattern.CASE_INSENSITIVE);

	private volatile String fetchedAddress = "";
	private volatile boolean fetchInFlight;
	private volatile long lastAttemptMs = Long.MIN_VALUE;
	private volatile long lastSuccessMs = Long.MIN_VALUE;

	private JoinAddressResolver() {
	}

	public static JoinAddressResolver get() {
		return INSTANCE;
	}

	/** Config fallback, or last successful gist fetch when the URL is set. */
	public String currentAddress() {
		String fetched = this.fetchedAddress;
		if (fetched != null && !fetched.isEmpty()) {
			return fetched;
		}
		return ModSettings.get().joinButtonAddress();
	}

	/** Kick a fetch if the URL is set and the cache is stale. Safe on any thread. */
	public void tick() {
		String url = ModSettings.get().joinButtonIpUrl();
		if (url.isEmpty()) {
			this.fetchedAddress = "";
			return;
		}

		long now = Util.getMillis();
		if (this.fetchInFlight) {
			return;
		}
		boolean haveAddress = this.fetchedAddress != null && !this.fetchedAddress.isEmpty();
		if (haveAddress && now - this.lastSuccessMs < REFRESH_MS) {
			return;
		}
		if (this.lastAttemptMs != Long.MIN_VALUE && now - this.lastAttemptMs < RETRY_FAIL_MS) {
			return;
		}

		this.startFetch(url);
	}

	private void startFetch(String url) {
		this.fetchInFlight = true;
		this.lastAttemptMs = Util.getMillis();
		Thread worker = new Thread(() -> this.fetch(url), "siegedempires-join-ip");
		worker.setDaemon(true);
		worker.start();
	}

	private void fetch(String url) {
		try {
			String address = downloadAddress(normalizeUrl(url));
			if (address != null) {
				String previous = this.fetchedAddress;
				this.fetchedAddress = address;
				this.lastSuccessMs = Util.getMillis();
				if (!address.equals(previous)) {
					Siegedempires.LOGGER.info("Join address from gist: {}", address);
				}
			} else {
				Siegedempires.LOGGER.warn("Join-address URL did not contain a valid host or host:port");
			}
		} catch (Exception e) {
			Siegedempires.LOGGER.warn("Failed to fetch join address from URL: {}", e.toString());
		} finally {
			this.fetchInFlight = false;
		}
	}

	static String normalizeUrl(String url) {
		String trimmed = url.trim();
		Matcher gist = GIST_HTML.matcher(trimmed);
		if (gist.matches()) {
			return "https://gist.githubusercontent.com/" + gist.group(1) + "/" + gist.group(2) + "/raw";
		}
		return trimmed;
	}

	private static String downloadAddress(String url) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
		conn.setInstanceFollowRedirects(true);
		conn.setConnectTimeout(TIMEOUT_MS);
		conn.setReadTimeout(TIMEOUT_MS);
		conn.setRequestProperty("User-Agent", "SiegedEmpires-JoinAddress");
		conn.setRequestProperty("Accept", "text/plain,*/*");
		try {
			int code = conn.getResponseCode();
			if (code < 200 || code >= 300) {
				throw new IllegalStateException("HTTP " + code);
			}
			try (InputStream in = conn.getInputStream()) {
				return parseAddress(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
		} finally {
			conn.disconnect();
		}
	}

	static String parseAddress(String body) {
		if (body == null) {
			return null;
		}
		String lower = body.toLowerCase(Locale.ROOT);
		if (lower.contains("<html") || lower.contains("<!doctype")) {
			return null;
		}
		for (String rawLine : body.split("\\R")) {
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int hash = line.indexOf('#');
			if (hash >= 0) {
				line = line.substring(0, hash).trim();
			}
			if (ServerAddress.isValidAddress(line)) {
				return line;
			}
			return null;
		}
		return null;
	}
}
