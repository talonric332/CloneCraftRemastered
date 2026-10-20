package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Clones on Game Progression that end up underground get back out to the surface when they have work up there. */
public class CaveGameTests {
	/** The top of the hill: ground level is y = 9 (relative). */
	private static final int SURFACE = 9;

	/**
	 * A solid hill from -8 to 15 (x and z), 9 blocks high, with a tree on top. The test only keeps its own small area
	 * running, so the hill's chunks are kept running too.
	 */
	private static void hill(final GameTestHelper h) {
		BlockPos low = h.absolutePos(new BlockPos(-8, 0, -8));
		BlockPos high = h.absolutePos(new BlockPos(15, 0, 15));
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = Math.min(low.getX(), high.getX()) >> 4; cx <= Math.max(low.getX(), high.getX()) >> 4; cx++) {
			for (int cz = Math.min(low.getZ(), high.getZ()) >> 4; cz <= Math.max(low.getZ(), high.getZ()) >> 4; cz++) {
				h.getLevel().setChunkForced(cx, cz, true);
				forced.add(new ChunkPos(cx, cz));
			}
		}
		h.runBeforeTestEnd(() -> forced.forEach(c -> h.getLevel().setChunkForced(c.x(), c.z(), false)));
		for (int x = -8; x <= 15; x++) {
			for (int z = -8; z <= 15; z++) {
				for (int y = 0; y < SURFACE; y++) {
					h.setBlock(x, y, z, y == SURFACE - 1 ? Blocks.GRASS_BLOCK : y >= SURFACE - 3 ? Blocks.DIRT : Blocks.STONE);
				}
				for (int y = SURFACE; y < SURFACE + 8; y++) {
					h.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
		// A tree up top.
		for (int y = SURFACE; y < SURFACE + 4; y++) {
			h.setBlock(11, y, 4, Blocks.OAK_LOG);
		}
		for (int x = 10; x <= 12; x++) {
			for (int z = 3; z <= 5; z++) {
				if (x != 11 || z != 4) {
					h.setBlock(x, SURFACE + 3, z, Blocks.OAK_LEAVES);
				}
			}
		}
		h.setBlock(11, SURFACE + 4, 4, Blocks.OAK_LEAVES);
	}

	private static void clear(final GameTestHelper h, final int x, final int y, final int z) {
		h.setBlock(x, y, z, Blocks.AIR);
	}

	private static CloneEntity clone(final GameTestHelper h, final int x, final int y, final int z) {
		CloneEntity clone = h.spawn(CloneCraft.CLONE, x + 0.5F, (float)y, z + 0.5F);
		clone.setGrownUp();
		clone.setOption(CloneOption.GAME_PROGRESSION, true);
		// A pickaxe (so it can dig) and food (so it isn't busy being hungry). It needs wood, which is up top.
		clone.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
		clone.getInventory().add(new ItemStack(Items.BREAD, 16));
		return clone;
	}

	private static int countSolid(final GameTestHelper h) {
		int count = 0;
		for (int x = -8; x <= 15; x++) {
			for (int z = -8; z <= 15; z++) {
				for (int y = 0; y < SURFACE; y++) {
					Block block = h.getBlockState(new BlockPos(x, y, z)).getBlock();
					if (block == Blocks.STONE || block == Blocks.DIRT || block == Blocks.GRASS_BLOCK) {
						count++;
					}
				}
			}
		}
		return count;
	}

	private static String where(final GameTestHelper h, final CloneEntity clone) {
		return "the clone is at " + h.relativeVec(clone.position()) + " doing \"" + clone.progressionGoal.describe() + "\"";
	}

	@GameTest(maxTicks = 3000, padding = 40)
	public void digsOutOfASealedCave(final GameTestHelper h) {
		hill(h);
		for (int x = 3; x <= 4; x++) {
			for (int z = 3; z <= 4; z++) {
				clear(h, x, 1, z);
				clear(h, x, 2, z);
			}
		}
		CloneEntity clone = clone(h, 3, 1, 3);
		h.succeedWhen(() -> h.assertTrue(clone.getY() >= h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() - 0.01,
			"Not out of the cave yet: " + where(h, clone)));
	}

	@GameTest(maxTicks = 1200, padding = 40)
	public void walksOutWhenThereIsAWay(final GameTestHelper h) {
		hill(h);
		// A cave room with a sloping tunnel up to the surface (one block up for each block along, 2 high).
		for (int x = 0; x <= 1; x++) {
			for (int z = 3; z <= 4; z++) {
				clear(h, x, 1, z);
				clear(h, x, 2, z);
			}
		}
		for (int step = 0; step < SURFACE; step++) {
			int x = 2 + step;
			for (int y = 1 + step; y <= 3 + step && y < SURFACE; y++) {
				clear(h, x, y, 3);
			}
		}
		int before = countSolid(h);
		CloneEntity clone = clone(h, 0, 1, 3);
		h.succeedWhen(() -> {
			h.assertTrue(clone.getY() >= h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() - 0.01, "Not out of the cave yet: " + where(h, clone));
			h.assertTrue(countSolid(h) == before, "It should have walked out through the tunnel, not dug (" + (before - countSolid(h)) + " blocks dug)");
		});
	}

	/**
	 * Going down for stone, a clone digs a staircase it can walk back up, never a shaft straight down. Checked every
	 * second: from wherever it's standing below ground, there's a walkable way back to where it started.
	 */
	@GameTest(maxTicks = 4000, padding = 40, maxAttempts = 3, requiredSuccesses = 1)
	public void digsAStaircaseDownNotAShaft(final GameTestHelper h) {
		hill(h);
		CloneEntity clone = clone(h, 3, SURFACE, 3);
		clone.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
		clone.getInventory().add(new ItemStack(Items.CRAFTING_TABLE));
		clone.getInventory().add(new ItemStack(Items.OAK_LOG, 3));
		BlockPos start = h.absolutePos(new BlockPos(3, SURFACE, 3));
		int[] deepest = {start.getY()};
		String[] trapped = {null};
		h.onEachTick(() -> {
			if (h.getTick() % 20 != 0 || trapped[0] != null || !clone.onGround()) {
				return;
			}
			BlockPos feet = clone.blockPosition();
			deepest[0] = Math.min(deepest[0], feet.getY());
			if (feet.getY() <= start.getY() - 2) {
				var path = clone.getNavigation().createPath(start, 1);
				if (path == null || !path.canReach()) {
					trapped[0] = "at " + h.relativePos(feet) + " (tick " + h.getTick() + ") it couldn't walk back up";
				}
			}
		});
		h.succeedWhen(() -> {
			h.assertTrue(trapped[0] == null, "Dug itself into a hole: " + trapped[0]);
			h.assertTrue(clone.getInventory().find(s -> s.is(Items.FURNACE)) >= 0, "No furnace yet (still digging for stone): " + where(h, clone));
			h.assertTrue(deepest[0] <= start.getY() - 3, "It should have gone down to the stone");
		});
	}

	/** A roof that isn't natural ground (a house, a tree's branches) isn't a cave: nothing gets dug through. */
	@GameTest(padding = 40)
	public void aHouseOrATreeIsNotACave(final GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		// A plank hut with a roof over (1..3, 1..3).
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				for (int y = 1; y <= 4; y++) {
					boolean wall = x == 0 || x == 4 || z == 0 || z == 4 || y == 4;
					h.setBlock(x, y, z, wall ? Blocks.OAK_PLANKS : Blocks.AIR);
				}
			}
		}
		CloneEntity inside = clone(h, 2, 1, 2);
		// A log overhead, like a big tree's branch.
		h.setBlock(6, 4, 6, Blocks.OAK_LOG);
		CloneEntity underTree = clone(h, 6, 1, 6);
		h.runAfterDelay(5, () -> {
			h.assertFalse(inside.progressionGoal.belowGround(h.getLevel()), "A clone in a house shouldn't think it's in a cave");
			h.assertFalse(underTree.progressionGoal.belowGround(h.getLevel()), "A clone under a tree branch shouldn't think it's in a cave");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 3000, padding = 40)
	public void climbsOutOfAPit(final GameTestHelper h) {
		hill(h);
		// A 1x1 hole, 7 deep, open to the sky.
		for (int y = 2; y < SURFACE; y++) {
			clear(h, 3, y, 3);
		}
		CloneEntity clone = clone(h, 3, 2, 3);
		h.succeedWhen(() -> h.assertTrue(clone.getY() >= h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() - 0.01,
			"Not out of the pit yet: " + where(h, clone)));
	}
}
