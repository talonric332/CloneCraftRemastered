package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/** Finds the closest enemy it can see (by team or the "Entities To Attack" list) and fights it. */
public class AttackEnemiesGoal extends Goal {
	private final CloneEntity clone;
	private int noImprovement;
	private int noSee;
	private int repath;
	private float lastHealth;

	public AttackEnemiesGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.clone.hasOption(CloneOption.FIGHT)) {
			if (this.clone.getTarget() != null && !this.clone.canAttackEntity(this.clone.getTarget())) {
				this.clone.setTarget(null);
			}
			return false;
		}
		LivingEntity target = this.clone.getTarget();
		if (target == null || !target.isAlive() || !this.clone.canAttackEntity(target)) {
			LivingEntity attack = this.closestEnemy(null);
			this.clone.setTarget(attack);
		}
		return this.clone.getTarget() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.clone.getTarget() != null && this.clone.getTarget().isAlive() && this.clone.hasOption(CloneOption.FIGHT);
	}

	@Override
	public void start() {
		this.noImprovement = 0;
		this.noSee = 0;
		this.repath = 0;
	}

	@Override
	public void stop() {
		this.clone.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.clone.getTarget();
		if (target == null || !target.isAlive() || !this.clone.canAttackEntity(target)) {
			this.clone.setTarget(null);
			return;
		}
		this.clone.getLookControl().setLookAt(target, 30.0F, 30.0F);

		if (this.clone.getRandom().nextInt(10) == 0) {
			LivingEntity closer = this.closestEnemy(null);
			if (closer != null && closer != target && this.clone.distanceToSqr(closer) < this.clone.distanceToSqr(target)) {
				this.clone.setTarget(closer);
				target = closer;
			}
		}
		if (--this.repath <= 0 || this.clone.getNavigation().isDone()) {
			this.repath = 4;
			this.clone.getNavigation().moveTo(target, 1.1);
		}

		this.clone.attackWithBestWeapon(target);

		if (this.clone.position().distanceToSqr(this.clone.xo, this.clone.yo, this.clone.zo) < 1.0E-4 && target.getHealth() == this.lastHealth) {
			if (++this.noImprovement > 30) {
				LivingEntity other = this.closestEnemy(target);
				if (other != null) {
					this.noImprovement = 0;
					this.clone.setTarget(other);
				}
			}
		} else {
			this.noImprovement = 0;
		}
		this.lastHealth = target.getHealth();

		if (!this.clone.hasLineOfSight(target)) {
			if (++this.noSee > 100) {
				this.clone.setTarget(this.closestEnemy(target));
				this.noSee = 0;
			}
		} else {
			this.noSee = 0;
		}
	}

	private @Nullable LivingEntity closestEnemy(final @Nullable LivingEntity exclude) {
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : this.clone.level().getEntitiesOfClass(LivingEntity.class, this.clone.getBoundingBox().inflate(32.0))) {
			if (e == exclude || e == this.clone || !e.isAlive()) {
				continue;
			}
			if (!this.clone.shouldProvokeAttack(e) || !this.clone.hasLineOfSight(e)) {
				continue;
			}
			double d = this.clone.distanceToSqr(e);
			if (d < bestDist) {
				bestDist = d;
				best = e;
			}
		}
		return best;
	}
}
