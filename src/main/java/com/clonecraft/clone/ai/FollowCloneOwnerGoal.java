package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** With "Follow" on, the clone walks after its owner and teleports to them if left 30 blocks behind. */
public class FollowCloneOwnerGoal extends Goal {
	private final CloneEntity clone;
	private @Nullable Player owner;
	private int recalc;

	public FollowCloneOwnerGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private boolean allowed() {
		return this.clone.hasOption(CloneOption.FOLLOW) && !this.clone.hasOption(CloneOption.GUARD)
			&& !this.clone.buildGoal.isRunning() && !this.clone.isIndependent();
	}

	@Override
	public boolean canUse() {
		if (!this.allowed()) {
			return false;
		}
		Player owner = this.clone.getOwnerPlayer();
		if (owner == null || owner.isSpectator() || this.clone.distanceToSqr(owner) <= 50.0) {
			return false;
		}
		this.owner = owner;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.owner != null && this.owner.isAlive() && this.allowed() && this.clone.distanceToSqr(this.owner) > 16.0;
	}

	@Override
	public void start() {
		this.recalc = 0;
	}

	@Override
	public void stop() {
		this.owner = null;
		this.clone.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (this.owner == null) {
			return;
		}
		this.clone.getLookControl().setLookAt(this.owner, 16.0F, this.clone.getMaxHeadXRot());
		if (--this.recalc > 0) {
			return;
		}
		this.recalc = 10;
		if (this.clone.distanceToSqr(this.owner) > 900.0) {
			this.teleportNear(this.owner);
		} else {
			double x = this.owner.getX() + this.clone.getRandom().nextInt(4) - 2;
			double z = this.owner.getZ() + this.clone.getRandom().nextInt(4) - 2;
			this.clone.getNavigation().moveTo(x, this.owner.getY(), z, 1.0);
		}
	}

	private void teleportNear(final Player owner) {
		BlockPos base = owner.blockPosition();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) < 1 && Math.abs(dz) < 1) {
					continue;
				}
				BlockPos pos = base.offset(dx, 0, dz);
				var level = this.clone.level();
				if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
					&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
					&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
					this.clone.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, this.clone.getYRot(), this.clone.getXRot());
					this.clone.getNavigation().stop();
					return;
				}
			}
		}
	}
}
