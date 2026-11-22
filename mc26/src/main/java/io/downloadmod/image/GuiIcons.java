package io.downloadmod.image;

import io.downloadmod.Config;
import io.downloadmod.compat.Compat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class GuiIcons {
	private static final int[] SIZES = {16, 32, 64, 128, 256};

	private GuiIcons() {
	}

	public static void draw(GuiGraphicsExtractor ctx, String name, int x, int y, int guiSize) {
		int want = guiSize * ImageCache.guiScale();
		int px = SIZES[SIZES.length - 1];
		for (int s : SIZES) {
			if (s >= want) {
				px = s;
				break;
			}
		}
		Identifier id = Compat.id(Config.MOD_ID, "textures/gui/" + name + "_" + px + ".png");
		Compat.drawTexture(ctx, id, x, y, guiSize, guiSize, px, px);
	}
}
