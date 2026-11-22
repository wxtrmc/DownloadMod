package io.downloadmod;

import io.downloadmod.compat.Compat;
import io.downloadmod.gui.DownloadModScreen;
import io.downloadmod.image.GuiIcons;
import io.downloadmod.install.PendingOps;
import io.downloadmod.update.UpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DownloadModClient implements ClientModInitializer {
	private static final int DISCORD_X = 6 + 110 + 4;

	@Override
	public void onInitializeClient() {
		ModConfig.load();
		PendingOps.loadLeftovers();
		UpdateChecker.start();
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof TitleScreen title) {
				List<AbstractWidget> buttons = Screens.getWidgets(screen);
				buttons.add(mainButton(screen));
				if (ModConfig.showDiscordButton) {
					Button discord = discordButton(screen);
					buttons.add(discord);

					ScreenEvents.afterExtract(screen).register((sc, ctx, mx, my, tick) -> {
						if (discord.visible) {
							GuiIcons.draw(ctx, "discord", discord.getX() + 2, discord.getY() + 2, 16);
						}
					});
				}
				UpdateChecker.promptIfNeeded(client, title);
			} else if (screen instanceof PauseScreen) {
				Screens.getWidgets(screen).add(mainButton(screen));
			}
		});
	}

	private static Button discordButton(Screen screen) {
		return Button.builder(Component.empty(), b -> Compat.openLink(screen, Config.DISCORD_URL, true))
			.pos(DISCORD_X, 6).size(20, 20)
			.tooltip(Tooltip.create(Component.translatable("downloadmod.tooltip.discord"))).build();
	}

	private static Button mainButton(Screen screen) {
		return Button.builder(Component.translatable("downloadmod.button"),
				b -> Compat.setScreen(Minecraft.getInstance(), new DownloadModScreen(screen)))
			.pos(6, 6).size(110, 20).build();
	}
}
