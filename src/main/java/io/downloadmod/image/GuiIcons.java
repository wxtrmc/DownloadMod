package io.downloadmod.image;

import io.downloadmod.Config;
import io.downloadmod.compat.Compat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class GuiIcons {
	private static final int[] SIZES = {16, 32, 64, 128, 256};

	private GuiIcons() {
	}

	public static void draw(DrawContext ctx, String name, int x, int y, int guiSize) {
		int want = guiSize * ImageCache.guiScale();
		int px = SIZES[SIZES.length - 1];
		for (int s : SIZES) {
			if (s >= want) {
				px = s;
				break;
			}
		}
		Identifier id = Identifier.of(Config.MOD_ID, "textures/gui/" + name + "_" + px + ".png");
		Compat.drawTexture(ctx, id, x, y, guiSize, guiSize, px, px);
	}
}
