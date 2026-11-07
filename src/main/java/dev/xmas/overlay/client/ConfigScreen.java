package dev.xmas.overlay.client;

import dev.xmas.overlay.Safe;
import dev.xmas.overlay.Skin;
import dev.xmas.overlay.XmasConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Clean, animated settings screen (client-style): sidebar tabs, switches, sliders, a skin picker with
 * photos, and key rebinding. Everything is drawn by hand, mouse input is polled so it does not depend on
 * vanilla widgets.
 */
public class ConfigScreen extends Screen {
	private static final int W = 520, H = 290, CX = 108, CY = 44;
	private static final int BG = 0xF2101218, PANEL = 0xFF181B24, PANEL2 = 0xFF20242F, HOVER = 0xFF2B3040;
	private static final int ACC = 0xFFE8202A, ACC2 = 0xFF1FA84F, TEXT = 0xFFEDEFF5, MUTED = 0xFF8B92A5;
	private static final String[] TABS = { "Skins", "Effects", "Hands", "Camera", "Keys" };

	private final Screen parent;
	private int tab = 0;
	private float tabY = 0;
	private long openMs = -1, lastMs;
	private float ga = 0, contentFade = 1;
	private boolean prevDown, firstFrame = true;
	private Slider drag;
	private KeyBinding capturing;
	private String hoverSkin;
	private final List<Ctl> ctls = new ArrayList<>();
	private final float[] tabHover = new float[TABS.length];
	private final float[] cardHover = new float[32];

	public ConfigScreen(Screen parent) {
		super(Text.literal("Christmas Overlay"));
		this.parent = parent;
	}

	@Override protected void init() { buildTab(); }

	// ------------------------------------------------------------------ helpers
	private int a(int argb) {
		int al = (int) (((argb >>> 24) & 0xFF) * ga);
		return (al << 24) | (argb & 0xFFFFFF);
	}
	private static float approach(float cur, float target, float rate, float dt) {
		float d = target - cur; float step = rate * dt;
		return Math.abs(d) <= step ? target : cur + Math.signum(d) * step;
	}
	private static int mixc(int c1, int c2, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
		int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
		int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
	private void rrect(DrawContext c, int x, int y, int w, int h, int r, int col) {
		col = a(col);
		if (w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) { c.fill(x, y, x + w, y + h, col); return; }
		c.fill(x + r, y, x + w - r, y + h, col);
		c.fill(x, y + r, x + r, y + h - r, col);
		c.fill(x + w - r, y + r, x + w, y + h - r, col);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
			c.fill(x + inset, y + i, x + r, y + i + 1, col);
			c.fill(x + w - r, y + i, x + w - inset, y + i + 1, col);
			c.fill(x + inset, y + h - 1 - i, x + r, y + h - i, col);
			c.fill(x + w - r, y + h - 1 - i, x + w - inset, y + h - i, col);
		}
	}
	private void text(DrawContext c, String s, int x, int y, int col) { c.drawText(textRenderer, s, x, y, a(col), false); }
	private void textC(DrawContext c, String s, int cx, int y, int col) { text(c, s, cx - textRenderer.getWidth(s) / 2, y, col); }
	private void textR(DrawContext c, String s, int rx, int y, int col) { text(c, s, rx - textRenderer.getWidth(s), y, col); }

	// ------------------------------------------------------------------ controls
	private abstract class Ctl {
		int x, y, w, h = 22; float hover;
		boolean in(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
		abstract void draw(DrawContext c, int mx, int my, float dt);
		boolean click(int mx, int my) { return false; }
	}

