package io.downloadmod.gui;

import io.downloadmod.compat.Compat;
import io.downloadmod.Async;
import io.downloadmod.api.ContentType;
import io.downloadmod.api.Models;
import io.downloadmod.api.ModrinthApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

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
		super(Component.translatable("downloadmod.versions.title", hit.title()));
		this.parent = parent;
		this.hit = hit;
		this.type = type;
		this.installedId = installedId;
		this.onPick = onPick;
	}

	private void load() {
		final Minecraft mc = Minecraft.getInstance();
		Async.run(() -> {
			try {
				List<Models.Version> list = ModrinthApi.versions(hit.projectId(), type);
				mc.execute(() -> {
					versions = list;
					if (Compat.currentScreen(mc) == this) {
						rebuildWidgets();
					}
				});
			} catch (Exception e) {
				final String msg = e.getMessage();
				mc.execute(() -> {
					error = msg;
					versions = List.of();
					if (Compat.currentScreen(mc) == this) {
						rebuildWidgets();
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
			Button b = Button.builder(Component.empty(), btn -> {
				Compat.setScreen(this.minecraft, parent);
				onPick.accept(v);
			}).pos(rowX, 34 + (i - from) * ROW_STEP).size(rowW, 20).build();
			b.active = !v.id().equals(installedId);
			addRenderableWidget(b);
		}
		int by = this.height - 26;
		Button prev = Button.builder(Component.literal("<"), b -> {
			page--;
			rebuildWidgets();
		}).pos(rowX, by).size(40, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		addRenderableWidget(Button.builder(Component.translatable("downloadmod.back"), b -> onClose())
			.pos(this.width / 2 - 50, by).size(100, 20).build());
		Button next = Button.builder(Component.literal(">"), b -> {
			page++;
			rebuildWidgets();
		}).pos(rowX + rowW - 40, by).size(40, 20).build();
		next.active = versions != null && to < versions.size();
		addRenderableWidget(next);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Compat.background(this, ctx, mouseX, mouseY, delta);
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
		if (versions == null) {
			ctx.centeredText(this.font, Component.translatable("downloadmod.versions.loading"),
				this.width / 2, this.height / 2, 0xFFAAAAAA);
			return;
		}
		if (error != null) {
			ctx.centeredText(this.font, Component.literal("Error: " + error), this.width / 2, this.height / 2, 0xFFFF5555);
			return;
		}
		if (versions.isEmpty()) {
			ctx.centeredText(this.font,
				Component.translatable("downloadmod.versions.none", ModrinthApi.minecraftVersion()), this.width / 2, this.height / 2, 0xFFFFD866);
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
			ctx.text(this.font,
				Component.literal(this.font.plainSubstrByWidth(v.versionNumber() + (installed ? "  (installed)" : ""), rowW / 2 - 12)),
				rowX + 8, y, installed ? 0xFFAAAAAA : 0xFFFFFFFF);
			ctx.text(this.font, Component.literal(kind), rowX + rowW / 2, y, kindColor);
			ctx.text(this.font, Component.literal(date), rowX + rowW - 8 - this.font.width(date), y, 0xFFAAAAAA);
		}
	}

	@Override
	public void onClose() {
		Compat.setScreen(this.minecraft, parent);
	}
}
