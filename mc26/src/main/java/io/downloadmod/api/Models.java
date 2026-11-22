package io.downloadmod.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public final class Models {
	private Models() {
	}

	public record SearchResponse(List<Hit> hits, @SerializedName("total_hits") int totalHits) {
	}

	public record Hit(
		@SerializedName("project_id") String projectId,
		String slug,
		String title,
		String description,
		String author,
		int downloads,
		@SerializedName("project_type") String projectType,
		@SerializedName("icon_url") String iconUrl,
		@SerializedName("featured_gallery") String featuredGallery,
		List<String> gallery
	) {
	}

	public record Project(
		String id,
		String slug,
		@SerializedName("source_url") String sourceUrl,
		@SerializedName("issues_url") String issuesUrl,
		@SerializedName("wiki_url") String wikiUrl,
		@SerializedName("discord_url") String discordUrl,
		List<GalleryItem> gallery
	) {
	}

	public record GalleryItem(String url, boolean featured, int ordering, String title, String description,
							  @SerializedName("raw_url") String rawUrl) {
	}

	public record Version(
		String id,
		@SerializedName("project_id") String projectId,
		String name,
		@SerializedName("version_number") String versionNumber,
		@SerializedName("version_type") String versionType,
		@SerializedName("date_published") String datePublished,
		@SerializedName("game_versions") List<String> gameVersions,
		List<String> loaders,
		List<FileInfo> files,
		List<Dependency> dependencies
	) {
	}

	public record FileInfo(Map<String, String> hashes, String url, String filename, boolean primary, long size) {
	}

	public record Dependency(
		@SerializedName("version_id") String versionId,
		@SerializedName("project_id") String projectId,
		@SerializedName("dependency_type") String dependencyType
	) {
	}
}
