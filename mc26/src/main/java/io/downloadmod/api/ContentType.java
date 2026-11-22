package io.downloadmod.api;

import java.util.List;

public enum ContentType {
	MOD("mod", "downloadmod.tab.mods", "mods", List.of("fabric"), true, true),
	RESOURCEPACK("resourcepack", "downloadmod.tab.resourcepacks", "resourcepacks", List.of("minecraft"), false, false),
	SHADER("shader", "downloadmod.tab.shaders", "shaderpacks", List.of("iris"), false, false),
	DATAPACK("datapack", "downloadmod.tab.datapacks", null, List.of("datapack"), false, false);

	public final String modrinthType;
	public final String translationKey;
	public final String folder;
	public final List<String> loaders;
	public final boolean needsFabricFacet;
	public final boolean requiresRestart;

	ContentType(String modrinthType, String translationKey, String folder, List<String> loaders,
				boolean needsFabricFacet, boolean requiresRestart) {
		this.modrinthType = modrinthType;
		this.translationKey = translationKey;
		this.folder = folder;
		this.loaders = loaders;
		this.needsFabricFacet = needsFabricFacet;
		this.requiresRestart = requiresRestart;
	}
}
