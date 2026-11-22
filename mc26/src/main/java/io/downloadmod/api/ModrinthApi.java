package io.downloadmod.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.downloadmod.Config;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class ModrinthApi {
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	private ModrinthApi() {
	}

	public static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("1.21");
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String send(HttpRequest req) throws IOException, InterruptedException {
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		int code = res.statusCode();
		if (code == 429) {
			throw new IOException("Rate limited by the API, try again in a minute");
		}
		if (code == 410) {
			throw new IOException("This API version was removed; update downloadmod.api");
		}
		if (code != 200) {
			throw new IOException("API returned HTTP " + code);
		}
		return res.body();
	}

	private static String get(String pathAndQuery) throws IOException, InterruptedException {
		return send(HttpRequest.newBuilder(URI.create(Config.API_BASE + pathAndQuery))
			.header("User-Agent", Config.USER_AGENT)
			.timeout(Duration.ofSeconds(20))
			.GET().build());
	}

	private static String post(String path, Object body) throws IOException, InterruptedException {
		return send(HttpRequest.newBuilder(URI.create(Config.API_BASE + path))
			.header("User-Agent", Config.USER_AGENT)
			.header("Content-Type", "application/json")
			.timeout(Duration.ofSeconds(30))
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build());
	}

	public static Models.SearchResponse search(String query, ContentType type, int offset, int limit)
		throws IOException, InterruptedException {
		StringBuilder facets = new StringBuilder("[[\"project_type:").append(type.modrinthType).append("\"]");
		facets.append(",[\"versions:").append(minecraftVersion()).append("\"]");
		if (type.needsFabricFacet) {
			facets.append(",[\"categories:fabric\"]");
		}
		facets.append("]");
		String q = "/search?query=" + enc(query) + "&facets=" + enc(facets.toString())
			+ "&index=" + (query.isBlank() ? "downloads" : "relevance")
			+ "&limit=" + limit + "&offset=" + offset;
		return GSON.fromJson(get(q), Models.SearchResponse.class);
	}

	public static List<Models.Version> versions(String projectId, ContentType type) throws IOException, InterruptedException {
		String q = "/project/" + enc(projectId) + "/version"
			+ "?game_versions=" + enc("[\"" + minecraftVersion() + "\"]")
			+ "&loaders=" + enc(GSON.toJson(type.loaders));
		List<Models.Version> list = GSON.fromJson(get(q), new TypeToken<List<Models.Version>>() {
		}.getType());
		return list == null ? List.of() : list;
	}

	public static Models.Version bestVersion(String projectId, ContentType type) throws IOException, InterruptedException {
		List<Models.Version> list = versions(projectId, type);
		if (list.isEmpty()) {
			return null;
		}
		for (Models.Version v : list) {
			if ("release".equals(v.versionType())) {
				return v;
			}
		}
		return list.get(0);
	}

	public static Models.Project project(String idOrSlug) throws IOException, InterruptedException {
		return GSON.fromJson(get("/project/" + enc(idOrSlug)), Models.Project.class);
	}

	public static Models.Version versionById(String versionId) throws IOException, InterruptedException {
		return GSON.fromJson(get("/version/" + enc(versionId)), Models.Version.class);
	}

	public static Map<String, Models.Version> versionFiles(List<String> sha512) throws IOException, InterruptedException {
		String json = post("/version_files", Map.of("hashes", sha512, "algorithm", "sha512"));
		Map<String, Models.Version> map = GSON.fromJson(json, new TypeToken<Map<String, Models.Version>>() {
		}.getType());
		return map == null ? Map.of() : map;
	}

	public static Map<String, Models.Version> versionFilesUpdate(List<String> sha512, ContentType type)
		throws IOException, InterruptedException {
		String json = post("/version_files/update", Map.of("hashes", sha512, "algorithm", "sha512",
			"loaders", type.loaders, "game_versions", List.of(minecraftVersion())));
		Map<String, Models.Version> map = GSON.fromJson(json, new TypeToken<Map<String, Models.Version>>() {
		}.getType());
		return map == null ? Map.of() : map;
	}
}
