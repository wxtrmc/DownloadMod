package io.downloadmod.install;

import io.downloadmod.api.ContentType;
import io.downloadmod.api.Models;
import io.downloadmod.api.ModrinthApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Resolver {
	private static final int MAX_DEPTH = 3;

	private Resolver() {
	}

	public static Path currentWorldDatapackDir() {
		IntegratedServer server = MinecraftClient.getInstance().getServer();
		return server == null ? null : server.getSavePath(WorldSavePath.DATAPACKS);
	}

	private static Path targetDir(ContentType type, Path datapackDir) throws IOException {
		if (type.folder != null) {
			return FabricLoader.getInstance().getGameDir().resolve(type.folder);
		}
		if (datapackDir == null) {
			throw new IOException("Choose a world for the data packs first");
		}
		return datapackDir;
	}

	public static List<PlanFile> resolveAll(List<DownloadQueue.Item> items, Path datapackDir, List<String> skipped)
		throws InterruptedException {
		Map<String, PlanFile> merged = new LinkedHashMap<>();
		for (DownloadQueue.Item it : items) {
			try {
				for (PlanFile f : resolve(it.hit(), it.type(), datapackDir)) {
					merged.putIfAbsent(f.targetDir() + "|" + f.filename(), f);
				}
			} catch (IOException e) {
				skipped.add(it.hit().title() + " (" + e.getMessage() + ")");
			}
		}
		return new ArrayList<>(merged.values());
	}

	public static List<PlanFile> resolve(Models.Hit hit, ContentType type, Path datapackDir)
		throws IOException, InterruptedException {
		Models.Version version = ModrinthApi.bestVersion(hit.projectId(), type);
		if (version == null) {
			throw new IOException("No compatible version for Minecraft " + ModrinthApi.minecraftVersion());
		}
		return resolveVersion(hit, type, version, datapackDir, null);
	}

	public static List<PlanFile> resolveVersion(Models.Hit hit, ContentType type, Models.Version version, Path datapackDir,
												Path replaces) throws IOException, InterruptedException {
		Path dir = targetDir(type, datapackDir);
		List<PlanFile> out = new ArrayList<>();
		Set<String> seenProjects = new HashSet<>();
		seenProjects.add(hit.projectId());
		add(out, hit.title(), version, dir, false, type, replaces);
		if (type == ContentType.MOD) {
			collectDependencies(version, dir, out, seenProjects, 1);
		}
		return out;
	}

	private static void collectDependencies(Models.Version version, Path dir, List<PlanFile> out, Set<String> seen,
											int depth) throws IOException, InterruptedException {
		if (depth > MAX_DEPTH || version.dependencies() == null) {
			return;
		}
		for (Models.Dependency dep : version.dependencies()) {
			if (!"required".equals(dep.dependencyType()) || dep.projectId() == null || !seen.add(dep.projectId())) {
				continue;
			}
			Models.Version depVersion = dep.versionId() != null
				? ModrinthApi.versionById(dep.versionId())
				: ModrinthApi.bestVersion(dep.projectId(), ContentType.MOD);
			if (depVersion == null) {
				throw new IOException("A required dependency has no compatible version");
			}
			add(out, depVersion.name() != null ? depVersion.name() : dep.projectId(), depVersion, dir, true, ContentType.MOD, null);
			collectDependencies(depVersion, dir, out, seen, depth + 1);
		}
	}

	private static void add(List<PlanFile> out, String title, Models.Version version, Path dir, boolean dependency,
							ContentType type, Path replaces) throws IOException {
		Models.FileInfo file = null;
		for (Models.FileInfo f : version.files()) {
			if (f.primary()) {
				file = f;
				break;
			}
		}
		if (file == null && !version.files().isEmpty()) {
			file = version.files().get(0);
		}
		if (file == null) {
			throw new IOException("Version has no files");
		}
		String sha512 = file.hashes() == null ? null : file.hashes().get("sha512");
		if (sha512 == null) {
			throw new IOException("The API did not provide a SHA-512 hash; refusing to download");
		}

		if (replaces == null
			&& (Files.exists(dir.resolve(file.filename()))
			|| (file.filename().startsWith("fabric-api") && FabricLoader.getInstance().isModLoaded("fabric-api")))) {
			return;
		}
		out.add(new PlanFile(title, file.filename(), file.url(), sha512, file.size(), dir, dependency, type, replaces));
	}
}
