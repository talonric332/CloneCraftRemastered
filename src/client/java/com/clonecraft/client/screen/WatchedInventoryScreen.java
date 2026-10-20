package com.clonecraft.client.screen;

import com.clonecraft.client.WatchHud;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The inventory of the clone you're watching, to look at (not change): armour on the left, its backpack, and the hotbar
 * along the bottom with the item it's holding outlined. Updates live. E or Escape closes it.
 */
public class WatchedInventoryScreen extends Screen {
	private static final int W = 194;
	private static final int H = 112;

	public WatchedInventoryScreen() {
		super(Component.literal("Clone Inventory"));
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (this.minecraft.options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void tick() {
		super.tick();
		if (WatchHud.watched(this.minecraft) == null) {
			this.onClose();
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		CloneEntity clone = WatchHud.watched(this.minecraft);
		if (clone == null) {
			return;
		}
		int left = (this.width - W) / 2;
		int top = (this.height - H) / 2;
		Ui.panel(g, left, top, W, H);
		g.text(this.font, clone.getCloneName() + "'s inventory", left + 8, top + 6, Ui.TEXT, false);
		Payloads.WatchedInventory data = WatchHud.inventoryOf(clone);
		if (data == null) {
			g.text(this.font, "Loading...", left + 8, top + 20, Ui.TEXT, false);
			return;
		}
		ItemStack hovered = ItemStack.EMPTY;
		// Armour down the left (head to feet), then the backpack (slots 9-35), then the hotbar (0-8) at the bottom.
		for (int i = 0; i < 4; i++) {
			hovered = this.slot(g, data, 36 + i, left + 8, top + 18 + i * 18, mouseX, mouseY, hovered, false);
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				hovered = this.slot(g, data, 9 + row * 9 + col, left + 30 + col * 18, top + 18 + row * 18, mouseX, mouseY, hovered, false);
			}
		}
		for (int col = 0; col < 9; col++) {
			hovered = this.slot(g, data, col, left + 30 + col * 18, top + 76, mouseX, mouseY, hovered, col == data.selected());
		}
		g.text(this.font, "Holding", left + 30, top + 96, Ui.TEXT, false);
		if (!hovered.isEmpty()) {
			g.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}

	private ItemStack slot(final GuiGraphicsExtractor g, final Payloads.WatchedInventory data, final int index, final int x, final int y,
		final int mouseX, final int mouseY, final ItemStack hovered, final boolean held) {
		Ui.slot(g, x, y);
		if (held) {
			// Outline the slot it's holding.
			g.fill(x - 2, y - 2, x + 18, y - 1, 0xFFFFFFFF);
			g.fill(x - 2, y + 17, x + 18, y + 18, 0xFFFFFFFF);
			g.fill(x - 2, y - 2, x - 1, y + 18, 0xFFFFFFFF);
			g.fill(x + 17, y - 2, x + 18, y + 18, 0xFFFFFFFF);
		}
		ItemStack stack = index < data.items().size() ? data.items().get(index) : ItemStack.EMPTY;
		if (stack.isEmpty()) {
			return hovered;
		}
		g.item(stack, x, y);
		g.itemDecorations(this.font, stack, x, y);
		return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16 ? stack : hovered;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
