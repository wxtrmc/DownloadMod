package io.downloadmod.install;

import io.downloadmod.Config;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class PendingOps {
	private static final List<String> LINES = new ArrayList<>();
	private static boolean hookRegistered = false;
	private static boolean spawned = false;

	private PendingOps() {
	}

	private static Path dataFile() {
		return FabricLoader.getInstance().getGameDir().resolve("downloadmod").resolve("pending-ops.txt");
	}

	private static String esc(String s) {
		return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
	}

	public static synchronized void loadLeftovers() {
		try {
			if (Files.exists(dataFile())) {
				LINES.addAll(Files.readAllLines(dataFile(), StandardCharsets.UTF_8));
				if (!LINES.isEmpty()) {
					registerHook();
				}
			}
		} catch (IOException ignored) {
		}
	}

	public static synchronized void addReplace(Path oldFile, Path tmp, Path fin) {
		LINES.add("DELETE\t" + esc(oldFile.toString()));
		LINES.add("MOVE\t" + esc(tmp.toString()) + "\t" + esc(fin.toString()));
		persist();
		registerHook();
	}

	public static synchronized boolean hasWork() {
		return !LINES.isEmpty();
	}

	private static void persist() {
		try {
			Files.createDirectories(dataFile().getParent());
			Files.write(dataFile(), LINES, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private static void registerHook() {
		if (hookRegistered) {
			return;
		}
		hookRegistered = true;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				spawnHelper(null, null);
			} catch (Throwable ignored) {
			}
		}, "downloadmod-pending"));
	}

	static synchronized void spawnHelper(List<String> launchCommand, Path cwd) throws IOException {
		if (spawned || (LINES.isEmpty() && launchCommand == null)) {
			return;
		}
		spawned = true;
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path log = gameDir.resolve("logs").resolve("downloadmod-restart.log");

		StringBuilder sb = new StringBuilder();
		sb.append("LOG\t").append(esc(log.toString())).append('\n');
		sb.append("PENDING\t").append(esc(dataFile().toString())).append('\n');
		for (String l : LINES) {
			sb.append(l).append('\n');
		}
		if (launchCommand != null) {
			sb.append("CWD\t").append(esc(cwd.toString())).append('\n');
			for (String a : launchCommand) {
				sb.append("ARG\t").append(esc(a)).append('\n');
			}
		}
		Path ops = Files.createTempFile("downloadmod-ops", ".txt");
		Files.writeString(ops, sb.toString(), StandardCharsets.UTF_8);

		Path self = FabricLoader.getInstance().getModContainer(Config.MOD_ID).orElseThrow()
			.getOrigin().getPaths().get(0);
		Path classPath = self;
		if (Files.isRegularFile(self)) {
			classPath = Files.createTempFile("downloadmod-helper", ".jar");
			Files.copy(self, classPath, StandardCopyOption.REPLACE_EXISTING);
		}
		String javaExe = ProcessHandle.current().info().command()
			.orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
		List<String> cmd = List.of(javaExe, "-cp", classPath.toString(), "io.downloadmod.helper.ExitHelper",
			String.valueOf(ProcessHandle.current().pid()), ops.toString());
		new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(Redirect.DISCARD).start();
		RestartManager.log("Helper started (queued file operations: " + LINES.size() / 2
			+ (launchCommand != null ? ", will start the game again" : "") + ")");
	}
}
