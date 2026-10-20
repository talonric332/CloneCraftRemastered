package com.clonecraft.clone.learning;

import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * One clone's side of learning to fight. Every half second in a fight it describes the situation to the
 * shared {@link CombatBrain}, picks a move, and learns from how the last one went (reinforcement learning:
 * Q-learning, with a small memory of recent moments it replays so it learns from each one more than once).
 *
 * <p>Damage it deals and kills count for a move, damage it takes and dying count against it. Since a move's value
 * includes what usually happens next, it can learn that stepping back now saves damage a moment later.
 */
public final class CombatLearner {
	public static final int DECIDE_EVERY = 10;
	private static final float LEARNING_RATE = 0.02F;
	private static final float FUTURE = 0.8F;
	private static final float DEALT = 0.15F;
	private static final float TAKEN = 0.6F;
	private static final float KILL = 2.0F;
	private static final float DEATH = -4.0F;
	private static final int MEMORY = 256;
	private static final int REPLAYS = 8;

	private record Moment(float[] state, int move, float reward, float @Nullable [] next, boolean @Nullable [] nextAllowed) {}

	private final CloneEntity clone;
	private final List<Moment> memory = new ArrayList<>();
	private int countdown;
	private CombatMove move = CombatMove.PRESS;
	private float @Nullable [] lastState;
	private @Nullable LivingEntity lastTarget;
	private float lastTargetHealth;
	private float lastOwnHealth;
	private float fightDealt;
	private float fightTaken;
	private boolean killed;

	public CombatLearner(final CloneEntity clone) {
		this.clone = clone;
	}

	public CombatMove move() {
		return this.move;
	}

	private CombatBrain brain(final ServerLevel level) {
		return CombatBrains.get(level, this.clone.getOwnerId());
	}

	/** Called every tick of a fight. Returns the move to make. */
	public CombatMove tick(final ServerLevel level, final LivingEntity target) {
		if (--this.countdown <= 0) {
			this.countdown = DECIDE_EVERY;
			this.decide(level, target);
		}
		return this.move;
	}

	/** What happened since the last decision, as a score. */
	private float rewardSince(final @Nullable LivingEntity target) {
		float dealt = 0.0F;
		float bonus = 0.0F;
		if (this.lastTarget != null) {
			if (this.lastTarget != target || !this.lastTarget.isAlive()) {
				if (this.lastTarget.isDeadOrDying()) {
					dealt = this.lastTargetHealth;
					bonus += KILL;
					this.killed = true;
				}
			} else {
				dealt = Math.max(0.0F, this.lastTargetHealth - target.getHealth());
			}
		}
		float taken = Math.max(0.0F, this.lastOwnHealth - this.clone.getHealth());
		this.fightDealt += dealt;
		this.fightTaken += taken;
		return dealt * DEALT - taken * TAKEN + bonus;
	}

	private void decide(final ServerLevel level, final LivingEntity target) {
		CombatBrain brain = this.brain(level);
		float[] state = this.describe(target);
		boolean[] allowed = this.allowed(target);
		if (this.lastState != null) {
			this.learn(brain, new Moment(this.lastState, this.move.ordinal(), this.rewardSince(target), state, allowed));
		}
		this.move = this.choose(brain, state, allowed);
		brain.decisions++;
		brain.moveCounts[this.move.ordinal()]++;
		this.lastState = state;
		this.lastTarget = target;
		this.lastTargetHealth = target.getHealth();
		this.lastOwnHealth = this.clone.getHealth();
	}

	/** The fight is over (the enemy died, got away, or the clone stopped fighting). */
	public void endFight(final ServerLevel level) {
		if (this.lastState == null) {
			return;
		}
		CombatBrain brain = this.brain(level);
		this.learn(brain, new Moment(this.lastState, this.move.ordinal(), this.rewardSince(null), null, null));
		brain.recordFight(this.killed, false, this.fightDealt, this.fightTaken);
		this.reset();
		CombatBrains.changed(level);
	}

	/**
	 * Something bad happened that its fighting let happen (a creeper blew up the house nearby): the move it last
	 * made in that situation counts as that much worse. (If it wasn't fighting, there's nothing to blame.)
	 */
	public void penalize(final ServerLevel level, final float amount) {
		float[] state = this.lastState;
		CombatMove move = this.move;
		// The fight may have just ended (the creeper blew up): blame its last move then, if that was only a moment ago.
		if (state == null && this.endedState != null && this.clone.tickCount - this.endedAt <= 100) {
			state = this.endedState;
			move = this.endedMove;
		}
		if (state == null) {
			return;
		}
		this.learn(this.brain(level), new Moment(state, move.ordinal(), -amount, null, null));
		CombatBrains.changed(level);
	}

	/** The last moment of the last fight, for a little while after it ends. */
	private float @Nullable [] endedState;
	private CombatMove endedMove = CombatMove.PRESS;
	private int endedAt;

	/** It died: whatever it was doing gets the blame. */
	public void died(final ServerLevel level) {
		if (this.lastState == null) {
			return;
		}
		CombatBrain brain = this.brain(level);
		float reward = this.rewardSince(null) - Math.max(0.0F, this.clone.getHealth()) * TAKEN + DEATH;
		this.learn(brain, new Moment(this.lastState, this.move.ordinal(), reward, null, null));
		brain.recordFight(false, true, this.fightDealt, this.fightTaken);
		this.reset();
		CombatBrains.changed(level);
	}

