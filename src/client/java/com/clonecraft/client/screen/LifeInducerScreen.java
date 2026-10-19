package com.clonecraft.client.screen;

import com.clonecraft.block.LifeInducerBlockEntity;
import com.clonecraft.menu.LifeInducerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Up to 27 unactivated spawn eggs, and how much lightning power is stored. */
public class LifeInducerScreen extends AbstractContainerScreen<LifeInducerMenu> {
	public LifeInducerScreen(final LifeInducerMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, 176, 190);
		this.inventoryLabelY = 96;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.panel(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
		for (Slot slot : this.menu.slots) {
			Ui.slot(g, this.leftPos + slot.x, this.topPos + slot.y);
		}
		int power = this.menu.power();
		Ui.bar(g, this.leftPos + 8, this.topPos + 18, 160, 8, power / (float)LifeInducerBlockEntity.MAX_POWER, 0xFF55AAFF);
	}

	@Override
	protected void extractLabels(final GuiGraphicsExtractor g, final int xm, final int ym) {
		g.text(this.font, this.title, 8, 6, Ui.TEXT, false);
		String power = "Power: " + this.menu.power() + " / " + LifeInducerBlockEntity.MAX_POWER;
		g.text(this.font, power, this.imageWidth - 8 - this.font.width(power), 6, 0xFF2255AA, false);
		g.text(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, Ui.TEXT, false);
	}
}
