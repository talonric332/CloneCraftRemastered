package com.clonecraft.clone.learning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * What a player's clones have learned about mining: a neural network that rates each {@link MiningMove} for the
 * situation (depth, which ore it's after, caves nearby...), by how much ore a stretch of tunnel dug that way usually
 * turns up per minute. Shared by all the clones one player owns, and saved with the world.
 */
public final class MiningBrain {
	public static final int INPUTS = 10;
	public static final int HIDDEN = 12;

	public NeuralNet net;
	public int stretches;
	public final long[] moveCounts = new long[MiningMove.values().length];
	/** Ore value per minute lately (each new stretch counts for a tenth). */
	public float avgValue;
	public float totalValue;

	public static final Codec<MiningBrain> CODEC = RecordCodecBuilder.create(i -> i.group(
		NeuralNet.CODEC.fieldOf("net").forGetter(b -> b.net),
		Codec.INT.optionalFieldOf("stretches", 0).forGetter(b -> b.stretches),
		Codec.LONG.listOf().optionalFieldOf("moves", List.of()).forGetter(b -> {
			List<Long> list = new ArrayList<>();
			for (long c : b.moveCounts) {
				list.add(c);
			}
			return list;
		}),
		Codec.FLOAT.optionalFieldOf("avg_value", 0.0F).forGetter(b -> b.avgValue),
		Codec.FLOAT.optionalFieldOf("total_value", 0.0F).forGetter(b -> b.totalValue)
	).apply(i, MiningBrain::fromSaved));

	public MiningBrain(final long seed) {
		this.net = new NeuralNet(INPUTS, HIDDEN, MiningMove.values().length, seed);
		// A new brain leans towards tunnelling straight on (how clones always mined), so it starts no worse than before.
		this.net.setOutputBias(MiningMove.TUNNEL.ordinal(), 0.2F);
	}

	private static MiningBrain fromSaved(final NeuralNet net, final int stretches, final List<Long> moves, final float avgValue, final float totalValue) {
		MiningBrain brain = new MiningBrain(0L);
		if (net.inputs == INPUTS && net.hidden == HIDDEN && net.outputs == MiningMove.values().length) {
			brain.net = net;
			brain.stretches = stretches;
			for (int k = 0; k < moves.size() && k < brain.moveCounts.length; k++) {
				brain.moveCounts[k] = moves.get(k);
			}
			brain.avgValue = avgValue;
			brain.totalValue = totalValue;
		}
		return brain;
	}

	/** How often it picks a move completely at random (as well as trying moves it hasn't used much: see {@link #bonus}). */
	public float exploreChance() {
		return 0.05F;
	}

	/**
	 * A bonus for moves it hasn't tried much yet, added to how good it rates them, so each gets a fair chance before
	 * it settles on what works ("optimism in the face of uncertainty"). It shrinks as a move gets tried more.
	 */
	public float bonus(final MiningMove move) {
		return 0.6F / (float)Math.sqrt(1.0 + this.moveCounts[move.ordinal()]);
	}

	public void record(final MiningMove move, final float valuePerMinute) {
		this.stretches++;
		this.moveCounts[move.ordinal()]++;
		this.totalValue += valuePerMinute;
		float weight = this.stretches == 1 ? 1.0F : 0.1F;
		this.avgValue += (valuePerMinute - this.avgValue) * weight;
	}
}
