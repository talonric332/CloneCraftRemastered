package com.clonecraft.clone;

import com.clonecraft.entity.CloneEntity;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A player-style inventory for clones: 36 slots (the first 9 are the hotbar) plus 4 armour slots.
 * The clone holds whatever is in its selected hotbar slot.
 */
public class CloneInventory implements Container {
	public static final int MAIN_SIZE = 36;
	public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final CloneEntity clone;
	public final NonNullList<ItemStack> main = NonNullList.withSize(MAIN_SIZE, ItemStack.EMPTY);
	public int selected = 0;

	public CloneInventory(final CloneEntity clone) {
		this.clone = clone;
	}

	// ---------------------------------------------------------------- held item

	/** Makes sure the clone is holding the item in its selected slot, and notices if that item changed or broke. */
	public void syncHeld() {
		ItemStack held = this.clone.getMainHandItem();
		ItemStack slot = this.main.get(this.selected);
		if (held != slot) {
			if (held.isEmpty() && !slot.isEmpty() && slot.getCount() > 0) {
				this.clone.setItemSlot(EquipmentSlot.MAINHAND, slot);
			} else {
				this.main.set(this.selected, held);
			}
		}
		if (slot.isEmpty() && !held.isEmpty() && held.getCount() <= 0) {
			this.main.set(this.selected, ItemStack.EMPTY);
		}
	}

	public void select(final int hotbarSlot) {
		this.syncHeld();
		this.selected = Math.max(0, Math.min(8, hotbarSlot));
		this.clone.setItemSlot(EquipmentSlot.MAINHAND, this.main.get(this.selected));
	}

	/** Moves an item from anywhere in the inventory onto the hotbar and selects it. */
	public void selectFromInventory(final int index) {
		if (index < 9) {
			this.select(index);
			return;
		}
		this.syncHeld();
		int target = this.freeHotbarSlot();
		if (target < 0) {
			target = this.selected;
		}
		ItemStack moving = this.main.get(index);
		this.main.set(index, this.main.get(target));
		this.main.set(target, moving);
		this.select(target);
	}

	private int freeHotbarSlot() {
		if (this.main.get(this.selected).isEmpty()) {
			return this.selected;
		}
		for (int i = 0; i < 9; i++) {
			if (this.main.get(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	/** Selects an empty hand, so good tools are not worn out on blocks that don't need them. */
	public void selectEmptyHand() {
		int free = this.freeHotbarSlot();
		if (free >= 0) {
			this.select(free);
		}
	}

	// ---------------------------------------------------------------- searching

	public int find(final Predicate<ItemStack> test) {
		this.syncHeld();
		for (int i = 0; i < MAIN_SIZE; i++) {
			if (!this.main.get(i).isEmpty() && test.test(this.main.get(i))) {
				return i;
			}
		}
		return -1;
	}

	public int count(final Predicate<ItemStack> test) {
		this.syncHeld();
		int total = 0;
		for (ItemStack s : this.main) {
			if (!s.isEmpty() && test.test(s)) {
				total += s.getCount();
			}
		}
		return total;
	}

	/** Uses up one matching item. Returns false if there wasn't one. */
	public boolean consumeOne(final Predicate<ItemStack> test) {
		int i = this.find(test);
		if (i < 0) {
			return false;
		}
		this.main.get(i).shrink(1);
		if (this.main.get(i).isEmpty()) {
			this.main.set(i, ItemStack.EMPTY);
		}
		this.syncHeld();
		return true;
	}

	/** How many of this stack would fit. */
	public int fitCount(final ItemStack stack) {
		int room = 0;
		for (ItemStack s : this.main) {
			if (s.isEmpty()) {
				room += stack.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(s, stack)) {
				room += Math.max(0, s.getMaxStackSize() - s.getCount());
			}
		}
		return Math.min(room, stack.getCount());
	}

	/** Puts as much of the stack as possible into the inventory. The stack shrinks by the amount taken. Returns that amount. */
	public int add(final ItemStack stack) {
		this.syncHeld();
		int start = stack.getCount();
		for (int i = 0; i < MAIN_SIZE && !stack.isEmpty(); i++) {
			ItemStack s = this.main.get(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
				int move = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
				s.grow(move);
				stack.shrink(move);
			}
		}
		for (int i = 0; i < MAIN_SIZE && !stack.isEmpty(); i++) {
			if (this.main.get(i).isEmpty()) {
				this.main.set(i, stack.split(stack.getMaxStackSize()));
			}
		}
		this.select(this.selected);
		return start - stack.getCount();
	}

	public void dropAll() {
		this.syncHeld();
		for (int i = 0; i < MAIN_SIZE; i++) {
			if (!this.main.get(i).isEmpty() && this.clone.level() instanceof net.minecraft.server.level.ServerLevel level) {
				this.clone.spawnAtLocation(level, this.main.get(i));
			}
			this.main.set(i, ItemStack.EMPTY);
		}
		this.clone.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------- Container (used by the clone's GUI)

	@Override
	public int getContainerSize() {
		return MAIN_SIZE + 4;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < this.getContainerSize(); i++) {
			if (!this.getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(final int slot) {
		if (slot < MAIN_SIZE) {
			return slot == this.selected && !this.clone.level().isClientSide() ? this.clone.getMainHandItem() : this.main.get(slot);
		}
		return this.clone.getItemBySlot(ARMOR_SLOTS[slot - MAIN_SIZE]);
	}

	@Override
	public ItemStack removeItem(final int slot, final int count) {
		ItemStack current = this.getItem(slot);
		if (current.isEmpty() || count <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = current.split(count);
		this.setItem(slot, current.isEmpty() ? ItemStack.EMPTY : current);
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(final int slot) {
		ItemStack current = this.getItem(slot);
		this.setItem(slot, ItemStack.EMPTY);
		return current;
	}

	@Override
	public void setItem(final int slot, final ItemStack stack) {
		if (slot < MAIN_SIZE) {
			this.main.set(slot, stack);
			if (slot == this.selected) {
				this.clone.setItemSlot(EquipmentSlot.MAINHAND, stack);
			}
		} else {
			this.clone.setItemSlot(ARMOR_SLOTS[slot - MAIN_SIZE], stack);
		}
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.clone.isAlive() && (player.distanceToSqr(this.clone) < 64.0 || this.clone.hasRemoteAccess(player));
	}

	@Override
	public void clearContent() {
		this.main.replaceAll(s -> ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------- saving

	public void save(final ValueOutput output) {
		this.syncHeld();
		ContainerHelper.saveAllItems(output.child("CloneInventory"), this.main);
		output.putInt("SelectedSlot", this.selected);
	}

	public void load(final ValueInput input) {
		input.child("CloneInventory").ifPresent(child -> ContainerHelper.loadAllItems(child, this.main));
		this.selected = input.getIntOr("SelectedSlot", 0);
		this.clone.setItemSlot(EquipmentSlot.MAINHAND, this.main.get(this.selected));
	}
}
