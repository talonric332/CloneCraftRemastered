package com.clonecraft.client.screen;

import com.clonecraft.network.Payloads;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Bulk Edit's "Entities To Attack" / "Blocks To Break" picker. Each entry cycles Keep / Add / Remove, like the option
 * buttons cycle Keep / ON / OFF. Turning "Others" to Remove replaces the whole list with just the Add entries.
 */
public class BulkListScreen extends Screen {
	/** What Bulk Edit will do to one of the lists. Kept by the Bulk Edit screen, so it survives going back and forth. */
	public static class Changes {
		static final int ADD = 1;
		static final int REMOVE = 2;

		final Map<Identifier, Integer> marks = new LinkedHashMap<>();
		boolean replace;

		boolean isEmpty() {
			return !this.replace && this.marks.isEmpty();
		}

		void reset() {
			this.marks.clear();
			this.replace = false;
		}

		int count(final int mark) {
			return (int)this.marks.values().stream().filter(m -> m == mark).count();
		}

		Payloads.ListEdit toPayload() {
			List<Identifier> add = new ArrayList<>();
			List<Identifier> remove = new ArrayList<>();
			this.marks.forEach((id, mark) -> (mark == ADD ? add : remove).add(id));
			return new Payloads.ListEdit(add, this.replace ? List.of() : remove, this.replace);
		}

		/** Short summary for the Bulk Edit button, e.g. "Keep", "+3 -1" or "only 3". */
		Component summary() {
			if (this.isEmpty()) {
				return Component.literal("Keep").withStyle(ChatFormatting.GRAY);
			}
			if (this.replace) {
				return Component.literal("only " + this.count(ADD)).withStyle(ChatFormatting.GOLD);
			}
			return Component.literal("+" + this.count(ADD)).withStyle(ChatFormatting.GREEN)
				.append(Component.literal(" -" + this.count(REMOVE)).withStyle(ChatFormatting.RED));
		}
	}

	private final Screen parent;
	private final ChooseListScreen.Kind kind;
	private final Changes changes;
	private final List<ChooseListScreen.Entry> all;
	private List<ChooseListScreen.Entry> shown;
	private double scroll;
	private int listTop;
	private int listBottom;
	private Button othersButton;

	public BulkListScreen(final Screen parent, final ChooseListScreen.Kind kind, final Changes changes) {
		super(Component.literal(kind == ChooseListScreen.Kind.ENTITIES ? "Bulk Edit: Entities To Attack" : "Bulk Edit: Blocks To Break"));
		this.parent = parent;
		this.kind = kind;
		this.changes = changes;
		this.all = ChooseListScreen.entries(kind);
		this.shown = new ArrayList<>(this.all);
	}

