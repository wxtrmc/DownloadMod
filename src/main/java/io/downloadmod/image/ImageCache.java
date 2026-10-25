package io.downloadmod.image;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import io.downloadmod.Async;
import io.downloadmod.Config;
import io.downloadmod.compat.Compat;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ImageCache {
	public record Img(Identifier id, int w, int h, int texW, int texH) {
	}

	private record Scaled(byte[] png, int guiW, int guiH, int texW, int texH) {
	}

	private interface Source {
		byte[] read() throws IOException, InterruptedException;
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("downloadmod/images");
	private static final boolean DEBUG = Boolean.getBoolean("downloadmod.debug");
	private static final Object LOADING = new Object();
	private static final Object FAILED = new Object();
	private static final int MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024;
	private static final long MAX_SOURCE_PIXELS = 40_000_000L;
	private static final int MAX_ENTRIES = 200;
	private static final long MAX_TEXTURE_BYTES = 128L * 1024 * 1024;

	private static final Map<String, Object> CACHE = new LinkedHashMap<>(64, 0.75f, true);
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(10)).build();
	private static int counter = 0;
	private static long textureBytes = 0;
	private static boolean pluginsReady = false;

	private ImageCache() {
	}

	public static int guiScale() {
		return (int) Math.max(1, Math.round(MinecraftClient.getInstance().getWindow().getScaleFactor()));
	}

	public static Img getUrl(String url, int maxW, int maxH) {
		if (url == null || url.isBlank()) {
			return null;
		}
		int s = guiScale();
		return lookup(maxW + "x" + maxH + "@" + s + "|" + url, () -> fetch(url), maxW, maxH, s, false);
	}

	public static boolean failed(String url, int maxW, int maxH) {
		String key = maxW + "x" + maxH + "@" + guiScale() + "|" + url;
		synchronized (CACHE) {
			return CACHE.get(key) == FAILED;
		}
	}

	public static Img getMod(ModContainer mod, int size) {
		if (mod == null) {
			return null;
		}
		int s = guiScale();
		return lookup("mod|" + mod.getMetadata().getId() + "|" + size + "@" + s, () -> readModIcon(mod, size), size, size, s, false);
	}

	public static Img getFile(Path file, int size, boolean crisp) {
		if (file == null || !Files.isRegularFile(file)) {
			return null;
		}
		int s = guiScale();
		long modified;
		try {
			modified = Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return null;
		}
		return lookup("file|" + file + "|" + modified + "|" + size + "@" + s, () -> readFile(file), size, size, s, crisp);
	}

	public static Img getPack(Path file, int size) {
		if (file == null) {
			return null;
		}
		int s = guiScale();
		long modified;
		try {
			modified = Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			return null;
		}
		return lookup("pack|" + file + "|" + modified + "|" + size + "@" + s, () -> readPackIcon(file), size, size, s, true);
	}

	private static byte[] readPackIcon(Path file) throws IOException {
		if (Files.isDirectory(file)) {
			return readFile(file.resolve("pack.png"));
		}
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
			java.util.zip.ZipEntry entry = zip.getEntry("pack.png");
			if (entry == null) {
				throw new IOException("No pack.png");
			}
			try (InputStream in = zip.getInputStream(entry)) {
				byte[] b = in.readNBytes(MAX_DOWNLOAD_BYTES + 1);
				if (b.length > MAX_DOWNLOAD_BYTES) {
					throw new IOException("Image too large");
				}
				return b;
			}
		}
	}

	public static void draw(DrawContext ctx, Img img, int x, int y) {
		Compat.drawTexture(ctx, img.id(), x, y, img.w(), img.h(), img.texW(), img.texH());
	}

	private static Img lookup(String key, Source source, int maxW, int maxH, int scale, boolean crisp) {
		synchronized (CACHE) {
			Object v = CACHE.get(key);
			if (v instanceof Img img) {
				return img;
			}
			if (v != null) {
				return null;
			}
			CACHE.put(key, LOADING);
		}
		Async.run(() -> {
			try {
				Scaled s = scale(source.read(), maxW, maxH, scale, crisp);
				MinecraftClient.getInstance().execute(() -> register(key, s));
			} catch (Throwable t) {
				if (DEBUG) {
					LOGGER.warn("Image failed [{}]: {}", key, t.toString());
				}
				synchronized (CACHE) {
					CACHE.put(key, FAILED);
				}
			}
		});
		return null;
	}

	private static byte[] fetch(String url) throws IOException, InterruptedException {
		URI uri = URI.create(url);
		if (!"https".equals(uri.getScheme()) || !Config.ALLOWED_IMAGE_HOSTS.contains(uri.getHost())) {
			throw new IOException("Blocked image host: " + uri.getHost());
		}
		HttpRequest req = HttpRequest.newBuilder(uri).header("User-Agent", Config.USER_AGENT)
			.timeout(Duration.ofSeconds(20)).GET().build();
		HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
		if (res.statusCode() != 200) {
			throw new IOException("HTTP " + res.statusCode());
		}
		try (InputStream in = res.body()) {
			byte[] b = in.readNBytes(MAX_DOWNLOAD_BYTES + 1);
			if (b.length > MAX_DOWNLOAD_BYTES) {
				throw new IOException("Image too large");
			}
			return b;
		}
	}

	private static byte[] readFile(Path file) throws IOException {
		if (Files.size(file) > MAX_DOWNLOAD_BYTES) {
			throw new IOException("Image too large");
		}
		return Files.readAllBytes(file);
	}

	private static byte[] readModIcon(ModContainer mod, int size) throws IOException {
		String id = mod.getMetadata().getId();
		String p = mod.getMetadata().getIconPath(512).or(() -> mod.getMetadata().getIconPath(size))
			.orElse("assets/" + id + "/icon.png");
		Path path = mod.findPath(p).orElseThrow(() -> new IOException("Icon not found in jar: " + p));
		return readFile(path);
	}

	private static synchronized void ensurePlugins() {
		if (pluginsReady) {
			return;
		}
		pluginsReady = true;
		System.setProperty("java.awt.headless", "true");
		try {
			IIORegistry.getDefaultInstance().registerServiceProvider(new WebPImageReaderSpi());
		} catch (Throwable t) {
			LOGGER.warn("WebP decoder could not be registered: {}", t.toString());
		}
	}

	private static Scaled scale(byte[] raw, int maxW, int maxH, int s, boolean crisp) throws IOException {
		ensurePlugins();
		BufferedImage in = ImageIO.read(new ByteArrayInputStream(raw));
		if (in == null) {
			throw new IOException("Unsupported image format");
		}
		if ((long) in.getWidth() * in.getHeight() > MAX_SOURCE_PIXELS) {
			throw new IOException("Image too big");
		}
		double f = Math.min((maxW * (double) s) / in.getWidth(), (maxH * (double) s) / in.getHeight());
		f = Math.min(f, 4.0);
		int guiW = Math.max(1, (int) Math.round(in.getWidth() * f / s));
		int guiH = Math.max(1, (int) Math.round(in.getHeight() * f / s));
		BufferedImage out = resample(in, guiW * s, guiH * s, crisp);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(out, "png", baos);
		return new Scaled(baos.toByteArray(), guiW, guiH, guiW * s, guiH * s);
	}

	private static BufferedImage resample(BufferedImage in, int w, int h, boolean crisp) {
		if (crisp && w >= in.getWidth() && h >= in.getHeight()) {
			return redraw(in, w, h, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		}
		BufferedImage cur = in.getType() == BufferedImage.TYPE_INT_ARGB ? in : redraw(in, in.getWidth(), in.getHeight(),
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		if (w > cur.getWidth() || h > cur.getHeight()) {
			int k = (int) Math.min(8, Math.ceil(Math.max(w / (double) cur.getWidth(), h / (double) cur.getHeight())));
			if (k > 1) {
				cur = redraw(cur, cur.getWidth() * k, cur.getHeight() * k, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			}
		}
		while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
			cur = redraw(cur, cur.getWidth() / 2, cur.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}
		if (cur.getWidth() != w || cur.getHeight() != h) {
			cur = redraw(cur, w, h, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		}
		return cur;
	}

	private static BufferedImage redraw(BufferedImage src, int w, int h, Object interpolation) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	private static void register(String key, Scaled s) {
		try {
			NativeImage image = NativeImage.read(new ByteArrayInputStream(s.png()));
			Identifier id = Identifier.of(Config.MOD_ID, "img/" + (counter++));
			NativeImageBackedTexture tex = Compat.newTexture(id, image);
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
			synchronized (CACHE) {
				CACHE.put(key, new Img(id, s.guiW(), s.guiH(), s.texW(), s.texH()));
				textureBytes += (long) s.texW() * s.texH() * 4;
				evict();
			}
		} catch (Throwable t) {
			if (DEBUG) {
				LOGGER.warn("Texture registration failed [{}]: {}", key, t.toString());
			}
			synchronized (CACHE) {
				CACHE.put(key, FAILED);
			}
		}
	}

	private static void evict() {
		Iterator<Map.Entry<String, Object>> it = CACHE.entrySet().iterator();
		while ((CACHE.size() > MAX_ENTRIES || textureBytes > MAX_TEXTURE_BYTES) && it.hasNext()) {
			Object v = it.next().getValue();
			if (v instanceof Img img) {
				MinecraftClient.getInstance().getTextureManager().destroyTexture(img.id());
				textureBytes -= (long) img.texW() * img.texH() * 4;
				it.remove();
			} else if (v == FAILED) {
				it.remove();
			}
		}
	}
}
