package com.clonecraft.client.screen;

import com.clonecraft.client.ClientNetworking;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.menu.CloneMenu;
import com.clonecraft.network.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The clone's main screen, laid out like the original: its inventory and armour, health, food and experience,
 * and buttons for Transfer XP, Options, Teams, Entities To Attack, Blocks To Break and Things To Build.
 * Creative players can also Copy the clone. Press Kill three times to kill it.
 */
public class CloneScreen extends AbstractContainerScreen<CloneMenu> {
	private Button killButton;
	private int killPresses;
	private EditBox copyAmount;

	public CloneScreen(final CloneMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, 176, 194);
		this.inventoryLabelY = 101;
	}

	private CloneEntity getClone() {
		return this.menu.clone;
	}

	@Override
	protected void init() {
		super.init();
		if (this.getClone() == null) {
			return;
		}
		int bx = Math.max(4, this.leftPos - 140);
		int by = this.topPos;
		this.addRenderableWidget(Button.builder(Component.literal("Transfer XP"), b -> this.action(Payloads.CloneAction.TRANSFER_XP, 0)).bounds(bx, by + 40, 110, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Options"), b -> this.open(new CloneOptionsScreen(this.getClone()))).bounds(bx, by + 64, 110, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Teams"), b -> this.open(new CloneTeamsScreen(this.getClone()))).bounds(bx, by + 88, 110, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Entities To Attack"), b -> this.open(new ChooseListScreen(this.getClone(), ChooseListScreen.Kind.ENTITIES)))
			.bounds(bx, by + 120, 110, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Blocks To Break"), b -> this.open(new ChooseListScreen(this.getClone(), ChooseListScreen.Kind.BLOCKS)))
			.bounds(bx, by + 144, 110, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Things To Build"), b -> this.open(new BuildScreen(this.getClone()))).bounds(bx, by + 168, 110, 20).build());

		int rx = Math.min(this.width - 104, this.leftPos + this.imageWidth + 10);
		boolean creative = this.minecraft.player.getAbilities().instabuild;
		// Only offer Claim when you don't already own this clone, and hide the button once you claim it.
		String owner = this.getClone().getOwnerName();
		boolean mine = owner.equals(this.minecraft.player.getGameProfile().name());
		if (!mine && (creative || owner.isEmpty())) {
			this.addRenderableWidget(Button.builder(Component.literal("Claim Clone"), b -> {
				this.action(Payloads.CloneAction.CLAIM, 0);
				b.visible = false;
				b.active = false;
			}).bounds(rx, by + 120, 100, 20).build());
		}
		if (creative) {
			this.copyAmount = new EditBox(this.font, rx, by + 16, 100, 20, Component.literal("Copies"));
			this.copyAmount.setValue("1");
			this.copyAmount.setMaxLength(2);
			this.addRenderableWidget(this.copyAmount);
			this.addRenderableWidget(Button.builder(Component.literal("Copy Clone"), b -> {
				try {
					int amount = Integer.parseInt(this.copyAmount.getValue());
					if (amount > 0) {
						this.action(Payloads.CloneAction.COPY, Math.min(50, amount));
					}
				} catch (NumberFormatException ignored) {
				}
			}).bounds(rx, by + 40, 100, 20).build());
		}
		this.killButton = this.addRenderableWidget(Button.builder(Component.literal("Kill"), b -> {
			this.killPresses++;
			if (this.killPresses >= 3) {
				this.action(Payloads.CloneAction.KILL, 0);
				this.onClose();
			} else {
				b.setMessage(Component.literal(this.killPresses == 1 ? "Kill?" : "Really kill?").withStyle(ChatFormatting.RED));
			}
		}).bounds(rx, by + 168, 100, 20).build());
	}

	private void action(final int action, final int value) {
		ClientNetworking.send(new Payloads.CloneAction(this.getClone().getId(), action, value));
	}

	/** Sub-screens close the container first; they reopen it when you leave them. */
	private void open(final Screen screen) {
		this.minecraft.player.closeContainer();
		this.minecraft.gui.setScreen(screen);
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.panel(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
		Ui.panel(g, this.leftPos - 26, this.topPos + 10, 26, 82);
		for (Slot slot : this.menu.slots) {
			Ui.slot(g, this.leftPos + slot.x, this.topPos + slot.y);
		}
		CloneEntity clone = this.getClone();
		if (clone == null) {
			return;
		}
		// The clone's selected hotbar slot.
		int sx = this.leftPos + 7 + clone.getInventory().selected * 18;
		int sy = this.topPos + 75;
		g.outline(sx, sy, 18, 18, 0xFFEEEE11);

		// Health, food and experience, like the original.
		int tx = Math.max(4, this.leftPos - 140);
		int ty = this.topPos;
		g.text(this.font, Component.literal("❤ " + Math.round(clone.getHealth()) + " / " + Math.round(clone.getMaxHealth())).withStyle(ChatFormatting.RED), tx, ty + 2, 0xFFFFFFFF);
		Ui.bar(g, tx, ty + 12, 110, 4, clone.getHealth() / Math.max(1, clone.getMaxHealth()), 0xFFDD2222);
		g.text(this.font, Component.literal("Food " + clone.getFoodLevel() + " / 20").withStyle(ChatFormatting.GOLD), tx, ty + 20, 0xFFFFFFFF);
		Ui.bar(g, tx, ty + 30, 110, 4, clone.getFoodLevel() / 20.0F, 0xFFCC8833);

		int xpX = this.leftPos + (this.imageWidth - 182) / 2;
		int xpY = this.topPos + this.imageHeight + 6;
		Ui.bar(g, xpX, xpY, 182, 5, clone.getXpProgress(), 0xFF80FF20);
		String level = String.valueOf(clone.getXpLevel());
		g.text(this.font, level, xpX + 91 - this.font.width(level) / 2, xpY - 9, 0xFF80FF20);

		int cooldown = clone.getShrinkCooldown();
		if (cooldown > 0) {
			int seconds = cooldown / 20;
			String time = (seconds / 3600 > 0 ? seconds / 3600 + "h " : "") + (seconds / 60 > 0 ? (seconds / 60) % 60 + "m " : "") + seconds % 60 + "s";
			g.text(this.font, "Shrink Cooldown: " + time, 5, this.height - 15, 0xFFFFFFFF);
		}
		if (clone.getBuildStage() >= 0) {
			g.text(this.font, "Building: " + clone.getBuildIndex() + " / " + clone.getBuildTotal() + " (stage " + (clone.getBuildStage() + 1) + " of 4)", 5, this.height - 27, 0xFF77EE77);
		}
	}

	@Override
	protected void extractLabels(final GuiGraphicsExtractor g, final int xm, final int ym) {
		CloneEntity clone = this.getClone();
		String name = clone == null ? "Clone" : clone.getCloneName();
		String team = clone == null ? "" : " (" + clone.getCloneTeam().displayName + ")";
		g.text(this.font, Component.literal(name + team), 8, 6, Ui.TEXT, false);
		g.text(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, Ui.TEXT, false);
	}
}
