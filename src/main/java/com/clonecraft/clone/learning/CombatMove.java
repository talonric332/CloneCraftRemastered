package com.clonecraft.clone.learning;

/** The ways a clone can fight, which its {@link CombatBrain} chooses between. */
public enum CombatMove {
	/** Walk up and hit whenever the weapon is ready (how clones always fought). */
	PRESS("pressing in"),
	/** Jump just before hitting, so the hit lands while falling: a critical hit for half as much again. */
	CRIT("critical jumps"),
	/** Hit, then step back while the weapon recharges, so the enemy can't hit back. */
	HIT_AND_BACK("hit and back off"),
	/** Circle round the enemy between hits, which makes arrows and swings miss. */
	CIRCLE("circling"),
	/** Keep some distance and use a bow. */
	SHOOT("shooting"),
	/** Get well clear (a creeper about to blow, too many enemies). */
	KEEP_CLEAR("keeping clear");

	public final String description;

	CombatMove(final String description) {
		this.description = description;
	}
}
