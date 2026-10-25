package io.downloadmod.gui;

import io.downloadmod.image.ImageCache;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

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
		super(Text.translatable("downloadmod.gallery"));
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
		ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> move(-1))
			.position(cx - 110, by).size(50, 20).build();
		prev.active = items.size() > 1;
		addDrawableChild(prev);
		addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.close"), b -> close())
			.position(cx - 50, by).size(100, 20).build());
		ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> move(1))
			.position(cx + 60, by).size(50, 20).build();
		next.active = items.size() > 1;
		addDrawableChild(next);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		if (items.isEmpty()) {
			return;
		}
		Item item = items.get(index);
		int cx = this.width / 2;
		ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal((index + 1) + " / " + items.size()), cx, 8, 0xFFFFFFFF);

		int boxX = 36;
		int boxW = this.width - 72;
		int boxY = 22;
		int boxH = this.height - 22 - 70;
		ImageCache.Img img = ImageCache.getUrl(item.best(), boxW, boxH);
		if (img == null && !item.best().equals(item.url()) && ImageCache.failed(item.best(), boxW, boxH)) {
			img = ImageCache.getUrl(item.url(), boxW, boxH);
		}
		if (img == null) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Loading..."), cx, boxY + boxH / 2, 0xFFAAAAAA);
		} else {
			ImageCache.draw(ctx, img, boxX + (boxW - img.w()) / 2, boxY + (boxH - img.h()) / 2);
		}
		if (items.size() > 1) {
			ImageCache.getUrl(items.get((index + 1) % items.size()).best(), boxW, boxH);
			ImageCache.getUrl(items.get((index - 1 + items.size()) % items.size()).best(), boxW, boxH);
		}

		int y = this.height - 64;
		if (item.title() != null && !item.title().isBlank()) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(item.title(), this.width - 20)),
				cx, y, 0xFFFFFFFF);
			y += 11;
		}
		if (item.description() != null && !item.description().isBlank()) {
			int lines = 0;
			for (OrderedText line : this.textRenderer.wrapLines(Text.literal(item.description()), this.width - 40)) {
				if (lines++ >= 2) {
					break;
				}
				ctx.drawCenteredTextWithShadow(this.textRenderer, line, cx, y, 0xFFBBBBBB);
				y += 10;
			}
		}
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
