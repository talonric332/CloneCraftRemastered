package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.learning.CombatBrains;
import com.clonecraft.clone.progression.MineStairs;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mining trips down to iron depth (y = 16). Test areas are higher up than that, so these build a block of stone
 * underneath (optionally with some iron and coal in it), from y = 0 to y = 30, and the clones start on top of it.
 */
public class MiningGameTests {
	private static final int TOP = 31;
	private static final int HALF = 32;

	/** The middle of the top of the mine block (absolute). */
	private static BlockPos mineBlock(final GameTestHelper h, final long seed, final float oreChance) {
		ServerLevel level = h.getLevel();
		BlockPos origin = h.absolutePos(BlockPos.ZERO);
		int cx = origin.getX();
		int cz = origin.getZ();
		List<ChunkPos> forced = new ArrayList<>();
		for (int x = (cx - HALF) >> 4; x <= (cx + HALF) >> 4; x++) {
			for (int z = (cz - HALF) >> 4; z <= (cz + HALF) >> 4; z++) {
				level.setChunkForced(x, z, true);
				forced.add(new ChunkPos(x, z));
			}
		}
		h.runBeforeTestEnd(() -> forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false)));
		Random random = new Random(seed);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = cx - HALF; x <= cx + HALF; x++) {
			for (int z = cz - HALF; z <= cz + HALF; z++) {
				for (int y = 0; y <= TOP + 6; y++) {
					BlockState state;
					if (y >= TOP) {
						state = Blocks.AIR.defaultBlockState();
					} else if (y == TOP - 1) {
						state = Blocks.GRASS_BLOCK.defaultBlockState();
					} else if (y >= TOP - 3) {
						state = Blocks.DIRT.defaultBlockState();
					} else {
						float roll = random.nextFloat();
						state = (roll < oreChance ? Blocks.IRON_ORE : roll < oreChance * 2 ? Blocks.COAL_ORE : Blocks.STONE).defaultBlockState();
					}
					level.setBlock(pos.set(x, y, z), state, Block.UPDATE_CLIENTS);
				}
			}
		}
		// One iron ore 9 blocks down: the test world is superflat, where clones only start digging for ore they can sense.
		level.setBlock(new BlockPos(cx + 2, TOP - 9, cz + 2), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		return new BlockPos(cx, TOP, cz);
	}

	/** A clone ready to go mining for iron (it has everything stone can make). Removed when the test ends. */
	private static CloneEntity miner(final GameTestHelper h, final BlockPos at) {
		CloneEntity clone = CloneCraft.CLONE.create(h.getLevel(), EntitySpawnReason.COMMAND);
		clone.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		clone.setGrownUp();
		clone.setOption(CloneOption.GAME_PROGRESSION, true);
		clone.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
		for (ItemStack stack : List.of(new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_SHOVEL),
			new ItemStack(Items.FURNACE), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.BREAD, 32),
			new ItemStack(Items.OAK_PLANKS, 16), new ItemStack(Items.STICK, 16), new ItemStack(Items.COBBLESTONE, 32),
			new ItemStack(Items.BED.white()))) {
			clone.getInventory().add(stack);
		}
		h.getLevel().addFreshEntity(clone);
		h.runBeforeTestEnd(clone::discard);
		return clone;
	}

	private static String where(final CloneEntity clone) {
		return "at " + clone.blockPosition() + " doing \"" + clone.progressionGoal.describe() + "\"";
	}

	/** One clone digs a staircase down to go mining; a second one coming later walks down it instead of digging its own. */
	@GameTest(maxTicks = 6000, padding = 60)
	public void secondCloneTakesTheFirstOnesStairs(final GameTestHelper h) {
		BlockPos top = mineBlock(h, 1L, 0.0F);
		CloneEntity first = miner(h, top);
		CloneEntity[] second = {null};
		boolean[] tookStairs = {false};
		h.onEachTick(() -> {
			if (second[0] == null) {
				boolean shared = MineStairs.all(h.getLevel()).stream()
					.anyMatch(s -> s.top().closerThan(top, 4.0) && s.bottom().getY() <= top.getY() - 8);
				if (shared) {
					second[0] = miner(h, top.offset(1, 0, 1));
				}
			} else if (second[0].progressionGoal.isTakingStairs()) {
				tookStairs[0] = true;
			}
		});
		h.succeedWhen(() -> {
			h.assertTrue(second[0] != null, "The first clone hasn't shared a staircase yet: " + where(first));
			h.assertTrue(tookStairs[0], "The second clone should take the first one's stairs: " + where(second[0]));
			h.assertTrue(second[0].getBlockY() <= top.getY() - 8, "The second clone isn't down the stairs yet: " + where(second[0]));
		});
	}

	/**
	 * Finding iron like a player: the ore is buried in the rock (about 1 block in 50), so it has to dig, and it mines
	 * what shows in the walls of its tunnels.
	 */
	@GameTest(maxTicks = 12000, padding = 60)
	public void findsIronByTunnelling(final GameTestHelper h) {
		BlockPos top = mineBlock(h, 3L, 0.02F);
		CloneEntity clone = miner(h, top);
		h.succeedWhen(() -> h.assertTrue(clone.getInventory().find(s -> s.is(Items.RAW_IRON) || s.is(Items.IRON_INGOT)) >= 0,
			"No iron yet: " + where(clone)));
	}

	// ---------------------------------------------------------------- experiment (not part of the normal tests)
	// CLONECRAFT_TRAINING=mining: two clones mine for iron in a big block of stone where iron is rare except in a
	// rich layer 8 blocks below where clones start tunnelling. One uses the mining brain, the other always tunnels
	// straight (how clones used to mine). Logs how much ore each finds per minute, early on and later.
	// CLONECRAFT_STRETCHES sets how many stretches of mining (default 30).

	private static final int BIG = 48;

	@GameTest(maxTicks = 100000, padding = 110)
	public void miningExperimentLearning(final GameTestHelper h) {
		this.experiment(h, null, "learning");
	}

	@GameTest(maxTicks = 100000, padding = 110)
	public void miningExperimentTunnelOnly(final GameTestHelper h) {
		this.experiment(h, com.clonecraft.clone.learning.MiningMove.TUNNEL, "tunnel only");
	}

	private record Stretch(String move, float perMinute, int y) {}

	private void experiment(final GameTestHelper h, final com.clonecraft.clone.learning.@org.jspecify.annotations.Nullable MiningMove only, final String label) {
		if (!"mining".equals(System.getenv("CLONECRAFT_TRAINING"))) {
			h.succeed();
			return;
		}
		int wanted = Integer.parseInt(System.getenv().getOrDefault("CLONECRAFT_STRETCHES", "30"));
		ServerLevel level = h.getLevel();
		BlockPos origin = h.absolutePos(BlockPos.ZERO);
		int cx = origin.getX();
		int cz = origin.getZ();
		List<ChunkPos> forced = new ArrayList<>();
		for (int x = (cx - BIG) >> 4; x <= (cx + BIG) >> 4; x++) {
			for (int z = (cz - BIG) >> 4; z <= (cz + BIG) >> 4; z++) {
				level.setChunkForced(x, z, true);
				forced.add(new ChunkPos(x, z));
			}
		}
		h.runBeforeTestEnd(() -> forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false)));
		Random random = new Random(label.hashCode());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = cx - BIG; x <= cx + BIG; x++) {
			for (int z = cz - BIG; z <= cz + BIG; z++) {
				for (int y = -16; y <= TOP + 6; y++) {
					BlockState state;
					if (y >= TOP) {
						state = Blocks.AIR.defaultBlockState();
					} else if (y == TOP - 1) {
						state = Blocks.GRASS_BLOCK.defaultBlockState();
					} else if (y >= TOP - 3) {
						state = Blocks.DIRT.defaultBlockState();
					} else {
						// Rich in iron from y = 4 to 10, otherwise rare.
						float iron = y >= 4 && y <= 10 ? 0.03F : 0.003F;
						float roll = random.nextFloat();
						state = (roll < iron ? Blocks.IRON_ORE : roll < iron + 0.003F ? Blocks.COAL_ORE : Blocks.STONE).defaultBlockState();
					}
					level.setBlock(pos.set(x, y, z), state, Block.UPDATE_CLIENTS);
				}
			}
		}
		level.setBlock(new BlockPos(cx + 2, TOP - 9, cz + 2), Blocks.IRON_ORE.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockPos top = new BlockPos(cx, TOP, cz);
		java.util.UUID owner = java.util.UUID.randomUUID();
		CloneEntity clone = miner(h, top);
		clone.setOwnerId(owner);
		clone.getInventory().add(new ItemStack(Items.BREAD, 32));
		clone.mining.forced = only;
		CombatBrains.MINING.reset(level, owner);
		List<Stretch> results = new ArrayList<>();
		h.onEachTick(() -> {
			if (results.size() >= wanted) {
				return;
			}
			if (h.getTick() % 100 == 0) {
				// It keeps wanting iron: what it's found is taken away (the brain has already counted it).
				for (int i = 0; i < clone.getInventory().main.size(); i++) {
					ItemStack s = clone.getInventory().main.get(i);
					if (s.is(Items.RAW_IRON) || s.is(Items.IRON_INGOT) || s.is(Items.IRON_ORE)) {
						clone.getInventory().main.set(i, ItemStack.EMPTY);
					}
				}
				clone.setHealth(clone.getMaxHealth());
				// Keep it inside the block of stone.
				BlockPos feet = clone.blockPosition();
				if (Math.abs(feet.getX() - cx) > BIG - 8 || Math.abs(feet.getZ() - cz) > BIG - 8) {
					BlockPos back = new BlockPos(cx, Math.min(feet.getY(), TOP - 4), cz);
					level.setBlock(back, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					level.setBlock(back.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					clone.teleportTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
					clone.progressionGoal.resetMiningPlan();
				}
			}
			int stretches = clone.mining.brain(level).stretches;
			if (stretches > results.size()) {
				Stretch s = new Stretch(clone.mining.lastMove().name(), clone.mining.lastPerMinute(), clone.getBlockY());
				results.add(s);
				CloneCraft.LOGGER.info("[mining-exp] {} stretch {}: {} found {} a minute (now at y={})", label, results.size(), s.move(),
					String.format("%.1f", s.perMinute()), s.y());
			}
		});
		h.succeedWhen(() -> {
			h.assertTrue(results.size() >= wanted, label + ": " + results.size() + " of " + wanted + " stretches done, " + where(clone));
			int third = Math.max(1, results.size() / 3);
			CloneCraft.LOGGER.info("[mining-exp] {} first {} stretches: {} a minute; last {}: {} a minute", label, third,
				String.format("%.2f", average(results.subList(0, third))), third,
				String.format("%.2f", average(results.subList(results.size() - third, results.size()))));
		});
	}

	private static float average(final List<Stretch> stretches) {
		float sum = 0;
		for (Stretch s : stretches) {
			sum += s.perMinute();
		}
		return sum / stretches.size();
	}

	/** At mining depth, the mining brain picks how each stretch of tunnel goes, and learns from how it went. */
	@GameTest(maxTicks = 8000, padding = 60)
	public void miningBrainLearnsFromAStretch(final GameTestHelper h) {
		CombatBrains.MINING.reset(h.getLevel(), null);
		BlockPos top = mineBlock(h, 2L, 0.0F);
		CloneEntity clone = miner(h, top);
		h.succeedWhen(() -> h.assertTrue(clone.mining.brain(h.getLevel()).stretches >= 1,
			"No stretch of mining finished yet: " + where(clone)));
	}
}
