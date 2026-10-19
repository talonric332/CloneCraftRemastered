package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * With "Smart Memory" on, clones remember where chests and barrels are. When the build AI runs out of a block,
 * the clone walks to a remembered container that has it and takes some out.
 */
public class FetchItemGoal extends Goal {
	private final CloneEntity clone;
	private @Nullable Predicate<ItemStack> wanted;
	private @Nullable BlockPos container;
	private int ticks;

	public FetchItemGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	/** Asks the clone to go and get an item. Returns false if it doesn't know anywhere that has one. */
	public boolean fetch(final Predicate<ItemStack> wanted) {
		if (!this.clone.hasOption(CloneOption.SMART_MEMORY)) {
			return false;
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : this.clone.knownContainers) {
			if (this.clone.level().getBlockEntity(pos) instanceof Container c && this.slotWith(c, wanted) >= 0) {
				double d = this.clone.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				if (d < bestDist) {
					bestDist = d;
					best = pos;
				}
			}
		}
		if (best == null) {
			return false;
		}
		this.wanted = wanted;
		this.container = best;
		return true;
	}

	public boolean isFetching() {
		return this.wanted != null;
	}

	private int slotWith(final Container container, final Predicate<ItemStack> test) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (!container.getItem(i).isEmpty() && test.test(container.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean canUse() {
		return this.wanted != null && this.container != null;
	}

	@Override
	public void start() {
		this.ticks = 0;
	}

	@Override
	public void tick() {
		if (this.container == null || this.wanted == null) {
			return;
		}
		if (++this.ticks > 600) {
			this.done();
			return;
		}
		double dist = this.clone.distanceToSqr(this.container.getX() + 0.5, this.container.getY() + 0.5, this.container.getZ() + 0.5);
		if (dist > 9.0) {
			if (this.ticks % 10 == 1) {
				this.clone.getNavigation().moveTo(this.container.getX() + 0.5, this.container.getY(), this.container.getZ() + 0.5, 1.0);
			}
			return;
		}
		this.clone.getLookControl().setLookAt(this.container.getX() + 0.5, this.container.getY() + 0.5, this.container.getZ() + 0.5);
		if (this.clone.level().getBlockEntity(this.container) instanceof Container c) {
			this.clone.level().playSound(null, this.container, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
			for (int taken = 0; taken < 64; ) {
				int slot = this.slotWith(c, this.wanted);
				if (slot < 0) {
					break;
				}
				ItemStack stack = c.getItem(slot);
				int before = stack.getCount();
				ItemStack moving = stack.copyWithCount(Math.min(before, 64 - taken));
				int added = this.clone.getInventory().add(moving);
				if (added <= 0) {
					break;
				}
				stack.shrink(added);
				taken += added;
				c.setChanged();
			}
			this.clone.level().playSound(null, this.container, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
		}
		this.done();
	}

	private void done() {
		this.wanted = null;
		this.container = null;
		this.clone.getNavigation().stop();
	}

	@Override
	public boolean canContinueToUse() {
		return this.wanted != null && this.container != null;
	}
}
