package io.downloadmod.gui;

import io.downloadmod.compat.Compat;
import io.downloadmod.Async;
import io.downloadmod.servers.ServerCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class AddServerScreen extends Screen {
	private static final int FIELD_W = 240;
	private static final long WAIT_MS = 10_000L;
	private static long lastSubmit = 0L;

	private final Screen parent;
	private final Runnable onAdded;

	private EditBox nameField;
	private EditBox addressField;
	private EditBox descField;
	private Button submit;
	private Component status = Component.empty();
	private int statusColor = 0xFFFF5555;
	private boolean busy = false;

	private String name = "";
	private String address = "";
	private String desc = "";

	public AddServerScreen(Screen parent, Runnable onAdded) {
		super(Component.translatable("downloadmod.add_my_server.title"));
		this.parent = parent;
		this.onAdded = onAdded;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - FIELD_W / 2;
		int y = this.height / 2 - 62;

		nameField = new EditBox(this.font, x, y + 12, FIELD_W, 20, Component.translatable("downloadmod.add_my_server.name"));
		nameField.setMaxLength(50);
		nameField.setValue(name);
		addRenderableWidget(nameField);

		addressField = new EditBox(this.font, x, y + 52, FIELD_W, 20, Component.translatable("downloadmod.add_my_server.address"));
		addressField.setMaxLength(255);
		addressField.setValue(address);
		addRenderableWidget(addressField);

		descField = new EditBox(this.font, x, y + 92, FIELD_W, 20, Component.translatable("downloadmod.add_my_server.description"));
		descField.setMaxLength(140);
		descField.setValue(desc);
		addRenderableWidget(descField);

		submit = Button.builder(Component.translatable("downloadmod.add_my_server.submit"), b -> submit())
			.pos(x, y + 124).size(FIELD_W / 2 - 2, 20).build();
		submit.active = !busy;
		addRenderableWidget(submit);
		addRenderableWidget(Button.builder(Component.translatable("downloadmod.cancel"), b -> onClose())
			.pos(x + FIELD_W / 2 + 2, y + 124).size(FIELD_W / 2 - 2, 20).build());

		setInitialFocus(nameField);
	}

	private void remember() {
		name = nameField.getValue();
		address = addressField.getValue();
		desc = descField.getValue();
	}

	private void submit() {
		if (busy) {
			return;
		}
		if (lastSubmit != 0L && System.currentTimeMillis() - lastSubmit < WAIT_MS) {
			statusColor = 0xFFFFAA00;
			status = Component.literal("Wait...");
			return;
		}
		remember();
		String bad = name.trim().isEmpty() ? "Enter a server name." : ServerCatalog.validateAddress(address);
		if (bad != null) {
			statusColor = 0xFFFF5555;
			status = Component.literal(bad);
			return;
		}
		lastSubmit = System.currentTimeMillis();
		final String n = name;
		final String a = address;
		final String d = desc;
		busy = true;
		submit.active = false;
		statusColor = 0xFFAAAAAA;
		status = Component.literal("Sending...");
		final Minecraft mc = Minecraft.getInstance();
		Async.run(() -> {
			final ServerCatalog.Result r = ServerCatalog.submit(n, a, d);
			mc.execute(() -> {
				if (Compat.currentScreen(mc) != this) {
					return;
				}
				busy = false;
				submit.active = true;
				lastSubmit = System.currentTimeMillis();
				if (r.ok()) {
					Compat.setScreen(mc, parent);
					if (onAdded != null) {
						onAdded.run();
					}
				} else {
					statusColor = 0xFFFF5555;
					status = Component.literal(r.message());
				}
			});
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		Compat.background(this, ctx, mouseX, mouseY, delta);
		super.extractRenderState(ctx, mouseX, mouseY, delta);
		int x = this.width / 2 - FIELD_W / 2;
		int y = this.height / 2 - 62;
		ctx.centeredText(this.font, this.title, this.width / 2, y - 24, 0xFFFFFFFF);
		ctx.text(this.font, Component.translatable("downloadmod.add_my_server.name"), x, y, 0xFFAAAAAA);
		ctx.text(this.font, Component.translatable("downloadmod.add_my_server.address"), x, y + 40, 0xFFAAAAAA);
		ctx.text(this.font, Component.translatable("downloadmod.add_my_server.description"), x, y + 80, 0xFFAAAAAA);
		ctx.centeredText(this.font, status, this.width / 2, y + 152, statusColor);
	}

	@Override
	public void onClose() {
		Compat.setScreen(this.minecraft, parent);
	}
}