	private class Toggle extends Ctl {
		final String label; final BooleanSupplier get; final Consumer<Boolean> set; float k;
		Toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
			this.x = x; this.y = y; this.w = w; this.label = label; this.get = get; this.set = set; this.k = get.getAsBoolean() ? 1 : 0;
		}
		void draw(DrawContext c, int mx, int my, float dt) {
			hover = approach(hover, in(mx, my) ? 1 : 0, 8, dt);
			k = approach(k, get.getAsBoolean() ? 1 : 0, 9, dt);
			rrect(c, x, y, w, h, 4, mixc(PANEL2, HOVER, hover));
			text(c, label, x + 8, y + 7, TEXT);
			int px = x + w - 34, py = y + 5;
			rrect(c, px, py, 26, 12, 6, mixc(0xFF3A3F50, ACC2, k));
			int kx = px + 2 + (int) (14 * k);
			rrect(c, kx, py + 2, 8, 8, 4, 0xFFFFFFFF);
		}
		boolean click(int mx, int my) {
			if (!in(mx, my)) return false;
			set.accept(!get.getAsBoolean()); XmasConfig.save(); return true;
		}
	}

	private class Slider extends Ctl {
		final String label; final int min, max; final IntSupplier get; final IntConsumer set; final IntFunction<String> fmt; float shown;
		Slider(int x, int y, int w, String label, int min, int max, IntSupplier get, IntConsumer set, IntFunction<String> fmt) {
			this.x = x; this.y = y; this.w = w; this.label = label; this.min = min; this.max = max; this.get = get; this.set = set; this.fmt = fmt;
			this.shown = (get.getAsInt() - min) / (float) (max - min);
		}
		void draw(DrawContext c, int mx, int my, float dt) {
			hover = approach(hover, (in(mx, my) || drag == this) ? 1 : 0, 8, dt);
			float target = (get.getAsInt() - min) / (float) (max - min);
			shown = approach(shown, target, 6, dt);
			rrect(c, x, y, w, h, 4, mixc(PANEL2, HOVER, hover));
			int fw = Math.max(0, (int) ((w - 2) * shown));
			if (fw > 0) rrect(c, x + 1, y + 1, fw, h - 2, 3, mixc(0xFF7A1A20, ACC, 0.35f + 0.4f * hover));
			text(c, label, x + 8, y + 7, TEXT);
			textR(c, fmt.apply(get.getAsInt()), x + w - 8, y + 7, TEXT);
		}
		boolean click(int mx, int my) {
			if (!in(mx, my)) return false;
			drag = this; dragTo(mx); return true;
		}
		void dragTo(int mx) {
			float f = Math.max(0, Math.min(1, (mx - x - 4) / (float) (w - 8)));
			set.accept(min + Math.round(f * (max - min)));
		}
	}

	private class KeyButton extends Ctl {
		final String label; final KeyBinding bind;
		KeyButton(int x, int y, int w, String label, KeyBinding bind) { this.x = x; this.y = y; this.w = w; this.label = label; this.bind = bind; }
		void draw(DrawContext c, int mx, int my, float dt) {
			hover = approach(hover, in(mx, my) ? 1 : 0, 8, dt);
			rrect(c, x, y, w, h, 4, mixc(PANEL2, HOVER, hover));
			text(c, label, x + 8, y + 7, TEXT);
			boolean cap = capturing == bind;
			String v = cap ? "Press a key..." : bind.getBoundKeyLocalizedText().getString();
			int bw = Math.max(52, textRenderer.getWidth(v) + 16);
			rrect(c, x + w - bw - 5, y + 3, bw, h - 6, 4, cap ? ACC : 0xFF343A4C);
			textC(c, v, x + w - bw / 2 - 5, y + 7, TEXT);
		}
		boolean click(int mx, int my) { if (!in(mx, my)) return false; capturing = bind; return true; }
	}

	private class Label extends Ctl {
		final String s; final int col;
		Label(int x, int y, String s, int col) { this.x = x; this.y = y; this.s = s; this.col = col; this.w = 0; this.h = 0; }
		void draw(DrawContext c, int mx, int my, float dt) { text(c, s, x, y, col); }
	}

