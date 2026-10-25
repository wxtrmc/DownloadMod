package io.downloadmod.gui;

import io.downloadmod.image.ImageCache;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class WorldPickerScreen extends Screen {
	private static final int ROW_H = 36;
	private static final int ROW_STEP = 40;

	private final Screen parent;
	private final Consumer<Path> onPicked;
	private List<Path> worlds;
	private List<String> names;
	private int page = 0;
	private int pageSize = 4;
	private int rowX;
	private int rowW;

	public WorldPickerScreen(Screen parent, Consumer<Path> onPicked) {
		super(Text.translatable("downloadmod.world.title"));
		this.parent = parent;
		this.onPicked = onPicked;
	}

	private static List<Path> findWorlds() {
		Path saves = FabricLoader.getInstance().getGameDir().resolve("saves");
		if (!Files.isDirectory(saves)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(saves)) {
			List<Path> out = new ArrayList<>(s.filter(Files::isDirectory)
				.filter(p -> Files.exists(p.resolve("level.dat"))).toList());
			out.sort(Comparator.comparingLong((Path p) -> p.resolve("level.dat").toFile().lastModified()).reversed());
			return out;
		} catch (IOException e) {
			return List.of();
		}
	}

	private static int indexOf(byte[] data, byte[] key) {
		for (int i = 0; i <= data.length - key.length; i++) {
			int j = 0;
			while (j < key.length && data[i + j] == key[j]) {
				j++;
			}
			if (j == key.length) {
				return i;
			}
		}
		return -1;
	}

	private static String levelName(Path world) {
		try (InputStream in = new GZIPInputStream(Files.newInputStream(world.resolve("level.dat")))) {
			byte[] data = in.readNBytes(8 * 1024 * 1024);
			byte[] key = {8, 0, 9, 'L', 'e', 'v', 'e', 'l', 'N', 'a', 'm', 'e'};
			int at = indexOf(data, key);
			if (at < 0) {
				return null;
			}
			int p = at + key.length;
			int len = ((data[p] & 0xFF) << 8) | (data[p + 1] & 0xFF);
			p += 2;
			if (p + len > data.length) {
				return null;
			}
			return new String(data, p, len, StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	@Override
	protected void init() {
		if (worlds == null) {
			worlds = findWorlds();
			names = new ArrayList<>();
			for (Path w : worlds) {
				String n = levelName(w);
				names.add(n == null || n.isBlank() ? w.getFileName().toString() : n);
			}
		}
		rowW = Math.min(340, this.width - 40);
		rowX = (this.width - rowW) / 2;
		pageSize = Math.max(2, (this.height - 36 - 40) / ROW_STEP);
		int from = page * pageSize;
		int to = Math.min(worlds.size(), from + pageSize);
		for (int i = from; i < to; i++) {
			final Path world = worlds.get(i);
			addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
				this.client.setScreen(parent);
				onPicked.accept(world.resolve("datapacks"));
			}).position(rowX, 34 + (i - from) * ROW_STEP).size(rowW, ROW_H).build());
		}
		int by = this.height - 26;
		ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> {
			page--;
			clearAndInit();
		}).position(rowX, by).size(40, 20).build();
		prev.active = page > 0;
		addDrawableChild(prev);
		addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.cancel"), b -> close())
			.position(this.width / 2 - 50, by).size(100, 20).build());
		ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> {
			page++;
			clearAndInit();
		}).position(rowX + rowW - 40, by).size(40, 20).build();
		next.active = to < worlds.size();
		addDrawableChild(next);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);
		if (worlds == null) {
			return;
		}
		if (worlds.isEmpty()) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("downloadmod.world.none"),
				this.width / 2, this.height / 2, 0xFFFFD866);
			return;
		}
		int from = page * pageSize;
		int to = Math.min(worlds.size(), from + pageSize);
		for (int i = from; i < to; i++) {
			Path world = worlds.get(i);
			int y = 34 + (i - from) * ROW_STEP;
			ImageCache.Img icon = ImageCache.getFile(world.resolve("icon.png"), 32, true);
			if (icon == null) {
				ctx.fill(rowX + 3, y + 2, rowX + 35, y + 34, 0xFF3A3A3A);
			} else {
				ImageCache.draw(ctx, icon, rowX + 3 + (32 - icon.w()) / 2, y + 2 + (32 - icon.h()) / 2);
			}
			String date = LocalDate.ofInstant(Instant.ofEpochMilli(world.resolve("level.dat").toFile().lastModified()),
				ZoneId.systemDefault()).toString();
			ctx.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(names.get(i), rowW - 48)),
				rowX + 42, y + 6, 0xFFFFFFFF);
			ctx.drawTextWithShadow(this.textRenderer,
				Text.literal(this.textRenderer.trimToWidth(world.getFileName() + "  -  " + date, rowW - 48)),
				rowX + 42, y + 20, 0xFFAAAAAA);
		}
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
