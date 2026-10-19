package com.clonecraft.client.screen;

import com.clonecraft.block.CentrifugeBlockEntity;
import com.clonecraft.menu.CentrifugeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Eight test tubes around the edge, fuel in the middle, and a Spin / Stop button. */
public class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {
	private Button spin;
	private float angle;

	public CentrifugeScreen(final CentrifugeMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, 176, 196);
		this.inventoryLabelY = 102;
	}

	@Override
	protected void init() {
		super.init();
		this.spin = this.addRenderableWidget(Button.builder(Component.literal("Spin"), b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0))
			.bounds(this.leftPos + this.imageWidth - 44, this.topPos + 4, 40, 20).build());
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.spin.setMessage(Component.literal(this.menu.isSpinning() ? "Stop" : "Spin"));
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.panel(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
		int cx = this.leftPos + 88;
		int cy = this.topPos + 52;
		// The spinning drum.
		if (this.menu.isSpinning()) {
			this.angle += a * 0.35F;
		}
		g.fill(cx - 46, cy - 46, cx + 46, cy + 46, 0xFF9A9A9A);
		for (int i = 0; i < 8; i++) {
			double ang = this.angle + i * Math.PI / 4;
			int px = cx + (int)Math.round(Math.cos(ang) * 40);
			int py = cy + (int)Math.round(Math.sin(ang) * 40);
			g.fill(px - 2, py - 2, px + 2, py + 2, 0xFF5A5A5A);
		}
		for (Slot slot : this.menu.slots) {
			Ui.slot(g, this.leftPos + slot.x, this.topPos + slot.y);
		}
		// Fuel left, around the middle slot.
		if (this.menu.burnTotal() > 0 && this.menu.burnTime() > 0) {
			int h = Mth.ceil(14.0F * this.menu.burnTime() / this.menu.burnTotal());
			g.fill(this.leftPos + 99, this.topPos + 59 - h, this.leftPos + 102, this.topPos + 59, 0xFFFF8800);
		}
		Ui.bar(g, this.leftPos + 8, this.topPos + 96, 160, 4, this.menu.spinTime() / (float)CentrifugeBlockEntity.SPIN_TIME, 0xFF33CC33);
	}

	@Override
	protected void extractLabels(final GuiGraphicsExtractor g, final int xm, final int ym) {
		g.text(this.font, this.title, 8, 6, Ui.TEXT, false);
		g.text(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, Ui.TEXT, false);
	}
}
