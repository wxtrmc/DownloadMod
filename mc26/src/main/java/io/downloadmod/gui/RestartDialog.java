package io.downloadmod.gui;

import io.downloadmod.install.RestartManager;
import io.downloadmod.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;

final class RestartDialog {
	private RestartDialog() {
	}

	static void open(Screen back) {
		final Minecraft mc = Minecraft.getInstance();
		final boolean can = RestartManager.canRestart();
		Compat.setScreen(mc, new ConfirmScreen(yes -> {
			if (!yes) {
				Compat.setScreen(mc, back);
				return;
			}
			try {
				if (can) {
					RestartManager.restart();
				} else {
					RestartManager.closeGame();
				}
			} catch (IOException e) {
				Compat.setScreen(mc, back);
			}
		},
			Component.translatable(can ? "downloadmod.restart.confirm.title" : "downloadmod.close.confirm.title"),
			Component.translatable(can ? "downloadmod.restart.confirm.body" : "downloadmod.close.confirm.body")));
	}
}
