package io.downloadmod.servers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.downloadmod.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ServerCatalog {
	public record Entry(String name, String address, String description) {
		public String iconUrl() {
			return "https://api.mcsrvstat.us/icon/" + address;
		}
	}

	private static final Gson GSON = new Gson();

	private ServerCatalog() {
	}

	private static List<Entry> builtIn() {
		List<Entry> l = new ArrayList<>();
		l.add(new Entry("Hypixel", "mc.hypixel.net",
			"The largest and most famous Minecraft network: Bed Wars, SkyWars, SkyBlock, Murder Mystery."));
		l.add(new Entry("DonutSMP", "donutsmp.net",
			"A hugely popular survival (SMP) server."));
		l.add(new Entry("Wynncraft", "play.wynncraft.com",
			"A full MMORPG built inside Minecraft: quests, classes and dungeons. Java only."));
		l.add(new Entry("CubeCraft", "play.cubecraft.net",
			"Competitive minigames such as SkyWars and EggWars. Java and Bedrock."));
		l.add(new Entry("PikaNetwork", "play.pika-network.net",
			"One of the most active servers, with thousands of players online. Java and Bedrock."));
		l.add(new Entry("ManaCube", "play.manacube.com",
			"Parkour, Skyblock, Survival and KitPvP."));
		l.add(new Entry("Complex Gaming", "hub.mc-complex.com",
			"Survival plus Pokemon-style modes (Pixelmon/Cobblemon). Some modes may need extra mods."));
		l.add(new Entry("2b2t", "2b2t.org",
			"The legendary, long-running anarchy server."));
		l.add(new Entry("Purple Prison", "purpleprison.net",
			"OP Prison: ranks, economy, progression and PvP zones. Java only."));
		l.add(new Entry("MCC Island", "play.mccisland.net",
			"Minigames and parkour."));
		l.add(new Entry("AkumaMC", "best.akumamc.net",
			"OP Prison and Skyblock with seasonal resets."));
		l.add(new Entry("Minemen Club", "na.minemen.club",
			"Competitive PvP practice server, premium accounts only. EU: eu.minemen.club."));
		l.add(new Entry("OPBlocks", "opblocks.com",
			"A popular free-to-play server network."));
		l.add(new Entry("The Mining Dead", "hub.miningdead.com",
			"Survival, role-playing and PvP."));
		l.add(new Entry("Minecadia", "play.minecadia.com",
			"A popular server network listed among the best of 2026."));
		return l;
	}

	public static List<Entry> load() {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(Config.SERVERS_URL))
				.header("User-Agent", Config.USER_AGENT).timeout(Duration.ofSeconds(10)).GET().build();
			HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() == 200) {
				List<Entry> remote = GSON.fromJson(res.body(), new TypeToken<List<Entry>>() {
				}.getType());
				if (remote != null) {
					List<Entry> ok = new ArrayList<>();
					for (Entry e : remote) {
						if (e != null && e.name() != null && e.address() != null) {
							ok.add(new Entry(e.name(), e.address(), e.description() == null ? "" : e.description()));
						}
					}
					if (!ok.isEmpty()) {
						return ok;
					}
				}
			}
		} catch (IOException | RuntimeException e) {
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return builtIn();
	}

	public record Result(boolean ok, String message) {
	}

	private static String normAddress(String a) {
		String s = a.trim().toLowerCase(Locale.ROOT);
		return s.endsWith(":25565") ? s.substring(0, s.length() - 6) : s;
	}

	private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");
	private static final Pattern TLD = Pattern.compile("[a-z]{2,24}|xn--[a-z0-9-]{2,50}");

	private static String hostOf(String address) {
		String a = address.trim().toLowerCase(Locale.ROOT);
		int colon = a.lastIndexOf(':');
		return colon < 0 ? a : a.substring(0, colon);
	}

	public static String validateAddress(String address) {
		String a = address.trim().toLowerCase(Locale.ROOT);
		if (a.isEmpty()) {
			return "Enter the server IP.";
		}
		if (a.length() > 253 || a.chars().anyMatch(ch -> ch <= ' ')) {
			return "Invalid IP / address.";
		}
		String host = a;
		int colon = a.lastIndexOf(':');
		if (colon >= 0) {
			host = a.substring(0, colon);
			String port = a.substring(colon + 1);
			if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
				return "Invalid port.";
			}
		}
		if (host.isEmpty() || host.equals("localhost")) {
			return "Invalid IP / address.";
		}
		if (host.matches("[0-9.]+")) {
			String[] parts = host.split("\\.", -1);
			if (parts.length != 4) {
				return "Invalid IP / address.";
			}
			int[] o = new int[4];
			for (int i = 0; i < 4; i++) {
				if (!parts[i].matches("[0-9]{1,3}") || Integer.parseInt(parts[i]) > 255) {
					return "Invalid IP / address.";
				}
				o[i] = Integer.parseInt(parts[i]);
			}
			boolean local = o[0] == 0 || o[0] == 10 || o[0] == 127 || o[0] >= 224
				|| (o[0] == 169 && o[1] == 254)
				|| (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
				|| (o[0] == 192 && o[1] == 168)
				|| (o[0] == 100 && o[1] >= 64 && o[1] <= 127);
			return local ? "Use a public server IP." : null;
		}
		String[] labels = host.split("\\.", -1);
		if (labels.length < 2) {
			return "Invalid IP / address.";
		}
		for (String l : labels) {
			if (!LABEL.matcher(l).matches()) {
				return "Invalid IP / address.";
			}
		}
		if (!TLD.matcher(labels[labels.length - 1]).matches()) {
			return "Invalid IP / address.";
		}
		return null;
	}

	public static Result submit(String name, String address, String description) {
		name = name.trim();
		address = address.trim();
		if (name.isEmpty()) {
			return new Result(false, "Enter a server name.");
		}
		String bad = validateAddress(address);
		if (bad != null) {
			return new Result(false, bad);
		}

		for (Entry e : load()) {
			if (normAddress(e.address()).equals(normAddress(address))) {
				return new Result(false, "This IP is already in the list.");
			}
			if (e.name().trim().equalsIgnoreCase(name)) {
				return new Result(false, "This name is already taken.");
			}
		}

		ServerStatus.Status st = ServerStatus.fetch(address);
		if (st.reached()) {
			if (!st.online()) {
				return new Result(false, "Server not found or offline. Check the IP and try again.");
			}
		} else {
			try {
				InetAddress.getByName(hostOf(address));
			} catch (IOException e) {
				return new Result(false, "This address does not exist.");
			}
		}

		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		body.addProperty("address", address);
		body.addProperty("description", description.trim());
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(Config.SERVERS_ADD_URL))
				.header("User-Agent", Config.USER_AGENT)
				.header("Content-Type", "application/json")
				.header("X-API-Key", Config.SERVERS_API_KEY)
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
			HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() == 201) {
				return new Result(true, "Your server was added!");
			}
			String error = "";
			try {
				JsonObject o = GSON.fromJson(res.body(), JsonObject.class);
				if (o != null && o.has("error")) {
					error = o.get("error").getAsString();
				}
			} catch (RuntimeException ignored) {
			}
			return new Result(false, switch (error) {
				case "ip_exists" -> "This IP is already in the list.";
				case "name_exists" -> "This name is already taken.";
				case "invalid_name" -> "Invalid name (max 50 characters).";
				case "invalid_address" -> "Invalid IP / address.";
				case "too_many_requests" -> "Wait...";
				default -> "Could not add the server (error " + res.statusCode() + ").";
			});
		} catch (IOException | RuntimeException e) {
			return new Result(false, "Could not reach the server list API.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Result(false, "Cancelled.");
		}
	}

	public static boolean addToGame(Entry entry) {
		ServerList list = new ServerList(MinecraftClient.getInstance());
		list.loadFile();
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).address.equalsIgnoreCase(entry.address())) {
				return false;
			}
		}
		list.add(new ServerInfo(entry.name(), entry.address(), ServerInfo.ServerType.OTHER), false);
		list.saveFile();
		return true;
	}
}
