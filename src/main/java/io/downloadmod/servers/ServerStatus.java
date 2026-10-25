package io.downloadmod.servers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.downloadmod.Config;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public final class ServerStatus {
	public record Status(boolean reached, boolean online, int players) {
		public static final Status UNKNOWN = new Status(false, false, -1);
	}

	private record Cached(Status status, long time) {
	}

	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
	private static final long CACHE_MS = 90_000L;

	private ServerStatus() {
	}

	public static Status fetch(String address) {
		String key = address.trim().toLowerCase();
		Cached c = CACHE.get(key);
		if (c != null && System.currentTimeMillis() - c.time() < CACHE_MS) {
			return c.status();
		}
		Status s = request(key);
		if (s.reached()) {
			CACHE.put(key, new Cached(s, System.currentTimeMillis()));
		}
		return s;
	}

	private static Status request(String address) {
		Status direct = ping(address);
		if (direct.reached()) {
			return direct;
		}
		return api(address);
	}

	private static Status ping(String address) {
		String host = address;
		int port = 25565;
		boolean explicitPort = false;
		int colon = address.lastIndexOf(':');
		if (colon > 0) {
			host = address.substring(0, colon);
			try {
				port = Integer.parseInt(address.substring(colon + 1));
				explicitPort = true;
			} catch (NumberFormatException e) {
				return Status.UNKNOWN;
			}
		}
		String connectHost = host;
		int connectPort = port;
		if (!explicitPort) {
			String[] srv = srv(host);
			if (srv != null) {
				connectHost = srv[0];
				connectPort = Integer.parseInt(srv[1]);
			}
		}
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(connectHost, connectPort), 3000);
			socket.setSoTimeout(4000);
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			DataInputStream in = new DataInputStream(socket.getInputStream());

			ByteArrayOutputStream hb = new ByteArrayOutputStream();
			DataOutputStream h = new DataOutputStream(hb);
			writeVarInt(h, 0x00);
			writeVarInt(h, 767);
			byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
			writeVarInt(h, hostBytes.length);
			h.write(hostBytes);
			h.writeShort(port);
			writeVarInt(h, 1);
			writeVarInt(out, hb.size());
			out.write(hb.toByteArray());
			writeVarInt(out, 1);
			writeVarInt(out, 0x00);
			out.flush();

			int len = readVarInt(in);
			if (len <= 0 || len > (1 << 21)) {
				return Status.UNKNOWN;
			}
			if (readVarInt(in) != 0x00) {
				return Status.UNKNOWN;
			}
			int jsonLen = readVarInt(in);
			if (jsonLen <= 0 || jsonLen > (1 << 21)) {
				return Status.UNKNOWN;
			}
			byte[] data = new byte[jsonLen];
			in.readFully(data);
			JsonObject o = GSON.fromJson(new String(data, StandardCharsets.UTF_8), JsonObject.class);
			if (o == null) {
				return Status.UNKNOWN;
			}
			int players = 0;
			if (o.has("players") && o.get("players").isJsonObject()) {
				JsonObject p = o.getAsJsonObject("players");
				if (p.has("online")) {
					players = p.get("online").getAsInt();
				}
			}
			return new Status(true, true, players);
		} catch (IOException | RuntimeException e) {
			return Status.UNKNOWN;
		}
	}

	private static void writeVarInt(DataOutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	private static int readVarInt(DataInputStream in) throws IOException {
		int value = 0;
		int shift = 0;
		while (true) {
			int b = in.readUnsignedByte();
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return value;
			}
			shift += 7;
			if (shift > 35) {
				throw new IOException("VarInt too big");
			}
		}
	}

	private static String[] srv(String host) {
		try {
			Hashtable<String, String> env = new Hashtable<>();
			env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
			env.put("java.naming.provider.url", "dns:");
			env.put("com.sun.jndi.dns.timeout.initial", "1500");
			env.put("com.sun.jndi.dns.timeout.retries", "1");
			DirContext ctx = new InitialDirContext(env);
			try {
				Attribute a = ctx.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"}).get("srv");
				if (a != null && a.size() > 0) {
					String[] p = a.get(0).toString().split(" ");
					if (p.length >= 4) {
						String target = p[3].endsWith(".") ? p[3].substring(0, p[3].length() - 1) : p[3];
						return new String[]{target, p[2]};
					}
				}
			} finally {
				ctx.close();
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static Status api(String address) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.mcsrvstat.us/3/" + address))
				.header("User-Agent", Config.USER_AGENT).timeout(Duration.ofSeconds(6)).GET().build();
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) {
				return Status.UNKNOWN;
			}
			JsonObject o = GSON.fromJson(res.body(), JsonObject.class);
			if (o == null || !o.has("online")) {
				return Status.UNKNOWN;
			}
			boolean online = o.get("online").getAsBoolean();
			int players = -1;
			if (online) {
				players = 0;
				if (o.has("players") && o.get("players").isJsonObject()) {
					JsonObject p = o.getAsJsonObject("players");
					if (p.has("online")) {
						players = p.get("online").getAsInt();
					}
				}
			}
			return new Status(true, online, players);
		} catch (java.io.IOException | RuntimeException e) {
			return Status.UNKNOWN;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Status.UNKNOWN;
		}
	}

	public static Map<String, Status> fetchAll(List<String> addresses) {
		Map<String, Status> out = new HashMap<>();
		ExecutorService pool = Executors.newFixedThreadPool(8, r -> {
			Thread t = new Thread(r, "downloadmod-status");
			t.setDaemon(true);
			return t;
		});
		try {
			List<Callable<Status>> tasks = new ArrayList<>();
			for (String a : addresses) {
				tasks.add(() -> fetch(a));
			}
			List<Future<Status>> futures = pool.invokeAll(tasks, 8, TimeUnit.SECONDS);
			for (int i = 0; i < futures.size(); i++) {
				Status s = Status.UNKNOWN;
				try {
					if (!futures.get(i).isCancelled()) {
						s = futures.get(i).get();
					}
				} catch (Exception ignored) {
				}
				out.put(addresses.get(i), s);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			pool.shutdownNow();
		}
		return out;
	}
}