	private class SkinCard extends Ctl {
		final Skin skin; final int idx;
		SkinCard(int x, int y, Skin skin, int idx) { this.x = x; this.y = y; this.w = 46; this.h = 46; this.skin = skin; this.idx = idx; }
		void draw(DrawContext c, int mx, int my, float dt) {
			boolean hov = in(mx, my);
			boolean sel = XmasConfig.get().skin.equals(skin.id);
			cardHover[idx] = approach(cardHover[idx], hov ? 1 : 0, 10, dt);
			if (hov) hoverSkin = skin.id;
			float hv = cardHover[idx];
			float s = 1f + 0.10f * hv;
			int cx = x + w / 2, cy = y + h / 2;
			c.getMatrices().pushMatrix();
			c.getMatrices().translate(cx, cy);
			c.getMatrices().scale(s, s);
			c.getMatrices().translate(-cx, -cy);
			int border = sel ? mixc(ACC, ACC2, 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 350.0)) : mixc(0xFF2A2F3D, 0xFFFFFFFF, hv * 0.6f);
			rrect(c, x - 2, y - 2, w + 4, h + 4, 5, border);
			Identifier tex = Identifier.of("xmasoverlay", "textures/gui/skin/" + skin.id + ".png");
			c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, 192, 192, 192, 192, 0x00FFFFFF | ((int) (255 * ga) << 24));
			if (sel) { rrect(c, x + w - 12, y + h - 12, 10, 10, 5, ACC2); text(c, "v", x + w - 9, y + h - 11, 0xFFFFFFFF); }
			c.getMatrices().popMatrix();
		}
		boolean click(int mx, int my) {
			if (!in(mx, my)) return false;
			XmasConfig.get().skin = skin.id; XmasConfig.save(); return true;
		}
	}

	// ------------------------------------------------------------------ tabs
	private void buildTab() {
		ctls.clear(); drag = null; hoverSkin = null;
		XmasConfig c = XmasConfig.get();
		int L = CX, R = CX + 206, CW = 196, RW = 206;
		switch (tab) {
			case 0 -> {
				List<Skin> all = Skin.ALL;
				for (int i = 0; i < all.size(); i++)
					ctls.add(new SkinCard(CX + 142 + (i % 5) * 54, CY + (i / 5) * 56 + 2, all.get(i), i));
				ctls.add(new Toggle(L, CY + 168, 132, "3D knife + skins", () -> c.customKnife, v -> c.customKnife = v));
				ctls.add(new Toggle(L, CY + 192, 132, "Show my hand", () -> c.showHand, v -> c.showHand = v));
				ctls.add(new Toggle(L, CY + 216, 132, "Tricks", () -> c.tricksEnabled, v -> c.tricksEnabled = v));
				ctls.add(new Slider(CX + 142, CY + 184, 266, "Knife size", 50, 200, () -> c.knifeSize, v -> c.knifeSize = v, v -> v + "%"));
			}
			case 1 -> {
				ctls.add(new Toggle(L, CY, CW, "Mining overlay", () -> c.overlayEnabled, v -> c.overlayEnabled = v));
				ctls.add(new Slider(R, CY, RW, "Overlay opacity", 5, 100, () -> c.overlayOpacity, v -> c.overlayOpacity = v, v -> v + "%"));
				ctls.add(new Toggle(L, CY + 30, CW, "Mining particles", () -> c.miningParticles, v -> c.miningParticles = v));
				ctls.add(new Slider(R, CY + 30, RW, "Particle amount", 1, 10, () -> c.particleDensity, v -> c.particleDensity = v, v -> "" + v));
				ctls.add(new Toggle(L, CY + 60, CW, "Knife particles", () -> c.knifeParticles, v -> c.knifeParticles = v));
				ctls.add(new Toggle(L, CY + 90, CW, "Galaxy night sky", () -> c.galaxySky, v -> c.galaxySky = v));
				ctls.add(new Slider(R, CY + 90, RW, "Star brightness", 100, 300, () -> c.starBoost, v -> c.starBoost = v, v -> v + "%"));
				ctls.add(new Label(L, CY + 128, "Particles change with the knife skin you picked.", MUTED));
				ctls.add(new Label(L, CY + 142, "The galaxy sky shows at night (it uses the End-style sky texture).", MUTED));
			}
			case 2 -> {
				ctls.add(new Slider(L, CY, 300, "Swing speed (only you see it)", 25, 300, () -> c.swingSpeed, v -> c.swingSpeed = v, v -> v + "%"));
				ctls.add(new Toggle(L, CY + 30, CW, "Move all held items inward", () -> c.itemShiftEnabled, v -> c.itemShiftEnabled = v));
				ctls.add(new Slider(R, CY + 30, RW, "Inward amount", 0, 100, () -> c.itemShift, v -> c.itemShift = v, v -> v + "%"));
				ctls.add(new Label(L, CY + 66, "Knife position in your hand (if the hand and knife do not line up):", MUTED));
				ctls.add(new Slider(L, CY + 82, 300, "Left / right", -50, 50, () -> c.gripX, v -> c.gripX = v, v -> "" + v));
				ctls.add(new Slider(L, CY + 108, 300, "Up / down", -50, 50, () -> c.gripY, v -> c.gripY = v, v -> "" + v));
				ctls.add(new Slider(L, CY + 134, 300, "Forward / back", -50, 50, () -> c.gripZ, v -> c.gripZ = v, v -> "" + v));
			}
			case 3 -> {
				ctls.add(new Slider(L, CY, 340, "Camera zoom out", 40, 100, () -> c.zoomOut, v -> c.zoomOut = v, v -> String.format("%.2fx", v / 100f)));
				ctls.add(new Label(L, CY + 36, "Pulls the camera back like the 0.5x lens on a phone (100% = normal).", MUTED));
				ctls.add(new Label(L, CY + 50, "Your hand and held items stay where they are.", MUTED));
			}
			default -> {
				ctls.add(new KeyButton(L, CY, 340, "Trick key (hold it to do tricks)", XmasOverlayClient.trickKey));
				ctls.add(new KeyButton(L, CY + 30, 340, "Open this menu", XmasOverlayClient.configKey));
				ctls.add(new Label(L, CY + 66, "Click a button, then press the key you want. Esc cancels.", MUTED));
				ctls.add(new Label(L, CY + 80, "Hold the trick key with the knife in your hand. With an empty hand you get a chat message.", MUTED));
			}
		}
		contentFade = 0;
	}

	// ------------------------------------------------------------------ render
	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		if (openMs < 0) openMs = now;
		float dt = Math.min(0.1f, Math.max(0.001f, (now - (lastMs == 0 ? now - 16 : lastMs)) / 1000f));
		lastMs = now;
		float open = Math.min(1f, (now - openMs) / 260f);
		open = 1 - (1 - open) * (1 - open) * (1 - open);
		ga = open;
		contentFade = approach(contentFade, 1, 6, dt);

		MinecraftClient mc = MinecraftClient.getInstance();
		Window win = mc.getWindow();
		double rawX = mc.mouse.getX() * win.getScaledWidth() / (double) win.getWidth();
		double rawY = mc.mouse.getY() * win.getScaledHeight() / (double) win.getHeight();
		boolean down = GLFW.glfwGetMouseButton(win.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (firstFrame) { prevDown = down; firstFrame = false; }

		float ps = Math.min(1f, Math.min(width / (W + 24f), height / (H + 24f)));
		float px = (width - W * ps) / 2f, py = (height - H * ps) / 2f + (1 - open) * 14;
		int mx = (int) ((rawX - px) / ps), my = (int) ((rawY - py) / ps);

		// click / drag handling (in panel coordinates)
		if (down && !prevDown) onClick(mx, my);
		if (down && drag != null) drag.dragTo(mx);
		if (!down && prevDown) { drag = null; XmasConfig.save(); }
		prevDown = down;

		// dim the world
		ctx.fill(0, 0, width, height, (int) (0x99 * open) << 24);

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(px, py);
		ctx.getMatrices().scale(ps, ps);
		hoverSkin = null;
		try {
			drawPanel(ctx, mx, my, dt);
		} finally {
			ctx.getMatrices().popMatrix();
		}
	}

	private void drawPanel(DrawContext c, int mx, int my, float dt) {
		// shadow + panel
		for (int i = 6; i >= 1; i--) rrect(c, -i, -i + 3, W + 2 * i, H + 2 * i, 10 + i, 0x08000000 | 0x000000);
		rrect(c, 0, 0, W, H, 10, BG);
		// christmas colour bar along the top
		for (int i = 0; i < 52; i++) {
			int col = (i % 2 == 0) ? mixc(ACC, 0xFFFFFFFF, 0.1f) : ACC2;
			c.fill(14 + i * 9, 0, 14 + i * 9 + 5, 2, a(col));
		}
		text(c, "* Christmas Overlay", 16, 13, TEXT);
		textR(c, "x", W - 16, 13, MUTED);
		// sidebar
		rrect(c, 8, 34, 92, H - 42, 6, PANEL);
		int target = 40 + tab * 32;
		tabY = approach(tabY, target, 400, dt * 0 + dt);
		if (Math.abs(tabY - target) > 80) tabY = target;
		tabY += (target - tabY) * Math.min(1f, dt * 14f);
		rrect(c, 12, (int) tabY, 84, 28, 5, 0x40E8202A);
		rrect(c, 12, (int) tabY + 6, 3, 16, 1, ACC);
		for (int i = 0; i < TABS.length; i++) {
			boolean in = mx >= 12 && mx < 96 && my >= 40 + i * 32 && my < 68 + i * 32;
			tabHover[i] = approach(tabHover[i], in ? 1 : 0, 8, dt);
			if (i != tab && tabHover[i] > 0) rrect(c, 12, 40 + i * 32, 84, 28, 5, (int) (0x30 * tabHover[i]) << 24 | 0xFFFFFF);
			text(c, TABS[i], 26, 40 + i * 32 + 10, i == tab ? TEXT : mixc(MUTED, TEXT, tabHover[i]));
		}
		boolean rin = mx >= 12 && mx < 96 && my >= H - 40 && my < H - 14;
		rrect(c, 12, H - 40, 84, 26, 5, rin ? 0xFF3A1A1E : PANEL2);
		textC(c, "Reset all", 54, H - 32, rin ? TEXT : MUTED);

		// content
		float keep = ga;
		ga = ga * contentFade;
		if (tab == 0) drawSkinPreview(c);
		for (Ctl t : ctls) t.draw(c, mx, my, dt);
		ga = keep;
	}

	private void drawSkinPreview(DrawContext c) {
		Skin s = Skin.byId(hoverSkin != null ? hoverSkin : XmasConfig.get().skin);
		rrect(c, CX - 2, CY - 2, 132, 132, 6, mixc(ACC, ACC2, 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 700.0)));
		Identifier tex = Identifier.of("xmasoverlay", "textures/gui/skin/" + s.id + ".png");
		c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, CX, CY, 0f, 0f, 128, 128, 192, 192, 192, 192, 0x00FFFFFF | ((int) (255 * ga) << 24));
		text(c, s.name, CX, CY + 135, TEXT);
		// wrap the blurb on two lines
		String[] words = s.blurb.split(" ");
		StringBuilder line = new StringBuilder(); int yy = CY + 147;
		for (String wd : words) {
			if (textRenderer.getWidth(line + wd) > 128 && line.length() > 0) { text(c, line.toString().trim(), CX, yy, MUTED); yy += 10; line.setLength(0); }
			line.append(wd).append(' ');
		}
		if (line.length() > 0) text(c, line.toString().trim(), CX, yy, MUTED);
	}

	private void onClick(int mx, int my) {
		if (capturing != null) { capturing = null; return; }
		if (mx >= W - 34 && mx < W && my >= 0 && my < 30) { close(); return; }
		for (int i = 0; i < TABS.length; i++) {
			if (mx >= 12 && mx < 96 && my >= 40 + i * 32 && my < 68 + i * 32) { if (tab != i) { tab = i; buildTab(); } return; }
		}
		if (mx >= 12 && mx < 96 && my >= H - 40 && my < H - 14) { XmasConfig.reset(); Safe.enableAll(); buildTab(); return; }
		for (Ctl t : ctls) if (t.click(mx, my)) return;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (capturing != null) {
			if (input.isEscape()) { capturing = null; return true; }
			capturing.setBoundKey(InputUtil.fromKeyCode(input));
			KeyBinding.updateKeysByCode();
			MinecraftClient.getInstance().options.write();
			capturing = null;
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void close() {
		XmasConfig.save();
		MinecraftClient.getInstance().setScreen(parent);
	}
}
