package io.downloadmod.install;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LauncherCommand {
	private static final String THESEUS_MAIN = "com.modrinth.theseus.MinecraftLaunch";

	private LauncherCommand() {
	}

	static List<String> forRelaunch(List<String> command, List<String> removed) {
		return forRelaunch(command, removed, File.pathSeparator);
	}

	static List<String> forRelaunch(List<String> command, List<String> removed, String pathSeparator) {
		if (!command.contains(THESEUS_MAIN)) {
			return command;
		}
		List<String> out = new ArrayList<>();
		for (int i = 0; i < command.size(); i++) {
			String a = command.get(i);
			if (a.equals(THESEUS_MAIN) || a.startsWith("-Dmodrinth.internal.")) {
				removed.add(a);
				continue;
			}
			if (a.startsWith("-javaagent:") && isTheseusJar(a.substring("-javaagent:".length()))) {
				removed.add(a);
				continue;
			}
			if ((a.equals("-cp") || a.equals("-classpath")) && i + 1 < command.size()) {
				out.add(a);
				List<String> kept = new ArrayList<>();
				for (String entry : command.get(++i).split(java.util.regex.Pattern.quote(pathSeparator), -1)) {
					if (isTheseusJar(entry)) {
						removed.add(entry);
					} else {
						kept.add(entry);
					}
				}
				out.add(String.join(pathSeparator, kept));
				continue;
			}
			out.add(a);
		}
		return out;
	}

	private static boolean isTheseusJar(String path) {
		String n = path.toLowerCase(Locale.ROOT).replace('\\', '/');
		return n.equals("theseus.jar") || n.endsWith("/theseus.jar");
	}
}
