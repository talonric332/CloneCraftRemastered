package com.clonecraft.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws the simple, vanilla-looking panels and slot boxes the CloneCraft screens use. */
public final class Ui {
	private Ui() {}

	public static final int PANEL = 0xFFC6C6C6;
	public static final int LIGHT = 0xFFFFFFFF;
	public static final int DARK = 0xFF555555;
	public static final int SLOT = 0xFF8B8B8B;
	public static final int TEXT = 0xFF404040;

	/** A raised grey panel like vanilla inventories. */
	public static void panel(final GuiGraphicsExtractor g, final int x, final int y, final int w, final int h) {
		g.fill(x, y, x + w, y + h, 0xFF000000);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
		g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
		g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
		g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, DARK);
		g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, DARK);
		g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL);
	}

	/** A sunken 18x18 slot box; x/y are the slot's item position (like Slot.x). */
	public static void slot(final GuiGraphicsExtractor g, final int x, final int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, SLOT);
		g.fill(x - 1, y - 1, x + 17, y, 0xFF373737);
		g.fill(x - 1, y - 1, x, y + 17, 0xFF373737);
		g.fill(x, y + 16, x + 17, y + 17, LIGHT);
		g.fill(x + 16, y, x + 17, y + 17, LIGHT);
	}

	public static void bar(final GuiGraphicsExtractor g, final int x, final int y, final int w, final int h, final float fraction, final int color) {
		g.fill(x, y, x + w, y + h, 0xFF202020);
		int filled = Math.round(w * Math.max(0.0F, Math.min(1.0F, fraction)));
		if (filled > 0) {
			g.fill(x, y, x + filled, y + h, color);
		}
	}
}
