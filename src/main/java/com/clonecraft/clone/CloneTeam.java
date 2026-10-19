package com.clonecraft.clone;

/** The original CloneCraft teams and who fights whom. */
public enum CloneTeam {
	RED("Red", 0xFF0000),
	ORANGE("Orange", 0xFF8000),
	YELLOW("Yellow", 0xFFFF00),
	GREEN("Green", 0x00FF00),
	BLUE("Blue", 0x3355FF),
	LIGHT_BLUE("LightBlue", 0x0080FF),
	PURPLE("Purple", 0xC800FF),
	PINK("Pink", 0xFFAAFF),
	GOOD("Good", 0xFFFFFF),
	EVIL("Evil", 0x555555),
	RAMPANT("Rampant", 0x884444);

	public final String displayName;
	public final int color;

	CloneTeam(final String displayName, final int color) {
		this.displayName = displayName;
		this.color = color;
	}

	public static CloneTeam byId(final int id) {
		return id >= 0 && id < values().length ? values()[id] : GOOD;
	}

	public static CloneTeam byName(final String name) {
		for (CloneTeam team : values()) {
			if (team.displayName.equalsIgnoreCase(name) || team.name().equalsIgnoreCase(name)) {
				return team;
			}
		}
		return null;
	}

	public boolean isColour() {
		return this != GOOD && this != EVIL && this != RAMPANT;
	}

	/** Same rules as the original: Rampant fights everyone, Evil fights everyone else, colours fight other colours. */
	public boolean attacksTeam(final CloneTeam other) {
		if (this == RAMPANT || other == RAMPANT) {
			return true;
		}
		if (this != other && (this == EVIL || other == EVIL)) {
			return true;
		}
		return other != this && other != GOOD && this != GOOD;
	}

	public String info() {
		return switch (this) {
			case GOOD -> "The Good team will only attack the 'Evil' team.";
			case EVIL -> "The Evil team will attack all other teams, including you.. So, be careful ;)";
			case RAMPANT -> "The Rampant team will attack everything. Every Clone, every entity, every player.";
			default -> "The " + this.displayName + " team will attack all other teams, except the 'Good' team.";
		};
	}
}
