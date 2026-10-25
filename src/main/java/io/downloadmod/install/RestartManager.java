package io.downloadmod.install;

import io.downloadmod.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RestartManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("downloadmod/restart");
	private static final boolean DEBUG = Boolean.getBoolean("downloadmod.debug");
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	private static volatile boolean restartPending = false;
	private static final AtomicBoolean HOOK = new AtomicBoolean(false);
	private static Optional<Plan> cachedPlan;

	record Plan(List<String> command, Path cwd, String source) {
	}

	private RestartManager() {
	}

	public static boolean isRestartPending() {
		return restartPending;
	}

	public static void markRestartNeeded() {
		restartPending = true;
	}

	public static boolean canRestart() {
		return plan().isPresent();
	}

	public static void closeGame() {
		MinecraftClient.getInstance().scheduleStop();
	}

	static synchronized Optional<Plan> plan() {
		if (cachedPlan == null) {
			cachedPlan = buildPlan();
		}
		return cachedPlan;
	}

	static Path childOutputFile() {
		return FabricLoader.getInstance().getGameDir().resolve("logs").resolve("downloadmod-child-output.log");
	}

	static void log(String line) {
		try {
			Path file = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("downloadmod-restart.log");
			Files.createDirectories(file.getParent());
			Files.writeString(file, LocalDateTime.now() + "  " + line + System.lineSeparator(),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
		}
		if (DEBUG) {
			LOGGER.info(line);
		}
	}

	public static void restart() throws IOException {
		Plan plan = plan().orElseThrow(() -> new IOException("Cannot rebuild the launch command"));
		boolean detached = ModConfig.restartDetached || PendingOps.hasWork();
		log("Restart requested, mode=" + (detached ? "detached" : "attached") + ", source=" + plan.source()
			+ ", command=" + redact(plan.command()) + ", cwd=" + plan.cwd());
		if (detached) {
			PendingOps.spawnHelper(plan.command(), plan.cwd());
		} else if (HOOK.compareAndSet(false, true)) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> runChildThenHalt(plan), "downloadmod-restart"));
		}
		MinecraftClient.getInstance().scheduleStop();
	}

	private static void runChildThenHalt(Plan plan) {
		int code;
		long t0 = System.currentTimeMillis();
		log("Shutdown hook running, starting the new game process");
		try {
			Files.createDirectories(childOutputFile().getParent());
			Process child = new ProcessBuilder(plan.command()).directory(plan.cwd().toFile())
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.appendTo(childOutputFile().toFile()))
				.start();
			log("New game process started (pid " + child.pid() + ")");
			code = child.waitFor();
			long seconds = (System.currentTimeMillis() - t0) / 1000;
			log("New game process ended with exit code " + code + " after " + seconds + "s"
				+ (seconds < 15 ? "  <-- it died right after starting, see logs/downloadmod-child-output.log" : ""));
		} catch (IOException | InterruptedException e) {
			log("Could not start the new game process: " + e);
			code = 1;
		}
		Runtime.getRuntime().halt(code);
	}

	private static Optional<Plan> buildPlan() {
		String sunCommand = System.getProperty("sun.java.command", "");
		if (sunCommand.startsWith("org.multimc.") || sunCommand.startsWith("org.prismlauncher.")) {
			return Optional.empty();
		}
		ProcessHandle.Info info = ProcessHandle.current().info();
		String exe = info.command().orElseGet(() ->
			Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString());
		List<String> cmd = new ArrayList<>();
		String source;

		Optional<String[]> osArgs = info.arguments();
		Optional<List<String>> windowsArgs = WINDOWS ? WindowsCommandLine.read() : Optional.empty();
		if (!WINDOWS && osArgs.isPresent() && osArgs.get().length > 0) {
			cmd.add(exe);
			cmd.addAll(Arrays.asList(osArgs.get()));
			source = "process-info";
		} else if (windowsArgs.isPresent()) {
			cmd.addAll(windowsArgs.get());
			cmd.set(0, exe);
			source = "windows-api";
		} else {
			if (sunCommand.isBlank()) {
				return Optional.empty();
			}
			cmd.add(exe);
			cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
			String cp = System.getProperty("java.class.path", "");
			if (!cp.isBlank()) {
				cmd.add("-cp");
				cmd.add(cp);
			}
			cmd.addAll(splitMainCommand(sunCommand));
			source = "jvm-properties";
		}
		List<String> removed = new ArrayList<>();
		List<String> cleaned = LauncherCommand.forRelaunch(cmd, removed);
		if (!removed.isEmpty()) {
			source = source + "+launcher-cleaned(" + removed.size() + " removed)";
		}
		return Optional.of(new Plan(cleaned, Path.of("").toAbsolutePath(), source));
	}

	static List<String> splitMainCommand(String s) {
		String[] t = s.trim().split(" +");
		List<String> out = new ArrayList<>();
		int i = 0;
		while (i < t.length && !t[i].startsWith("--")) {
			out.add(t[i]);
			i++;
		}
		boolean lastWasKey = false;
		for (; i < t.length; i++) {
			String tok = t[i];
			if (tok.startsWith("--")) {
				out.add(tok);
				lastWasKey = true;
			} else if (lastWasKey) {
				out.add(tok);
				lastWasKey = false;
			} else {
				int last = out.size() - 1;
				out.set(last, out.get(last) + " " + tok);
			}
		}
		return out;
	}

	private static List<String> redact(List<String> cmd) {
		List<String> out = new ArrayList<>();
		boolean hide = false;
		for (String c : cmd) {
			if (hide) {
				out.add("<hidden>");
				hide = false;
				continue;
			}
			out.add(c);
			String l = c.toLowerCase(Locale.ROOT);
			if (l.startsWith("--") && (l.contains("token") || l.contains("session") || l.contains("uuid") || l.contains("xuid"))) {
				hide = true;
			}
		}
		return out;
	}
}
