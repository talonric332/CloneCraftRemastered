package com.clonecraft.menu;

import com.clonecraft.CloneCraft;
import com.clonecraft.block.LifeInducerBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class LifeInducerMenu extends AbstractContainerMenu {
	private final Container container;
	private final ContainerData data;

	public LifeInducerMenu(final int id, final Inventory inventory) {
		this(id, inventory, new SimpleContainer(27), new SimpleContainerData(1));
	}

	public LifeInducerMenu(final int id, final Inventory inventory, final Container container, final ContainerData data) {
		super(CloneCraft.LIFE_INDUCER_MENU, id);
		this.container = container;
		this.data = data;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 35 + row * 18) {
					@Override
					public boolean mayPlace(final ItemStack stack) {
						return LifeInducerBlockEntity.isValid(stack);
					}

					@Override
					public int getMaxStackSize() {
						return 1;
					}
				});
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 108 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 166));
		}
		this.addDataSlots(data);
	}

	public int power() {
		return this.data.get(0);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < 27) {
			if (!this.moveItemStackTo(stack, 27, 63, true)) {
				return ItemStack.EMPTY;
			}
		} else if (LifeInducerBlockEntity.isValid(stack)) {
			boolean moved = false;
			for (int i = 0; i < 27 && !stack.isEmpty(); i++) {
				Slot target = this.slots.get(i);
				if (!target.hasItem()) {
					target.setByPlayer(stack.split(1));
					moved = true;
				}
			}
			if (!moved) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.container.stillValid(player);
	}
}
