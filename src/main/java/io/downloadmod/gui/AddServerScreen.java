package io.downloadmod.gui;

import io.downloadmod.Async;
import io.downloadmod.servers.ServerCatalog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class AddServerScreen extends Screen {
	private static final int FIELD_W = 240;
	private static final long WAIT_MS = 10_000L;
	private static long lastSubmit = 0L;

	private final Screen parent;
	private final Runnable onAdded;

	private TextFieldWidget nameField;
	private TextFieldWidget addressField;
	private TextFieldWidget descField;
	private ButtonWidget submit;
	private Text status = Text.empty();
	private int statusColor = 0xFFFF5555;
	private boolean busy = false;

	private String name = "";
	private String address = "";
	private String desc = "";

	public AddServerScreen(Screen parent, Runnable onAdded) {
		super(Text.translatable("downloadmod.add_my_server.title"));
		this.parent = parent;
		this.onAdded = onAdded;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - FIELD_W / 2;
		int y = this.height / 2 - 62;

		nameField = new TextFieldWidget(this.textRenderer, x, y + 12, FIELD_W, 20, Text.translatable("downloadmod.add_my_server.name"));
		nameField.setMaxLength(50);
		nameField.setText(name);
		addDrawableChild(nameField);

		addressField = new TextFieldWidget(this.textRenderer, x, y + 52, FIELD_W, 20, Text.translatable("downloadmod.add_my_server.address"));
		addressField.setMaxLength(255);
		addressField.setText(address);
		addDrawableChild(addressField);

		descField = new TextFieldWidget(this.textRenderer, x, y + 92, FIELD_W, 20, Text.translatable("downloadmod.add_my_server.description"));
		descField.setMaxLength(140);
		descField.setText(desc);
		addDrawableChild(descField);

		submit = ButtonWidget.builder(Text.translatable("downloadmod.add_my_server.submit"), b -> submit())
			.position(x, y + 124).size(FIELD_W / 2 - 2, 20).build();
		submit.active = !busy;
		addDrawableChild(submit);
		addDrawableChild(ButtonWidget.builder(Text.translatable("downloadmod.cancel"), b -> close())
			.position(x + FIELD_W / 2 + 2, y + 124).size(FIELD_W / 2 - 2, 20).build());

		setInitialFocus(nameField);
	}

	private void remember() {
		name = nameField.getText();
		address = addressField.getText();
		desc = descField.getText();
	}

	private void submit() {
		if (busy) {
			return;
		}
		if (lastSubmit != 0L && System.currentTimeMillis() - lastSubmit < WAIT_MS) {
			statusColor = 0xFFFFAA00;
			status = Text.literal("Wait...");
			return;
		}
		remember();
		String bad = name.trim().isEmpty() ? "Enter a server name." : ServerCatalog.validateAddress(address);
		if (bad != null) {
			statusColor = 0xFFFF5555;
			status = Text.literal(bad);
			return;
		}
		lastSubmit = System.currentTimeMillis();
		final String n = name;
		final String a = address;
		final String d = desc;
		busy = true;
		submit.active = false;
		statusColor = 0xFFAAAAAA;
		status = Text.literal("Sending...");
		final MinecraftClient mc = MinecraftClient.getInstance();
		Async.run(() -> {
			final ServerCatalog.Result r = ServerCatalog.submit(n, a, d);
			mc.execute(() -> {
				if (mc.currentScreen != this) {
					return;
				}
				busy = false;
				submit.active = true;
				lastSubmit = System.currentTimeMillis();
				if (r.ok()) {
					mc.setScreen(parent);
					if (onAdded != null) {
						onAdded.run();
					}
				} else {
					statusColor = 0xFFFF5555;
					status = Text.literal(r.message());
				}
			});
		});
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int x = this.width / 2 - FIELD_W / 2;
		int y = this.height / 2 - 62;
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, y - 24, 0xFFFFFFFF);
		ctx.drawTextWithShadow(this.textRenderer, Text.translatable("downloadmod.add_my_server.name"), x, y, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.translatable("downloadmod.add_my_server.address"), x, y + 40, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer, Text.translatable("downloadmod.add_my_server.description"), x, y + 80, 0xFFAAAAAA);
		ctx.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, y + 152, statusColor);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
