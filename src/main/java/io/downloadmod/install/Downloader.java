package io.downloadmod.install;

import io.downloadmod.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class Downloader {
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(15)).build();

	private Downloader() {
	}

	public static long totalSize(List<PlanFile> files) {
		return files.stream().mapToLong(PlanFile::size).sum();
	}

	public static void download(List<PlanFile> files, AtomicLong progress) throws IOException, InterruptedException {
		for (PlanFile f : files) {
			downloadOne(f, progress);
		}
	}

	private static void downloadOne(PlanFile f, AtomicLong progress) throws IOException, InterruptedException {
		URI uri = URI.create(f.url());
		if (!"https".equals(uri.getScheme()) || !Config.ALLOWED_DOWNLOAD_HOSTS.contains(uri.getHost())) {
			throw new IOException("Blocked download host: " + uri.getHost());
		}
		String name = f.filename();
		if (name.contains("/") || name.contains("\\") || name.contains("..") || name.isBlank()) {
			throw new IOException("Unsafe file name");
		}
		Files.createDirectories(f.targetDir());
		String saveAs = f.replaces() != null ? name + ".dmnew" : name;
		Path target = f.targetDir().resolve(saveAs).normalize();
		if (!target.startsWith(f.targetDir().normalize())) {
			throw new IOException("Unsafe target path");
		}
		Path part = f.targetDir().resolve(saveAs + ".part");

		HttpRequest req = HttpRequest.newBuilder(uri).header("User-Agent", Config.USER_AGENT).GET().build();
		HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
		if (res.statusCode() != 200) {
			throw new IOException("Download failed: HTTP " + res.statusCode());
		}
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-512");
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(part)) {
			byte[] buf = new byte[16384];
			int n;
			while ((n = in.read(buf)) != -1) {
				out.write(buf, 0, n);
				md.update(buf, 0, n);
				progress.addAndGet(n);
			}
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(part);
			throw e;
		}
		String actual = HexFormat.of().formatHex(md.digest());
		if (!actual.equalsIgnoreCase(f.sha512())) {
			Files.deleteIfExists(part);
			throw new IOException("Checksum mismatch for " + name + "; file discarded");
		}
		try {
			Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
