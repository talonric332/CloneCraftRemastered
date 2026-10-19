package com.clonecraft.client.screen;

import com.clonecraft.client.ClientNetworking;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.Payloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The Options screen: the clone's name at the top (type a player's username to wear their skin), then a toggle
 * for each of the 18 options. Hover an option to read what it does.
 */
public class CloneOptionsScreen extends CloneSubScreen {
	private EditBox nameBox;
	private int options;
	private final List<Button> optionButtons = new ArrayList<>();

	public CloneOptionsScreen(final CloneEntity clone) {
		super(clone, Component.literal("Clone Options"));
		this.options = clone.getOptions();
	}

	@Override
	protected void init() {
		this.nameBox = new EditBox(this.font, 10, 62, this.width - 20, 20, Component.literal("Name"));
		this.nameBox.setMaxLength(32);
		this.nameBox.setValue(this.clone.getCloneName());
		this.addRenderableWidget(this.nameBox);

		CloneOption[] all = CloneOption.values();
		int gap = 4;
		int top = 90;
		// Use more columns when the screen is short, so every option (and Done) fits.
		int columns = 2;
		while (columns < 4 && (this.height - top - 6) / (int)Math.ceil((all.length + 1) / (double)columns) < 18) {
			columns++;
		}
		int rows = (int)Math.ceil((all.length + 1) / (double)columns);
		int buttonW = (this.width - 20 - gap * (columns - 1)) / columns;
		int buttonH = Math.max(12, Math.min(20, (this.height - top - 6) / rows - gap));
		this.optionButtons.clear();
		for (int i = 0; i < all.length; i++) {
			CloneOption option = all[i];
			int col = i / rows;
			int row = i % rows;
			Button button = Button.builder(this.label(option), b -> {
				this.options ^= option.bit();
				b.setMessage(this.label(option));
				ClientNetworking.send(new Payloads.CloneAction(this.clone.getId(), Payloads.CloneAction.SET_OPTIONS, this.options));
			}).bounds(10 + col * (buttonW + gap), top + row * (buttonH + gap), buttonW, buttonH).build();
			this.optionButtons.add(button);
			this.addRenderableWidget(button);
		}
		int last = all.length;
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
			.bounds(10 + (last / rows) * (buttonW + gap), top + (last % rows) * (buttonH + gap), buttonW, buttonH).build());
	}

	private Component label(final CloneOption option) {
		boolean on = (this.options & option.bit()) != 0;
		return Component.literal(option.label + ": ").append(Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	@Override
	protected void save() {
		String name = this.nameBox.getValue().trim();
		if (!name.isEmpty() && !name.equals(this.clone.getCloneName())) {
			ClientNetworking.send(new Payloads.SetName(this.clone.getId(), name));
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.fill(4, 4, this.width - 4, 56, 0x44AAAAAA);
		String info = "Name (also the player whose skin the clone wears):";
		for (int i = 0; i < this.optionButtons.size(); i++) {
			if (this.optionButtons.get(i).isHovered()) {
				info = CloneOption.values()[i].description;
			}
		}
		g.textWithWordWrap(this.font, Component.literal(info), 12, 14, this.width - 24, 0xFFFFFFFF);
	}
}
