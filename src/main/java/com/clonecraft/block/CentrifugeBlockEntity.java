package com.clonecraft.block;

import com.clonecraft.CloneCraft;
import com.clonecraft.menu.CentrifugeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CentrifugeBlockEntity extends BaseContainerBlockEntity {
	public static final int SPIN_TIME = 1600;
	public static final int FUEL_SLOT = 0;

	private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
	private int burnTime;
	private int burnTotal;
	private int spinTime;
	private boolean spinning;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(final int id) {
			return switch (id) {
				case 0 -> CentrifugeBlockEntity.this.burnTime;
				case 1 -> CentrifugeBlockEntity.this.burnTotal;
				case 2 -> CentrifugeBlockEntity.this.spinTime;
				case 3 -> CentrifugeBlockEntity.this.spinning ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(final int id, final int value) {
			switch (id) {
				case 0 -> CentrifugeBlockEntity.this.burnTime = value;
				case 1 -> CentrifugeBlockEntity.this.burnTotal = value;
				case 2 -> CentrifugeBlockEntity.this.spinTime = value;
				case 3 -> CentrifugeBlockEntity.this.spinning = value != 0;
			}
		}

		@Override
		public int getCount() {
			return 4;
		}
	};

	public CentrifugeBlockEntity(final BlockPos pos, final BlockState state) {
		super(CloneCraft.CENTRIFUGE_BE, pos, state);
	}

	public static boolean isFuel(final ItemStack stack) {
		return stack.has(DataComponents.COOKING_FUEL);
	}

	public static int fuelTime(final ItemStack stack) {
		if (stack.is(Items.LAVA_BUCKET)) {
			return 20000;
		}
		if (stack.is(Items.COAL_BLOCK)) {
			return 16000;
		}
		if (stack.is(Items.BLAZE_ROD)) {
			return 2400;
		}
		if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return 1600;
		}
		return isFuel(stack) ? 300 : 0;
	}

	public static boolean isBlood(final ItemStack stack) {
		return stack.is(CloneCraft.BLOOD_TEST_TUBE);
	}

	private boolean hasBlood() {
		int blood = 0;
		for (int i = 1; i < 9; i++) {
			ItemStack s = this.items.get(i);
			if (s.isEmpty()) {
				continue;
			}
			if (isBlood(s)) {
				blood++;
			} else if (!s.is(CloneCraft.SEPARATED_TEST_TUBE)) {
				return false;
			}
		}
		return blood > 0;
	}

	/** The Spin / Stop button (and redstone pulses). */
	public void toggleSpin() {
		if (this.spinning) {
			this.spinning = false;
			this.spinTime = 0;
		} else if (this.hasBlood() && (this.burnTime > 0 || fuelTime(this.items.get(FUEL_SLOT)) > 0)) {
			this.spinning = true;
		}
		this.setChanged();
	}

	public void serverTick(final ServerLevel level, final BlockPos pos, final BlockState state) {
		if (this.burnTime > 0) {
			this.burnTime--;
		}
		if (this.spinning) {
			if (!this.hasBlood()) {
				this.spinning = false;
				this.spinTime = 0;
			} else {
				if (this.burnTime <= 0) {
					ItemStack fuel = this.items.get(FUEL_SLOT);
					int time = fuelTime(fuel);
					if (time > 0) {
						this.burnTime = this.burnTotal = time;
						ItemStack remainder = fuel.getItem().getCraftingRemainder() == null ? ItemStack.EMPTY : fuel.getItem().getCraftingRemainder().create();
						fuel.shrink(1);
						if (fuel.isEmpty()) {
							this.items.set(FUEL_SLOT, remainder);
						}
					} else {
						this.spinning = false;
						this.spinTime = 0;
					}
				}
				if (this.spinning && ++this.spinTime >= SPIN_TIME) {
					for (int i = 1; i < 9; i++) {
						ItemStack s = this.items.get(i);
						if (isBlood(s)) {
							ItemStack separated = new ItemStack(CloneCraft.SEPARATED_TEST_TUBE);
							separated.applyComponents(s.getComponentsPatch());
							this.items.set(i, separated);
						}
					}
					this.spinning = false;
					this.spinTime = 0;
					level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F, 1.2F);
				}
			}
			this.setChanged();
		}
		if (state.getValue(CentrifugeBlock.SPINNING) != this.spinning) {
			level.setBlock(pos, state.setValue(CentrifugeBlock.SPINNING, this.spinning), Block.UPDATE_ALL);
		}
	}

	public boolean isSpinning() {
		return this.spinning;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.clonecraft.centrifuge");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(final NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return 9;
	}

	@Override
	public boolean canPlaceItem(final int slot, final ItemStack stack) {
		if (slot == FUEL_SLOT) {
			return isFuel(stack);
		}
		return isBlood(stack) && this.items.get(slot).isEmpty() && !this.spinning;
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	@Override
	protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
		return new CentrifugeMenu(containerId, inventory, this, this.data);
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.burnTime = input.getIntOr("BurnTime", 0);
		this.burnTotal = input.getIntOr("BurnTotal", 0);
		this.spinTime = input.getIntOr("SpinTime", 0);
		this.spinning = input.getBooleanOr("Spinning", false);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("BurnTime", this.burnTime);
		output.putInt("BurnTotal", this.burnTotal);
		output.putInt("SpinTime", this.spinTime);
		output.putBoolean("Spinning", this.spinning);
	}
}
