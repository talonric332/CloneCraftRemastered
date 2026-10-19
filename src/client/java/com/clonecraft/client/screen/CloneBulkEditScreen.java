package com.clonecraft.client.screen;

import com.clonecraft.client.ClientNetworking;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.CloneTeam;
import com.clonecraft.network.Payloads;
import com.clonecraft.network.RemoteControl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Clone Remote's bulk editor. Tick the clones on the left, then choose what to change on the right: each option can be
 * left alone, turned on or turned off, the team can be set, and one clone's attack/break lists can be copied to the rest.
 * Anything left on "Keep" isn't touched. Names (and Female, which picks the name) can't be bulk edited.
 */
public class CloneBulkEditScreen extends Screen {
	private static final int ROW = 14;
	private static final int TOP = 30;
	private static final int KEEP = 0;
	private static final int ON = 1;
	private static final int OFF = 2;

	private final Screen parent;
	private final List<Payloads.RemoteClone> clones;
	private final Set<UUID> selected = new LinkedHashSet<>();
	private final List<CloneOption> options = new ArrayList<>();
	private final int[] optionState;
	private final List<Button> optionButtons = new ArrayList<>();
	private int team = -1;
	private int listSource = -1;
	private Button teamButton;
	private Button listButton;
	private Button applyButton;
	private double scroll;
	private int listX;
	private int listW;
	private int listBottom;

	public CloneBulkEditScreen(final Screen parent, final List<Payloads.RemoteClone> clones) {
		super(Component.literal("Bulk Edit Clones"));
		this.parent = parent;
		this.clones = clones;
		for (Payloads.RemoteClone clone : clones) {
			this.selected.add(clone.uuid());
		}
		for (CloneOption option : CloneOption.values()) {
			if ((option.bit() & RemoteControl.BULK_LOCKED) == 0) {
				this.options.add(option);
			}
		}
		this.optionState = new int[this.options.size()];
	}

