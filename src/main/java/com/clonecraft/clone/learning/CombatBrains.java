package com.clonecraft.clone.learning;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Every player's clones' {@link CombatBrain} and {@link MiningBrain}, saved with the world. Server thread only. */
public final class CombatBrains {
	public static final UUID UNOWNED = SharedBrains.UNOWNED;

	private static final SharedBrains<CombatBrain> COMBAT = new SharedBrains<>("combat_brains", CombatBrain.CODEC,
		k -> new CombatBrain(k.getMostSignificantBits() ^ k.getLeastSignificantBits()));
	public static final SharedBrains<MiningBrain> MINING = new SharedBrains<>("mining_brains", MiningBrain.CODEC,
		k -> new MiningBrain(k.getMostSignificantBits() ^ k.getLeastSignificantBits()));

	private CombatBrains() {
	}

	/** Loads the class, so the saved data types are registered before any world loads. */
	public static void register() {
	}

	public static CombatBrain get(final ServerLevel level, final @Nullable UUID owner) {
		return COMBAT.get(level, owner);
	}

	public static void reset(final ServerLevel level, final @Nullable UUID owner) {
		COMBAT.reset(level, owner);
	}

	public static void changed(final ServerLevel level) {
		COMBAT.changed(level);
	}
}
