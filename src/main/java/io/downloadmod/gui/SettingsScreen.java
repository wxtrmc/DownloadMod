package io.downloadmod.gui;

import io.downloadmod.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SettingsScreen extends Screen {
	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Text.translatable("downloadmod.settings.title"));
		this.parent = parent;
	}

	private static Text label(String key, boolean on) {
		return Text.translatable(key).append(Text.literal(": ")).append(on ? ScreenTexts.ON : ScreenTexts.OFF);
	}

	private static Text restartLabel() {
		return Text.translatable("downloadmod.settings.restart_mode", Text.translatable(
			ModConfig.restartDetached ? "downloadmod.restart_mode.detached" : "downloadmod.restart_mode.attached"));
	}

	private ButtonWidget toggle(int y, String key, BooleanSupplier get, Consumer<Boolean> set) {
		return ButtonWidget.builder(label(key, get.getAsBoolean()), b -> {
			boolean now = !get.getAsBoolean();
			set.accept(now);
			ModConfig.save();
			b.setMessage(label(key, now));
		}).position(this.width / 2 - 100, y).size(200, 20).build();
	}

	@Override
	protected void init() {
		int y = this.height / 4;
		addDrawableChild(toggle(y, "downloadmod.settings.discord",
			() -> ModConfig.showDiscordButton, v -> ModConfig.showDiscordButton = v));
		addDrawableChild(toggle(y + 24, "downloadmod.settings.updates",
			() -> ModConfig.checkForUpdates, v -> ModConfig.checkForUpdates = v));
		addDrawableChild(toggle(y + 48, "downloadmod.settings.world_packs",
			() -> ModConfig.showWorldPacks, v -> ModConfig.showWorldPacks = v));
		addDrawableChild(ButtonWidget.builder(restartLabel(), b -> {
			ModConfig.restartDetached = !ModConfig.restartDetached;
			ModConfig.save();
			b.setMessage(restartLabel());
		}).position(this.width / 2 - 100, y + 72).size(200, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
			.position(this.width / 2 - 100, this.height - 28).size(200, 20).build());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 4 - 20, 0xFFFFFFFF);
		int y = this.height / 4 + 98;
		for (OrderedText line : this.textRenderer.wrapLines(Text.translatable("downloadmod.settings.restart_hint"), 220)) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, y, 0xFFAAAAAA);
			y += 10;
		}
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
