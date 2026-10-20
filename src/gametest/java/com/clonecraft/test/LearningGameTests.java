package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.learning.CombatBrain;
import com.clonecraft.clone.learning.CombatBrains;
import com.clonecraft.clone.learning.CombatMove;
import com.clonecraft.clone.learning.NeuralNet;
import com.clonecraft.entity.CloneEntity;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class LearningGameTests {
	private record Fight(float taken, int ticks, boolean died) {}

	/**
	 * The network on its own, on a made-up problem: in situation A choice 0 pays and choice 1 costs, in situation B
	 * it's the other way round. After practice it should pick the right one in both.
	 */
	@GameTest
	public void networkLearnsWhichChoicePays(final GameTestHelper h) {
		NeuralNet net = new NeuralNet(3, 8, 2, 42L);
		float[] a = {1.0F, 0.0F, 0.3F};
		float[] b = {0.0F, 1.0F, 0.3F};
		Random random = new Random(1L);
		for (int step = 0; step < 4000; step++) {
			boolean inA = random.nextBoolean();
			int choice = random.nextInt(2);
			float reward = (inA == (choice == 0)) ? 1.0F : -1.0F;
			net.train(inA ? a : b, choice, reward, 0.05F);
		}
		float[] qa = net.predict(a);
		float[] qb = net.predict(b);
		h.assertTrue(qa[0] > qa[1] + 0.5F, "In situation A it should rate choice 0 well above 1: " + Arrays.toString(qa));
		h.assertTrue(qb[1] > qb[0] + 0.5F, "In situation B it should rate choice 1 well above 0: " + Arrays.toString(qb));
		h.succeed();
	}

	@GameTest
	public void brainSavesAndLoadsTheSame(final GameTestHelper h) {
		CombatBrain brain = new CombatBrain(7L);
		brain.fights = 12;
		brain.wins = 9;
		brain.decisions = 345;
		brain.moveCounts[CombatMove.CRIT.ordinal()] = 40;
		float[] x = new float[CombatBrain.INPUTS];
		Arrays.fill(x, 0.5F);
		brain.net.train(x, 2, 3.0F, 0.1F);
		var json = CombatBrain.CODEC.encodeStart(JsonOps.INSTANCE, brain).getOrThrow();
		CombatBrain loaded = CombatBrain.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		h.assertTrue(Arrays.equals(brain.net.predict(x), loaded.net.predict(x)), "The loaded network should give the same answers");
		h.assertTrue(loaded.fights == 12 && loaded.wins == 9 && loaded.decisions == 345, "The record should load back");
		h.assertTrue(loaded.moveCounts[CombatMove.CRIT.ordinal()] == 40, "Move counts should load back");
		h.succeed();
	}

	/** A clone fights a husk: afterwards its owner's brain has the fight on record and has picked moves. */
	@GameTest(maxTicks = 600, padding = 40)
	public void cloneLearnsFromAFight(final GameTestHelper h) {
		CombatBrains.reset(h.getLevel(), null);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		CloneEntity clone = h.spawn(CloneCraft.CLONE, 1.5F, 1.0F, 1.5F);
		clone.setGrownUp();
		clone.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		h.spawn(EntityTypes.HUSK, 6, 1, 6);
		h.succeedWhen(() -> {
			CombatBrain brain = CombatBrains.get(h.getLevel(), null);
			h.assertTrue(brain.fights >= 1, "No fight recorded yet");
			h.assertTrue(brain.wins >= 1, "The clone should have won (it has a diamond sword)");
			h.assertTrue(brain.decisions > 0, "No moves picked");
		});
	}

	// ---------------------------------------------------------------- experiments (not part of the normal tests)
	// CLONECRAFT_TRAINING=learn: clones sharing one brain fight husk after husk, and it logs how the early fights
	// compare with the late ones. CLONECRAFT_TRAINING=moves: one arena per move, each always making that move, to see
	// how good each move is on its own. CLONECRAFT_FIGHTS sets how many fights.

	@GameTest(maxTicks = 120000, padding = 60)
	public void trainingExperiment(final GameTestHelper h) {
		this.experiment(h, "learn", null);
	}

	@GameTest(maxTicks = 120000, padding = 60)
	public void onlyPressing(final GameTestHelper h) {
		this.experiment(h, "moves", CombatMove.PRESS);
	}

	@GameTest(maxTicks = 120000, padding = 60)
	public void onlyCriticalJumps(final GameTestHelper h) {
		this.experiment(h, "moves", CombatMove.CRIT);
	}

	@GameTest(maxTicks = 120000, padding = 60)
	public void onlyHitAndBack(final GameTestHelper h) {
		this.experiment(h, "moves", CombatMove.HIT_AND_BACK);
	}

	@GameTest(maxTicks = 120000, padding = 60)
	public void onlyCircling(final GameTestHelper h) {
		this.experiment(h, "moves", CombatMove.CIRCLE);
	}

	private void experiment(final GameTestHelper h, final String mode, final @org.jspecify.annotations.Nullable CombatMove only) {
		if (!mode.equals(System.getenv("CLONECRAFT_TRAINING"))) {
			h.succeed();
			return;
		}
		String label = only == null ? "learning" : only.name();
		int fightsWanted = Integer.parseInt(System.getenv().getOrDefault("CLONECRAFT_FIGHTS", "100"));
		if (only == null) {
			CombatBrains.reset(h.getLevel(), null);
		}
		// The test only keeps its own small area running: keep the whole arena's chunks running too, or the fighters
		// freeze at a chunk edge.
		BlockPos low = h.absolutePos(new BlockPos(-12, 0, -12));
		BlockPos high = h.absolutePos(new BlockPos(19, 0, 19));
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = Math.min(low.getX(), high.getX()) >> 4; cx <= Math.max(low.getX(), high.getX()) >> 4; cx++) {
			for (int cz = Math.min(low.getZ(), high.getZ()) >> 4; cz <= Math.max(low.getZ(), high.getZ()) >> 4; cz++) {
				h.getLevel().setChunkForced(cx, cz, true);
				forced.add(new ChunkPos(cx, cz));
			}
		}
		h.runBeforeTestEnd(() -> forced.forEach(c -> h.getLevel().setChunkForced(c.x(), c.z(), false)));
		for (int x = -12; x < 20; x++) {
			for (int z = -12; z < 20; z++) {
				h.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					h.setBlock(x, y, z, x == -12 || x == 19 || z == -12 || z == 19 ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		List<Fight> fights = new ArrayList<>();
		CloneEntity[] clone = {null};
		LivingEntity[] husk = {null};
		int[] started = {0};
		h.onEachTick(() -> {
			if (fights.size() >= fightsWanted) {
				return;
			}
			if (clone[0] == null || !clone[0].isAlive()) {
				if (clone[0] != null) {
					fights.add(new Fight(20.0F, (int)h.getTick() - started[0], true));
					if (husk[0] != null) {
						husk[0].discard();
						husk[0] = null;
					}
				}
				clone[0] = h.spawn(CloneCraft.CLONE, 4.5F, 1.0F, 4.5F);
				clone[0].setGrownUp();
				clone[0].setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
				clone[0].combat.forced = only;
			}
			if (husk[0] == null || !husk[0].isAlive() || h.getTick() - started[0] > 600) {
				if (husk[0] != null) {
					if (!husk[0].isAlive()) {
						fights.add(new Fight(clone[0].getMaxHealth() - clone[0].getHealth(), (int)h.getTick() - started[0], false));
					}
					husk[0].discard();
				}
				clone[0].setHealth(clone[0].getMaxHealth());
				husk[0] = h.spawn(EntityTypes.HUSK, 10, 1, 10);
				started[0] = (int)h.getTick();
				if (fights.size() % 20 == 0 && !fights.isEmpty()) {
					report(h, label, fights.subList(fights.size() - 20, fights.size()), fights.size());
					CloneCraft.LOGGER.info("[training] {} moves so far: {}", label, Arrays.toString(CombatBrains.get(h.getLevel(), null).moveCounts));
				}
			}
		});
		h.succeedWhen(() -> {
			h.assertTrue(fights.size() >= fightsWanted, fights.size() + " of " + fightsWanted + " fights done");
			if (only == null) {
				CloneCraft.LOGGER.info("[training] moves used: {}", Arrays.toString(CombatBrains.get(h.getLevel(), null).moveCounts));
			}
			report(h, label + " ALL", fights, fights.size());
		});
	}

	private static void report(final GameTestHelper h, final String label, final List<Fight> fights, final int upTo) {
		float taken = 0;
		int ticks = 0;
		int deaths = 0;
		for (Fight f : fights) {
			taken += f.taken();
			ticks += f.ticks();
			deaths += f.died() ? 1 : 0;
		}
		CombatBrain brain = CombatBrains.get(h.getLevel(), null);
		CloneCraft.LOGGER.info("[training] {} fights {}-{}: damage taken {} per fight, {} ticks per fight, {} deaths (exploring {}%)",
			label, upTo - fights.size() + 1, upTo, String.format("%.2f", taken / fights.size()), ticks / fights.size(), deaths,
			Math.round(brain.exploreChance() * 100));
	}
}
