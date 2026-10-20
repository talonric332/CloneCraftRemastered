package com.clonecraft.clone.learning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * What a group of clones has learned about fighting: a neural network that rates each {@link CombatMove} for the
 * situation it's in, plus a record of how it's been doing. Shared by all the clones one player owns, and saved with
 * the world, so learning isn't lost when a clone dies.
 */
public final class CombatBrain {
	public static final int INPUTS = 13;
	public static final int HIDDEN = 16;

	public NeuralNet net;
	public int fights;
	public int wins;
	public int deaths;
	/** How many moves it has picked, ever: exploring new moves gets rarer as this goes up. */
	public long decisions;
	public final long[] moveCounts = new long[CombatMove.values().length];
	/** Recent averages per fight (each new fight counts for a tenth). */
	public float avgDealt;
	public float avgTaken;

	public static final Codec<CombatBrain> CODEC = RecordCodecBuilder.create(i -> i.group(
		NeuralNet.CODEC.fieldOf("net").forGetter(b -> b.net),
		Codec.INT.optionalFieldOf("fights", 0).forGetter(b -> b.fights),
		Codec.INT.optionalFieldOf("wins", 0).forGetter(b -> b.wins),
		Codec.INT.optionalFieldOf("deaths", 0).forGetter(b -> b.deaths),
		Codec.LONG.optionalFieldOf("decisions", 0L).forGetter(b -> b.decisions),
		Codec.LONG.listOf().optionalFieldOf("moves", List.of()).forGetter(b -> {
			List<Long> list = new ArrayList<>();
			for (long c : b.moveCounts) {
				list.add(c);
			}
			return list;
		}),
		Codec.FLOAT.optionalFieldOf("avg_dealt", 0.0F).forGetter(b -> b.avgDealt),
		Codec.FLOAT.optionalFieldOf("avg_taken", 0.0F).forGetter(b -> b.avgTaken)
	).apply(i, CombatBrain::fromSaved));

	public CombatBrain(final long seed) {
		this.net = fresh(seed);
	}

	/** A new network leans towards pressing the attack (how clones always fought), so it starts no worse than before. */
	private static NeuralNet fresh(final long seed) {
		NeuralNet net = new NeuralNet(INPUTS, HIDDEN, CombatMove.values().length, seed);
		net.setOutputBias(CombatMove.PRESS.ordinal(), 0.2F);
		return net;
	}

	private static CombatBrain fromSaved(final NeuralNet net, final int fights, final int wins, final int deaths, final long decisions,
		final List<Long> moves, final float avgDealt, final float avgTaken) {
		CombatBrain brain = new CombatBrain(0L);
		// A network saved by a different version (other inputs or moves) can't be used: start that one again.
		if (net.inputs == INPUTS && net.hidden == HIDDEN && net.outputs == CombatMove.values().length) {
			brain.net = net;
			brain.fights = fights;
			brain.wins = wins;
			brain.deaths = deaths;
			brain.decisions = decisions;
			for (int k = 0; k < moves.size() && k < brain.moveCounts.length; k++) {
				brain.moveCounts[k] = moves.get(k);
			}
			brain.avgDealt = avgDealt;
			brain.avgTaken = avgTaken;
		}
		return brain;
	}

	/** How often it tries a random move instead of the one it rates best: often at first, rarely once experienced. */
	public float exploreChance() {
		return 0.03F + 0.27F * (float)Math.exp(-this.decisions / 1500.0);
	}

	public void recordFight(final boolean won, final boolean died, final float dealt, final float taken) {
		this.fights++;
		if (won) {
			this.wins++;
		}
		if (died) {
			this.deaths++;
		}
		float weight = this.fights == 1 ? 1.0F : 0.1F;
		this.avgDealt += (dealt - this.avgDealt) * weight;
		this.avgTaken += (taken - this.avgTaken) * weight;
	}
}
