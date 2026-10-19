package com.clonecraft.clone.ai;

import com.clonecraft.entity.CloneEntity;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Runs the "kill N zombies" chat command: hunts down that many of the named creatures, then reports back. */
public class CommandGoal extends Goal {
	private final CloneEntity clone;
	private @Nullable Set<EntityType<?>> types;
	private int remaining;
	private @Nullable UUID commander;
	private @Nullable LivingEntity lastAttacked;

	public CommandGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.noneOf(Goal.Flag.class));
	}

	public void setKillTask(final Set<EntityType<?>> types, final int amount, final Player commander) {
		this.types = types;
		this.remaining = amount;
		this.commander = commander.getUUID();
		this.lastAttacked = null;
	}

	public void clear() {
		this.types = null;
		this.remaining = 0;
		this.lastAttacked = null;
	}

	@Override
	public boolean canUse() {
		return this.types != null && this.remaining > 0;
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (this.types == null) {
			return;
		}
		if (this.lastAttacked != null && !this.lastAttacked.isAlive()) {
			this.remaining--;
			this.lastAttacked = null;
			if (this.remaining <= 0) {
				this.clear();
				this.clone.setTarget(null);
				this.clone.getNavigation().stop();
				Player p = this.commander == null ? null : this.clone.level().getPlayerByUUID(this.commander);
				this.clone.say("Okay! I'm done!", p);
				return;
			}
		}
		LivingEntity target = this.clone.getTarget();
		if (target == null || !target.isAlive() || !this.types.contains(target.getType())) {
			LivingEntity best = null;
			double bestDist = Double.MAX_VALUE;
			for (LivingEntity e : this.clone.level().getEntitiesOfClass(LivingEntity.class, this.clone.getBoundingBox().inflate(32, 16, 32))) {
				if (e != this.clone && e.isAlive() && this.types.contains(e.getType()) && this.clone.hasLineOfSight(e)) {
					double d = e.distanceToSqr(this.clone);
					if (d < bestDist) {
						bestDist = d;
						best = e;
					}
				}
			}
			if (best != null) {
				this.clone.setTarget(best);
				target = best;
			}
		}
		if (target != null && target.isAlive() && this.types.contains(target.getType())) {
			this.lastAttacked = target;
		}
	}
}
