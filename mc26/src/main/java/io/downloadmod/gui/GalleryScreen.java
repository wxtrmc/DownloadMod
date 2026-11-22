package io.downloadmod.gui;

import io.downloadmod.compat.Compat;
import io.downloadmod.image.ImageCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class GalleryScreen extends Screen {
	public record Item(String url, String title, String description, String fullUrl) {
		public Item(String url, String title, String description) {
			this(url, title, description, null);
		}

		String best() {
			return fullUrl == null || fullUrl.isBlank() ? url : fullUrl;
		}
	}

	private final Screen parent;
	private final List<Item> items;
	private final Consumer<Integer> onIndexChanged;
	private int index;

	public GalleryScreen(Screen parent, List<Item> items, int start, Consumer<Integer> onIndexChanged) {
		super(Component.translatable("downloadmod.gallery"));
		this.parent = parent;
		this.items = items;
		this.index = Math.max(0, Math.min(start, items.size() - 1));
		this.onIndexChanged = onIndexChanged;
	}

	private void move(int delta) {
		index = (index + delta + items.size()) % items.size();
		onIndexChanged.accept(index);
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int by = this.height - 26;
		Button prev = Button.builder(Component.literal("<"), b -> move(-1))
			.pos(cx - 110, by).size(50, 20).build();
		prev.active = items.size() > 1;
		addRenderableWidget(prev);
		addRenderableWidget(Button.builder(Component.translatable("downloadmod.close"), b -> onClose())
			.pos(cx - 50, by).size(100, 20).build());
		Button next = Button.builder(Component.literal(">"), b -> move(1))
			.pos(cx + 60, by).size(50, 20).build();
		next.active = items.size() > 1;
		addRenderableWidget(next);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Compat.background(this, ctx, mouseX, mouseY, delta);
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		if (items.isEmpty()) {
			return;
		}
		Item item = items.get(index);
		int cx = this.width / 2;
		ctx.centeredText(this.font, Component.literal((index + 1) + " / " + items.size()), cx, 8, 0xFFFFFFFF);

		int boxX = 36;
		int boxW = this.width - 72;
		int boxY = 22;
		int boxH = this.height - 22 - 70;
		ImageCache.Img img = ImageCache.getUrl(item.best(), boxW, boxH);
		if (img == null && !item.best().equals(item.url()) && ImageCache.failed(item.best(), boxW, boxH)) {
			img = ImageCache.getUrl(item.url(), boxW, boxH);
		}
		if (img == null) {
			ctx.centeredText(this.font, Component.literal("Loading..."), cx, boxY + boxH / 2, 0xFFAAAAAA);
		} else {
			ImageCache.draw(ctx, img, boxX + (boxW - img.w()) / 2, boxY + (boxH - img.h()) / 2);
		}
		if (items.size() > 1) {
			ImageCache.getUrl(items.get((index + 1) % items.size()).best(), boxW, boxH);
			ImageCache.getUrl(items.get((index - 1 + items.size()) % items.size()).best(), boxW, boxH);
		}

		int y = this.height - 64;
		if (item.title() != null && !item.title().isBlank()) {
			ctx.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth(item.title(), this.width - 20)),
				cx, y, 0xFFFFFFFF);
			y += 11;
		}
		if (item.description() != null && !item.description().isBlank()) {
			int lines = 0;
			for (FormattedCharSequence line : this.font.split(Component.literal(item.description()), this.width - 40)) {
				if (lines++ >= 2) {
					break;
				}
				ctx.centeredText(this.font, line, cx, y, 0xFFBBBBBB);
				y += 10;
			}
		}
	}

	@Override
	public void onClose() {
		Compat.setScreen(this.minecraft, parent);
	}
}
