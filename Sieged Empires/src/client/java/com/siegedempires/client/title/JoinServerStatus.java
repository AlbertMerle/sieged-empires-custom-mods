package com.siegedempires.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.Util;

/**
 * Periodically status-pings the Join address (gist fetch, then
 * {@code join-button-ip}) so the title-screen Join button can enable only
 * when the server is reachable.
 */
public final class JoinServerStatus {
	private static final JoinServerStatus INSTANCE = new JoinServerStatus();
	/** ~1 second between ping attempts once the previous attempt finishes. */
	private static final int PING_INTERVAL_TICKS = 20;
	private static final long PING_TIMEOUT_MS = 4000L;

	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private int ticksUntilNextPing;
	private boolean pingInFlight;
	private long pingStartedMs;
	private int pingGeneration;
	private boolean online;
	private String lastAddress = "";
	/** Static init must run on the render thread; background I/O threads break ServiceLoader. */
	private boolean resolverInitialized;

	private JoinServerStatus() {
	}

	public static JoinServerStatus get() {
		return INSTANCE;
	}

	public boolean isOnline() {
		return this.online;
	}

	public void tick(Minecraft minecraft) {
		JoinAddressResolver.get().tick();
		this.ensureResolverInitialized();
		this.pinger.tick();

		if (this.pingInFlight && Util.getMillis() - this.pingStartedMs >= PING_TIMEOUT_MS) {
			this.finishPing(false, this.pingGeneration);
			this.pingGeneration++;
			this.pinger.removeAll();
		}

		String address = JoinAddressResolver.get().currentAddress();
		if (address.isEmpty()
				|| !ServerAddress.isValidAddress(address)
				|| !minecraft.allowsMultiplayer()) {
			if (!address.equals(this.lastAddress) || this.online || this.pingInFlight) {
				this.lastAddress = address;
				this.pingGeneration++;
				this.pingInFlight = false;
				this.online = false;
				this.pinger.removeAll();
			}
			this.ticksUntilNextPing = 0;
			return;
		}

		if (!address.equals(this.lastAddress)) {
			this.lastAddress = address;
			this.pingGeneration++;
			this.pingInFlight = false;
			this.online = false;
			this.pinger.removeAll();
			this.ticksUntilNextPing = 0;
		}

		if (this.pingInFlight) {
			return;
		}

		if (this.ticksUntilNextPing > 0) {
			this.ticksUntilNextPing--;
			return;
		}

		this.ticksUntilNextPing = PING_INTERVAL_TICKS;
		this.startPing(minecraft, address);
	}

	public void shutdown() {
		this.pingGeneration++;
		this.pinger.removeAll();
		this.pingInFlight = false;
		this.ticksUntilNextPing = 0;
	}

	private void startPing(Minecraft minecraft, String address) {
		final int generation = ++this.pingGeneration;
		this.pingInFlight = true;
		this.pingStartedMs = Util.getMillis();

		ServerData data = new ServerData("Sieged Empires", address, ServerData.Type.OTHER);
		data.setState(ServerData.State.PINGING);

		if (!this.resolverInitialized) {
			this.finishPing(false, generation);
			return;
		}

		Util.nonCriticalIoPool().execute(() -> {
			try {
				this.pinger.pingServer(
						data,
						() -> {
						},
						() -> minecraft.execute(() -> this.finishPing(true, generation)),
						EventLoopGroupHolder.remote(minecraft.options.useNativeTransport())
				);
			} catch (Throwable ignored) {
				minecraft.execute(() -> this.finishPing(false, generation));
			}
		});
	}

	private void ensureResolverInitialized() {
		if (this.resolverInitialized) {
			return;
		}
		try {
			ServerNameResolver.DEFAULT.getClass();
			this.resolverInitialized = true;
		} catch (Throwable ignored) {
		}
	}

	private void finishPing(boolean reachable, int generation) {
		if (generation != this.pingGeneration) {
			return;
		}
		this.online = reachable;
		this.pingInFlight = false;
	}
}
