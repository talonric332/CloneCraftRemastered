package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** With "Curious" on, the clone looks at nearby people and creatures, at chests it knows about, or just around. */
public class LookIdleGoal extends Goal {
	private final CloneEntity clone;
	private @Nullable LivingEntity watch;
	private @Nullable BlockPos lookBlock;
	private double lookX;
	private double lookY;
	private double lookZ;
	private int idleTime;

	public LookIdleGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.clone.hasOption(CloneOption.CURIOUS) && !this.clone.buildGoal.isRunning() && this.clone.getRandom().nextFloat() < 0.1F;
	}

	@Override
	public boolean canContinueToUse() {
		return this.idleTime >= 0;
	}

	@Override
	public void start() {
		this.watch = null;
		this.lookBlock = null;
		this.idleTime = -1;
		if (this.clone.getRandom().nextFloat() > 0.3F) {
			LivingEntity closest = null;
			boolean player = false;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : this.clone.level().getEntitiesOfClass(LivingEntity.class, this.clone.getBoundingBox().inflate(8, 3, 8))) {
				if (e == this.clone || !this.clone.hasLineOfSight(e)) {
					continue;
				}
				boolean isPlayer = e instanceof Player && this.clone.getRandom().nextFloat() > 0.7F;
				double d = e.distanceToSqr(this.clone);
				if (closest == null || (isPlayer && !player) || (d < best && (!player || isPlayer))) {
					closest = e;
					best = d;
					player = isPlayer;
				}
			}
			if (closest != null) {
				this.watch = closest;
				this.idleTime = 100 + this.clone.getRandom().nextInt(60);
				return;
			}
		}
		if (this.clone.getRandom().nextFloat() < 0.05F && !this.clone.knownContainers.isEmpty()) {
			BlockPos nearest = null;
			double best = 64;
			for (BlockPos p : this.clone.knownContainers) {
				double d = this.clone.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
				if (d < best) {
					best = d;
					nearest = p;
				}
			}
			if (nearest != null) {
				this.lookBlock = nearest;
				this.idleTime = 40;
				return;
			}
		}
		double angle = Math.PI * 2 * this.clone.getRandom().nextDouble();
		this.lookX = Math.cos(angle);
		this.lookZ = Math.sin(angle);
		this.lookY = Math.max(0, this.clone.getRandom().nextFloat() - 0.5F);
		this.idleTime = 20 + this.clone.getRandom().nextInt(20);
	}

	@Override
	public void tick() {
		this.idleTime--;
		if (this.watch != null) {
			this.clone.getLookControl().setLookAt(this.watch, 10.0F, this.clone.getMaxHeadXRot());
		} else if (this.lookBlock != null) {
			this.clone.getLookControl().setLookAt(this.lookBlock.getX() + 0.5, this.lookBlock.getY() + 0.5, this.lookBlock.getZ() + 0.5);
		} else {
			this.clone.getLookControl().setLookAt(this.clone.getX() + this.lookX, this.clone.getEyeY() + this.lookY, this.clone.getZ() + this.lookZ);
		}
	}
}
