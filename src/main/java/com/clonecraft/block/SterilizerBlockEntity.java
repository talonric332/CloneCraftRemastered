package com.clonecraft.block;

import com.clonecraft.CloneCraft;
import com.clonecraft.dna.DnaItems;
import com.clonecraft.item.NeedleItem;
import com.clonecraft.item.TestTubeItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

public class SterilizerBlockEntity extends BlockEntity {
	private static final int CLEAN_TIME = 600;
	private static final int MAX_DIRTINESS = 32;
	private static final int MAX_ITEMS = 16;

	private final List<ItemStack> items = new ArrayList<>();
	private final List<Integer> timers = new ArrayList<>();
	public int dirtiness;

	public SterilizerBlockEntity(final BlockPos pos, final BlockState state) {
		super(CloneCraft.STERILIZER_BE, pos, state);
	}

	public static boolean hasFire(final Level level, final BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(BlockTags.FIRE) || (below.getBlock() instanceof CampfireBlock && below.getValue(CampfireBlock.LIT));
	}

	private static boolean needsCleaning(final ItemStack stack) {
		boolean needle = stack.getItem() instanceof NeedleItem;
		boolean tube = stack.getItem() instanceof TestTubeItem;
		if (!needle && !tube) {
			return false;
		}
		boolean empty = (needle && ((NeedleItem)stack.getItem()).kind == NeedleItem.Kind.EMPTY) || (tube && ((TestTubeItem)stack.getItem()).kind == TestTubeItem.Kind.EMPTY);
		return !empty || DnaItems.isDirty(stack);
	}

	public void serverTick(final ServerLevel level, final BlockPos pos, final BlockState state) {
		int water = state.getValue(SterilizerBlock.WATER);
		boolean working = (water == 1 || water == 3) && hasFire(level, pos);
		if (!working) {
			if (!this.items.isEmpty()) {
				this.dropItems(false);
			}
			if (water == 3) {
				level.setBlock(pos, state.setValue(SterilizerBlock.WATER, 1), Block.UPDATE_ALL);
			}
			return;
		}

		for (int i = 0; i < this.items.size(); ) {
			int t = this.timers.get(i) - 1;
			if (t <= 0) {
				ItemStack clean = new ItemStack(this.items.get(i).getItem() instanceof NeedleItem ? CloneCraft.NEEDLE : CloneCraft.TEST_TUBE);
				this.eject(level, clean);
				this.items.remove(i);
				this.timers.remove(i);
				this.dirtiness++;
				this.setChanged();
			} else {
				this.timers.set(i, t);
				i++;
			}
		}

		if (this.dirtiness >= MAX_DIRTINESS) {
			this.dropItems(false);
			level.setBlock(pos, state.setValue(SterilizerBlock.WATER, 2), Block.UPDATE_ALL);
			return;
		}

		if (this.items.size() < MAX_ITEMS && this.items.size() + this.dirtiness < MAX_DIRTINESS && level.getGameTime() % 5 == 0) {
			AABB box = new AABB(pos.getX() + 0.1, pos.getY() + 0.3, pos.getZ() + 0.1, pos.getX() + 0.9, pos.getY() + 1.2, pos.getZ() + 0.9);
			for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
				ItemStack stack = entity.getItem();
				if (needsCleaning(stack)) {
					this.items.add(stack.split(1));
					this.timers.add(CLEAN_TIME);
					if (stack.isEmpty()) {
						entity.discard();
					}
					this.setChanged();
					break;
				}
			}
		}

		int wanted = this.items.isEmpty() ? 1 : 3;
		if (water != wanted) {
			level.setBlock(pos, state.setValue(SterilizerBlock.WATER, wanted), Block.UPDATE_ALL);
		}
	}

	private void eject(final Level level, final ItemStack stack) {
		ItemEntity entity = new ItemEntity(level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5, stack);
		entity.setPickUpDelay(10);
		entity.setDeltaMovement(0, 0.5, 0);
		level.addFreshEntity(entity);
	}

	public void dropItems(final boolean cleaned) {
		if (this.level != null) {
			for (ItemStack stack : this.items) {
				this.eject(this.level, stack);
			}
		}
		this.items.clear();
		this.timers.clear();
		this.setChanged();
	}

	@Override
	public void preRemoveSideEffects(final BlockPos pos, final BlockState state) {
		this.dropItems(false);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("dirtiness", this.dirtiness);
		output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), this.items);
		output.putIntArray("timers", this.timers.stream().mapToInt(Integer::intValue).toArray());
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.dirtiness = input.getIntOr("dirtiness", 0);
		this.items.clear();
		this.timers.clear();
		List<ItemStack> loaded = input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
		int[] times = input.getIntArray("timers").orElse(new int[0]);
		for (int i = 0; i < loaded.size(); i++) {
			this.items.add(loaded.get(i));
			this.timers.add(i < times.length ? times[i] : CLEAN_TIME);
		}
	}
}