	@Override
	protected void init() {
		this.listX = 10;
		this.listW = Math.max(110, Math.min(170, this.width / 3));
		this.listBottom = this.height - 34;
		this.clampScroll();

		int listBy = this.height - 26;
		this.addRenderableWidget(Button.builder(Component.literal("All"), b -> {
			this.clones.forEach(c -> this.selected.add(c.uuid()));
			this.refreshApply();
		}).bounds(this.listX, listBy, this.listW / 2 - 2, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("None"), b -> {
			this.selected.clear();
			this.refreshApply();
		}).bounds(this.listX + this.listW / 2 + 2, listBy, this.listW - this.listW / 2 - 2, 20).build());

		// The right side: one button per option, then Team and Lists, laid out in as many columns as needed to fit.
		int x0 = this.listX + this.listW + 10;
		int areaW = this.width - 10 - x0;
		int gap = 4;
		int count = this.options.size() + 2;
		int avail = this.listBottom - TOP;
		int columns = 1;
		while (columns < 4 && avail / (int)Math.ceil(count / (double)columns) < 18) {
			columns++;
		}
		columns = Math.max(columns, areaW >= 300 ? 2 : 1);
		int rows = (int)Math.ceil(count / (double)columns);
		int buttonW = (areaW - gap * (columns - 1)) / columns;
		int buttonH = Math.max(12, Math.min(20, avail / rows - gap));

		this.optionButtons.clear();
		for (int i = 0; i < count; i++) {
			int x = x0 + (i / rows) * (buttonW + gap);
			int y = TOP + (i % rows) * (buttonH + gap);
			if (i < this.options.size()) {
				int index = i;
				Button button = Button.builder(this.optionLabel(index), b -> {
					this.optionState[index] = (this.optionState[index] + 1) % 3;
					b.setMessage(this.optionLabel(index));
					this.refreshApply();
				}).bounds(x, y, buttonW, buttonH).build();
				this.optionButtons.add(this.addRenderableWidget(button));
			} else if (i == this.options.size()) {
				this.teamButton = this.addRenderableWidget(Button.builder(this.teamLabel(), b -> {
					this.team = this.team + 1 >= CloneTeam.values().length ? -1 : this.team + 1;
					b.setMessage(this.teamLabel());
					this.refreshApply();
				}).bounds(x, y, buttonW, buttonH).build());
			} else {
				int labelW = buttonW - 12;
				this.listButton = this.addRenderableWidget(Button.builder(this.listLabel(labelW), b -> {
					this.listSource = this.listSource + 1 >= this.clones.size() ? -1 : this.listSource + 1;
					b.setMessage(this.listLabel(labelW));
					this.refreshApply();
				}).bounds(x, y, buttonW, buttonH).build());
				this.listButton.active = !this.clones.isEmpty();
			}
		}

		int by = this.height - 26;
		int bx = x0 + Math.max(0, (areaW - 244) / 2);
		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
			Arrays.fill(this.optionState, KEEP);
			this.team = -1;
			this.listSource = -1;
			this.rebuildWidgets();
		}).bounds(bx, by, 76, 20).build());
		this.applyButton = this.addRenderableWidget(Button.builder(Component.literal("Apply"), b -> this.apply()).bounds(bx + 80, by, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose()).bounds(bx + 164, by, 80, 20).build());
		this.refreshApply();
	}

	private Component optionLabel(final int index) {
		CloneOption option = this.options.get(index);
		return Component.literal(option.label + ": ").append(switch (this.optionState[index]) {
			case ON -> Component.literal("ON").withStyle(ChatFormatting.GREEN);
			case OFF -> Component.literal("OFF").withStyle(ChatFormatting.RED);
			default -> Component.literal("Keep").withStyle(ChatFormatting.GRAY);
		});
	}

	private Component teamLabel() {
		if (this.team < 0) {
			return Component.literal("Team: ").append(Component.literal("Keep").withStyle(ChatFormatting.GRAY));
		}
		CloneTeam t = CloneTeam.values()[this.team];
		return Component.literal("Team: ").append(Component.literal(t.displayName).withColor(t.color));
	}

	private Component listLabel(final int width) {
		if (this.listSource < 0) {
			return Component.literal("Lists: ").append(Component.literal("Keep").withStyle(ChatFormatting.GRAY));
		}
		String prefix = "Lists from ";
		String name = this.font.plainSubstrByWidth(this.clones.get(this.listSource).name(), Math.max(10, width - this.font.width(prefix)));
		return Component.literal(prefix).append(Component.literal(name).withStyle(ChatFormatting.AQUA));
	}

	private boolean hasChanges() {
		if (this.team >= 0 || this.listSource >= 0) {
			return true;
		}
		for (int state : this.optionState) {
			if (state != KEEP) {
				return true;
			}
		}
		return false;
	}

	private void refreshApply() {
		if (this.applyButton != null) {
			this.applyButton.active = !this.selected.isEmpty() && this.hasChanges();
		}
	}

	private void apply() {
		int set = 0;
		int clear = 0;
		for (int i = 0; i < this.options.size(); i++) {
			if (this.optionState[i] == ON) {
				set |= this.options.get(i).bit();
			} else if (this.optionState[i] == OFF) {
				clear |= this.options.get(i).bit();
			}
		}
		Optional<UUID> source = this.listSource >= 0 ? Optional.of(this.clones.get(this.listSource).uuid()) : Optional.empty();
		List<UUID> targets = new ArrayList<>(this.selected);
		if (targets.size() > Payloads.BulkEdit.MAX_CLONES) {
			targets = targets.subList(0, Payloads.BulkEdit.MAX_CLONES);
		}
		ClientNetworking.send(new Payloads.BulkEdit(targets, set, clear, this.team, source));
		this.onClose();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private void clampScroll() {
		int max = Math.max(0, this.clones.size() * ROW - (this.listBottom - TOP));
		this.scroll = Math.max(0, Math.min(max, this.scroll));
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (x >= this.listX && x < this.listX + this.listW) {
			this.scroll -= scrollY * ROW * 2;
			this.clampScroll();
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (mx >= this.listX && mx < this.listX + this.listW && my >= TOP && my < this.listBottom) {
			int index = (int)((my - TOP + this.scroll) / ROW);
			if (index >= 0 && index < this.clones.size()) {
				UUID id = this.clones.get(index).uuid();
				if (!this.selected.remove(id)) {
					this.selected.add(id);
				}
				this.refreshApply();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, "Bulk Edit (" + this.selected.size() + " of " + this.clones.size() + " selected)", this.width / 2, 6, 0xFFFFFFFF);

		String hint = "Click an option to cycle Keep / ON / OFF. Names aren't changed.";
		for (int i = 0; i < this.optionButtons.size(); i++) {
			if (this.optionButtons.get(i).isHovered()) {
				hint = this.options.get(i).description;
			}
		}
		if (this.teamButton != null && this.teamButton.isHovered()) {
			hint = this.team < 0 ? "Click to choose a team for every selected clone." : CloneTeam.values()[this.team].info();
		}
		if (this.listButton != null && this.listButton.isHovered()) {
			hint = "Copy one clone's 'Entities To Attack' and 'Blocks To Break' lists to every selected clone.";
		}
		g.centeredText(this.font, this.font.plainSubstrByWidth(hint, this.width - 20), this.width / 2, 17, 0xFFAAAAAA);

		int left = this.listX;
		int right = this.listX + this.listW;
		g.fill(left - 2, TOP - 2, right + 2, this.listBottom + 2, 0x88000000);
		g.enableScissor(left, TOP, right, this.listBottom);
		for (int i = (int)(this.scroll / ROW); i < this.clones.size(); i++) {
			int y = TOP + i * ROW - (int)this.scroll;
			if (y > this.listBottom) {
				break;
			}
			Payloads.RemoteClone clone = this.clones.get(i);
			boolean on = this.selected.contains(clone.uuid());
			boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW && mouseY >= TOP && mouseY < this.listBottom;
			g.fill(left, y, right, y + ROW - 1, on ? 0x6633AA33 : (hover ? 0x44FFFFFF : 0x22FFFFFF));
			g.text(this.font, on ? "✔" : "", left + 3, y + 3, 0xFF55FF55);
			String label = clone.name() + (i == this.listSource ? " (lists)" : "");
			g.text(this.font, this.font.plainSubstrByWidth(label, this.listW - 16), left + 13, y + 3, on ? 0xFF88FF88 : 0xFFFFFFFF);
		}
		g.disableScissor();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
