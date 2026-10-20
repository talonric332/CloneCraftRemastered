package com.clonecraft.client.screen;

import com.clonecraft.client.ClientNetworking;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.Payloads;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Clone Remote screen: every clone you own, with buttons to watch it, manage it, change its mode, or teleport it to
 * you, and what Game Progression has it doing. "Manage" only works for clones in your dimension that your game has
 * loaded; teleport the others first. "Watch" works anywhere.
 */
public class CloneRemoteScreen extends Screen {
	private static final int ROW_H = 24;
	private static final int TOP = 28;
	private static final String[] MODES = {"None", "Follow", "Stay", "Wander"};

	private List<Payloads.RemoteClone> clones;
	private int page;
	private int perPage = 1;
	private int listX;
	private int listW;

	public CloneRemoteScreen(final List<Payloads.RemoteClone> clones) {
		super(Component.literal("Clone Remote"));
		this.clones = clones;
	}

	/** New data from the server: redraw the buttons. */
	public void update(final List<Payloads.RemoteClone> clones) {
		this.clones = clones;
		this.rebuildWidgets();
	}

	@Override
	protected void init() {
		// Wider on big screens, so there's room for each clone's task next to its name.
		this.listW = Math.min(this.width - 20, 560);
		this.listX = (this.width - this.listW) / 2;
		this.perPage = Math.max(1, (this.height - TOP - 34) / ROW_H);
		int pages = Math.max(1, (this.clones.size() + this.perPage - 1) / this.perPage);
		this.page = Math.max(0, Math.min(this.page, pages - 1));

		int right = this.listX + this.listW;
		for (int i = 0; i < this.perPage; i++) {
			int index = this.page * this.perPage + i;
			if (index >= this.clones.size()) {
				break;
			}
			Payloads.RemoteClone clone = this.clones.get(index);
			int y = TOP + i * ROW_H;
			this.addRenderableWidget(Button.builder(Component.literal("Teleport"), b -> this.send(clone.uuid(), Payloads.RemoteAction.TELEPORT, 0))
				.bounds(right - 60, y, 60, 20).build());
			int[] mode = {Math.floorMod(clone.mode(), MODES.length)};
			this.addRenderableWidget(Button.builder(Component.literal("Mode: " + MODES[mode[0]]), b -> {
				// Update the label straight away instead of waiting for the server to send the list back.
				mode[0] = (mode[0] + 1) % MODES.length;
				b.setMessage(Component.literal("Mode: " + MODES[mode[0]]));
				this.send(clone.uuid(), Payloads.RemoteAction.SET_MODE, mode[0]);
			}).bounds(right - 148, y, 84, 20).build());
			Button manage = Button.builder(Component.literal("Manage"), b -> this.send(clone.uuid(), Payloads.RemoteAction.MANAGE, 0))
				.bounds(right - 212, y, 60, 20).build();
			manage.active = clone.sameLevel() && this.minecraft.level != null && this.minecraft.level.getEntity(clone.entityId()) instanceof CloneEntity;
			this.addRenderableWidget(manage);
			// See through the clone's eyes; Sneak comes back.
			this.addRenderableWidget(Button.builder(Component.literal("Watch"), b -> {
				this.send(clone.uuid(), Payloads.RemoteAction.WATCH, 0);
				this.onClose();
			}).bounds(right - 266, y, 50, 20).build());
		}

		int by = this.height - 26;
		int cx = this.width / 2;
		Button all = this.addRenderableWidget(Button.builder(Component.literal("Teleport All"),
			b -> this.send(UUID.randomUUID(), Payloads.RemoteAction.TELEPORT_ALL, 0)).bounds(cx - 166, by, 80, 20).build());
		all.active = !this.clones.isEmpty();
		Button bulk = this.addRenderableWidget(Button.builder(Component.literal("Bulk Edit"),
			b -> this.minecraft.gui.setScreen(new CloneBulkEditScreen(this, this.clones))).bounds(cx - 82, by, 80, 20).build());
		bulk.active = !this.clones.isEmpty();
		this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> this.send(UUID.randomUUID(), Payloads.RemoteAction.REFRESH, 0))
			.bounds(cx + 2, by, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(cx + 86, by, 80, 20).build());
		if (pages > 1) {
			Button prev = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				this.page--;
				this.rebuildWidgets();
			}).bounds(cx - 190, by, 20, 20).build());
			prev.active = this.page > 0;
			Button nextPage = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				this.page++;
				this.rebuildWidgets();
			}).bounds(cx + 170, by, 20, 20).build());
			nextPage.active = this.page < pages - 1;
		}
	}

	private void send(final UUID clone, final int action, final int value) {
		ClientNetworking.send(new Payloads.RemoteAction(clone, action, value));
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, "Clone Remote (" + this.clones.size() + " clone" + (this.clones.size() == 1 ? "" : "s") + ")", this.width / 2, 10, 0xFFFFFFFF);
		if (this.clones.isEmpty()) {
			g.centeredText(this.font, Component.literal("You don't own any clones in loaded areas.").withStyle(ChatFormatting.GRAY), this.width / 2, TOP + 10, 0xFFFFFFFF);
			return;
		}
		int textW = this.listW - 270;
		for (int i = 0; i < this.perPage; i++) {
			int index = this.page * this.perPage + i;
			if (index >= this.clones.size()) {
				break;
			}
			Payloads.RemoteClone clone = this.clones.get(index);
			int y = TOP + i * ROW_H;
			String name = this.font.plainSubstrByWidth(clone.name(), textW);
			g.text(this.font, name, this.listX, y + 1, 0xFFFFFFFF);
			if (!clone.task().isEmpty()) {
				// What Game Progression has it doing, after the name.
				int taskX = this.listX + this.font.width(name) + 6;
				g.text(this.font, this.font.plainSubstrByWidth(clone.task(), Math.max(0, this.listX + textW - taskX)), taskX, y + 1, 0xFFFFCC55);
			}
			String where;
			if (clone.sameLevel() && this.minecraft.player != null) {
				where = Math.round(Math.sqrt(this.minecraft.player.blockPosition().distSqr(clone.pos()))) + " blocks away";
			} else {
				String dim = clone.dimension();
				where = "in " + dim.substring(dim.indexOf(':') + 1);
			}
			String info = "❤ " + Math.round(clone.health()) + "/" + Math.round(clone.maxHealth()) + "  " + where;
			g.text(this.font, this.font.plainSubstrByWidth(info, textW), this.listX, y + 11, 0xFFAAAAAA);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
