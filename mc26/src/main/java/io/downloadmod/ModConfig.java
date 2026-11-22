package io.downloadmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("downloadmod.json");

	public static boolean showDiscordButton = true;
	public static boolean checkForUpdates = true;
	public static boolean restartDetached = false;
	public static boolean showWorldPacks = false;

	private static final class Data {
		boolean showDiscordButton = true;
		boolean checkForUpdates = true;
		boolean restartDetached = false;
		boolean showWorldPacks = false;
	}

	private ModConfig() {
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				Data d = GSON.fromJson(Files.readString(FILE), Data.class);
				if (d != null) {
					showDiscordButton = d.showDiscordButton;
					checkForUpdates = d.checkForUpdates;
					restartDetached = d.restartDetached;
					showWorldPacks = d.showWorldPacks;
				}
			}
		} catch (IOException | RuntimeException ignored) {
		}
	}

	public static void save() {
		Data d = new Data();
		d.showDiscordButton = showDiscordButton;
		d.checkForUpdates = checkForUpdates;
		d.restartDetached = restartDetached;
		d.showWorldPacks = showWorldPacks;
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(d));
		} catch (IOException ignored) {
		}
	}
}
