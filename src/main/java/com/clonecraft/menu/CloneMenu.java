package com.clonecraft.menu;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneInventory;
import com.clonecraft.entity.CloneEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** The clone's main screen: its inventory and armour on top, yours below ("Transfer Items" in the original). */
public class CloneMenu extends AbstractContainerMenu {
	public final @Nullable CloneEntity clone;
	private final CloneInventory container;

	/** Client side: finds the clone by the entity id the server sent. */
	public static CloneMenu fromNetwork(final int id, final Inventory inventory, final Integer entityId) {
		Entity entity = inventory.player.level().getEntity(entityId);
		return new CloneMenu(id, inventory, entity instanceof CloneEntity c ? c : null);
	}

	public CloneMenu(final int id, final Inventory inventory, final @Nullable CloneEntity clone) {
		super(CloneCraft.CLONE_MENU, id);
		this.clone = clone;
		this.container = clone == null ? null : clone.getInventory();
		if (this.container == null) {
			return;
		}
		// Clone hotbar (bottom row of its inventory) and main inventory.
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(this.container, 9 + col + row * 9, 8 + col * 18, 18 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(this.container, col, 8 + col * 18, 76));
		}
		// Armour, down the left edge.
		for (int i = 0; i < 4; i++) {
			EquipmentSlot equip = CloneInventory.ARMOR_SLOTS[i];
			this.addSlot(new Slot(this.container, CloneInventory.MAIN_SIZE + i, -18, 18 + i * 18) {
				@Override
				public boolean mayPlace(final ItemStack stack) {
					var equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
					return equippable != null && equippable.slot() == equip;
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		// Player inventory.
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 112 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 170));
		}
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		int cloneEnd = 40;
		if (index < cloneEnd) {
			if (!this.moveItemStackTo(stack, cloneEnd, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			var equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
			boolean moved = false;
			if (equippable != null) {
				for (int i = 36; i < 40; i++) {
					if (!this.slots.get(i).hasItem() && this.slots.get(i).mayPlace(stack)) {
						this.slots.get(i).setByPlayer(stack.split(1));
						moved = true;
						break;
					}
				}
			}
			if (!moved && !this.moveItemStackTo(stack, 0, 36, false)) {
				return ItemStack.EMPTY;
			}
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
		return this.container != null && this.container.stillValid(player);
	}
}
