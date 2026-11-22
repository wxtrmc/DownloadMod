package io.downloadmod.update;

import io.downloadmod.compat.Compat;
import io.downloadmod.Async;
import io.downloadmod.Config;
import io.downloadmod.ModConfig;
import io.downloadmod.api.ContentType;
import io.downloadmod.api.Models;
import io.downloadmod.api.ModrinthApi;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class UpdateChecker {
	private record Update(String version) {
	}

	private static volatile Update pending;
	private static boolean started = false;
	private static boolean prompted = false;

	private UpdateChecker() {
	}

	private static String pageUrl() {
		return "https://modrinth.com/mod/" + Config.MODRINTH_SLUG;
	}

	public static void start() {
		if (started || !ModConfig.checkForUpdates || Config.MODRINTH_SLUG.isBlank()) {
			return;
		}
		started = true;
		final Minecraft mc = Minecraft.getInstance();
		Async.run(() -> {
			try {
				Models.Version latest = ModrinthApi.bestVersion(Config.MODRINTH_SLUG, ContentType.MOD);
				String current = FabricLoader.getInstance().getModContainer(Config.MOD_ID)
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
				if (latest != null && current != null && isNewer(latest.versionNumber(), current)) {
					pending = new Update(latest.versionNumber());
					mc.execute(() -> {
						if (Compat.currentScreen(mc) instanceof TitleScreen title) {
							promptIfNeeded(mc, title);
						}
					});
				}
			} catch (Exception ignored) {
			}
		});
	}

	private static boolean isNewer(String remote, String local) {
		try {
			return Version.parse(remote).compareTo(Version.parse(local)) > 0;
		} catch (VersionParsingException e) {
			return !remote.equals(local);
		}
	}

	public static void promptIfNeeded(Minecraft mc, TitleScreen title) {
		Update u = pending;
		if (u == null || prompted || !ModConfig.checkForUpdates) {
			return;
		}
		prompted = true;
		String current = FabricLoader.getInstance().getModContainer(Config.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		mc.execute(() -> Compat.setScreen(mc, new ConfirmScreen(yes -> {
			if (yes) {
				Compat.openLink(title, pageUrl(), true);
			} else {
				Compat.setScreen(mc, title);
			}
		}, Component.translatable("downloadmod.update.title"), Component.translatable("downloadmod.update.body", u.version(), current))));
	}
}
