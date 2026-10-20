package com.clonecraft.clone;

import com.clonecraft.entity.CloneEntity;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * Items a clone mines or gets from its own kills are reserved for that clone for a while, so other clones nearby don't
 * grab them first. Players can always pick them up. Claims only live in memory: they're short, so nothing is saved.
 */
public final class DropClaims {
	private DropClaims() {}

	/** 30 seconds. */
	public static final int CLAIM_TICKS = 600;

	private record Claim(UUID owner, long until) {}

	/** Weak keys: an item entity that's picked up or despawns drops out of the map by itself. Server thread only. */
	private static final Map<ItemEntity, Claim> CLAIMS = new WeakHashMap<>();

	/** Claims the items that just appeared in this area (this tick or the last) for the clone. */
	public static void claimNew(final ServerLevel level, final AABB area, final CloneEntity clone) {
		Claim claim = new Claim(clone.getUUID(), level.getGameTime() + CLAIM_TICKS);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, i -> i.isAlive() && i.getAge() <= 1)) {
			CLAIMS.put(item, claim);
		}
	}

	/** Can this clone take the item? Yes unless another clone claimed it and that clone is still around to collect it. */
	public static boolean mayTake(final ItemEntity item, final CloneEntity clone) {
		Claim claim = CLAIMS.get(item);
		if (claim == null || claim.owner().equals(clone.getUUID())) {
			return true;
		}
		if (!(item.level() instanceof ServerLevel level) || level.getGameTime() > claim.until()) {
			CLAIMS.remove(item);
			return true;
		}
		Entity claimer = level.getEntity(claim.owner());
		if (claimer == null || !claimer.isAlive() || claimer.distanceToSqr(item) > 32 * 32) {
			CLAIMS.remove(item);
			return true;
		}
		return false;
	}
}