	private void reset() {
		if (this.lastState != null) {
			this.endedState = this.lastState;
			this.endedMove = this.move;
			this.endedAt = this.clone.tickCount;
		}
		this.lastState = null;
		this.lastTarget = null;
		this.fightDealt = 0.0F;
		this.fightTaken = 0.0F;
		this.killed = false;
		this.countdown = 0;
		this.move = CombatMove.PRESS;
	}

	private void learn(final CombatBrain brain, final Moment moment) {
		this.memory.add(moment);
		if (this.memory.size() > MEMORY) {
			this.memory.remove(0);
		}
		train(brain.net, moment);
		for (int k = 0; k < REPLAYS && this.memory.size() > 1; k++) {
			train(brain.net, this.memory.get(this.clone.getRandom().nextInt(this.memory.size())));
		}
	}

	private static void train(final NeuralNet net, final Moment m) {
		float target = m.reward();
		if (m.next() != null) {
			target += FUTURE * best(net.predict(m.next()), m.nextAllowed());
		}
		net.train(m.state(), m.move(), target, LEARNING_RATE);
	}

	private static float best(final float[] values, final boolean @Nullable [] allowed) {
		float best = Float.NEGATIVE_INFINITY;
		for (int k = 0; k < values.length; k++) {
			if ((allowed == null || allowed[k]) && values[k] > best) {
				best = values[k];
			}
		}
		return best == Float.NEGATIVE_INFINITY ? 0.0F : best;
	}

	/** For tests: always make this move (to measure how good each move is on its own). */
	public @Nullable CombatMove forced;

	private CombatMove choose(final CombatBrain brain, final float[] state, final boolean[] allowed) {
		if (this.forced != null) {
			return this.forced;
		}
		CombatMove[] moves = CombatMove.values();
		if (this.clone.getRandom().nextFloat() < brain.exploreChance()) {
			List<CombatMove> options = new ArrayList<>();
			for (CombatMove m : moves) {
				if (allowed[m.ordinal()]) {
					options.add(m);
				}
			}
			return options.get(this.clone.getRandom().nextInt(options.size()));
		}
		float[] values = brain.net.predict(state);
		CombatMove pick = CombatMove.PRESS;
		for (CombatMove m : moves) {
			if (allowed[m.ordinal()] && values[m.ordinal()] > values[pick.ordinal()]) {
				pick = m;
			}
		}
		return pick;
	}

	private boolean hasBowAndArrows() {
		return this.clone.getInventory().find(s -> s.is(Items.BOW)) >= 0 && this.clone.getInventory().find(s -> s.is(ItemTags.ARROWS)) >= 0;
	}

	private boolean[] allowed(final LivingEntity target) {
		boolean[] allowed = new boolean[CombatMove.values().length];
		java.util.Arrays.fill(allowed, true);
		allowed[CombatMove.SHOOT.ordinal()] = this.hasBowAndArrows();
		// Nothing to dodge from an animal that won't fight back (hunting a cow, say): just hit it.
		boolean harmless = !(target instanceof net.minecraft.world.entity.monster.Enemy) && !(target instanceof CloneEntity)
			&& !(target instanceof net.minecraft.world.entity.player.Player)
			&& !(target instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == this.clone);
		if (harmless) {
			allowed[CombatMove.HIT_AND_BACK.ordinal()] = false;
			allowed[CombatMove.CIRCLE.ordinal()] = false;
			allowed[CombatMove.KEEP_CLEAR.ordinal()] = false;
		}
		return allowed;
	}

	/** The situation, as numbers from about 0 to 1, which is what the network sees. */
	public float[] describe(final LivingEntity target) {
		float[] x = new float[CombatBrain.INPUTS];
		x[0] = this.clone.getHealth() / this.clone.getMaxHealth();
		x[1] = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
		x[2] = (float)Math.min(this.clone.distanceTo(target), 16.0) / 16.0F;
		x[3] = this.clone.isAttackReady() ? 1.0F : 0.0F;
		x[4] = target instanceof RangedAttackMob ? 1.0F : 0.0F;
		x[5] = target instanceof Creeper ? 1.0F : 0.0F;
		x[6] = target instanceof Creeper creeper ? creeper.getSwelling(1.0F) : 0.0F;
		int monsters = this.clone.level().getEntitiesOfClass(Monster.class, this.clone.getBoundingBox().inflate(8.0), LivingEntity::isAlive).size();
		x[7] = Math.min(monsters, 4) / 4.0F;
		x[8] = this.hasBowAndArrows() ? 1.0F : 0.0F;
		x[9] = Math.min(this.clone.getArmorValue(), 20) / 20.0F;
		x[10] = target instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == this.clone ? 1.0F : 0.0F;
		x[11] = (float)Math.min(this.clone.getAttributeValue(Attributes.ATTACK_DAMAGE), 10.0) / 10.0F;
		x[12] = this.clone.hurtTime / 10.0F;
		return x;
	}
}
