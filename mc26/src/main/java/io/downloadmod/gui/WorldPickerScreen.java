package io.downloadmod.gui;

import io.downloadmod.compat.Compat;
import io.downloadmod.image.ImageCache;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

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
		super(Component.translatable("downloadmod.world.title"));
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
			addRenderableWidget(Button.builder(Component.empty(), b -> {
				Compat.setScreen(this.minecraft, parent);
				onPicked.accept(world.resolve("datapacks"));
			}).pos(rowX, 34 + (i - from) * ROW_STEP).size(rowW, ROW_H).build());
		}
		int by = this.height - 26;
		Button prev = Button.builder(Component.literal("<"), b -> {
			page--;
			rebuildWidgets();
		}).pos(rowX, by).size(40, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		addRenderableWidget(Button.builder(Component.translatable("downloadmod.cancel"), b -> onClose())
			.pos(this.width / 2 - 50, by).size(100, 20).build());
		Button next = Button.builder(Component.literal(">"), b -> {
			page++;
			rebuildWidgets();
		}).pos(rowX + rowW - 40, by).size(40, 20).build();
		next.active = to < worlds.size();
		addRenderableWidget(next);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Compat.background(this, ctx, mouseX, mouseY, delta);
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		ctx.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
		if (worlds == null) {
			return;
		}
		if (worlds.isEmpty()) {
			ctx.centeredText(this.font, Component.translatable("downloadmod.world.none"),
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
			ctx.text(this.font, Component.literal(this.font.plainSubstrByWidth(names.get(i), rowW - 48)),
				rowX + 42, y + 6, 0xFFFFFFFF);
			ctx.text(this.font,
				Component.literal(this.font.plainSubstrByWidth(world.getFileName() + "  -  " + date, rowW - 48)),
				rowX + 42, y + 20, 0xFFAAAAAA);
		}
	}

	@Override
	public void onClose() {
		Compat.setScreen(this.minecraft, parent);
	}
}
