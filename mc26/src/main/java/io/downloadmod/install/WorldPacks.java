package io.downloadmod.install;

import io.downloadmod.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class WorldPacks {
	public record Pack(String id, String name, String description, boolean enabled, Path file) {
	}

	private WorldPacks() {
	}

	public static boolean inWorld() {
		return Minecraft.getInstance().getSingleplayerServer() != null;
	}

	public static List<Pack> list() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			return List.of();
		}
		try {
			return CompletableFuture.supplyAsync(() -> read(server), server).get(5, TimeUnit.SECONDS);
		} catch (Exception e) {
			return List.of();
		}
	}

	private static List<Pack> read(IntegratedServer server) {
		PackRepository manager = server.getPackRepository();
		Collection<String> enabled = Compat.enabledPackIds(manager);
		Path dir = server.getWorldPath(LevelResource.DATAPACK_DIR);
		List<Pack> out = new ArrayList<>();
		for (net.minecraft.server.packs.repository.Pack p : manager.getAvailablePacks()) {
			String id = Compat.packId(p);
			if (!id.startsWith("file/")) {
				continue;
			}
			out.add(new Pack(id, p.getTitle().getString(), p.getDescription().getString(),
				enabled.contains(id), dir.resolve(id.substring(5))));
		}
		out.sort(Comparator.comparing((Pack p) -> p.name().toLowerCase()));
		return out;
	}

	public static boolean setEnabled(String id, boolean enable) {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			return false;
		}
		String quoted = "\"" + id.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		String command = "datapack " + (enable ? "enable " : "disable ") + quoted;
		try {
			CompletableFuture.runAsync(() -> runCommand(server, command), server)
				.get(5, TimeUnit.SECONDS);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static void runCommand(IntegratedServer server, String command) {
		Commands manager = server.getCommands();
		CommandSourceStack source = server.createCommandSourceStack();
		for (Method m : Commands.class.getMethods()) {
			Class<?>[] p = m.getParameterTypes();
			if (!Modifier.isStatic(m.getModifiers()) && p.length == 2
				&& p[0] == CommandSourceStack.class && p[1] == String.class) {
				try {
					m.invoke(manager, source, command);
					return;
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		throw new IllegalStateException("No command method found");
	}

	public static boolean delete(Pack pack) {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			return false;
		}
		Path dir = server.getWorldPath(LevelResource.DATAPACK_DIR).toAbsolutePath().normalize();
		Path file = pack.file().toAbsolutePath().normalize();
		if (!file.startsWith(dir) || file.equals(dir)) {
			return false;
		}
		if (pack.enabled() && !setEnabled(pack.id(), false)) {
			return false;
		}

		for (int i = 0; i < 10; i++) {
			try {
				deleteRecursive(file);
				return true;
			} catch (IOException e) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return false;
				}
			}
		}
		return false;
	}

	private static void deleteRecursive(Path p) throws IOException {
		if (Files.isDirectory(p)) {
			try (Stream<Path> walk = Files.walk(p)) {
				for (Path x : walk.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(x);
				}
			}
		} else {
			Files.deleteIfExists(p);
		}
	}
}
