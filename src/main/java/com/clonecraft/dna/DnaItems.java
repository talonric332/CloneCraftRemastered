package com.clonecraft.dna;

import com.clonecraft.CloneCraft;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** Small helpers for moving DNA between needles, tubes and eggs. */
public final class DnaItems {
	private DnaItems() {}

	public static boolean isDirty(final ItemStack stack) {
		return stack.has(CloneCraft.DIRTY);
	}

	/** An item of the given kind holding this DNA. Filled items always count as dirty. */
	public static ItemStack filled(final ItemLike item, final Dna dna) {
		ItemStack stack = new ItemStack(item);
		stack.set(CloneCraft.DNA, dna);
		stack.set(CloneCraft.DIRTY, Unit.INSTANCE);
		return stack;
	}

	public static ItemStack dirty(final ItemLike item) {
		ItemStack stack = new ItemStack(item);
		stack.set(CloneCraft.DIRTY, Unit.INSTANCE);
		return stack;
	}

	/** The empty, dirty needle or tube left behind after its contents are used. */
	public static ItemStack emptied(final ItemStack used) {
		if (used.getItem() instanceof com.clonecraft.item.NeedleItem) {
			return dirty(CloneCraft.NEEDLE);
		}
		if (used.getItem() instanceof com.clonecraft.item.TestTubeItem) {
			return dirty(CloneCraft.TEST_TUBE);
		}
		return ItemStack.EMPTY;
	}

	/** Replaces one item from the player's hand stack with a new one (like filling a bottle). */
	public static void exchangeOne(final ItemStack handStack, final LivingEntity holder, final ItemStack result, final net.minecraft.world.InteractionHand hand) {
		if (handStack.getCount() == 1) {
			holder.setItemInHand(hand, result);
			return;
		}
		handStack.shrink(1);
		if (holder instanceof Player player) {
			if (!player.getInventory().add(result) && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
				player.spawnAtLocation(level, result);
			}
		}
	}
}
