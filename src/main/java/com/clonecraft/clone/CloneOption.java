package com.clonecraft.clone;

/** The clone options (the 18 from the original Options screen, plus Game Progression), stored as bits in one number. */
public enum CloneOption {
	FIGHT("Fight", true, "Determines whether the clones do any fighting (and if other entities will attack this clone)"),
	SPRINT("Sprint", false, "Makes the clone run faster, however causes clones to become hungry faster"),
	FOLLOW("Follow", false, "Makes clones follow you"),
	PICKUP("Pickup Items", true, "When enabled, clones will pick up items"),
	WALK_TO_ITEMS("Walk To Items", false, "Clones will walk over and collect nearby items and experience"),
	RETALIATE("Retaliate", true, "Clones will attack a mob which attacks them, even if the mob is not on the kill list"),
	GUARD("Guard Position", false, "When set to true, the position the clone is standing will become its guard position."),
	WANDER("Wander", false, "Causes the clones to wander around aimlessly"),
	JUMP("Jump", false, "Causes clones to do jump attacks, causing a critical hit"),
	CURIOUS("Curious", true, "Causes the clones to look around"),
	STATS("Stats", true, "If turned off, health and food stats above the clone's head will not be rendered"),
	FEMALE("Female", false, "Slims the clone slightly and gives it a girl's name"),
	BREAK_BLOCKS("Break Blocks", false, "Enables/disables the ability to break any blocks at all (choose which in 'Blocks To Break')"),
	SMART_MEMORY("Smart Memory", false, "Clones scan the environment and remember the location of chests and other important blocks."),
	FARM("Farm", false, "Enables/disables a clone's farming ability: fully grown crops are harvested and replanted"),
	SHARE("Share Items", false, "Clones on the same team share spare armour, weapons and tools, and even out food, arrows and torches."),
	COMMAND("Commandable", true, "Enables clones to be commanded via in game text. Just talk to them, but begin with a \\ (backslash)"),
	SKIP_AIR("Skip Air Blocks", false, "When building, clones will skip over air blocks in the schematic"),
	// New options go at the end: options are saved as bits, so moving one would change saved clones' settings.
	GAME_PROGRESSION("Game Progression", false, "Enables autonomous gathering, mining, gear crafting, and Ender Dragon progression while AFK.");

	public final String label;
	public final boolean defaultValue;
	public final String description;

	CloneOption(final String label, final boolean defaultValue, final String description) {
		this.label = label;
		this.defaultValue = defaultValue;
		this.description = description;
	}

	public int bit() {
		return 1 << this.ordinal();
	}

	public static int defaults() {
		int value = 0;
		for (CloneOption option : values()) {
			if (option.defaultValue) {
				value |= option.bit();
			}
		}
		return value;
	}
}
