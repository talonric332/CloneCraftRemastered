package com.clonecraft.clone.progression;

import com.clonecraft.entity.CloneEntity;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * "Clone Storage" chests: chests clones make and place for spare items. They're ordinary chests with that name, so the
 * name (and the contents) are saved with the world, and you can open them too. Clones only ever put things in Clone
 * Storage, but they take what they need from any nearby chest or barrel.
 */
public final class CloneStorage {
	private CloneStorage() {}

	public static final String NAME = "Clone Storage";

	public static boolean isStorage(final @Nullable BlockEntity be) {
		return be instanceof ChestBlockEntity chest && chest.getCustomName() != null && NAME.equals(chest.getCustomName().getString());
	}

	/**
	 * Somewhere a clone may take items from: any chest or barrel (Clone Storage, a bonus chest, a village chest, your
	 * storage). Loot chests are filled the first time a clone looks inside, just like when a player opens them.
	 */
	public static boolean isSource(final @Nullable BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity;
	}

	/** The nearest Clone Storage chest in loaded chunks within the radius, optionally only ones holding a matching item. */
	public static @Nullable BlockPos nearest(final ServerLevel level, final BlockPos from, final int radius, final @Nullable Predicate<ItemStack> holding) {
		return nearest(level, from, radius, holding, CloneStorage::isStorage);
	}

	/** The nearest chest or barrel (of any kind) in loaded chunks within the radius holding a matching item. */
	public static @Nullable BlockPos nearestSource(final ServerLevel level, final BlockPos from, final int radius, final Predicate<ItemStack> holding) {
		return nearest(level, from, radius, holding, CloneStorage::isSource);
	}

	private static @Nullable BlockPos nearest(final ServerLevel level, final BlockPos from, final int radius, final @Nullable Predicate<ItemStack> holding,
		final Predicate<BlockEntity> kind) {
		BlockPos best = null;
		double bestDist = (double)radius * radius;
		for (int cx = (from.getX() - radius) >> 4; cx <= (from.getX() + radius) >> 4; cx++) {
			for (int cz = (from.getZ() - radius) >> 4; cz <= (from.getZ() + radius) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (!kind.test(be) || (holding != null && count((Container)be, holding) == 0)) {
						continue;
					}
					double d = be.getBlockPos().distSqr(from);
					if (d <= bestDist) {
						bestDist = d;
						best = be.getBlockPos();
					}
				}
			}
		}
		return best;
	}

	public static @Nullable Container at(final ServerLevel level, final BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ChestBlockEntity chest && isStorage(chest) ? chest : null;
	}

	/** Any chest or barrel at the position (to take from), or null. */
	public static @Nullable Container sourceAt(final ServerLevel level, final BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		BlockEntity be = level.getBlockEntity(pos);
		return isSource(be) ? (Container)be : null;
	}

	/**
	 * A loot chest (bonus chest, village or dungeon chest) only gets its items when someone first opens it. Fill it now,
	 * as a player opening it would, before a clone looks inside.
	 */
	public static void fillLoot(final Container container) {
		if (container instanceof net.minecraft.world.RandomizableContainer loot && loot.getLootTable() != null) {
			loot.unpackLootTable(null);
		}
	}

	/** Opens (or closes) a chest's lid or a barrel, so you can see a clone using it. */
	public static void setOpen(final ServerLevel level, final BlockPos pos, final boolean open) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock) {
			level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
			level.playSound(null, pos, open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
		} else if (state.hasProperty(net.minecraft.world.level.block.BarrelBlock.OPEN)) {
			level.setBlock(pos, state.setValue(net.minecraft.world.level.block.BarrelBlock.OPEN, open), net.minecraft.world.level.block.Block.UPDATE_ALL);
			level.playSound(null, pos, open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
		}
	}

	public static int count(final Container container, final Predicate<ItemStack> test) {
		fillLoot(container);
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (!s.isEmpty() && test.test(s)) {
				total += s.getCount();
			}
		}
		return total;
	}

	/** Places a Clone Storage chest facing the given way. */
	public static void place(final ServerLevel level, final BlockPos pos, final Direction facing) {
		BlockState state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.SINGLE);
		level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			ItemStack named = new ItemStack(Items.CHEST);
			named.set(DataComponents.CUSTOM_NAME, Component.literal(NAME));
			chest.applyComponentsFromItemStack(named);
			chest.setChanged();
		}
		level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
	}

	/** Puts as much of the stack in the chest as fits. The stack shrinks by what went in. */
	public static void put(final Container chest, final ItemStack stack) {
		for (int i = 0; i < chest.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack s = chest.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
				int move = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
				s.grow(move);
				stack.shrink(move);
			}
		}
		for (int i = 0; i < chest.getContainerSize() && !stack.isEmpty(); i++) {
			if (chest.getItem(i).isEmpty()) {
				chest.setItem(i, stack.split(stack.getMaxStackSize()));
			}
		}
		chest.setChanged();
	}

	/** Moves up to max matching items from the chest into the clone's inventory. Returns how many it took. */
	public static int take(final Container chest, final CloneEntity clone, final Predicate<ItemStack> test, final int max) {
		fillLoot(chest);
		int taken = 0;
		for (int i = 0; i < chest.getContainerSize() && taken < max; i++) {
			ItemStack s = chest.getItem(i);
			if (s.isEmpty() || !test.test(s)) {
				continue;
			}
			ItemStack moving = s.copyWithCount(Math.min(s.getCount(), max - taken));
			// Like picking it up: better armour is put on straight away.
			int before = moving.getCount();
			clone.pickupItem(moving);
			int added = before - moving.getCount();
			if (added <= 0) {
				break;
			}
			s.shrink(added);
			if (s.isEmpty()) {
				chest.setItem(i, ItemStack.EMPTY);
			}
			taken += added;
		}
		chest.setChanged();
		return taken;
	}

	public static void openSound(final ServerLevel level, final BlockPos pos) {
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
	}
}
