package com.clonecraft.client.screen;

import com.clonecraft.client.ClientNetworking;
import com.clonecraft.network.Payloads;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Clone Remote's bulk editor. Tick the clones on the left, then pick what to change with the three buttons on the
 * right (Entities To Attack, Blocks To Break, Options), then press Apply. Each button shows what will change.
 * Nothing happens to the clones until Apply. Names (and Female, which picks the name) can't be bulk edited.
 */
public class CloneBulkEditScreen extends Screen {
	private static final int ROW = 14;
	private static final int TOP = 30;

	private final Screen parent;
	private final List<Payloads.RemoteClone> clones;
	private final Set<UUID> selected = new LinkedHashSet<>();
	private final BulkListScreen.Changes attackChanges = new BulkListScreen.Changes();
	private final BulkListScreen.Changes breakChanges = new BulkListScreen.Changes();
	private final BulkOptionsScreen.Changes optionChanges = new BulkOptionsScreen.Changes();
	private Button applyButton;
	private double scroll;
	private int listX;
	private int listW;
	private int listBottom;
	private int menuX;
	private int menuW;
	private int[] menuY = new int[3];

	public CloneBulkEditScreen(final Screen parent, final List<Payloads.RemoteClone> clones) {
		super(Component.literal("Bulk Edit Clones"));
		this.parent = parent;
		this.clones = clones;
		for (Payloads.RemoteClone clone : clones) {
			this.selected.add(clone.uuid());
		}
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

		// The menu: three big buttons, each with a line under it saying what will change.
		int areaX = this.listX + this.listW + 10;
		int areaW = this.width - 10 - areaX;
		this.menuW = Math.min(areaW, 220);
		this.menuX = areaX + (areaW - this.menuW) / 2;
		int step = Math.max(34, Math.min(46, (this.listBottom - TOP) / 3));
		int start = TOP + Math.max(0, (this.listBottom - TOP - step * 3) / 2);
		for (int i = 0; i < 3; i++) {
			this.menuY[i] = start + i * step;
		}
		this.addRenderableWidget(Button.builder(Component.literal("Entities To Attack..."),
			b -> this.minecraft.gui.setScreen(new BulkListScreen(this, ChooseListScreen.Kind.ENTITIES, this.attackChanges)))
			.bounds(this.menuX, this.menuY[0], this.menuW, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Blocks To Break..."),
			b -> this.minecraft.gui.setScreen(new BulkListScreen(this, ChooseListScreen.Kind.BLOCKS, this.breakChanges)))
			.bounds(this.menuX, this.menuY[1], this.menuW, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Options..."),
			b -> this.minecraft.gui.setScreen(new BulkOptionsScreen(this, this.optionChanges)))
			.bounds(this.menuX, this.menuY[2], this.menuW, 20).build());

		int by = this.height - 26;
		int bx = areaX + Math.max(0, (areaW - 244) / 2);
		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
			this.attackChanges.reset();
			this.breakChanges.reset();
			this.optionChanges.reset();
			this.refreshApply();
		}).bounds(bx, by, 76, 20).build());
		this.applyButton = this.addRenderableWidget(Button.builder(Component.literal("Apply"), b -> this.apply()).bounds(bx + 80, by, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose()).bounds(bx + 164, by, 80, 20).build());
		this.refreshApply();
	}

	private boolean hasChanges() {
		return !this.attackChanges.isEmpty() || !this.breakChanges.isEmpty() || !this.optionChanges.isEmpty();
	}

	private void refreshApply() {
		if (this.applyButton != null) {
			this.applyButton.active = !this.selected.isEmpty() && this.hasChanges();
		}
	}

	private void apply() {
		List<UUID> targets = new ArrayList<>(this.selected);
		if (targets.size() > Payloads.BulkEdit.MAX_CLONES) {
			targets = targets.subList(0, Payloads.BulkEdit.MAX_CLONES);
		}
		ClientNetworking.send(new Payloads.BulkEdit(targets, this.optionChanges.setMask(), this.optionChanges.clearMask(), this.optionChanges.team,
			this.attackChanges.toPayload(), this.breakChanges.toPayload()));
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
		g.centeredText(this.font, "Bulk Edit (" + this.selected.size() + " of " + this.clones.size() + " clones selected)", this.width / 2, 6, 0xFFFFFFFF);
		g.centeredText(this.font, this.font.plainSubstrByWidth("Tick clones on the left, choose what to change, then press Apply.", this.width - 20),
			this.width / 2, 17, 0xFFAAAAAA);

		// What each menu button will change.
		Component[] summaries = {this.attackChanges.summary(), this.breakChanges.summary(), this.optionChanges.summary()};
		for (int i = 0; i < 3; i++) {
			g.centeredText(this.font, summaries[i], this.menuX + this.menuW / 2, this.menuY[i] + 23, 0xFFFFFFFF);
		}

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
			g.text(this.font, this.font.plainSubstrByWidth(clone.name(), this.listW - 16), left + 13, y + 3, on ? 0xFF88FF88 : 0xFFFFFFFF);
		}
		g.disableScissor();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
