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
		Vec3 pos = this.pickSpot();
		if (pos == null) {
			return false;
		}
		this.clone.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.0);
		return true;
	}

	/** A random spot nearby. Out under the open sky, it stays under the open sky rather than strolling into a cave. */
	private @org.jspecify.annotations.Nullable Vec3 pickSpot() {
		if (!(this.clone.level() instanceof net.minecraft.server.level.ServerLevel level) || level.dimensionType().hasCeiling()) {
			return DefaultRandomPos.getPos(this.clone, 10, 7);
		}
		net.minecraft.core.BlockPos feet = this.clone.blockPosition();
		boolean outside = ProgressionGoal.surfaceAt(level, feet.getX(), feet.getZ()) <= feet.getY() + 1;
		Vec3 any = null;
		for (int tries = 0; tries < 8; tries++) {
			Vec3 pos = DefaultRandomPos.getPos(this.clone, 10, 7);
			if (pos == null) {
				continue;
			}
			net.minecraft.core.BlockPos spot = net.minecraft.core.BlockPos.containing(pos);
			if (!outside || !level.isLoaded(spot) || ProgressionGoal.surfaceAt(level, spot.getX(), spot.getZ()) <= spot.getY() + 1) {
				return pos;
			}
			any = any == null ? pos : any;
		}
		// Only cave spots nearby: stay put this time rather than head underground.
		return outside ? null : any;
	}

	@Override
	public boolean canContinueToUse() {
		return !this.clone.getNavigation().isDone() && this.clone.hasOption(CloneOption.WANDER) && !this.clone.hasOption(CloneOption.GUARD);
	}
}
