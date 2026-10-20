package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.learning.CombatMove;
import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Finds the closest enemy it can see (by team or the "Entities To Attack" list) and fights it. */
public class AttackEnemiesGoal extends Goal {
	private final CloneEntity clone;
	private int noImprovement;
	private int noSee;
	private int repath;
	private float lastHealth;
	private int critWait;

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
		if (this.clone.level() instanceof ServerLevel level) {
			this.clone.combat.endFight(level);
		}
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
		if (this.clone.level() instanceof ServerLevel level) {
			this.perform(this.clone.combat.tick(level, target), target);
		}

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

	// ---------------------------------------------------------------- moves (chosen by the clone's combat brain)

	private void perform(final CombatMove move, final LivingEntity target) {
		if (move != CombatMove.CRIT) {
			this.critWait = 0;
		}
		switch (move) {
			case PRESS -> {
				this.approach(target);
				this.clone.attackWithBestWeapon(target);
			}
			case CRIT -> {
				this.approach(target);
				// Jump from just outside reach, so it's in reach on the way down.
				boolean close = this.clone.distanceTo(target) < CloneEntity.REACH + 1.0;
				if (close && this.clone.isAttackReady() && this.clone.onGround()) {
					// Can't jump (in water, under a ceiling...)? Don't wait forever: just hit.
					if (++this.critWait > 15) {
						this.clone.tryMeleeHit(target);
					} else {
						this.clone.jumpFromGround();
					}
				} else if (!this.clone.onGround() && this.clone.getDeltaMovement().y < 0.0) {
					// Falling: the hit is a critical one.
					if (this.clone.tryMeleeHit(target)) {
						this.critWait = 0;
					}
				}
			}
			case HIT_AND_BACK -> {
				// Its reach is longer than a zombie's: hit from the edge of it, then step straight back out while recharging.
				if (this.clone.isAttackReady()) {
					this.approach(target);
					this.clone.tryMeleeHit(target);
				} else {
					this.stepBack(target, CloneEntity.REACH + 1.0);
				}
			}
			case CIRCLE -> {
				if (this.clone.isAttackReady() && this.clone.tryMeleeHit(target)) {
					return;
				}
				this.circle(target);
			}
			case SHOOT -> {
				double dist = this.clone.distanceTo(target);
				if (dist < 5.0) {
					this.backAway(target, 6.0);
				} else if (dist > 16.0) {
					this.approach(target);
				} else {
					this.clone.getNavigation().stop();
				}
				this.clone.attackWithBestWeapon(target);
			}
			case KEEP_CLEAR -> this.backAway(target, 8.0);
		}
	}

	private void approach(final LivingEntity target) {
		if (--this.repath <= 0 || this.clone.getNavigation().isDone()) {
			this.repath = 4;
			this.clone.getNavigation().moveTo(target, 1.1);
		}
	}

	private void backAway(final LivingEntity target, final double far) {
		if (this.clone.distanceTo(target) >= far) {
			this.clone.getNavigation().stop();
			return;
		}
		if (--this.repath <= 0 || this.clone.getNavigation().isDone()) {
			this.repath = 8;
			Vec3 to = DefaultRandomPos.getPosAway(this.clone, 8, 4, target.position());
			if (to != null) {
				this.clone.getNavigation().moveTo(to.x, to.y, to.z, 1.2);
			}
		}
	}

	/** A quick step straight back from the enemy (keeping facing it), to just outside its reach. */
	private void stepBack(final LivingEntity target, final double far) {
		if (this.clone.distanceTo(target) >= far) {
			this.clone.getNavigation().stop();
			return;
		}
		if (--this.repath <= 0 || this.clone.getNavigation().isDone()) {
			this.repath = 3;
			Vec3 away = this.clone.position().subtract(target.position());
			away = new Vec3(away.x, 0.0, away.z);
			if (away.lengthSqr() < 1.0E-3) {
				away = new Vec3(1.0, 0.0, 0.0);
			}
			Vec3 to = this.clone.position().add(away.normalize().scale(2.5));
			if (!this.clone.getNavigation().moveTo(to.x, to.y, to.z, 1.3)) {
				// Wall behind it: back away sideways-ish instead.
				this.backAway(target, far);
			}
		}
	}

	/** Moves round the enemy, a bit further each time, staying close enough to hit. */
	private void circle(final LivingEntity target) {
		if (--this.repath <= 0 || this.clone.getNavigation().isDone()) {
			this.repath = 6;
			Vec3 from = this.clone.position().subtract(target.position());
			from = new Vec3(from.x, 0.0, from.z);
			if (from.lengthSqr() < 1.0E-3) {
				from = new Vec3(1.0, 0.0, 0.0);
			}
			float turn = (this.clone.getId() % 2 == 0 ? 1.0F : -1.0F) * 0.9F;
			Vec3 to = target.position().add(from.normalize().yRot(turn).scale(2.5));
			this.clone.getNavigation().moveTo(to.x, to.y, to.z, 1.15);
		}
	}

	/**
	 * On Game Progression a clone looks after itself and its home like a player would: it fights hostile monsters
	 * that come close (or come near the house), creepers included, even if they aren't on its "Entities To Attack"
	 * list. Not neutral ones (endermen, zombified piglins...) unless they start it.
	 */
	private boolean defendsAgainst(final LivingEntity e) {
		if (!this.clone.hasOption(CloneOption.GAME_PROGRESSION) || !(e instanceof net.minecraft.world.entity.monster.Enemy)
			|| e instanceof net.minecraft.world.entity.NeutralMob) {
			return false;
		}
		if (this.clone.distanceToSqr(e) <= 12.0 * 12.0) {
			return true;
		}
		if (this.clone.level() instanceof ServerLevel level) {
			var home = com.clonecraft.clone.progression.HomeBase.get(level, this.clone.getOwnerId());
			return home != null && home.bounds().inflate(12.0).contains(e.position());
		}
		return false;
	}

	private @Nullable LivingEntity closestEnemy(final @Nullable LivingEntity exclude) {
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : this.clone.level().getEntitiesOfClass(LivingEntity.class, this.clone.getBoundingBox().inflate(32.0))) {
			if (e == exclude || e == this.clone || !e.isAlive()) {
				continue;
			}
			if (!(this.clone.shouldProvokeAttack(e) || this.defendsAgainst(e)) || !this.clone.hasLineOfSight(e)) {
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
