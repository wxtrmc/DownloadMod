package io.downloadmod.gui;

import io.downloadmod.compat.Compat;
import io.downloadmod.Async;
import io.downloadmod.api.ContentType;
import io.downloadmod.install.Downloader;
import io.downloadmod.install.InstalledIndex;
import io.downloadmod.install.PlanFile;
import io.downloadmod.install.RestartManager;
import io.downloadmod.install.Swapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

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
		super(Component.translatable("downloadmod.install.title", name));
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
			onClose();
			return;
		}
		rebuildWidgets();
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
					addRenderableWidget(Button.builder(Component.literal("x"), b -> dropFile(f))
						.pos(this.width - 34, LIST_TOP + (i - from) * LIST_STEP).size(20, 12).build());
				}
				Button prev = Button.builder(Component.literal("<"), b -> {
					page--;
					rebuildWidgets();
				}).pos(10, this.height - 52).size(26, 16).build();
				prev.active = page > 0;
				addRenderableWidget(prev);
				Button next = Button.builder(Component.literal(">"), b -> {
					page++;
					rebuildWidgets();
				}).pos(40, this.height - 52).size(26, 16).build();
				next.active = to < plan.size();
				addRenderableWidget(next);
				addRenderableWidget(Button.builder(Component.translatable("downloadmod.confirm"), b -> start())
					.pos(cx - 104, by).size(100, 20).build());
				addRenderableWidget(Button.builder(Component.translatable("downloadmod.cancel"), b -> onClose())
					.pos(cx + 4, by).size(100, 20).build());
			}
			case DONE -> {
				if (needsRestart()) {
					String key = RestartManager.canRestart() ? "downloadmod.restart" : "downloadmod.close_game";
					addRenderableWidget(Button.builder(Component.translatable(key), b -> RestartDialog.open(this))
						.pos(cx - 104, by).size(100, 20).build());
					addRenderableWidget(Button.builder(Component.translatable("downloadmod.later"), b -> onClose())
						.pos(cx + 4, by).size(100, 20).build());
				} else {
					addRenderableWidget(Button.builder(Component.translatable("downloadmod.back"), b -> onClose())
						.pos(cx - 50, by).size(100, 20).build());
				}
			}
			case ERROR -> addRenderableWidget(Button.builder(Component.translatable("downloadmod.back"), b -> onClose())
				.pos(cx - 50, by).size(100, 20).build());
			case DOWNLOADING -> {
			}
		}
	}

	private void start() {
		final Minecraft mc = Minecraft.getInstance();
		final List<PlanFile> snapshot = List.copyOf(plan);
		total = Math.max(1, Downloader.totalSize(snapshot));
		state = State.DOWNLOADING;
		rebuildWidgets();
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
					if (Compat.currentScreen(mc) == this) {
						rebuildWidgets();
					}
				});
			} catch (Exception e) {
				final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
				mc.execute(() -> {
					error = msg;
					state = State.ERROR;
					if (Compat.currentScreen(mc) == this) {
						rebuildWidgets();
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
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Compat.background(this, ctx, mouseX, mouseY, delta);
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		int cx = this.width / 2;
		ctx.centeredText(this.font, this.title, cx, 12, 0xFFFFFFFF);
		switch (state) {
			case CONFIRM -> {
				ctx.centeredText(this.font, Component.translatable("downloadmod.install.review"), cx, 34, 0xFFDDDDDD);
				int from = page * pageSize;
				int to = Math.min(plan.size(), from + pageSize);
				for (int i = from; i < to; i++) {
					PlanFile f = plan.get(i);
					String line = f.filename() + "  (" + mb(f.size()) + ")"
						+ (f.dependency() ? "  [dependency]" : "")
						+ (f.replaces() != null ? "  [replaces " + f.replaces().getFileName() + "]" : "");
					ctx.text(this.font, Component.literal(this.font.plainSubstrByWidth(line, this.width - 54)),
						10, LIST_TOP + (i - from) * LIST_STEP + 2, 0xFFFFFFFF);
				}
				int warnY = this.height - 68;
				if (removedDependency) {
					ctx.text(this.font,
						Component.literal(this.font.plainSubstrByWidth(Component.translatable("downloadmod.install.removed_dep").getString(), this.width - 20)),
						10, warnY, 0xFFFF8855);
					warnY -= 10;
				}
				if (note != null && !note.isBlank()) {
					ctx.text(this.font, Component.literal(this.font.plainSubstrByWidth(note, this.width - 20)),
						10, warnY, 0xFFFFD866);
				}
				ctx.text(this.font,
					Component.literal("Total: " + mb(Downloader.totalSize(plan)) + "   (" + (page + 1) + "/" + (((plan.size() - 1) / pageSize) + 1) + ")"),
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
				ctx.centeredText(this.font,
					Component.literal((int) (frac * 100) + "%  (" + mb(progress.get()) + " / " + mb(total) + ")"),
					cx, y1 + 20, 0xFFFFFFFF);
			}
			case DONE -> {
				int dy = 40;
				for (String key : doneKeys()) {
					for (FormattedCharSequence line : this.font.split(Component.translatable(key), this.width - 40)) {
						ctx.centeredText(this.font, line, cx, dy, 0xFF77FF77);
						dy += 11;
					}
					dy += 6;
				}
				if (needsRestart() && !RestartManager.canRestart()) {
					for (FormattedCharSequence line : this.font.split(Component.translatable("downloadmod.restart.manual"), this.width - 40)) {
						ctx.centeredText(this.font, line, cx, dy, 0xFFFFD866);
						dy += 11;
					}
				}
			}
			case ERROR -> {
				int y = 40;
				for (FormattedCharSequence line : this.font.split(Component.literal(error), this.width - 40)) {
					ctx.centeredText(this.font, line, cx, y, 0xFFFF5555);
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
	public void onClose() {
		Compat.setScreen(this.minecraft, parent);
	}
}
