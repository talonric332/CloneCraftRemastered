package com.clonecraft.clone.learning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A small neural network: inputs, one hidden layer of tanh neurons, and one output per choice (how good it thinks
 * each choice is). It learns by gradient descent, one example at a time.
 */
public final class NeuralNet {
	public final int inputs;
	public final int hidden;
	public final int outputs;
	/** All the weights in one array: hidden layer (inputs + bias for each neuron), then output layer (hidden + bias). */
	final float[] w;

	public static final Codec<NeuralNet> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("inputs").forGetter(n -> n.inputs),
		Codec.INT.fieldOf("hidden").forGetter(n -> n.hidden),
		Codec.INT.fieldOf("outputs").forGetter(n -> n.outputs),
		Codec.FLOAT.listOf().fieldOf("weights").forGetter(NeuralNet::weightList)
	).apply(i, NeuralNet::fromSaved));

	public NeuralNet(final int inputs, final int hidden, final int outputs, final long seed) {
		this.inputs = inputs;
		this.hidden = hidden;
		this.outputs = outputs;
		this.w = new float[size(inputs, hidden, outputs)];
		Random random = new Random(seed);
		// Small random starting weights, scaled to the layer size so the neurons don't start saturated.
		float h = (float)(1.0 / Math.sqrt(inputs + 1));
		float o = (float)(1.0 / Math.sqrt(hidden + 1));
		int split = hidden * (inputs + 1);
		for (int k = 0; k < this.w.length; k++) {
			this.w[k] = (float)(random.nextGaussian() * (k < split ? h : o) * 0.5);
		}
	}

	private NeuralNet(final int inputs, final int hidden, final int outputs, final float[] w) {
		this.inputs = inputs;
		this.hidden = hidden;
		this.outputs = outputs;
		this.w = w;
	}

	private static int size(final int inputs, final int hidden, final int outputs) {
		return hidden * (inputs + 1) + outputs * (hidden + 1);
	}

	private static NeuralNet fromSaved(final int inputs, final int hidden, final int outputs, final List<Float> weights) {
		float[] w = new float[size(inputs, hidden, outputs)];
		for (int k = 0; k < w.length && k < weights.size(); k++) {
			float v = weights.get(k);
			w[k] = Float.isFinite(v) ? v : 0.0F;
		}
		return new NeuralNet(inputs, hidden, outputs, w);
	}

	private List<Float> weightList() {
		List<Float> list = new ArrayList<>(this.w.length);
		for (float v : this.w) {
			list.add(v);
		}
		return list;
	}

	public NeuralNet copy() {
		return new NeuralNet(this.inputs, this.hidden, this.outputs, this.w.clone());
	}

	private int outBase() {
		return this.hidden * (this.inputs + 1);
	}

	/** Sets an output's starting bias, so a new network leans towards a sensible choice before it has learned anything. */
	public void setOutputBias(final int output, final float bias) {
		this.w[this.outBase() + output * (this.hidden + 1) + this.hidden] = bias;
	}

	private float[] hiddenLayer(final float[] x) {
		float[] h = new float[this.hidden];
		for (int j = 0; j < this.hidden; j++) {
			int base = j * (this.inputs + 1);
			float sum = this.w[base + this.inputs];
			for (int i = 0; i < this.inputs; i++) {
				sum += this.w[base + i] * x[i];
			}
			h[j] = (float)Math.tanh(sum);
		}
		return h;
	}

	private float output(final float[] h, final int k) {
		int base = this.outBase() + k * (this.hidden + 1);
		float sum = this.w[base + this.hidden];
		for (int j = 0; j < this.hidden; j++) {
			sum += this.w[base + j] * h[j];
		}
		return sum;
	}

	/** How good the network thinks each choice is, for this situation. */
	public float[] predict(final float[] x) {
		float[] h = this.hiddenLayer(x);
		float[] out = new float[this.outputs];
		for (int k = 0; k < this.outputs; k++) {
			out[k] = this.output(h, k);
		}
		return out;
	}

	/**
	 * Nudges the network so that its value for one choice in this situation moves towards the target.
	 * The error is capped, so one surprising result can't throw everything it has learned away.
	 */
	public void train(final float[] x, final int choice, final float target, final float rate) {
		float[] h = this.hiddenLayer(x);
		float error = Math.max(-1.0F, Math.min(1.0F, this.output(h, choice) - target));
		int base = this.outBase() + choice * (this.hidden + 1);
		for (int j = 0; j < this.hidden; j++) {
			// Back through the hidden neuron (tanh' = 1 - h^2), using the output weight before it's changed.
			float back = error * this.w[base + j] * (1.0F - h[j] * h[j]);
			this.w[base + j] -= rate * error * h[j];
			int hb = j * (this.inputs + 1);
			for (int i = 0; i < this.inputs; i++) {
				this.w[hb + i] -= rate * back * x[i];
			}
			this.w[hb + this.inputs] -= rate * back;
		}
		this.w[base + this.hidden] -= rate * error;
	}
}
