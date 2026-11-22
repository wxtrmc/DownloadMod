package io.downloadmod.gui;

import io.downloadmod.compat.Compat;
import io.downloadmod.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SettingsScreen extends Screen {
	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.translatable("downloadmod.settings.title"));
		this.parent = parent;
	}

	private static Component label(String key, boolean on) {
		return Component.translatable(key).append(Component.literal(": ")).append(on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
	}

	private static Component restartLabel() {
		return Component.translatable("downloadmod.settings.restart_mode", Component.translatable(
			ModConfig.restartDetached ? "downloadmod.restart_mode.detached" : "downloadmod.restart_mode.attached"));
	}

	private Button toggle(int y, String key, BooleanSupplier get, Consumer<Boolean> set) {
		return Button.builder(label(key, get.getAsBoolean()), b -> {
			boolean now = !get.getAsBoolean();
			set.accept(now);
			ModConfig.save();
			b.setMessage(label(key, now));
		}).pos(this.width / 2 - 100, y).size(200, 20).build();
	}

	@Override
	protected void init() {
		int y = this.height / 4;
		addRenderableWidget(toggle(y, "downloadmod.settings.discord",
			() -> ModConfig.showDiscordButton, v -> ModConfig.showDiscordButton = v));
		addRenderableWidget(toggle(y + 24, "downloadmod.settings.updates",
			() -> ModConfig.checkForUpdates, v -> ModConfig.checkForUpdates = v));
		addRenderableWidget(toggle(y + 48, "downloadmod.settings.world_packs",
			() -> ModConfig.showWorldPacks, v -> ModConfig.showWorldPacks = v));
		addRenderableWidget(Button.builder(restartLabel(), b -> {
			ModConfig.restartDetached = !ModConfig.restartDetached;
			ModConfig.save();
			b.setMessage(restartLabel());
		}).pos(this.width / 2 - 100, y + 72).size(200, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.pos(this.width / 2 - 100, this.height - 28).size(200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Compat.background(this, ctx, mouseX, mouseY, delta);
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.centeredText(this.font, this.title, this.width / 2, this.height / 4 - 20, 0xFFFFFFFF);
		int y = this.height / 4 + 98;
		for (FormattedCharSequence line : this.font.split(Component.translatable("downloadmod.settings.restart_hint"), 220)) {
			ctx.centeredText(this.font, line, this.width / 2, y, 0xFFAAAAAA);
			y += 10;
		}
	}

	@Override
	public void onClose() {
		Compat.setScreen(this.minecraft, parent);
	}
}
