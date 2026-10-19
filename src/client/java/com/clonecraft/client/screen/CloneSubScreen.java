package com.clonecraft.client.screen;

import com.clonecraft.client.ClientNetworking;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base for the screens opened from a clone's main screen. Leaving goes back to the main screen. */
public abstract class CloneSubScreen extends Screen {
	protected final CloneEntity clone;

	protected CloneSubScreen(final CloneEntity clone, final Component title) {
		super(title);
		this.clone = clone;
	}

	protected void save() {
	}

	@Override
	public void onClose() {
		this.save();
		ClientNetworking.send(new Payloads.CloneAction(this.clone.getId(), Payloads.CloneAction.OPEN_MAIN, 0));
		super.onClose();
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.clone.isAlive()) {
			this.minecraft.gui.setScreen(null);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	protected void title(final GuiGraphicsExtractor g, final String text) {
		g.centeredText(this.font, text, this.width / 2, 8, 0xFFFFFFFF);
	}
}
