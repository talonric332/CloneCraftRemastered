package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import org.jspecify.annotations.Nullable;

/** With "Walk To Items" on, the clone walks over to nearby items and experience orbs to collect them. */
public class WalkToItemsGoal extends Goal {
	private final CloneEntity clone;
	private @Nullable Entity item;
	private int counter;

	public WalkToItemsGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	private boolean allowed() {
		return this.clone.hasOption(CloneOption.PICKUP) && this.clone.hasOption(CloneOption.WALK_TO_ITEMS) && this.clone.getTarget() == null;
	}

	@Override
	public boolean canUse() {
		if (!this.allowed() || !this.clone.getNavigation().isDone()) {
			return false;
		}
		this.item = null;
		double best = Double.MAX_VALUE;
		for (Entity e : this.clone.level().getEntities(this.clone, this.clone.getBoundingBox().inflate(16, 8, 16),
			e -> (e instanceof ItemEntity ie && !ie.hasPickUpDelay() && this.clone.getInventory().fitCount(ie.getItem()) > 0
				&& com.clonecraft.clone.DropClaims.mayTake(ie, this.clone)) || e instanceof ExperienceOrb)) {
			if (!this.clone.hasLineOfSight(e)) {
				continue;
			}
			double d = this.clone.distanceToSqr(e);
			if (d < best) {
				best = d;
				this.item = e;
			}
		}
		return this.item != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.allowed() && this.item != null && this.item.isAlive() && this.counter < 1000;
	}

	@Override
	public void start() {
		this.counter = 0;
	}

	@Override
	public void stop() {
		this.item = null;
	}

	@Override
	public void tick() {
		if (this.item != null && this.counter++ % 10 == 0) {
			this.clone.getNavigation().moveTo(this.item.getX(), this.item.getY(), this.item.getZ(), 1.0);
		}
	}
}
