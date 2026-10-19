package com.clonecraft.menu;

import com.clonecraft.CloneCraft;
import com.clonecraft.block.CentrifugeBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CentrifugeMenu extends AbstractContainerMenu {
	/** Where the 8 tube slots sit around the middle (the screen spins them while it works). */
	public static final int[][] TUBE_POSITIONS = {{116, 44}, {105, 69}, {80, 80}, {55, 69}, {44, 44}, {55, 19}, {80, 8}, {105, 19}};

	private final Container container;
	private final ContainerData data;

	public CentrifugeMenu(final int id, final Inventory inventory) {
		this(id, inventory, new SimpleContainer(9), new SimpleContainerData(4));
	}

	public CentrifugeMenu(final int id, final Inventory inventory, final Container container, final ContainerData data) {
		super(CloneCraft.CENTRIFUGE_MENU, id);
		this.container = container;
		this.data = data;
		for (int i = 0; i < 8; i++) {
			int slot = i + 1;
			this.addSlot(new Slot(container, slot, TUBE_POSITIONS[i][0], TUBE_POSITIONS[i][1]) {
				@Override
				public boolean mayPlace(final ItemStack stack) {
					return CentrifugeBlockEntity.isBlood(stack) && CentrifugeMenu.this.data.get(3) == 0;
				}

				@Override
				public boolean mayPickup(final Player player) {
					return CentrifugeMenu.this.data.get(3) == 0;
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		this.addSlot(new Slot(container, CentrifugeBlockEntity.FUEL_SLOT, 80, 44) {
			@Override
			public boolean mayPlace(final ItemStack stack) {
				return CentrifugeBlockEntity.isFuel(stack);
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 114 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 172));
		}
		this.addDataSlots(data);
	}

	public int burnTime() {
		return this.data.get(0);
	}

	public int burnTotal() {
		return this.data.get(1);
	}

	public int spinTime() {
		return this.data.get(2);
	}

	public boolean isSpinning() {
		return this.data.get(3) != 0;
	}

	@Override
	public boolean clickMenuButton(final Player player, final int id) {
		if (id == 0 && this.container instanceof CentrifugeBlockEntity be) {
			be.toggleSpin();
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < 9) {
			if (!slot.mayPickup(player) || !this.moveItemStackTo(stack, 9, 45, true)) {
				return ItemStack.EMPTY;
			}
		} else if (CentrifugeBlockEntity.isBlood(stack)) {
			boolean moved = false;
			for (int i = 0; i < 8 && !stack.isEmpty(); i++) {
				Slot tube = this.slots.get(i);
				if (!tube.hasItem() && tube.mayPlace(stack)) {
					tube.setByPlayer(stack.split(1));
					moved = true;
				}
			}
			if (!moved) {
				return ItemStack.EMPTY;
			}
		} else if (CentrifugeBlockEntity.isFuel(stack)) {
			if (!this.moveItemStackTo(stack, 8, 9, false)) {
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
