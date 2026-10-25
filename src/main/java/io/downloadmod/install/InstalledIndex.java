package io.downloadmod.install;

import io.downloadmod.Async;
import io.downloadmod.api.ContentType;
import io.downloadmod.api.Models;
import io.downloadmod.api.ModrinthApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class InstalledIndex {
	public record Entry(Path file, ContentType type, Models.Version current, Models.Version latest) {
		public boolean updateAvailable() {
			return latest != null && current != null && !latest.id().equals(current.id());
		}
	}

	private static volatile Map<String, Entry> byProject = Map.of();
	private static volatile boolean dirty = true;
	private static volatile boolean loading = false;
	private static final Map<String, String> HASHES = new ConcurrentHashMap<>();

	private InstalledIndex() {
	}

	public static Entry get(String projectId) {
		return byProject.get(projectId);
	}

	public static void markDirty() {
		dirty = true;
	}

	public static void refreshIfNeeded(Runnable onDone) {
		if (!dirty || loading) {
			return;
		}
		loading = true;
		dirty = false;
		final MinecraftClient mc = MinecraftClient.getInstance();
		Async.run(() -> {
			try {
				byProject = scan();
			} catch (Exception e) {
				dirty = true;
			} finally {
				loading = false;
			}
			mc.execute(onDone);
		});
	}

	private static Map<String, Entry> scan() throws IOException, InterruptedException {
		Path game = FabricLoader.getInstance().getGameDir();
		Map<ContentType, Path> dirs = new EnumMap<>(ContentType.class);
		dirs.put(ContentType.MOD, game.resolve("mods"));
		dirs.put(ContentType.RESOURCEPACK, game.resolve("resourcepacks"));
		dirs.put(ContentType.SHADER, game.resolve("shaderpacks"));

		Map<String, Path> fileByHash = new LinkedHashMap<>();
		Map<String, ContentType> typeByHash = new HashMap<>();
		for (Map.Entry<ContentType, Path> d : dirs.entrySet()) {
			if (!Files.isDirectory(d.getValue())) {
				continue;
			}
			String ext = d.getKey() == ContentType.MOD ? ".jar" : ".zip";
			try (Stream<Path> files = Files.list(d.getValue())) {
				for (Path p : files.toList()) {
					if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(ext)) {
						String h = sha512(p);
						fileByHash.putIfAbsent(h, p);
						typeByHash.putIfAbsent(h, d.getKey());
					}
				}
			}
		}
		if (fileByHash.isEmpty()) {
			return Map.of();
		}

		Map<String, Models.Version> current = ModrinthApi.versionFiles(new ArrayList<>(fileByHash.keySet()));
		Map<String, Models.Version> latest = new HashMap<>();
		for (ContentType type : dirs.keySet()) {
			List<String> hashes = fileByHash.keySet().stream().filter(h -> typeByHash.get(h) == type).toList();
			if (!hashes.isEmpty()) {
				latest.putAll(ModrinthApi.versionFilesUpdate(hashes, type));
			}
		}

		Map<String, Entry> out = new HashMap<>();
		for (Map.Entry<String, Models.Version> e : current.entrySet()) {
			Path file = fileByHash.get(e.getKey());
			if (file == null || e.getValue().projectId() == null) {
				continue;
			}
			out.putIfAbsent(e.getValue().projectId(),
				new Entry(file, typeByHash.get(e.getKey()), e.getValue(), latest.get(e.getKey())));
		}
		return out;
	}

	private static String sha512(Path p) throws IOException {
		String key = p + "|" + Files.size(p) + "|" + Files.getLastModifiedTime(p).toMillis();
		String cached = HASHES.get(key);
		if (cached != null) {
			return cached;
		}
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-512");
			try (InputStream in = Files.newInputStream(p)) {
				byte[] buf = new byte[65536];
				int n;
				while ((n = in.read(buf)) != -1) {
					md.update(buf, 0, n);
				}
			}
			String hex = HexFormat.of().formatHex(md.digest());
			HASHES.put(key, hex);
			return hex;
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}
}
