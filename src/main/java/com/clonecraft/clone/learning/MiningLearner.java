package com.clonecraft.clone.learning;

import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * One clone's side of learning to mine. Each time it starts a new stretch of tunnel it asks the shared
 * {@link MiningBrain} which {@link MiningMove} to make, counts the ore it actually mines, and when the stretch is over
 * teaches the brain how much ore per minute that move turned up in that situation. (Each choice is judged on its own
 * result, so this is the simplest kind of reinforcement learning: a "contextual bandit".)
 */
public final class MiningLearner {
	private static final float LEARNING_RATE = 0.03F;
	/** Ore value per minute is divided by this before the network sees it, to keep its numbers small. */
	public static final float SCALE = 5.0F;
	private static final int MEMORY = 64;
	private static final int REPLAYS = 8;

	private record Stretch(float[] state, int move, float target) {}

	private final CloneEntity clone;
	private final List<Stretch> memory = new ArrayList<>();
	/** Ore it has started mining this stretch, and what each is worth once it's actually dug out. */
	private final Map<BlockPos, Float> pending = new HashMap<>();
	private float @Nullable [] state;
	private MiningMove move = MiningMove.TUNNEL;
	private long startTick;
	private float value;
	private float lastPerMinute;
	/** For tests: always make this move. */
	public @Nullable MiningMove forced;

	public MiningLearner(final CloneEntity clone) {
		this.clone = clone;
	}

	public MiningBrain brain(final ServerLevel level) {
		return CombatBrains.MINING.get(level, this.clone.getOwnerId());
	}

	public boolean inStretch() {
		return this.state != null;
	}

	public MiningMove move() {
		return this.move;
	}

	/** How long the current stretch has been going, in ticks. */
	public long stretchTicks() {
		return this.state == null ? 0 : this.clone.level().getGameTime() - this.startTick;
	}

	private MiningMove lastMove = MiningMove.TUNNEL;

	/** How the last finished stretch went: the move, and the ore value per minute it turned up. */
	public MiningMove lastMove() {
		return this.lastMove;
	}

	public float lastPerMinute() {
		return this.lastPerMinute;
	}

	/** Whether each of the last few stretches found any ore. */
	private final java.util.ArrayDeque<Boolean> recentHits = new java.util.ArrayDeque<>();

	/** How many of its last few stretches found any ore (0 to 1). */
	public float recentHitRate() {
		if (this.recentHits.isEmpty()) {
			return 0.5F;
		}
		return (float)this.recentHits.stream().filter(b -> b).count() / this.recentHits.size();
	}

	/** Ore value per minute in the last stretch, scaled down (one of the things the brain sees). */
	public float lastResult() {
		return Math.min(1.0F, this.lastPerMinute / (SCALE * 4.0F));
	}

	/** Starts a stretch: picks a move for this situation from those allowed. */
	public MiningMove start(final ServerLevel level, final float[] situation, final boolean[] allowed) {
		this.end(level);
		MiningBrain brain = this.brain(level);
		MiningMove pick;
		if (this.forced != null && allowed[this.forced.ordinal()]) {
			pick = this.forced;
		} else if (this.clone.getRandom().nextFloat() < brain.exploreChance()) {
			List<MiningMove> options = new ArrayList<>();
			for (MiningMove m : MiningMove.values()) {
				if (allowed[m.ordinal()]) {
					options.add(m);
				}
			}
			pick = options.get(this.clone.getRandom().nextInt(options.size()));
		} else {
			// The best rated, with a bonus for moves it hasn't tried much.
			float[] values = brain.net.predict(situation);
			pick = null;
			float best = Float.NEGATIVE_INFINITY;
			for (MiningMove m : MiningMove.values()) {
				float score = values[m.ordinal()] + brain.bonus(m);
				if (allowed[m.ordinal()] && score > best) {
					best = score;
					pick = m;
				}
			}
			if (pick == null) {
				pick = MiningMove.TUNNEL;
			}
		}
		this.state = situation;
		this.move = pick;
		this.startTick = level.getGameTime();
		this.value = 0.0F;
		this.pending.clear();
		return pick;
	}

	/** It's starting to mine an ore block worth this much (it counts once the block is actually gone). */
	public void mining(final BlockPos pos, final float worth) {
		if (this.state != null && worth > 0.0F) {
			this.pending.putIfAbsent(pos.immutable(), worth);
		}
	}

	/** Counts the ore it has finished digging out. */
	public void tick(final ServerLevel level, final java.util.function.Predicate<BlockPos> stillThere) {
		for (Iterator<Map.Entry<BlockPos, Float>> it = this.pending.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<BlockPos, Float> entry = it.next();
			if (!level.isLoaded(entry.getKey())) {
				it.remove();
			} else if (!stillThere.test(entry.getKey())) {
				this.value += entry.getValue();
				it.remove();
			}
		}
	}

	/** The stretch is over: learn from how much ore it turned up per minute. */
	public void end(final ServerLevel level) {
		if (this.state == null) {
			return;
		}
		this.tick(level, pos -> false);
		float minutes = Math.max(0.25F, (level.getGameTime() - this.startTick) / 1200.0F);
		float perMinute = this.value / minutes;
		this.lastPerMinute = perMinute;
		this.lastMove = this.move;
		this.recentHits.addLast(this.value > 0.0F);
		if (this.recentHits.size() > 4) {
			this.recentHits.removeFirst();
		}
		MiningBrain brain = this.brain(level);
		Stretch stretch = new Stretch(this.state, this.move.ordinal(), Math.min(4.0F, perMinute / SCALE));
		this.memory.add(stretch);
		if (this.memory.size() > MEMORY) {
			this.memory.remove(0);
		}
		brain.net.train(stretch.state(), stretch.move(), stretch.target(), LEARNING_RATE);
		for (int k = 0; k < REPLAYS && this.memory.size() > 1; k++) {
			Stretch old = this.memory.get(this.clone.getRandom().nextInt(this.memory.size()));
			brain.net.train(old.state(), old.move(), old.target(), LEARNING_RATE);
		}
		brain.record(this.move, perMinute);
		this.state = null;
		CombatBrains.MINING.changed(level);
	}
}
