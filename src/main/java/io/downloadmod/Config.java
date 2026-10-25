package io.downloadmod;

import java.util.Set;

public final class Config {
	public static final String MOD_ID = "downloadmod";

	public static final String USER_AGENT = "MishariM12752/download-mod/1.0.0 (https://discord.gg/ZFfxJevHrg)";

	public static final String API_BASE = System.getProperty("downloadmod.api", "https://api.modrinth.com/v2");

	public static final Set<String> ALLOWED_DOWNLOAD_HOSTS = Set.of("cdn.modrinth.com");

	public static final Set<String> ALLOWED_IMAGE_HOSTS = Set.of("cdn.modrinth.com", "api.mcsrvstat.us");

	public static final String SERVERS_BASE = System.getProperty("downloadmod.servers", "https://api.wxtrmc.net");

	public static final String SERVERS_URL = SERVERS_BASE + "/raw";

	public static final String SERVERS_ADD_URL = SERVERS_BASE + "/add";

	public static final String SERVERS_API_KEY = "CONTACT THE OWNER FOR THE API";

	public static final String DISCORD_URL = "https://discord.gg/ZFfxJevHrg";

	public static final String MODRINTH_SLUG = "downloadmod";

	private Config() {
	}
}
