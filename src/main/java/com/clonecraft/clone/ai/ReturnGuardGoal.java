package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/** With "Guard Position" on, the clone returns to the spot it was standing on when guarding started. */
public class ReturnGuardGoal extends Goal {
	private final CloneEntity clone;
	private int ticks;

	public ReturnGuardGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!this.clone.hasOption(CloneOption.GUARD) || this.clone.getGuardPos() == null) {
			return false;
		}
		this.checkGuardPos();
		return this.clone.getGuardPos() != null && this.distanceSq() > 0.5;
	}

	@Override
	public boolean canContinueToUse() {
		return this.clone.hasOption(CloneOption.GUARD) && this.clone.getGuardPos() != null && this.distanceSq() > 0.5;
	}

	@Override
	public void start() {
		this.ticks = 0;
	}

	private double distanceSq() {
		BlockPos pos = this.clone.getGuardPos();
		return this.clone.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
	}

	private void checkGuardPos() {
		BlockPos pos = this.clone.getGuardPos();
		Level level = this.clone.level();
		if (!level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
			this.clone.setGuardPos(null);
			this.clone.say("My guard position has been compromised. The floor is gone!", this.clone.getOwnerPlayer());
		} else if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			|| !level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty()) {
			this.clone.setGuardPos(null);
			this.clone.say("My guard position has been compromised. There are blocks in the way!", this.clone.getOwnerPlayer());
		}
	}

	@Override
	public void tick() {
		if (++this.ticks % 10 != 0) {
			return;
		}
		this.checkGuardPos();
		BlockPos pos = this.clone.getGuardPos();
		if (pos == null) {
			return;
		}
		if (this.distanceSq() > 1600 || this.ticks > 400) {
			this.clone.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, this.clone.getYRot(), this.clone.getXRot());
			this.clone.getNavigation().stop();
		} else {
			this.clone.getNavigation().moveTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 1.0);
		}
	}
}
