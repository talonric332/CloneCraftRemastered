package com.clonecraft.client.screen;

import com.clonecraft.client.ClientNetworking;
import com.clonecraft.network.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Names and saves the area picked with the Schematic Selector. */
public class SaveSchematicScreen extends Screen {
	private final BlockPos a;
	private final BlockPos b;
	private EditBox name;
	private boolean overwrite;
	private Button overwriteButton;

	public SaveSchematicScreen(final BlockPos a, final BlockPos b) {
		super(Component.literal("Save Schematic"));
		this.a = a;
		this.b = b;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - 100;
		this.name = new EditBox(this.font, x, 50, 200, 20, Component.literal("Schematic Name"));
		this.name.setMaxLength(40);
		this.name.setHint(Component.literal("Schematic name"));
		this.addRenderableWidget(this.name);
		this.setInitialFocus(this.name);
		this.overwriteButton = this.addRenderableWidget(Button.builder(this.overwriteLabel(), btn -> {
			this.overwrite = !this.overwrite;
			btn.setMessage(this.overwriteLabel());
		}).bounds(x, 140, 200, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Save Schematic"), btn -> {
			if (!this.name.getValue().isBlank()) {
				ClientNetworking.send(new Payloads.SaveSchematic(this.name.getValue(), this.a, this.b, this.overwrite));
				this.onClose();
			}
		}).bounds(x, 166, 200, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose()).bounds(x, 190, 200, 20).build());
	}

	private Component overwriteLabel() {
		return Component.literal("Overwrite if it exists: ").append(Component.literal(this.overwrite ? "YES" : "NO").withStyle(this.overwrite ? ChatFormatting.RED : ChatFormatting.GREEN));
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		g.centeredText(this.font, "Schematic Name:", this.width / 2, 36, 0xFFFFFFFF);
		int sx = Math.abs(this.a.getX() - this.b.getX()) + 1;
		int sy = Math.abs(this.a.getY() - this.b.getY()) + 1;
		int sz = Math.abs(this.a.getZ() - this.b.getZ()) + 1;
		g.centeredText(this.font, "Width " + sx + "   Height " + sy + "   Length " + sz, this.width / 2, 84, 0xFF99DDFF);
		g.centeredText(this.font, "Block Count: " + (sx * sy * sz), this.width / 2, 98, 0xFF99DDFF);
		g.centeredText(this.font, "From (" + this.a.toShortString() + ") to (" + this.b.toShortString() + ")", this.width / 2, 112, 0xFFAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
