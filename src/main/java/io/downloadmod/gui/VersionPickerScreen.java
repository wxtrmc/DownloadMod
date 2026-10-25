package io.downloadmod.gui;

import io.downloadmod.Async;
import io.downloadmod.api.ContentType;
import io.downloadmod.api.Models;
import io.downloadmod.api.ModrinthApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

public class VersionPickerScreen extends Screen {
	private static final int ROW_STEP = 24;

	private final Screen parent;
	private final Models.Hit hit;
	private final ContentType type;
	private final String installedId;
	private final Consumer<Models.Version> onPick;
	private List<Models.Version> versions;
	private String error;
	private boolean started = false;
	private int page = 0;
	private int pageSize = 6;
	private int rowX;
	private int rowW;

	public VersionPickerScreen(Screen parent, Models.Hit hit, ContentType type, String installedId,
							   Consumer<Models.Version> onPick) {
		super(Text.translatable("downloadmod.versions.title", hit.title()));
		this.parent = parent;
		this.hit = hit;
		this.type = type;
		this.installedId = installedId;
		this.onPick = onPick;
	}

	private void load() {
		final MinecraftClient mc = MinecraftClient.getInstance();
		Async.run(() -> {
			try {
				List<Models.Version> list = ModrinthApi.versions(hit.projectId(), type);
				mc.execute(() -> {
					versions = list;
					if (mc.currentScreen == this) {
						clearAndInit();
					}
				});
			} catch (Exception e) {
				final String msg = e.getMessage();
				mc.execute(() -> {
					error = msg;
					versions = List.of();
					if (mc.currentScreen == this) {
						clearAndInit();
					}
				});
			}
		});
	}

	@Override
	protected void init() {
		if (!started) {
			started = true;
			load();
		}
		rowW = Math.min(380, this.width - 40);
		rowX = (this.width - rowW) / 2;
		pageSize = Math.max(3, (this.height - 36 - 40) / ROW_STEP);
		int from = page * pageSize;
		int to = versions == null ? 0 : Math.min(versions.size(), from + pageSize);
		for (int i = from; i < to; i++) {
			final Models.Version v = versions.get(i);
			ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> {
				this.client.setScreen(parent);
				onPick.accept(v);
			}).position(rowX, 34 + (i - from) * ROW_STEP).size(rowW, 20).build();
			b.active = !v.id().equals(installedId);
			addDrawableChild(b);
		}
		int by = this.height - 26;
		ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> {
			page--;
			clearAndInit();
		}).position(rowX, by).size(40, 20).build();
		prev.active = page > 0;
		addDrawableChild(prev);
		addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.back"), b -> close())
			.position(this.width / 2 - 50, by).size(100, 20).build());
		ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> {
			page++;
			clearAndInit();
		}).position(rowX + rowW - 40, by).size(40, 20).build();
		next.active = versions != null && to < versions.size();
		addDrawableChild(next);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
		if (versions == null) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("downloadmod.versions.loading"),
				this.width / 2, this.height / 2, 0xFFAAAAAA);
			return;
		}
		if (error != null) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Error: " + error), this.width / 2, this.height / 2, 0xFFFF5555);
			return;
		}
		if (versions.isEmpty()) {
			ctx.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("downloadmod.versions.none", ModrinthApi.minecraftVersion()), this.width / 2, this.height / 2, 0xFFFFD866);
			return;
		}
		int from = page * pageSize;
		int to = Math.min(versions.size(), from + pageSize);
		for (int i = from; i < to; i++) {
			Models.Version v = versions.get(i);
			int y = 34 + (i - from) * ROW_STEP + 6;
			String kind = v.versionType() == null ? "" : v.versionType();
			int kindColor = "release".equals(kind) ? 0xFF55FF55 : "beta".equals(kind) ? 0xFFFFD866 : 0xFFFF7755;
			String date = v.datePublished() == null || v.datePublished().length() < 10 ? "" : v.datePublished().substring(0, 10);
			boolean installed = v.id().equals(installedId);
			ctx.drawTextWithShadow(this.textRenderer,
				Text.literal(this.textRenderer.trimToWidth(v.versionNumber() + (installed ? "  (installed)" : ""), rowW / 2 - 12)),
				rowX + 8, y, installed ? 0xFFAAAAAA : 0xFFFFFFFF);
			ctx.drawTextWithShadow(this.textRenderer, Text.literal(kind), rowX + rowW / 2, y, kindColor);
			ctx.drawTextWithShadow(this.textRenderer, Text.literal(date), rowX + rowW - 8 - this.textRenderer.getWidth(date), y, 0xFFAAAAAA);
		}
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
