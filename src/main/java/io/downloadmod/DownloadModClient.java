package io.downloadmod;

import io.downloadmod.gui.DownloadModScreen;
import io.downloadmod.image.GuiIcons;
import io.downloadmod.install.PendingOps;
import io.downloadmod.update.UpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

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
				List<ClickableWidget> buttons = Screens.getButtons(screen);
				buttons.add(mainButton(screen));
				if (ModConfig.showDiscordButton) {
					buttons.add(discordButton(screen));
					ScreenEvents.afterRender(screen).register((sc, ctx, mx, my, tick) ->
						GuiIcons.draw(ctx, "discord", DISCORD_X + 2, 8, 16));
				}
				UpdateChecker.promptIfNeeded(client, title);
			} else if (screen instanceof GameMenuScreen) {
				Screens.getButtons(screen).add(mainButton(screen));
			}
		});
	}

	private static ButtonWidget discordButton(Screen screen) {
		return ButtonWidget.builder(Text.empty(), b -> ConfirmLinkScreen.open(screen, Config.DISCORD_URL, true))
			.position(DISCORD_X, 6).size(20, 20)
			.tooltip(Tooltip.of(Text.translatable("downloadmod.tooltip.discord"))).build();
	}

	private static ButtonWidget mainButton(Screen screen) {
		return ButtonWidget.builder(Text.translatable("downloadmod.button"),
				b -> MinecraftClient.getInstance().setScreen(new DownloadModScreen(screen)))
			.position(6, 6).size(110, 20).build();
	}
}
