package io.downloadmod.update;

import io.downloadmod.Async;
import io.downloadmod.Config;
import io.downloadmod.ModConfig;
import io.downloadmod.api.ContentType;
import io.downloadmod.api.Models;
import io.downloadmod.api.ModrinthApi;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;

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
		final MinecraftClient mc = MinecraftClient.getInstance();
		Async.run(() -> {
			try {
				Models.Version latest = ModrinthApi.bestVersion(Config.MODRINTH_SLUG, ContentType.MOD);
				String current = FabricLoader.getInstance().getModContainer(Config.MOD_ID)
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
				if (latest != null && current != null && isNewer(latest.versionNumber(), current)) {
					pending = new Update(latest.versionNumber());
					mc.execute(() -> {
						if (mc.currentScreen instanceof TitleScreen title) {
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

	public static void promptIfNeeded(MinecraftClient mc, TitleScreen title) {
		Update u = pending;
		if (u == null || prompted || !ModConfig.checkForUpdates) {
			return;
		}
		prompted = true;
		String current = FabricLoader.getInstance().getModContainer(Config.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		mc.execute(() -> mc.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				ConfirmLinkScreen.open(title, pageUrl(), true);
			} else {
				mc.setScreen(title);
			}
		}, Text.translatable("downloadmod.update.title"), Text.translatable("downloadmod.update.body", u.version(), current))));
	}
}
