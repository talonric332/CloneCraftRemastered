package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A clone on Game Progression that's badly hurt with monsters about stops fighting and runs away (sprinting) until
 * it's out of reach or has healed up a bit, instead of fighting to the death.
 */
public class FleeGoal extends Goal {
	/** Run below this share of its health... */
	private static final float FLEE_BELOW = 0.35F;
	/** ...and stop once healed past this, or once no monster is this close. */
	private static final float SAFE_ABOVE = 0.6F;
	private static final double DANGER_RANGE = 10.0;
	private static final double SAFE_RANGE = 16.0;

	private final CloneEntity clone;
	private @Nullable Vec3 runTo;
	/** When cornered with nowhere to run, it fights back until this tick instead. */
	private int fightBackUntil;
	private int stuckTicks;

	public FleeGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.TARGET));
	}

	private List<Monster> monstersWithin(final double range) {
		return this.clone.level().getEntitiesOfClass(Monster.class, this.clone.getBoundingBox().inflate(range), m -> m.isAlive());
	}

	@Override
	public boolean canUse() {
		return this.clone.tickCount >= this.fightBackUntil && this.clone.hasOption(CloneOption.GAME_PROGRESSION) && this.clone.getHealth() < this.clone.getMaxHealth() * FLEE_BELOW
			&& !this.monstersWithin(DANGER_RANGE).isEmpty();
	}

	@Override
	public boolean canContinueToUse() {
		return this.clone.tickCount >= this.fightBackUntil && this.clone.hasOption(CloneOption.GAME_PROGRESSION) && this.clone.getHealth() < this.clone.getMaxHealth() * SAFE_ABOVE
			&& !this.monstersWithin(SAFE_RANGE).isEmpty();
	}

	@Override
	public void start() {
		this.clone.setTarget(null);
		this.runTo = null;
		this.stuckTicks = 0;
		this.clone.showMessage("Running away!");
	}

	@Override
	public void stop() {
		this.clone.setSprinting(false);
		this.clone.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.clone.setTarget(null);
		this.clone.setSprinting(true);
		// Cornered: a monster right next to it and it isn't getting anywhere. Fight back for a couple of seconds.
		Monster closest = this.monstersWithin(3.0).stream()
			.min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(this.clone))).orElse(null);
		this.stuckTicks = closest != null && this.clone.distanceTo(closest) < 2.0 ? this.stuckTicks + 1 : 0;
		if (this.stuckTicks > 20 && closest != null) {
			this.fightBackUntil = this.clone.tickCount + 40;
			this.clone.setTarget(closest);
			this.clone.showMessage("Cornered!");
			return;
		}
		if (this.runTo != null && !this.clone.getNavigation().isDone()) {
			return;
		}
		// Away from the middle of the monsters around it.
		List<Monster> monsters = this.monstersWithin(SAFE_RANGE);
		if (monsters.isEmpty()) {
			return;
		}
		Vec3 danger = Vec3.ZERO;
		for (Monster monster : monsters) {
			danger = danger.add(monster.position());
		}
		danger = danger.scale(1.0 / monsters.size());
		this.runTo = DefaultRandomPos.getPosAway(this.clone, 16, 7, danger);
		if (this.runTo == null) {
			this.runTo = DefaultRandomPos.getPosAway(this.clone, 8, 4, danger);
		}
		if (this.runTo != null && this.clone.getNavigation().moveTo(this.runTo.x, this.runTo.y, this.runTo.z, 1.4)) {
			return;
		}
		// Nowhere to path to: head away from them, but only over solid ground (never off a cliff or into lava).
		Vec3 away = this.clone.position().subtract(danger);
		away = new Vec3(away.x, 0.0, away.z);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(this.clone.getRandom().nextDouble() - 0.5, 0.0, this.clone.getRandom().nextDouble() - 0.5);
		}
		away = away.normalize();
		this.runTo = null;
		for (double angle : new double[]{0, 45, -45, 90, -90}) {
			Vec3 dir = away.yRot((float)Math.toRadians(angle));
			Vec3 to = this.clone.position().add(dir.scale(4.0));
			if (this.safeToStand(net.minecraft.core.BlockPos.containing(to))) {
				this.clone.getMoveControl().setWantedPosition(to.x, this.clone.getY(), to.z, 1.4);
				return;
			}
		}
	}

	/** Room to stand (feet and head clear) with solid ground no more than 2 blocks below, and no lava. */
	private boolean safeToStand(final net.minecraft.core.BlockPos pos) {
		var level = this.clone.level();
		if (!level.isLoaded(pos) || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
			|| !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
			return false;
		}
		for (int down = 1; down <= 3; down++) {
			var below = pos.below(down);
			var state = level.getBlockState(below);
			if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
				return false;
			}
			if (!state.getCollisionShape(level, below).isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
