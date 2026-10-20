package com.clonecraft.clone.progression;

import com.clonecraft.entity.CloneEntity;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;

/**
 * Trading with villagers and wandering traders, the way a player would: the clone pays the same prices, the trade uses
 * up the offer's stock, and the villager gets its experience (and levels up) as usual. To get emeralds it sells spare
 * items to the same trader (sticks to a fletcher, coal to a toolsmith, crops to a farmer...).
 */
public final class CloneTrading {
	private CloneTrading() {}

	/** Villagers (not babies) and wandering traders nearby that aren't busy trading with a player. */
	public static List<AbstractVillager> merchantsNear(final ServerLevel level, final CloneEntity clone, final double range) {
		return level.getEntitiesOfClass(AbstractVillager.class, clone.getBoundingBox().inflate(range, range / 2, range),
			m -> m.isAlive() && !m.isBaby() && !m.isTrading() && level.isLoaded(m.blockPosition()));
	}

	/** The first offer from this trader whose result is wanted (and isn't sold out), or null. */
	public static @Nullable MerchantOffer offerFor(final AbstractVillager merchant, final Predicate<ItemStack> wanted) {
		for (MerchantOffer offer : merchant.getOffers()) {
			if (!offer.isOutOfStock() && wanted.test(offer.getResult())) {
				return offer;
			}
		}
		return null;
	}

	private static int has(final CloneEntity clone, final ItemCost cost) {
		return clone.getInventory().count(cost::test);
	}

	/** How many emeralds the clone could get by selling its spare items to this trader. spare says how many of an item can go. */
	public static int emeraldsFromSelling(final CloneEntity clone, final AbstractVillager merchant, final ToIntFunction<Predicate<ItemStack>> spare) {
		int emeralds = 0;
		for (MerchantOffer offer : merchant.getOffers()) {
			if (!isSale(offer)) {
				continue;
			}
			int times = Math.min(spare.applyAsInt(offer.getItemCostA()::test) / Math.max(1, offer.getCostA().getCount()), offer.getMaxUses() - offer.getUses());
			emeralds += Math.max(0, times) * offer.getResult().getCount();
		}
		return emeralds;
	}

	/** An offer that gives emeralds for one kind of item (what the clone can sell its spares into). */
	private static boolean isSale(final MerchantOffer offer) {
		return !offer.isOutOfStock() && offer.getResult().is(Items.EMERALD) && offer.getCostB().isEmpty() && !offer.getCostA().is(Items.EMERALD);
	}

	/** Can the clone pay for the offer, counting emeralds it could still get by selling spares to this trader? */
	public static boolean affordable(final CloneEntity clone, final AbstractVillager merchant, final MerchantOffer offer,
		final ToIntFunction<Predicate<ItemStack>> spare) {
		int emeraldsNeeded = 0;
		for (ItemStack cost : new ItemStack[]{offer.getCostA(), offer.getCostB()}) {
			if (cost.isEmpty()) {
				continue;
			}
			if (cost.is(Items.EMERALD)) {
				emeraldsNeeded += cost.getCount();
			} else if (clone.getInventory().count(s -> ItemStack.isSameItem(s, cost)) < cost.getCount()) {
				return false;
			}
		}
		int emeralds = clone.getInventory().count(s -> s.is(Items.EMERALD));
		return emeralds >= emeraldsNeeded || emeralds + emeraldsFromSelling(clone, merchant, spare) >= emeraldsNeeded;
	}

	/** Emeralds the offer costs. */
	public static int emeraldCost(final MerchantOffer offer) {
		int total = 0;
		for (ItemStack cost : new ItemStack[]{offer.getCostA(), offer.getCostB()}) {
			if (cost.is(Items.EMERALD)) {
				total += cost.getCount();
			}
		}
		return total;
	}

	/** Sells spare items to the trader until the clone has enough emeralds (or there's nothing more it can sell). */
	public static void sellFor(final CloneEntity clone, final AbstractVillager merchant, final int emeraldsWanted,
		final ToIntFunction<Predicate<ItemStack>> spare) {
		for (int i = 0; i < 32 && clone.getInventory().count(s -> s.is(Items.EMERALD)) < emeraldsWanted; i++) {
			MerchantOffer sale = null;
			for (MerchantOffer offer : merchant.getOffers()) {
				if (isSale(offer) && spare.applyAsInt(offer.getItemCostA()::test) >= offer.getCostA().getCount()) {
					sale = offer;
					break;
				}
			}
			if (sale == null || !trade(clone, merchant, sale)) {
				return;
			}
		}
	}

	/** Does one trade: takes the payment from the clone, gives it the goods, and tells the trader (stock, experience). */
	public static boolean trade(final CloneEntity clone, final AbstractVillager merchant, final MerchantOffer offer) {
		if (offer.isOutOfStock() || has(clone, offer.getItemCostA()) < offer.getCostA().getCount()) {
			return false;
		}
		ItemCost costB = offer.getItemCostB().orElse(null);
		if (costB != null && has(clone, costB) < offer.getCostB().getCount()) {
			return false;
		}
		pay(clone, offer.getItemCostA(), offer.getCostA().getCount());
		if (costB != null) {
			pay(clone, costB, offer.getCostB().getCount());
		}
		ItemStack goods = offer.assemble();
		merchant.notifyTrade(offer);
		ProgressionCrafting.give(clone, goods);
		if (clone.level() instanceof ServerLevel level) {
			level.playSound(null, merchant.blockPosition(), merchant.getNotifyTradeSound(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
		return true;
	}

	private static void pay(final CloneEntity clone, final ItemCost cost, final int count) {
		for (int i = 0; i < count; i++) {
			clone.getInventory().consumeOne(cost::test);
		}
	}
}
