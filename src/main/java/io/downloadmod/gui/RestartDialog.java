package io.downloadmod.gui;

import io.downloadmod.install.RestartManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.io.IOException;

final class RestartDialog {
	private RestartDialog() {
	}

	static void open(Screen back) {
		final MinecraftClient mc = MinecraftClient.getInstance();
		final boolean can = RestartManager.canRestart();
		mc.setScreen(new ConfirmScreen(yes -> {
			if (!yes) {
				mc.setScreen(back);
				return;
			}
			try {
				if (can) {
					RestartManager.restart();
				} else {
					RestartManager.closeGame();
				}
			} catch (IOException e) {
				mc.setScreen(back);
			}
		},
			Text.translatable(can ? "downloadmod.restart.confirm.title" : "downloadmod.close.confirm.title"),
			Text.translatable(can ? "downloadmod.restart.confirm.body" : "downloadmod.close.confirm.body")));
	}
}
