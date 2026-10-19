package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/** With "Wander" on, the clone strolls around aimlessly. */
public class WanderGoal extends Goal {
	private final CloneEntity clone;

	public WanderGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!this.clone.hasOption(CloneOption.WANDER) || this.clone.hasOption(CloneOption.GUARD) || this.clone.getRandom().nextInt(120) != 0) {
			return false;
		}
		Vec3 pos = DefaultRandomPos.getPos(this.clone, 10, 7);
		if (pos == null) {
			return false;
		}
		this.clone.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.0);
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return !this.clone.getNavigation().isDone() && this.clone.hasOption(CloneOption.WANDER) && !this.clone.hasOption(CloneOption.GUARD);
	}
}
