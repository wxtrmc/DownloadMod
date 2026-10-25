package io.downloadmod.gui;

import io.downloadmod.Async;
import io.downloadmod.api.ContentType;
import io.downloadmod.install.Downloader;
import io.downloadmod.install.InstalledIndex;
import io.downloadmod.install.PlanFile;
import io.downloadmod.install.RestartManager;
import io.downloadmod.install.Swapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InstallScreen extends Screen {
	private enum State { CONFIRM, DOWNLOADING, DONE, ERROR }

	private static final int LIST_TOP = 52;
	private static final int LIST_STEP = 14;

	private final Screen parent;
	private final List<PlanFile> plan;
	private final String note;
	private final Runnable onSuccess;
	private final AtomicLong progress = new AtomicLong();
	private State state = State.CONFIRM;
	private String error = "";
	private long total = 1;
	private int page = 0;
	private int pageSize = 6;
	private boolean removedDependency = false;
	private boolean swapWaiting = false;

	public InstallScreen(Screen parent, String name, List<PlanFile> plan, String note, Runnable onSuccess) {
		super(Text.translatable("downloadmod.install.title", name));
		this.parent = parent;
		this.plan = new ArrayList<>(plan);
		this.note = note;
		this.onSuccess = onSuccess;
	}

	private Set<ContentType> types() {
		return plan.stream().map(PlanFile::type).collect(Collectors.toSet());
	}

	private boolean needsRestart() {
		return types().contains(ContentType.MOD);
	}

	private static String mb(long bytes) {
		return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
	}

	private void dropFile(PlanFile f) {
		if (f.dependency()) {
			removedDependency = true;
		}
		plan.remove(f);
		if (plan.isEmpty()) {
			close();
			return;
		}
		clearAndInit();
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int by = this.height - 28;
		switch (state) {
			case CONFIRM -> {
				pageSize = Math.max(2, (this.height - LIST_TOP - 82) / LIST_STEP);
				page = Math.max(0, Math.min(page, (plan.size() - 1) / pageSize));
				int from = page * pageSize;
				int to = Math.min(plan.size(), from + pageSize);
				for (int i = from; i < to; i++) {
					final PlanFile f = plan.get(i);
					addDrawableChild(ButtonWidget.builder(Text.literal("x"), b -> dropFile(f))
						.position(this.width - 34, LIST_TOP + (i - from) * LIST_STEP).size(20, 12).build());
				}
				ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> {
					page--;
					clearAndInit();
				}).position(10, this.height - 52).size(26, 16).build();
				prev.active = page > 0;
				addDrawableChild(prev);
				ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> {
					page++;
					clearAndInit();
				}).position(40, this.height - 52).size(26, 16).build();
				next.active = to < plan.size();
				addDrawableChild(next);
				addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.confirm"), b -> start())
					.position(cx - 104, by).size(100, 20).build());
				addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.cancel"), b -> close())
					.position(cx + 4, by).size(100, 20).build());
			}
			case DONE -> {
				if (needsRestart()) {
					String key = RestartManager.canRestart() ? "downloadmod.restart" : "downloadmod.close_game";
					addDrawableChild(ButtonWidget.builder(Text.translatable(key), b -> RestartDialog.open(this))
						.position(cx - 104, by).size(100, 20).build());
					addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.later"), b -> close())
						.position(cx + 4, by).size(100, 20).build());
				} else {
					addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.back"), b -> close())
						.position(cx - 50, by).size(100, 20).build());
				}
			}
			case ERROR -> addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.back"), b -> close())
				.position(cx - 50, by).size(100, 20).build());
			case DOWNLOADING -> {
			}
		}
	}

	private void start() {
		final MinecraftClient mc = MinecraftClient.getInstance();
		final List<PlanFile> snapshot = List.copyOf(plan);
		total = Math.max(1, Downloader.totalSize(snapshot));
		state = State.DOWNLOADING;
		clearAndInit();
		Async.run(() -> {
			try {
				Downloader.download(snapshot, progress);
				boolean waiting = Swapper.apply(snapshot);
				mc.execute(() -> {
					swapWaiting = waiting;
					if (needsRestart()) {
						RestartManager.markRestartNeeded();
					}
					InstalledIndex.markDirty();
					if (onSuccess != null) {
						onSuccess.run();
					}
					state = State.DONE;
					if (mc.currentScreen == this) {
						clearAndInit();
					}
				});
			} catch (Exception e) {
				final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
				mc.execute(() -> {
					error = msg;
					state = State.ERROR;
					if (mc.currentScreen == this) {
						clearAndInit();
					}
				});
			}
		});
	}

	private List<String> doneKeys() {
		List<String> keys = new ArrayList<>();
		Set<ContentType> types = types();
		if (types.contains(ContentType.MOD)) {
			keys.add("downloadmod.install.done.restart");
		}
		if (types.contains(ContentType.DATAPACK)) {
			keys.add("downloadmod.install.done.datapack");
		}
		if (types.contains(ContentType.RESOURCEPACK)) {
			keys.add("downloadmod.install.done.resourcepack");
		}
		if (types.contains(ContentType.SHADER)) {
			keys.add("downloadmod.install.done.shader");
		}
		if (swapWaiting) {
			keys.add("downloadmod.install.done.pending");
		}
		return keys;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int cx = this.width / 2;
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 12, 0xFFFFFFFF);
		switch (state) {
			case CONFIRM -> {
				ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("downloadmod.install.review"), cx, 34, 0xFFDDDDDD);
				int from = page * pageSize;
				int to = Math.min(plan.size(), from + pageSize);
				for (int i = from; i < to; i++) {
					PlanFile f = plan.get(i);
					String line = f.filename() + "  (" + mb(f.size()) + ")"
						+ (f.dependency() ? "  [dependency]" : "")
						+ (f.replaces() != null ? "  [replaces " + f.replaces().getFileName() + "]" : "");
					ctx.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(line, this.width - 54)),
						10, LIST_TOP + (i - from) * LIST_STEP + 2, 0xFFFFFFFF);
				}
				int warnY = this.height - 68;
				if (removedDependency) {
					ctx.drawTextWithShadow(this.textRenderer,
						Text.literal(this.textRenderer.trimToWidth(Text.translatable("downloadmod.install.removed_dep").getString(), this.width - 20)),
						10, warnY, 0xFFFF8855);
					warnY -= 10;
				}
				if (note != null && !note.isBlank()) {
					ctx.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(note, this.width - 20)),
						10, warnY, 0xFFFFD866);
				}
				ctx.drawTextWithShadow(this.textRenderer,
					Text.literal("Total: " + mb(Downloader.totalSize(plan)) + "   (" + (page + 1) + "/" + (((plan.size() - 1) / pageSize) + 1) + ")"),
					72, this.height - 49, 0xFFAAAAAA);
			}
			case DOWNLOADING -> {
				int barW = Math.min(240, this.width - 40);
				int x1 = cx - barW / 2;
				int y1 = this.height / 2 - 6;
				double frac = Math.min(1.0, progress.get() / (double) total);
				ctx.fill(x1 - 1, y1 - 1, x1 + barW + 1, y1 + 13, 0xFF000000);
				ctx.fill(x1, y1, x1 + barW, y1 + 12, 0xFF333333);
				ctx.fill(x1, y1, x1 + (int) (barW * frac), y1 + 12, 0xFF55CC55);
				ctx.drawCenteredTextWithShadow(this.textRenderer,
					Text.literal((int) (frac * 100) + "%  (" + mb(progress.get()) + " / " + mb(total) + ")"),
					cx, y1 + 20, 0xFFFFFFFF);
			}
			case DONE -> {
				int dy = 40;
				for (String key : doneKeys()) {
					for (OrderedText line : this.textRenderer.wrapLines(Text.translatable(key), this.width - 40)) {
						ctx.drawCenteredTextWithShadow(this.textRenderer, line, cx, dy, 0xFF77FF77);
						dy += 11;
					}
					dy += 6;
				}
				if (needsRestart() && !RestartManager.canRestart()) {
					for (OrderedText line : this.textRenderer.wrapLines(Text.translatable("downloadmod.restart.manual"), this.width - 40)) {
						ctx.drawCenteredTextWithShadow(this.textRenderer, line, cx, dy, 0xFFFFD866);
						dy += 11;
					}
				}
			}
			case ERROR -> {
				int y = 40;
				for (OrderedText line : this.textRenderer.wrapLines(Text.literal(error), this.width - 40)) {
					ctx.drawCenteredTextWithShadow(this.textRenderer, line, cx, y, 0xFFFF5555);
					y += 10;
				}
			}
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return state != State.DOWNLOADING;
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
