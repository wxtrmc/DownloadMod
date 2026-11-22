package io.downloadmod.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;

public final class Compat {
	private Compat() {
	}

	public static void drawTexture(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, int texW, int texH) {
		ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0F, 0.0F, w, h, texW, texH, texW, texH);
	}

	public static DynamicTexture newTexture(Identifier id, NativeImage image) {
		return new DynamicTexture(() -> id.toString(), image);
	}

	public static Identifier id(String namespace, String path) {
		return Identifier.fromNamespaceAndPath(namespace, path);
	}

	public static void openLink(Screen parent, String url, boolean trusted) {
		ConfirmLinkScreen.confirmLinkNow(parent, url, trusted);
	}

	public static ServerData newServer(String name, String address) {
		return new ServerData(name, address, ServerData.Type.OTHER);
	}

	public static void background(Screen screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
	}

	public static Collection<String> enabledPackIds(PackRepository manager) {
		return manager.getSelectedIds();
	}

	public static String packId(Pack profile) {
		return profile.getId();
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		mc.setScreenAndShow(screen);
	}

	public static void openFolder(Path folder) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String command = os.contains("win") ? "explorer.exe" : os.contains("mac") ? "open" : "xdg-open";
		try {
			new ProcessBuilder(command, folder.toAbsolutePath().toString()).start();
		} catch (IOException ignored) {
		}
	}

	public static Screen currentScreen(Minecraft mc) {
		return mc.gui.screen();
	}
}
