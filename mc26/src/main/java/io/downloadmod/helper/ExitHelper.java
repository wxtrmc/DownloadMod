package io.downloadmod.helper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ExitHelper {
	private static Path log;

	private ExitHelper() {
	}

	private static String unesc(String s) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				char n = s.charAt(++i);
				out.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static void log(String line) {
		if (log == null) {
			return;
		}
		try {
			Files.createDirectories(log.getParent());
			Files.writeString(log, LocalDateTime.now() + "  [helper] " + line + System.lineSeparator(),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
		}
	}

	public static void main(String[] args) throws Exception {
		long pid = Long.parseLong(args[0]);
		Path opsFile = Path.of(args[1]);
		List<String> lines = Files.readAllLines(opsFile, StandardCharsets.UTF_8);
		Files.deleteIfExists(opsFile);

		Path pending = null;
		Path cwd = null;
		List<String[]> ops = new ArrayList<>();
		List<String> launch = new ArrayList<>();
		for (String line : lines) {
			String[] p = line.split("\t", -1);
			switch (p[0]) {
				case "LOG" -> log = Path.of(unesc(p[1]));
				case "PENDING" -> pending = Path.of(unesc(p[1]));
				case "CWD" -> cwd = Path.of(unesc(p[1]));
				case "ARG" -> launch.add(unesc(p[1]));
				case "DELETE" -> ops.add(new String[]{"DELETE", unesc(p[1])});
				case "MOVE" -> ops.add(new String[]{"MOVE", unesc(p[1]), unesc(p[2])});
				default -> {
				}
			}
		}
		log("started, waiting for game process " + pid + " to exit");

		Optional<ProcessHandle> parent = ProcessHandle.of(pid);
		if (parent.isPresent()) {
			try {
				parent.get().onExit().get(180, TimeUnit.SECONDS);
			} catch (Exception e) {
				log("the game did not exit within 3 minutes; doing nothing");
				return;
			}
		}
		log("game process exited");

		boolean allOk = true;
		for (String[] op : ops) {
			boolean ok = false;
			for (int i = 0; i < 60 && !ok; i++) {
				try {
					if (op[0].equals("DELETE")) {
						Files.deleteIfExists(Path.of(op[1]));
					} else if (Files.exists(Path.of(op[1]))) {
						Files.move(Path.of(op[1]), Path.of(op[2]), StandardCopyOption.REPLACE_EXISTING);
					}
					ok = true;
				} catch (IOException e) {
					Thread.sleep(500);
				}
			}
			log((ok ? "done: " : "FAILED: ") + op[0] + " " + op[1] + (op.length > 2 ? " -> " + op[2] : ""));
			allOk &= ok;
		}
		if (allOk && pending != null) {
			Files.deleteIfExists(pending);
		}

		if (!launch.isEmpty()) {
			try {
				ProcessBuilder.Redirect output = log == null ? ProcessBuilder.Redirect.DISCARD
					: ProcessBuilder.Redirect.appendTo(log.resolveSibling("downloadmod-child-output.log").toFile());
				Process child = new ProcessBuilder(launch).directory(cwd.toFile()).redirectErrorStream(true)
					.redirectOutput(output).start();
				log("game started again (pid " + child.pid() + ")");
				if (child.waitFor(20, TimeUnit.SECONDS)) {
					int code = child.exitValue();
					log("the new game process ended within 20s with exit code " + code
						+ (code != 0 ? " - see logs/downloadmod-child-output.log" : ""));
				} else {
					log("the new game is still running after 20s: restart worked");
				}
			} catch (IOException e) {
				log("could not start the game: " + e);
			}
		}
	}
}
