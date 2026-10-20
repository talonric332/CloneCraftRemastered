package com.clonecraft.clone.learning;

/** The ways a clone can carry on mining when it starts a new stretch, which its {@link MiningBrain} picks. */
public enum MiningMove {
	/** Keep tunnelling straight ahead at this depth. */
	TUNNEL("tunnelling straight"),
	/**
	 * Strip mining, like players do: a main tunnel with side tunnels off both sides every 3 blocks, which shows
	 * the most rock (and so the most ore) for the digging.
	 */
	BRANCHES("strip mining"),
	/** Tunnel off to the left or right instead. */
	TURN("turning off"),
	/** Staircase down 8 blocks, then tunnel. */
	DEEPER("going deeper"),
	/** Staircase up 8 blocks, then tunnel. */
	HIGHER("going higher"),
	/** Head for a cave nearby (walking there if it can): ore shows in its walls without having to dig for it. */
	CAVE("exploring caves");

	public final String description;

	MiningMove(final String description) {
		this.description = description;
	}
}
