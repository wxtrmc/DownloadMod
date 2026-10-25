package io.downloadmod.gui;

import io.downloadmod.Async;
import io.downloadmod.api.ContentType;
import io.downloadmod.api.Models;
import io.downloadmod.api.ModrinthApi;
import io.downloadmod.image.GuiIcons;
import io.downloadmod.image.ImageCache;
import io.downloadmod.install.DownloadQueue;
import io.downloadmod.install.InstalledIndex;
import io.downloadmod.install.PlanFile;
import io.downloadmod.install.Resolver;
import io.downloadmod.install.RestartManager;
import io.downloadmod.ModConfig;
import io.downloadmod.install.WorldPacks;
import io.downloadmod.servers.ServerCatalog;
import io.downloadmod.servers.ServerStatus;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DownloadModScreen extends Screen {
	private enum Tab {
		INSTALLED(null, "downloadmod.tab.installed"),
		MODS(ContentType.MOD, "downloadmod.tab.mods"),
		RESOURCEPACKS(ContentType.RESOURCEPACK, "downloadmod.tab.resourcepacks"),
		SHADERS(ContentType.SHADER, "downloadmod.tab.shaders"),
		DATAPACKS(ContentType.DATAPACK, "downloadmod.tab.datapacks"),
		WORLD_PACKS(null, "downloadmod.tab.world_packs"),
		SERVERS(null, "downloadmod.tab.servers");

		final ContentType type;
		final String key;

		Tab(ContentType type, String key) {
			this.type = type;
			this.key = key;
		}
	}

	private record Link(String label, String url) {
	}

	private record Row(String title, String meta, String description, String iconUrl, ModContainer mod,
					   Models.Hit hit, ServerCatalog.Entry server, List<Link> links, WorldPacks.Pack pack) {
		Row(String title, String meta, String description, String iconUrl, ModContainer mod,
			Models.Hit hit, ServerCatalog.Entry server, List<Link> links) {
			this(title, meta, description, iconUrl, mod, hit, server, links, null);
		}
	}

	private static final Set<String> HIDDEN_MODS = Set.of("minecraft", "java", "fabricloader");
	private static final int ROW_STEP = 32;
	private static final int ROW_H = 30;
	private static final int ICON = 26;
	private static final long SEARCH_DELAY_MS = 350;

	private static Set<String> settingsIds;

	private final Screen parent;
	private final List<ClickableWidget> dynamic = new ArrayList<>();
	private final List<ButtonWidget> tabButtons = new ArrayList<>();
	private final List<Tab> tabsShown = new ArrayList<>();
	private final Map<String, Models.Project> projects = new HashMap<>();
	private final Set<String> requestedProjects = new HashSet<>();

	private Tab tab = Tab.MODS;
	private TextFieldWidget searchBox;
	private List<Row> rows = new ArrayList<>();
	private int page = 0;
	private int pageSize = 4;
	private int totalHits = 0;
	private int selected = -1;
	private boolean busy = false;
	private boolean firstLoad = true;
	private int requestId = 0;
	private Text status = Text.empty();
	private Text notice = null;
	private boolean multi = false;
	private int galleryIndex = 0;
	private boolean searchDirty = false;
	private long searchDueAt = 0;
	private boolean suppressSearch = false;
	private List<OrderedText> detailLines = List.of();
	private int previewY = -1;
	private int previewH = 0;

	public DownloadModScreen(Screen parent) {
		super(Text.translatable("downloadmod.title"));
		this.parent = parent;
	}

	private List<Tab> visibleTabs() {
		List<Tab> tabs = new ArrayList<>(List.of(Tab.INSTALLED, Tab.MODS, Tab.RESOURCEPACKS));
		if (FabricLoader.getInstance().isModLoaded("iris")) {
			tabs.add(Tab.SHADERS);
		}
		tabs.add(Tab.DATAPACKS);
		if (ModConfig.showWorldPacks && WorldPacks.inWorld()) {
			tabs.add(Tab.WORLD_PACKS);
		}
		tabs.add(Tab.SERVERS);
		return tabs;
	}

	private static void addLink(List<Link> list, String label, String url) {
		if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
			list.add(new Link(label, url));
		}
	}

	private boolean validSelection() {
		return selected >= 0 && selected < rows.size();
	}

	private <T extends ClickableWidget> T dyn(T widget) {
		addDrawableChild(widget);
		dynamic.add(widget);
		return widget;
	}

	private void refresh() {
		for (ClickableWidget w : dynamic) {
			remove(w);
		}
		dynamic.clear();
		updateTabStates();
		buildDynamic();
	}

	private void updateTabStates() {
		for (int i = 0; i < tabButtons.size(); i++) {
			tabButtons.get(i).active = tabsShown.get(i) != tab;
		}
	}

	private InstalledIndex.Entry installedOf(Row r) {
		return r.hit() == null ? null : InstalledIndex.get(r.hit().projectId());
	}

	@Override
	protected void init() {
		dynamic.clear();
		tabButtons.clear();
		tabsShown.clear();
		pageSize = Math.max(2, (this.height - 142) / ROW_STEP);
		buildStatic();
		buildDynamic();
		if (firstLoad) {
			firstLoad = false;
			runSearch();
		}
		InstalledIndex.refreshIfNeeded(() -> {
			if (this.client != null && this.client.currentScreen == this) {
				refresh();
			}
		});
	}

	private void buildStatic() {
		int leftW = Math.max(120, this.width / 2 - 14);
		tabsShown.addAll(visibleTabs());
		int tw = Math.min(100, (this.width - 20) / tabsShown.size());
		int x0 = (this.width - tw * tabsShown.size()) / 2;
		for (int i = 0; i < tabsShown.size(); i++) {
			Tab t = tabsShown.get(i);
			ButtonWidget b = ButtonWidget.builder(Text.translatable(t.key), btn -> switchTab(t))
				.position(x0 + i * tw, 22).size(tw - 2, 20).build();
			b.active = t != tab;
			tabButtons.add(b);
			addDrawableChild(b);
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.settings"),
			b -> this.client.setScreen(new SettingsScreen(this))).position(this.width - 66, 2).size(62, 18).build());

		if (searchBox == null) {
			searchBox = new TextFieldWidget(this.textRenderer, 10, 46, leftW, 20, Text.translatable("downloadmod.search"));
			searchBox.setChangedListener(s -> {
				if (!suppressSearch) {
					searchDirty = true;
					searchDueAt = System.currentTimeMillis() + SEARCH_DELAY_MS;
				}
			});
		}
		searchBox.setPosition(10, 46);
		searchBox.setWidth(leftW);
		addDrawableChild(searchBox);

		addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.back"), b -> close())
			.position(this.width - 106, this.height - 28).size(96, 20).build());
	}

	private void buildDynamic() {
		int leftW = Math.max(120, this.width / 2 - 14);
		int rx = this.width / 2 + 4;
		int rw = Math.max(100, this.width / 2 - 14);

		if (RestartManager.isRestartPending()) {
			String key = RestartManager.canRestart() ? "downloadmod.restart" : "downloadmod.close_game";
			dyn(ButtonWidget.builder(Text.translatable(key), b -> RestartDialog.open(this))
				.position(this.width - 66 - 4 - 110, 2).size(110, 18).build());
		}

		int shown = Math.min(rows.size(), pageSize);
		for (int i = 0; i < shown; i++) {
			final int idx = i;
			ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> select(idx))
				.position(10, 72 + i * ROW_STEP).size(leftW, ROW_H).build();
			b.active = idx != selected;
			dyn(b);
		}

		ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> {
			page--;
			runSearch();
			refresh();
		}).position(10, this.height - 52).size(30, 20).build();
		prev.active = page > 0 && !busy;
		dyn(prev);
		ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> {
			page++;
			runSearch();
			refresh();
		}).position(44, this.height - 52).size(30, 20).build();
		next.active = (page + 1) * pageSize < totalHits && !busy;
		dyn(next);

		if (tab == Tab.SERVERS) {
			dyn(ButtonWidget.builder(Text.translatable("downloadmod.add_my_server"),
					b -> this.client.setScreen(new AddServerScreen(this, this::runSearch)))
				.position(10, this.height - 28).size(120, 20).build());
		} else if (tab != Tab.WORLD_PACKS) {
			dyn(ButtonWidget.builder(Text.translatable(multi ? "downloadmod.multi.on" : "downloadmod.multi"), b -> {
				multi = !multi;
				refresh();
			}).position(10, this.height - 28).size(120, 20).build());
			if (multi) {
				ButtonWidget all = ButtonWidget.builder(Text.translatable("downloadmod.download_all", DownloadQueue.size()),
					b -> install(new ArrayList<>(DownloadQueue.items()), Text.translatable("downloadmod.download_all.name").getString()))
					.position(134, this.height - 28).size(120, 20).build();
				all.active = DownloadQueue.size() > 0 && !busy;
				dyn(all);
			}
		}

		detailLines = List.of();
		previewY = -1;
		previewH = 0;
		List<Link> links = List.of();
		Row sel = validSelection() ? rows.get(selected) : null;
		if (sel != null) {
			links = linksFor(sel);
			List<GalleryScreen.Item> gallery = galleryFor(sel);
			boolean hasGallery = !gallery.isEmpty();
			int top = 88;
			int bottomLimit = this.height - 80;
			int maxLines = Math.max(0, Math.min(4, (bottomLimit - top - (hasGallery ? 22 : 0)) / 10));
			List<OrderedText> lines = new ArrayList<>();
			InstalledIndex.Entry inst = installedOf(sel);
			if (inst != null) {
				lines.add(installedLine(inst));
			}
			if (sel.description() != null) {
				lines.addAll(this.textRenderer.wrapLines(Text.literal(sel.description()), rw));
			}
			detailLines = lines.subList(0, Math.min(maxLines, lines.size()));
			if (hasGallery) {
				galleryIndex = Math.max(0, Math.min(galleryIndex, gallery.size() - 1));
				int gy = top + detailLines.size() * 10 + 2;
				ButtonWidget gPrev = ButtonWidget.builder(Text.literal("<"),
					b -> galleryIndex = (galleryIndex - 1 + gallery.size()) % gallery.size())
					.position(rx, gy).size(20, 18).build();
				ButtonWidget gNext = ButtonWidget.builder(Text.literal(">"),
					b -> galleryIndex = (galleryIndex + 1) % gallery.size())
					.position(rx + 22, gy).size(20, 18).build();
				gPrev.active = gallery.size() > 1;
				gNext.active = gallery.size() > 1;
				dyn(gPrev);
				dyn(gNext);
				dyn(ButtonWidget.builder(Text.translatable("downloadmod.gallery.open", galleryIndex + 1, gallery.size()),
						b -> openGallery(gallery))
					.position(rx + 44, gy).size(Math.max(40, rw - 44), 18).build());
				previewY = gy + 22;
				previewH = bottomLimit - previewY;
				if (previewH >= 40) {
					dyn(ButtonWidget.builder(Text.empty(), b -> openGallery(gallery))
						.position(rx, previewY).size(rw, previewH).build());
				} else {
					previewH = 0;
				}
			}
		}

		if (!links.isEmpty()) {
			int n = links.size();
			int lw = (rw - (n - 1) * 2) / n;
			for (int i = 0; i < n; i++) {
				final Link link = links.get(i);
				dyn(ButtonWidget.builder(Text.literal(link.label()), b -> ConfirmLinkScreen.open(this, link.url(), true))
					.position(rx + i * (lw + 2), this.height - 76).size(lw, 18).build());
			}
		}

		if (tab == Tab.INSTALLED) {
			boolean settings = sel != null && hasModSettings(sel);
			int half = (rw - 2) / 2;
			dyn(ButtonWidget.builder(Text.translatable("downloadmod.open_folder"), b -> openModsFolder())
				.position(rx, this.height - 52).size(settings ? half : rw, 20).build());
			if (settings) {
				dyn(ButtonWidget.builder(Text.translatable("downloadmod.mod_settings"), b -> openModSettings(rows.get(selected)))
					.position(rx + half + 2, this.height - 52).size(rw - half - 2, 20).build());
			}
		} else if (tab == Tab.WORLD_PACKS) {
			final WorldPacks.Pack pack = sel == null ? null : sel.pack();
			int half = (rw - 2) / 2;
			ButtonWidget toggle = ButtonWidget.builder(
					Text.translatable(pack != null && pack.enabled() ? "downloadmod.pack.disable" : "downloadmod.pack.enable"),
					b -> togglePack(pack))
				.position(rx, this.height - 52).size(half, 20).build();
			toggle.active = pack != null && !busy;
			dyn(toggle);
			ButtonWidget delete = ButtonWidget.builder(Text.translatable("downloadmod.pack.delete"), b -> confirmDeletePack(pack))
				.position(rx + half + 2, this.height - 52).size(rw - half - 2, 20).build();
			delete.active = pack != null && !busy;
			dyn(delete);
		} else {
			InstalledIndex.Entry inst = sel == null ? null : installedOf(sel);
			Text label;
			boolean active;
			if (tab == Tab.SERVERS) {
				label = Text.translatable("downloadmod.add_server");
				active = sel != null && !busy;
			} else if (multi) {
				boolean queued = sel != null && sel.hit() != null && DownloadQueue.contains(sel.hit().projectId());
				label = Text.translatable(inst != null ? "downloadmod.installed"
					: queued ? "downloadmod.queue.remove" : "downloadmod.queue.add");
				active = sel != null && !busy && inst == null;
			} else if (inst == null) {
				label = Text.translatable("downloadmod.download");
				active = sel != null && !busy;
			} else if (inst.updateAvailable()) {
				label = Text.translatable("downloadmod.update_to", inst.latest().versionNumber());
				active = !busy;
			} else {
				label = Text.translatable("downloadmod.installed");
				active = false;
			}
			boolean showVersions = tab.type != null && sel != null && sel.hit() != null;
			int vw = showVersions ? 74 : 0;
			ButtonWidget action = ButtonWidget.builder(label, b -> onAction())
				.position(rx, this.height - 52).size(rw - (showVersions ? vw + 2 : 0), 20).build();
			action.active = active;
			dyn(action);
			if (showVersions) {
				ButtonWidget versions = ButtonWidget.builder(Text.translatable("downloadmod.versions"), b -> openVersions())
					.position(rx + rw - vw, this.height - 52).size(vw, 20).build();
				versions.active = !busy;
				dyn(versions);
			}
		}
	}

	private OrderedText installedLine(InstalledIndex.Entry inst) {
		String s = "Installed: " + inst.current().versionNumber()
			+ (inst.updateAvailable() ? "  (update: " + inst.latest().versionNumber() + ")" : "");
		return Text.literal(s).formatted(inst.updateAvailable() ? Formatting.YELLOW : Formatting.GREEN).asOrderedText();
	}

	private void togglePack(WorldPacks.Pack pack) {
		if (pack == null || busy) {
			return;
		}
		final MinecraftClient mc = MinecraftClient.getInstance();
		busy = true;
		status = Text.literal(pack.enabled() ? "Disabling..." : "Enabling...");
		refresh();
		Async.run(() -> {
			final boolean ok = WorldPacks.setEnabled(pack.id(), !pack.enabled());
			mc.execute(() -> {
				if (mc.currentScreen != this) {
					return;
				}
				busy = false;
				if (!ok) {
					notice = Text.literal("Could not change the data pack");
				}
				runSearch();
			});
		});
	}

	private void confirmDeletePack(WorldPacks.Pack pack) {
		if (pack == null || busy) {
			return;
		}
		final MinecraftClient mc = MinecraftClient.getInstance();
		mc.setScreen(new ConfirmScreen(yes -> {
			mc.setScreen(this);
			if (yes) {
				deletePack(pack);
			}
		}, Text.translatable("downloadmod.pack.delete.title"), Text.literal(pack.name())));
	}

	private void deletePack(WorldPacks.Pack pack) {
		final MinecraftClient mc = MinecraftClient.getInstance();
		busy = true;
		status = Text.literal("Deleting...");
		refresh();
		Async.run(() -> {
			final boolean ok = WorldPacks.delete(pack);
			mc.execute(() -> {
				if (mc.currentScreen != this) {
					return;
				}
				busy = false;
				if (!ok) {
					notice = Text.literal("Could not delete it (file in use). Close the world and delete it from the folder.");
				}
				runSearch();
			});
		});
	}

	private void switchTab(Tab t) {
		tab = t;
		page = 0;
		rows = new ArrayList<>();
		selected = -1;
		totalHits = 0;
		galleryIndex = 0;
		suppressSearch = true;
		searchBox.setText("");
		suppressSearch = false;
		searchDirty = false;
		runSearch();
		refresh();
	}

	private static String compact(int n) {
		if (n >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
		}
		if (n >= 1_000) {
			return String.format(Locale.ROOT, "%.1fK", n / 1_000.0);
		}
		return String.valueOf(n);
	}

	private void runSearch() {
		final MinecraftClient mc = MinecraftClient.getInstance();
		final int id = ++requestId;
		final Tab t = tab;
		final String q = searchBox == null ? "" : searchBox.getText().trim();
		final int p = page;
		final int size = pageSize;
		busy = true;
		status = Text.literal("Loading...");
		Async.run(() -> {
			try {
				List<Row> result = new ArrayList<>();
				int total;
				String lq = q.toLowerCase(Locale.ROOT);
				if (t == Tab.SERVERS) {
					List<ServerCatalog.Entry> filtered = ServerCatalog.load().stream()
						.filter(e -> lq.isEmpty() || e.address().toLowerCase(Locale.ROOT).contains(lq)).toList();
					java.util.Map<String, ServerStatus.Status> stats = ServerStatus.fetchAll(
						filtered.stream().map(ServerCatalog.Entry::address).toList());
					filtered = filtered.stream().sorted(Comparator.comparingInt(
						(ServerCatalog.Entry e) -> stats.getOrDefault(e.address(), ServerStatus.Status.UNKNOWN).players()).reversed()).toList();
					total = filtered.size();
					for (int i = p * size; i < Math.min(total, (p + 1) * size); i++) {
						ServerCatalog.Entry e = filtered.get(i);
						ServerStatus.Status st = stats.getOrDefault(e.address(), ServerStatus.Status.UNKNOWN);
						String meta = e.address() + (st.players() >= 0 ? "  -  " + String.format(Locale.US, "%,d", st.players()) + " online" : "  -  offline");
						result.add(new Row(e.name(), meta, e.description(), e.iconUrl(), null, null, e, List.of()));
					}
				} else if (t == Tab.INSTALLED) {
					List<ModContainer> mods = FabricLoader.getInstance().getAllMods().stream()
						.filter(m -> m.getContainingMod().isEmpty())
						.filter(m -> !HIDDEN_MODS.contains(m.getMetadata().getId()))
						.filter(m -> lq.isEmpty()
							|| m.getMetadata().getName().toLowerCase(Locale.ROOT).contains(lq)
							|| m.getMetadata().getId().toLowerCase(Locale.ROOT).contains(lq))
						.sorted(Comparator.comparing(m -> m.getMetadata().getName().toLowerCase(Locale.ROOT)))
						.toList();
					total = mods.size();
					for (int i = p * size; i < Math.min(total, (p + 1) * size); i++) {
						ModContainer m = mods.get(i);
						ModMetadata md = m.getMetadata();
						List<Link> ls = new ArrayList<>();
						addLink(ls, "Website", md.getContact().get("homepage").orElse(null));
						addLink(ls, "Source", md.getContact().get("sources").orElse(null));
						addLink(ls, "Issues", md.getContact().get("issues").orElse(null));
						result.add(new Row(md.getName(), "v" + md.getVersion().getFriendlyString() + "  -  " + md.getId(),
							md.getDescription(), null, m, null, null, ls));
					}
				} else if (t == Tab.WORLD_PACKS) {
					List<WorldPacks.Pack> packs = WorldPacks.list().stream()
						.filter(k -> lq.isEmpty() || k.name().toLowerCase(Locale.ROOT).contains(lq)
							|| k.id().toLowerCase(Locale.ROOT).contains(lq)).toList();
					total = packs.size();
					for (int i = p * size; i < Math.min(total, (p + 1) * size); i++) {
						WorldPacks.Pack k = packs.get(i);
						result.add(new Row(k.name(), (k.enabled() ? "Enabled" : "Disabled") + "  -  " + k.file().getFileName(),
							k.description(), null, null, null, null, List.of(), k));
					}
				} else {
					Models.SearchResponse r = ModrinthApi.search(q, t.type, p * size, size);
					total = r.totalHits();
					for (Models.Hit h : r.hits()) {
						result.add(new Row(h.title(), "by " + h.author() + "  -  " + compact(h.downloads()) + " downloads",
							h.description(), h.iconUrl(), null, h, null, null));
					}
				}
				final int finalTotal = total;
				mc.execute(() -> {
					if (id != requestId || mc.currentScreen != this) {
						return;
					}
					rows = result;
					totalHits = finalTotal;
					selected = -1;
					galleryIndex = 0;
					busy = false;
					status = notice != null ? notice
						: rows.isEmpty() ? Text.literal(t == Tab.WORLD_PACKS ? "No data packs in this world" : "No results") : Text.empty();
					notice = null;
					refresh();
				});
			} catch (Exception e) {
				final String msg = e.getMessage();
				mc.execute(() -> {
					if (id != requestId || mc.currentScreen != this) {
						return;
					}
					busy = false;
					status = Text.literal("Error: " + msg);
					refresh();
				});
			}
		});
	}

	private void select(int idx) {
		selected = idx;
		galleryIndex = 0;
		Row r = rows.get(idx);
		if (r.hit() != null) {
			final String pid = r.hit().projectId();
			if (!projects.containsKey(pid) && requestedProjects.add(pid)) {
				final MinecraftClient mc = MinecraftClient.getInstance();
				Async.run(() -> {
					try {
						Models.Project p = ModrinthApi.project(pid);
						mc.execute(() -> {
							projects.put(pid, p);
							if (mc.currentScreen == this) {
								refresh();
							}
						});
					} catch (Exception ignored) {
					}
				});
			}
		}
		refresh();
	}

	private List<Link> linksFor(Row r) {
		if (r.hit() == null) {
			return r.links() == null ? List.of() : r.links();
		}
		List<Link> l = new ArrayList<>();
		l.add(new Link("Modrinth", "https://modrinth.com/" + r.hit().projectType() + "/" + r.hit().slug()));
		Models.Project p = projects.get(r.hit().projectId());
		if (p != null) {
			addLink(l, "Source", p.sourceUrl());
			addLink(l, "Issues", p.issuesUrl());
			addLink(l, "Wiki", p.wikiUrl());
			addLink(l, "Discord", p.discordUrl());
		}
		return l;
	}

	private List<GalleryScreen.Item> galleryFor(Row r) {
		if (r.hit() == null) {
			return List.of();
		}
		List<GalleryScreen.Item> out = new ArrayList<>();
		Models.Project p = projects.get(r.hit().projectId());
		if (p != null && p.gallery() != null && !p.gallery().isEmpty()) {
			List<Models.GalleryItem> sorted = new ArrayList<>(p.gallery());
			sorted.sort(Comparator.comparing((Models.GalleryItem g) -> !g.featured()).thenComparingInt(Models.GalleryItem::ordering));
			for (Models.GalleryItem g : sorted) {
				if (g.url() != null) {
					out.add(new GalleryScreen.Item(g.url(), g.title(), g.description()));
				}
			}
			return out;
		}
		Models.Hit h = r.hit();
		if (h.gallery() != null) {
			for (String u : h.gallery()) {
				out.add(new GalleryScreen.Item(u, null, null));
			}
		}
		if (out.isEmpty() && h.featuredGallery() != null) {
			out.add(new GalleryScreen.Item(h.featuredGallery(), null, null));
		}
		return out;
	}

	private void openGallery(List<GalleryScreen.Item> gallery) {
		this.client.setScreen(new GalleryScreen(this, gallery, galleryIndex, i -> galleryIndex = i));
	}

	private void openModsFolder() {
		Util.getOperatingSystem().open(FabricLoader.getInstance().getGameDir().resolve("mods").toUri());
	}

	private static synchronized Set<String> settingsIds() {
		if (settingsIds == null) {
			Set<String> ids = new HashSet<>();
			try {
				for (EntrypointContainer<Object> c : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
					ids.add(c.getProvider().getMetadata().getId());
				}
			} catch (Throwable ignored) {
			}
			settingsIds = ids;
		}
		return settingsIds;
	}

	private static boolean hasModSettings(Row r) {
		return r.mod() != null && settingsIds().contains(r.mod().getMetadata().getId());
	}

	private void openModSettings(Row r) {
		String id = r.mod().getMetadata().getId();
		try {
			for (EntrypointContainer<Object> c : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
				if (!c.getProvider().getMetadata().getId().equals(id)) {
					continue;
				}
				Object api = c.getEntrypoint();
				Object factory = api.getClass().getMethod("getModConfigScreenFactory").invoke(api);
				Class<?> factoryType = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory", false,
					api.getClass().getClassLoader());
				Method create = factoryType.getMethod("create", Screen.class);
				Object screen = create.invoke(factory, this);
				if (screen instanceof Screen s) {
					this.client.setScreen(s);
					return;
				}
			}
			status = Text.literal("This mod has no settings screen.");
		} catch (Throwable t) {
			status = Text.literal("Settings unavailable (this mod's screen needs Mod Menu installed).");
		}
	}

	private void openVersions() {
		if (!validSelection()) {
			return;
		}
		final Row row = rows.get(selected);
		final ContentType type = tab.type;
		InstalledIndex.Entry inst = installedOf(row);
		final Path replaces = inst == null ? null : inst.file();
		this.client.setScreen(new VersionPickerScreen(this, row.hit(), type, inst == null ? null : inst.current().id(),
			v -> installVersion(row, type, v, replaces)));
	}

	private void onAction() {
		if (!validSelection()) {
			return;
		}
		final Row row = rows.get(selected);
		if (tab == Tab.SERVERS) {
			boolean added = ServerCatalog.addToGame(row.server());
			status = Text.literal(added ? "Added to your server list" : "Already in your server list");
			return;
		}
		if (multi) {
			boolean queued = DownloadQueue.toggle(row.hit(), tab.type);
			status = Text.literal(queued ? "Added to the list (" + DownloadQueue.size() + ")"
				: "Removed from the list (" + DownloadQueue.size() + ")");
			refresh();
			return;
		}
		InstalledIndex.Entry inst = installedOf(row);
		if (inst != null) {
			if (inst.updateAvailable()) {
				installVersion(row, tab.type, inst.latest(), inst.file());
			}
			return;
		}
		install(List.of(new DownloadQueue.Item(row.hit(), tab.type)), row.title());
	}

	private void install(List<DownloadQueue.Item> items, String name) {
		if (items.isEmpty()) {
			return;
		}
		boolean needsWorld = items.stream().anyMatch(i -> i.type() == ContentType.DATAPACK);
		if (!needsWorld) {
			resolveAndOpen(items, name, null);
			return;
		}
		Path dir = Resolver.currentWorldDatapackDir();
		if (dir != null) {
			resolveAndOpen(items, name, dir);
			return;
		}
		this.client.setScreen(new WorldPickerScreen(this, picked -> resolveAndOpen(items, name, picked)));
	}

	private void installVersion(Row row, ContentType type, Models.Version version, Path replaces) {
		if (type == ContentType.DATAPACK) {
			Path dir = Resolver.currentWorldDatapackDir();
			if (dir == null) {
				this.client.setScreen(new WorldPickerScreen(this, picked -> resolveVersionAndOpen(row, type, version, replaces, picked)));
				return;
			}
			resolveVersionAndOpen(row, type, version, replaces, dir);
			return;
		}
		resolveVersionAndOpen(row, type, version, replaces, null);
	}

	private void resolveVersionAndOpen(Row row, ContentType type, Models.Version version, Path replaces, Path datapackDir) {
		final MinecraftClient mc = MinecraftClient.getInstance();
		busy = true;
		status = Text.literal("Checking dependencies...");
		refresh();
		Async.run(() -> {
			try {
				List<PlanFile> plan = Resolver.resolveVersion(row.hit(), type, version, datapackDir, replaces);
				mc.execute(() -> {
					busy = false;
					if (plan.isEmpty()) {
						status = Text.literal("Already installed");
						refresh();
					} else {
						mc.setScreen(new InstallScreen(this, row.title(), plan, null, null));
					}
				});
			} catch (Exception e) {
				final String msg = e.getMessage();
				mc.execute(() -> {
					busy = false;
					status = Text.literal("Error: " + msg);
					refresh();
				});
			}
		});
	}

	private void resolveAndOpen(List<DownloadQueue.Item> items, String name, Path datapackDir) {
		final MinecraftClient mc = MinecraftClient.getInstance();
		busy = true;
		status = Text.literal("Checking compatible versions and dependencies...");
		refresh();
		Async.run(() -> {
			try {
				List<String> skipped = new ArrayList<>();
				List<PlanFile> plan = Resolver.resolveAll(items, datapackDir, skipped);
				String note = skipped.isEmpty() ? null : "Skipped: " + String.join(", ", skipped);
				mc.execute(() -> {
					busy = false;
					if (plan.isEmpty()) {
						status = Text.literal(skipped.isEmpty() ? "Already installed" : "Error: " + skipped.get(0));
						if (skipped.isEmpty()) {
							DownloadQueue.removeAll(items);
						}
						refresh();
					} else {
						mc.setScreen(new InstallScreen(this, name, plan, note, () -> DownloadQueue.removeAll(items)));
					}
				});
			} catch (Exception e) {
				final String msg = e.getMessage();
				mc.execute(() -> {
					busy = false;
					status = Text.literal("Error: " + msg);
					refresh();
				});
			}
		});
	}

	private static ImageCache.Img iconOf(Row r, int size) {
		if (r.pack() != null) {
			return ImageCache.getPack(r.pack().file(), size);
		}
		return r.mod() != null ? ImageCache.getMod(r.mod(), size) : ImageCache.getUrl(r.iconUrl(), size, size);
	}

	private void drawIcon(DrawContext ctx, Row r, int x, int y, int size) {
		ImageCache.Img img = iconOf(r, size);
		if (img == null) {
			ctx.fill(x, y, x + size, y + size, 0xFF3A3A3A);
			String initial = r.title() == null || r.title().isBlank() ? "?" : r.title().substring(0, 1).toUpperCase(Locale.ROOT);
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(initial), x + size / 2, y + size / 2 - 4, 0xFFAAAAAA);
		} else {
			ImageCache.draw(ctx, img, x + (size - img.w()) / 2, y + (size - img.h()) / 2);
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (searchDirty && System.currentTimeMillis() >= searchDueAt) {
			searchDirty = false;
			page = 0;
			runSearch();
		}

		super.render(ctx, mouseX, mouseY, delta);
		int titleW = this.textRenderer.getWidth(this.title);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
		GuiIcons.draw(ctx, "logo", this.width / 2 - titleW / 2 - 24, 1, 20);

		int leftW = Math.max(120, this.width / 2 - 14);
		int rx = this.width / 2 + 4;
		int rw = Math.max(100, this.width / 2 - 14);

		int shown = Math.min(rows.size(), pageSize);
		for (int i = 0; i < shown; i++) {
			Row r = rows.get(i);
			int y = 72 + i * ROW_STEP;
			drawIcon(ctx, r, 13, y + 2, ICON);
			int tx = 13 + ICON + 5;
			String tag = null;
			int tagColor = 0;
			InstalledIndex.Entry inst = installedOf(r);
			if (inst != null) {
				tag = inst.updateAvailable() ? "Update" : "Installed";
				tagColor = inst.updateAvailable() ? 0xFFFFD866 : 0xFF55FF55;
			}
			int tagW = tag == null ? 0 : this.textRenderer.getWidth(tag) + 6;
			ctx.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(r.title(), leftW - tx - 8 - tagW)),
				tx, y + 5, 0xFFFFFFFF);
			if (tag != null) {
				ctx.drawTextWithShadow(this.textRenderer, Text.literal(tag), 10 + leftW - tagW - 10, y + 5, tagColor);
			}
			ctx.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(r.meta(), leftW - tx - 8)),
				tx, y + 17, 0xFFAAAAAA);
			if (r.hit() != null && DownloadQueue.contains(r.hit().projectId())) {
				ctx.drawTextWithShadow(this.textRenderer, Text.literal("+"), 10 + leftW - 10, y + 17, 0xFF55FF55);
			}
		}

		if (validSelection()) {
			Row r = rows.get(selected);
			int y = 50;
			drawIcon(ctx, r, rx, y, 32);
			ctx.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(r.title(), rw - 38)),
				rx + 38, y + 4, 0xFFFFFFFF);
			ctx.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(r.meta(), rw - 38)),
				rx + 38, y + 18, 0xFFAAAAAA);
			int dy = 88;
			for (OrderedText line : detailLines) {
				ctx.drawTextWithShadow(this.textRenderer, line, rx, dy, 0xFFDDDDDD);
				dy += 10;
			}
			if (previewH >= 40) {
				List<GalleryScreen.Item> gallery = galleryFor(r);
				if (!gallery.isEmpty()) {
					int gi = Math.max(0, Math.min(galleryIndex, gallery.size() - 1));
					ImageCache.Img img = ImageCache.getUrl(gallery.get(gi).url(), rw - 4, previewH - 4);
					if (img != null) {
						ImageCache.draw(ctx, img, rx + (rw - img.w()) / 2, previewY + (previewH - img.h()) / 2);
					}
				}
			}
		}

		ctx.drawTextWithShadow(this.textRenderer,
			Text.literal(this.textRenderer.trimToWidth(status.getString(), leftW)), 10, this.height - 64, 0xFFFFD866);
		int pages = Math.max(1, (int) Math.ceil(totalHits / (double) pageSize));
		ctx.drawTextWithShadow(this.textRenderer, Text.literal((page + 1) + "/" + pages), 80, this.height - 46, 0xFFAAAAAA);
	}

	@Override
	public void close() {
		requestId++;
		this.client.setScreen(parent);
	}
}
