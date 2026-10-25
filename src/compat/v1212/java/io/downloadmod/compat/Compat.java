package io.downloadmod.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public final class Compat {
	private Compat() {
	}

	public static void drawTexture(DrawContext ctx, Identifier id, int x, int y, int w, int h, int texW, int texH) {
		ctx.drawTexture(RenderLayer::getGuiTextured, id, x, y, 0.0F, 0.0F, w, h, texW, texH, texW, texH);
	}

	public static NativeImageBackedTexture newTexture(Identifier id, NativeImage image) {
		return new NativeImageBackedTexture(image);
	}
}