	@Override
	protected void init() {
		this.listTop = 58;
		this.listBottom = this.height - 34;
		EditBox search = new EditBox(this.font, this.width / 2 - 100, 30, 200, 20, Component.literal("Search"));
		search.setHint(Component.literal("Search..."));
		search.setResponder(text -> {
			String q = text.toLowerCase(Locale.ROOT);
			this.shown = this.all.stream().filter(e -> e.name().getString().toLowerCase(Locale.ROOT).contains(q) || e.id().toString().contains(q)).toList();
			this.scroll = 0;
		});
		this.addRenderableWidget(search);

		boolean mobs = this.kind == ChooseListScreen.Kind.ENTITIES;
		int y = this.height - 26;
		// Widths sized to their labels, so "Others: Remove" fits on small screens too.
		int[] w = {56, 70, 70, 86, 48, 48};
		int x = this.width / 2 - (56 + 70 + 70 + 86 + 48 + 48 + 5 * 4) / 2;
		this.addRenderableWidget(Button.builder(Component.literal("Add All"), b -> this.addGroup(null)).bounds(x, y, w[0], 20).build());
		x += w[0] + 4;
		this.addRenderableWidget(Button.builder(Component.literal(mobs ? "+ Monsters" : "+ Ores"), b -> this.addGroup(mobs ? "mob" : "ore"))
			.bounds(x, y, w[1], 20).build());
		x += w[1] + 4;
		this.addRenderableWidget(Button.builder(Component.literal(mobs ? "+ Animals" : "+ Trees"), b -> this.addGroup(mobs ? "animal" : "tree"))
			.bounds(x, y, w[2], 20).build());
		x += w[2] + 4;
		this.othersButton = this.addRenderableWidget(Button.builder(this.othersLabel(), b -> {
			this.changes.replace = !this.changes.replace;
			b.setMessage(this.othersLabel());
		}).bounds(x, y, w[3], 20).build());
		x += w[3] + 4;
		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
			this.changes.reset();
			this.othersButton.setMessage(this.othersLabel());
		}).bounds(x, y, w[4], 20).build());
		x += w[4] + 4;
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(x, y, w[5], 20).build());
	}

	private Component othersLabel() {
		return Component.literal("Others: ").append(this.changes.replace
			? Component.literal("Remove").withStyle(ChatFormatting.RED)
			: Component.literal("Keep").withStyle(ChatFormatting.GRAY));
	}

	private void addGroup(final String group) {
		for (ChooseListScreen.Entry e : this.all) {
			if (group == null || e.group().equals(group)) {
				this.changes.marks.put(e.id(), Changes.ADD);
			}
		}
	}

	private int listLeft() {
		return this.width / 2 - 150;
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		int max = Math.max(0, this.shown.size() * ChooseListScreen.ROW - (this.listBottom - this.listTop));
		this.scroll = Math.max(0, Math.min(max, this.scroll - scrollY * ChooseListScreen.ROW * 2));
		return true;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (mx >= this.listLeft() && mx < this.listLeft() + 300 && my >= this.listTop && my < this.listBottom) {
			int index = (int)((my - this.listTop + this.scroll) / ChooseListScreen.ROW);
			if (index >= 0 && index < this.shown.size()) {
				// Keep -> Add -> Remove -> Keep
				Identifier id = this.shown.get(index).id();
				int mark = this.changes.marks.getOrDefault(id, 0);
				if (mark == 0) {
					this.changes.marks.put(id, Changes.ADD);
				} else if (mark == Changes.ADD) {
					this.changes.marks.put(id, Changes.REMOVE);
				} else {
					this.changes.marks.remove(id);
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, this.title.getString() + " (+" + this.changes.count(Changes.ADD) + " -" + this.changes.count(Changes.REMOVE) + ")",
			this.width / 2, 8, 0xFFFFFFFF);
		String hint = this.changes.replace
			? "Every selected clone's list becomes exactly the Add entries."
			: "Click to cycle Keep / Add / Remove. Keep leaves each clone's own choice alone.";
		g.centeredText(this.font, this.font.plainSubstrByWidth(hint, this.width - 20), this.width / 2, 19, 0xFFAAAAAA);

		int left = this.listLeft();
		int row = ChooseListScreen.ROW;
		g.fill(left - 2, this.listTop - 2, left + 302, this.listBottom + 2, 0x88000000);
		g.enableScissor(left, this.listTop, left + 300, this.listBottom);
		for (int i = (int)(this.scroll / row); i < this.shown.size(); i++) {
			int y = this.listTop + i * row - (int)this.scroll;
			if (y > this.listBottom) {
				break;
			}
			ChooseListScreen.Entry e = this.shown.get(i);
			int mark = this.changes.marks.getOrDefault(e.id(), 0);
			// With Others on Remove, anything not added will be removed, so show it that way.
			boolean removed = mark == Changes.REMOVE || (this.changes.replace && mark != Changes.ADD);
			boolean hover = mouseX >= left && mouseX < left + 300 && mouseY >= y && mouseY < y + row && mouseY >= this.listTop && mouseY < this.listBottom;
			int bg = mark == Changes.ADD ? 0x6633AA33 : mark == Changes.REMOVE ? 0x66AA3333 : (hover ? 0x44FFFFFF : 0x22FFFFFF);
			g.fill(left, y, left + 300, y + row - 1, bg);
			int textX = left + 6;
			if (!e.icon().isEmpty()) {
				g.item(e.icon(), left + 2, y + 1);
				textX = left + 24;
			}
			int color = mark == Changes.ADD ? 0xFF88FF88 : removed ? 0xFFFF8888 : 0xFFFFFFFF;
			g.text(this.font, e.name(), textX, y + 6, color);
			String label = mark == Changes.ADD ? "Add" : removed ? "Remove" : "Keep";
			g.text(this.font, label, left + 294 - this.font.width(label), y + 6, mark == Changes.ADD ? 0xFF55FF55 : removed ? 0xFFFF5555 : 0xFF888888);
		}
		g.disableScissor();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
