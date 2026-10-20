package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.ai.ProgressionGoal;
import com.clonecraft.clone.progression.CloneStorage;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.CloneSpectating;
import com.clonecraft.network.RemoteControl;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Game Progression: the option, crafting and smelting, and the AI chopping, mining and hunting in real worlds. */
public class ProgressionGameTests {
	// ---------------------------------------------------------------- helpers

	private static void fill(final GameTestHelper h, final Block block, final int fromY, final int toY) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = fromY; y <= toY; y++) {
					h.setBlock(x, y, z, block);
				}
			}
		}
	}

	/** A grown-up clone with Game Progression on. "thinking" clones have their AI; others only tick (crafting tests). */
	private static CloneEntity progressingClone(final GameTestHelper h, final int x, final int y, final int z, final boolean thinking) {
		CloneEntity clone = thinking
			? h.spawn(CloneCraft.CLONE, x + 0.5F, (float)y, z + 0.5F)
			: h.spawnWithNoFreeWill(CloneCraft.CLONE, x + 0.5F, (float)y, z + 0.5F);
		clone.setGrownUp();
		clone.setOption(CloneOption.GAME_PROGRESSION, true);
		return clone;
	}

	/** Logs what the clone is doing every 5 seconds (for working out why a test fails). */
	static void debug(final GameTestHelper h, final CloneEntity clone, final String name) {
		h.onEachTick(() -> {
			if (h.getTick() % 100 == 0) {
				BlockPos t = clone.progressionGoal.getCurrentTarget();
				CloneCraft.LOGGER.info("[prog-debug {}] t={} role={} obj={} at={} target={} mining={} inv={}", name, h.getTick(), clone.progressionGoal.getRole(),
					clone.progressionGoal.getObjective(), h.relativePos(clone.blockPosition()), t == null ? null : h.relativePos(t) + "=" + h.getLevel().getBlockState(t).getBlock(),
					clone.mineGoal.hasRequest(), clone.getInventory().main.stream().filter(s -> !s.isEmpty()).map(s -> s.getCount() + " " + s.getItem()).toList());
			}
		});
	}

	private static void give(final CloneEntity clone, final Item item, final int count) {
		clone.getInventory().add(new ItemStack(item, count));
	}

	private static int count(final CloneEntity clone, final Item item) {
		return clone.getInventory().count(s -> s.is(item));
	}

	private static boolean has(final CloneEntity clone, final Item item) {
		return count(clone, item) > 0;
	}

	// ---------------------------------------------------------------- the option

	@GameTest
	public void gameProgressionIsAnOffByDefaultOption(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		CloneEntity clone = h.spawnWithNoFreeWill(CloneCraft.CLONE, 3.5F, 1.0F, 3.5F);
		h.assertFalse(clone.hasOption(CloneOption.GAME_PROGRESSION), "Game Progression should start off");
		h.assertValueEqual(CloneOption.GAME_PROGRESSION.description,
			"Enables autonomous gathering, mining, gear crafting, and Ender Dragon progression while AFK.", "tooltip");
		h.assertTrue((CloneOption.GAME_PROGRESSION.bit() & RemoteControl.BULK_LOCKED) == 0, "Bulk Edit should be able to change it");
		clone.setOption(CloneOption.GAME_PROGRESSION, true);
		h.assertTrue((clone.getOptions() & CloneOption.GAME_PROGRESSION.bit()) != 0, "The option should be stored in the synced options");
		h.succeed();
	}

	// ---------------------------------------------------------------- crafting and smelting

	/** A crafting table right next to where the crafting tests put their clone (3, 1, 3). */
	private static void tableNextToClone(final GameTestHelper h) {
		h.setBlock(4, 1, 3, Blocks.CRAFTING_TABLE);
	}

	@GameTest
	public void makesAWoodenPickaxeAtAPlacedTable(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		tableNextToClone(h);
		CloneEntity clone = progressingClone(h, 3, 1, 3, false);
		give(clone, Items.OAK_LOG, 3);
		clone.progressionGoal.craftWhatWeCan();
		h.assertTrue(has(clone, Items.WOODEN_PICKAXE), "Should have made a wooden pickaxe at the table");
		h.assertFalse(has(clone, Items.CRAFTING_TABLE), "Shouldn't make a table to carry when there's one right there");
		h.succeed();
	}

	@GameTest
	public void noPickaxeWithoutAPlacedTable(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		CloneEntity clone = progressingClone(h, 3, 1, 3, false);
		give(clone, Items.CRAFTING_TABLE, 1);
		give(clone, Items.OAK_LOG, 3);
		clone.progressionGoal.craftWhatWeCan();
		h.assertFalse(has(clone, Items.WOODEN_PICKAXE), "A pickaxe needs a 3x3 grid: a crafting table in the inventory isn't enough");
		h.assertTrue(has(clone, Items.OAK_LOG) || has(clone, Items.OAK_PLANKS), "Shouldn't waste its wood");
		h.succeed();
	}

	@GameTest
	public void oneLogStillMakesTheCraftingTableFirst(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		CloneEntity clone = progressingClone(h, 3, 1, 3, false);
		give(clone, Items.BIRCH_LOG, 1);
		clone.progressionGoal.craftWhatWeCan();
		h.assertTrue(has(clone, Items.CRAFTING_TABLE), "One log (4 planks) should become a crafting table, not sticks");
		h.succeed();
	}

	@GameTest
	public void makesAWoodenSwordWhenThereIsNoStone(final GameTestHelper h) {
		fill(h, Blocks.GRASS_BLOCK, 0, 0);
		tableNextToClone(h);
		CloneEntity clone = progressingClone(h, 3, 1, 3, false);
		give(clone, Items.WOODEN_PICKAXE, 1);
		give(clone, Items.OAK_LOG, 2);
		clone.progressionGoal.craftWhatWeCan();
		h.assertTrue(has(clone, Items.WOODEN_SWORD), "Should have made a wooden sword to defend itself");
		h.succeed();
	}

	@GameTest(maxTicks = 240, padding = 40)
	public void runsAwayWhenBadlyHurt(final GameTestHelper h) {
		// A big walled-in floor, so there's room to run (the test area alone is only 8 blocks across) and the test's floor
		// (which floats over nothing) has no edge to run off.
		for (int x = -20; x < 28; x++) {
			for (int z = -20; z < 28; z++) {
				h.setBlock(x, 0, z, Blocks.STONE);
				// Take down the test area's invisible walls so it isn't cornered in an 8x8 box.
				if (x < 0 || x > 7 || z < 0 || z > 7) {
					for (int y = 1; y <= 4; y++) {
						h.setBlock(x, y, z, x == -20 || x == 27 || z == -20 || z == 27 ? Blocks.STONE : Blocks.AIR);
					}
				}
			}
		}
		CloneEntity clone = progressingClone(h, 2, 1, 2, true);
		clone.setHealth(4.0F);
		net.minecraft.world.entity.monster.zombie.Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 5, 1, 2);
		// At 4 health (and unarmed) a zombie kills it in two hits, and it can't win the fight: staying alive for
		// 10 seconds means it ran. (Exact distances vary: it may circle round while running.)
		h.runAtTickTime(200, () -> {
			h.assertTrue(clone.isAlive(), "The clone died instead of running away");
			h.assertTrue(zombie.isAlive(), "The zombie should still be there (the clone ran rather than fought)");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void sleepsInItsBedAtNight(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		h.setTime(18000);
		CloneEntity clone = progressingClone(h, 3, 1, 3, true);
		give(clone, Items.BED.white(), 1);
		boolean[] slept = {false};
		h.onEachTick(() -> {
			if (!slept[0] && clone.isSleeping()) {
				slept[0] = true;
				h.setTime(1000);
			}
		});
		h.succeedWhen(() -> {
			h.assertTrue(slept[0], "The clone hasn't gone to bed");
			h.assertFalse(clone.isSleeping(), "The clone should get up in the morning");
		});
		// Whatever happens, leave it daytime: the clock is shared with every other test.
		h.runBeforeTestEnd(() -> h.setTime(1000));
	}

	@GameTest
	public void craftsStoneToolsAndFurnace(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		tableNextToClone(h);
		CloneEntity clone = progressingClone(h, 3, 1, 3, false);
		give(clone, Items.WOODEN_PICKAXE, 1);
		give(clone, Items.COBBLESTONE, 13);
		give(clone, Items.OAK_LOG, 2);
		// Once per "think", like the AI: the furnace only comes after the stone pickaxe.
		clone.progressionGoal.craftWhatWeCan();
		clone.progressionGoal.craftWhatWeCan();
		h.assertTrue(has(clone, Items.STONE_PICKAXE), "Should have made a stone pickaxe");
		h.assertTrue(has(clone, Items.STONE_SWORD), "Should have made a stone sword");
		h.assertTrue(has(clone, Items.FURNACE), "Should have made a furnace");
		h.succeed();
	}

	@GameTest
	public void craftsFullIronGearAndWearsTheArmour(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		tableNextToClone(h);
		CloneEntity clone = progressingClone(h, 3, 1, 3, false);
		give(clone, Items.IRON_INGOT, 33);
		give(clone, Items.OAK_LOG, 3);
		clone.progressionGoal.craftWhatWeCan();
		h.assertTrue(clone.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "Should wear an iron helmet");
		h.assertTrue(clone.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "Should wear an iron chestplate");
		h.assertTrue(clone.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS), "Should wear iron leggings");
		h.assertTrue(clone.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "Should wear iron boots");
		for (Item tool : new Item[]{Items.IRON_PICKAXE, Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_SHOVEL}) {
			h.assertTrue(has(clone, tool), "Should have made " + tool);
		}
		h.assertValueEqual(clone.progressionGoal.ironNeeded(), 0, "iron still needed");
		h.assertValueEqual(count(clone, Items.IRON_INGOT), 0, "iron left over");
		h.succeed();
	}

	@GameTest(maxTicks = 500)
	public void furnaceSmeltsIronWithRealFuel(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		CloneEntity clone = progressingClone(h, 3, 1, 3, false);
		give(clone, Items.FURNACE, 1);
		give(clone, Items.RAW_IRON, 2);
		give(clone, Items.COAL, 1);
		// 10 seconds per item, like a real furnace.
		h.runAfterDelay(150, () -> h.assertValueEqual(count(clone, Items.IRON_INGOT), 0, "ingots after 7.5 seconds"));
		h.succeedWhen(() -> {
			h.assertValueEqual(count(clone, Items.IRON_INGOT), 2, "iron ingots");
			h.assertValueEqual(count(clone, Items.RAW_IRON), 0, "raw iron left");
			h.assertValueEqual(count(clone, Items.COAL), 0, "coal left (one coal burns for 8 items)");
		});
	}

	@GameTest
	public void craftsEyesOfEnder(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		CloneEntity clone = progressingClone(h, 3, 1, 3, false);
		give(clone, Items.ENDER_PEARL, 3);
		give(clone, Items.BLAZE_ROD, 1);
		clone.progressionGoal.craftWhatWeCan();
		h.assertValueEqual(count(clone, Items.ENDER_EYE), 2, "eyes of ender (one blaze rod makes two powder)");
		h.assertValueEqual(count(clone, Items.ENDER_PEARL), 1, "pearls left");
		h.succeed();
	}

	// ---------------------------------------------------------------- the AI in a real world

	@GameTest(maxTicks = 1600, padding = 40)
	public void stage1ChopsATreeAndMakesAPickaxe(final GameTestHelper h) {
		fill(h, Blocks.GRASS_BLOCK, 0, 0);
		for (int y = 1; y <= 4; y++) {
			h.setBlock(5, y, 5, Blocks.OAK_LOG);
		}
		for (int x = 4; x <= 6; x++) {
			for (int z = 4; z <= 6; z++) {
				if (x != 5 || z != 5) {
					h.setBlock(x, 4, z, Blocks.OAK_LEAVES);
				}
			}
		}
		h.setBlock(5, 5, 5, Blocks.OAK_LEAVES);
		CloneEntity clone = progressingClone(h, 1, 1, 1, true);
		h.succeedWhen(() -> {
			h.assertTrue(has(clone, Items.CRAFTING_TABLE), "No crafting table yet");
			h.assertTrue(has(clone, Items.WOODEN_PICKAXE), "No wooden pickaxe yet");
		});
	}

	@GameTest(maxTicks = 4000, padding = 40)
	public void stage2MinesStoneAndIron(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 3);
		h.setBlock(5, 1, 5, Blocks.IRON_ORE);
		h.setBlock(2, 2, 5, Blocks.COAL_ORE);
		h.setBlock(6, 2, 2, Blocks.IRON_ORE);
		CloneEntity clone = progressingClone(h, 1, 4, 1, true);
		give(clone, Items.CRAFTING_TABLE, 1);
		give(clone, Items.WOODEN_PICKAXE, 1);
		give(clone, Items.OAK_LOG, 3);
		h.succeedWhen(() -> {
			h.assertTrue(has(clone, Items.STONE_PICKAXE), "No stone pickaxe yet");
			h.assertTrue(has(clone, Items.FURNACE), "No furnace yet");
			h.assertTrue(has(clone, Items.RAW_IRON) || has(clone, Items.IRON_INGOT), "No iron yet");
		});
	}

	@GameTest(maxTicks = 1200, padding = 40)
	public void stage3HuntsWhenHungry(final GameTestHelper h) {
		fill(h, Blocks.GRASS_BLOCK, 0, 0);
		for (int i = 0; i < 8; i++) {
			h.setBlock(i, 1, 0, Blocks.OAK_FENCE);
			h.setBlock(i, 1, 7, Blocks.OAK_FENCE);
			h.setBlock(0, 1, i, Blocks.OAK_FENCE);
			h.setBlock(7, 1, i, Blocks.OAK_FENCE);
		}
		h.spawn(EntityTypes.COW, 5, 1, 5);
		CloneEntity clone = progressingClone(h, 2, 1, 2, true);
		give(clone, Items.CRAFTING_TABLE, 1);
		clone.getFoodData().setFoodLevel(6);
		h.succeedWhen(() -> h.assertTrue(clone.getFoodData().getFoodLevel() > 6, "The clone hasn't eaten yet"));
	}

	@GameTest(maxTicks = 300, padding = 40)
	public void neverMinesNextToLava(final GameTestHelper h) {
		fill(h, Blocks.GLASS, 0, 0);
		BlockPos stone = new BlockPos(4, 1, 4);
		h.setBlock(stone, Blocks.STONE);
		h.setBlock(5, 1, 4, Blocks.LAVA);
		h.setBlock(6, 1, 4, Blocks.GLASS);
		h.setBlock(5, 1, 3, Blocks.GLASS);
		h.setBlock(5, 1, 5, Blocks.GLASS);
		h.setBlock(5, 2, 4, Blocks.GLASS);
		CloneEntity clone = progressingClone(h, 2, 1, 2, true);
		give(clone, Items.CRAFTING_TABLE, 1);
		give(clone, Items.WOODEN_PICKAXE, 1);
		h.runAfterDelay(280, () -> {
			h.assertBlockPresent(Blocks.STONE, stone);
			h.assertTrue(clone.isAlive() && !clone.isInLava(), "The clone ended up in lava");
			h.succeed();
		});
	}

	// ---------------------------------------------------------------- drop claims

	@GameTest(maxTicks = 600, padding = 40)
	public void otherClonesLeaveMinedDropsAlone(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		h.setBlock(3, 1, 3, Blocks.OAK_LOG);
		// The miner stands a few blocks off; the other clone stands right where the log will drop.
		CloneEntity miner = h.spawn(CloneCraft.CLONE, 6.5F, 1.0F, 3.5F);
		miner.setGrownUp();
		miner.setOption(CloneOption.BREAK_BLOCKS, true);
		miner.setOption(CloneOption.WALK_TO_ITEMS, true);
		miner.breakBlocks.add(net.minecraft.resources.Identifier.withDefaultNamespace("oak_log"));
		CloneEntity bystander = h.spawnWithNoFreeWill(CloneCraft.CLONE, 3.5F, 1.0F, 4.5F);
		bystander.setGrownUp();
		// While the claim lasts (30 seconds after the log drops), the bystander must never take it. After that it's
		// fair game, so items can't stay locked away forever.
		h.onEachTick(() -> h.assertValueEqual(count(bystander, Items.OAK_LOG), 0, "logs the bystander took while the miner's claim lasted"));
		h.runAtTickTime(550, () -> {
			h.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(3, 1, 3));
			h.succeed();
		});
	}

	// ---------------------------------------------------------------- Clone Storage

	private static net.minecraft.world.Container storageAt(final GameTestHelper h, final BlockPos rel) {
		CloneStorage.place(h.getLevel(), h.absolutePos(rel), net.minecraft.core.Direction.NORTH);
		return CloneStorage.at(h.getLevel(), h.absolutePos(rel));
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void storesSpareItemsInCloneStorage(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		net.minecraft.world.Container chest = storageAt(h, new BlockPos(6, 1, 3));
		CloneEntity clone = progressingClone(h, 2, 1, 3, true);
		give(clone, Items.WOODEN_PICKAXE, 1);
		// 27 stacks of bones + the pickaxe leaves 8 free slots: time to store.
		for (int i = 0; i < 27; i++) {
			give(clone, Items.BONE, 64);
		}
		h.succeedWhen(() -> {
			h.assertValueEqual(CloneStorage.count(chest, s -> s.is(Items.BONE)), 27 * 64, "bones in Clone Storage");
			h.assertValueEqual(count(clone, Items.BONE), 0, "bones the clone still carries");
		});
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void takesWoodFromCloneStorage(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		net.minecraft.world.Container chest = storageAt(h, new BlockPos(6, 1, 3));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 16));
		CloneEntity clone = progressingClone(h, 2, 1, 3, true);
		h.succeedWhen(() -> h.assertTrue(CloneStorage.count(chest, s -> s.is(Items.OAK_LOG)) < 16
			&& (has(clone, Items.OAK_LOG) || has(clone, Items.OAK_PLANKS) || has(clone, Items.CRAFTING_TABLE)), "The clone hasn't taken any wood"));
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void takesWoodFromAnyChest(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		h.setBlock(6, 1, 3, Blocks.CHEST);
		net.minecraft.world.Container chest = h.getBlockEntity(new BlockPos(6, 1, 3), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 16));
		CloneEntity clone = progressingClone(h, 2, 1, 3, true);
		h.succeedWhen(() -> h.assertTrue(CloneStorage.count(chest, s -> s.is(Items.OAK_LOG)) < 16
			&& (has(clone, Items.OAK_LOG) || has(clone, Items.OAK_PLANKS) || has(clone, Items.CRAFTING_TABLE)), "The clone hasn't taken wood from the chest"));
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void usesTheBonusChest(final GameTestHelper h) {
		fill(h, Blocks.GRASS_BLOCK, 0, 0);
		h.setBlock(6, 1, 3, Blocks.CHEST);
		var chest = h.getBlockEntity(new BlockPos(6, 1, 3), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
		chest.setLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.SPAWN_BONUS_CHEST);
		chest.setLootTableSeed(12345L);
		CloneEntity clone = progressingClone(h, 2, 1, 3, true);
		h.succeedWhen(() -> h.assertTrue(has(clone, Items.OAK_LOG) || has(clone, Items.SPRUCE_LOG) || has(clone, Items.BIRCH_LOG)
			|| has(clone, Items.JUNGLE_LOG) || has(clone, Items.ACACIA_LOG) || has(clone, Items.DARK_OAK_LOG)
			|| clone.getInventory().count(s -> s.is(net.minecraft.tags.ItemTags.PLANKS)) > 0 || has(clone, Items.CRAFTING_TABLE)
			|| has(clone, Items.WOODEN_PICKAXE) || has(clone, Items.STONE_PICKAXE), "The clone hasn't used the bonus chest"));
	}

	@GameTest(maxTicks = 1200, padding = 40)
	public void growsATreeWithBoneMeal(final GameTestHelper h) {
		fill(h, Blocks.GRASS_BLOCK, 0, 0);
		CloneEntity clone = progressingClone(h, 1, 1, 1, true);
		give(clone, Items.OAK_SAPLING, 2);
		give(clone, Items.BONE_MEAL, 32);
		h.succeedWhen(() -> h.assertTrue(has(clone, Items.OAK_LOG) || has(clone, Items.OAK_PLANKS) || has(clone, Items.CRAFTING_TABLE),
			"The clone hasn't got any wood from a tree it grew"));
	}

	@GameTest(maxTicks = 300, padding = 40)
	public void plantsSaplingsWhenThereAreNoTrees(final GameTestHelper h) {
		fill(h, Blocks.GRASS_BLOCK, 0, 0);
		CloneEntity clone = progressingClone(h, 1, 1, 1, true);
		give(clone, Items.BIRCH_SAPLING, 1);
		h.succeedWhen(() -> {
			boolean[] planted = {false};
			h.forEveryBlockInStructure(pos -> planted[0] |= h.getBlockState(pos).is(Blocks.BIRCH_SAPLING));
			h.assertTrue(planted[0], "No sapling planted yet");
		});
	}

	@GameTest(maxTicks = 300, padding = 40)
	public void takesBetterToolsFromAChest(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		h.setBlock(6, 1, 3, Blocks.CHEST);
		net.minecraft.world.Container chest = h.getBlockEntity(new BlockPos(6, 1, 3), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.STONE_PICKAXE));
		chest.setItem(1, new ItemStack(Items.IRON_HELMET));
		chest.setItem(2, new ItemStack(Items.DIRT, 8));
		CloneEntity clone = progressingClone(h, 2, 1, 3, true);
		give(clone, Items.WOODEN_PICKAXE, 1);
		h.succeedWhen(() -> {
			h.assertTrue(has(clone, Items.STONE_PICKAXE), "Should have taken the better pickaxe");
			h.assertTrue(clone.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET), "Should be wearing the helmet");
			h.assertValueEqual(CloneStorage.count(chest, s -> s.is(Items.DIRT)), 8, "dirt left in the chest (nothing it needs)");
		});
	}

	// ---------------------------------------------------------------- trading

	private static <T extends net.minecraft.world.entity.npc.villager.AbstractVillager> T trader(final GameTestHelper h,
		final net.minecraft.world.entity.EntityType<T> type, final net.minecraft.world.item.trading.MerchantOffer... offers) {
		T merchant = h.spawnWithNoFreeWill(type, 6.5F, 1.0F, 3.5F);
		if (merchant instanceof net.minecraft.world.entity.npc.villager.Villager villager) {
			// Unemployed villagers don't trade (and lose any trades they're given): make this one a toolsmith first.
			villager.setVillagerData(villager.getVillagerData().withProfession(h.getLevel().registryAccess(),
				net.minecraft.world.entity.npc.villager.VillagerProfession.TOOLSMITH));
			villager.setVillagerXp(1);
		}
		net.minecraft.world.item.trading.MerchantOffers list = new net.minecraft.world.item.trading.MerchantOffers();
		list.addAll(List.of(offers));
		// Set the trade list itself (overrideOffers only affects the client's trading screen).
		CloneCraftGameTests.setField(merchant, net.minecraft.world.entity.npc.villager.AbstractVillager.class, "offers", list);
		return merchant;
	}

	private static net.minecraft.world.item.trading.MerchantOffer offer(final Item cost, final int costCount, final Item result, final int resultCount) {
		return new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(cost, costCount),
			new ItemStack(result, resultCount), 12, 1, 0.05F);
	}

	@GameTest(maxTicks = 400, padding = 40)
	public void buysABetterPickaxeFromAVillager(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		var villager = trader(h, EntityTypes.VILLAGER, offer(Items.EMERALD, 3, Items.IRON_PICKAXE, 1));
		CloneEntity clone = progressingClone(h, 2, 1, 3, true);
		give(clone, Items.WOODEN_PICKAXE, 1);
		give(clone, Items.EMERALD, 5);
		h.succeedWhen(() -> {
			h.assertTrue(has(clone, Items.IRON_PICKAXE), "Should have bought the iron pickaxe");
			h.assertValueEqual(count(clone, Items.EMERALD), 2, "emeralds left (paid 3)");
			h.assertValueEqual(villager.getOffers().get(0).getUses(), 1, "times the villager's offer was used");
		});
	}

	@GameTest(maxTicks = 500, padding = 40)
	public void sellsSparesForEmeraldsToBuyFood(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		trader(h, EntityTypes.VILLAGER, offer(Items.WHEAT, 20, Items.EMERALD, 1), offer(Items.EMERALD, 1, Items.BREAD, 6));
		CloneEntity clone = progressingClone(h, 2, 1, 3, true);
		give(clone, Items.WHEAT, 64);
		clone.getFoodData().setFoodLevel(6);
		h.succeedWhen(() -> {
			h.assertTrue(clone.getFoodData().getFoodLevel() > 6, "The clone hasn't eaten bought food yet");
			h.assertValueEqual(count(clone, Items.WHEAT), 44, "wheat left (sold 20 for an emerald)");
		});
	}

	@GameTest(maxTicks = 500, padding = 40)
	public void buysWoodFromAWanderingTraderOnSuperflat(final GameTestHelper h) {
		fill(h, Blocks.GRASS_BLOCK, 0, 0);
		trader(h, EntityTypes.WANDERING_TRADER, offer(Items.EMERALD, 1, Items.OAK_LOG, 8));
		CloneEntity clone = progressingClone(h, 2, 1, 3, true);
		give(clone, Items.EMERALD, 2);
		h.succeedWhen(() -> h.assertTrue(has(clone, Items.OAK_LOG) || has(clone, Items.OAK_PLANKS) || has(clone, Items.CRAFTING_TABLE),
			"The clone hasn't bought any wood"));
	}

	@GameTest(maxTicks = 200, padding = 40)
	public void asksForStoneWhenThereIsNoneToDig(final GameTestHelper h) {
		fill(h, Blocks.GRASS_BLOCK, 0, 0);
		h.setBlock(3, 1, 5, Blocks.CRAFTING_TABLE);
		CloneEntity clone = progressingClone(h, 3, 1, 3, true);
		give(clone, Items.WOODEN_PICKAXE, 1);
		give(clone, Items.OAK_LOG, 8);
		CloneCraft.LOGGER.info("[flat-check] the game test world is flat: {}", h.getLevel().isFlat());
		h.succeedWhen(() -> {
			if (!h.getLevel().isFlat()) {
				return;
			}
			h.assertTrue(clone.progressionGoal.getObjective() == ProgressionGoal.Objective.NEED,
				"In a superflat world with no stone it should ask for some, but is: " + clone.progressionGoal.getObjective());
			h.assertTrue(clone.progressionGoal.describe().contains("cobblestone"), "Should say it needs cobblestone: " + clone.progressionGoal.describe());
		});
	}

	@GameTest(maxTicks = 800, padding = 40)
	public void makesAndFillsItsOwnCloneStorage(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		h.setBlock(3, 1, 5, Blocks.CRAFTING_TABLE);
		CloneEntity clone = progressingClone(h, 3, 1, 3, true);
		give(clone, Items.WOODEN_PICKAXE, 1);
		for (int i = 0; i < 4; i++) {
			give(clone, Items.OAK_LOG, 64);
		}
		for (int i = 0; i < 23; i++) {
			give(clone, Items.BONE, 64);
		}
		h.succeedWhen(() -> {
			BlockPos chest = CloneStorage.nearest(h.getLevel(), clone.blockPosition(), 8, s -> s.is(Items.BONE));
			h.assertTrue(chest != null, "No Clone Storage chest with bones in it yet");
		});
	}

	// ---------------------------------------------------------------- teams and watching

	@GameTest(maxTicks = 300, padding = 40)
	public void ownedClonesSplitTheWork(final GameTestHelper h) {
		fill(h, Blocks.GRASS_BLOCK, 0, 0);
		ServerPlayer owner = h.makeMockServerPlayerInLevel();
		List<CloneEntity> clones = new java.util.ArrayList<>();
		for (int i = 0; i < 4; i++) {
			CloneEntity clone = progressingClone(h, 1 + i * 2, 1, 4, true);
			clone.setOwner(owner);
			clones.add(clone);
		}
		h.succeedWhen(() -> {
			Set<ProgressionGoal.Role> roles = new java.util.HashSet<>();
			for (CloneEntity clone : clones) {
				roles.add(clone.progressionGoal.getRole());
			}
			h.assertValueEqual(roles, Set.of(ProgressionGoal.Role.values()), "jobs given out");
		});
	}

	@GameTest(maxTicks = 100, padding = 40)
	public void watchingAClonePutsYouBackAfterwards(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		ServerPlayer player = h.makeMockServerPlayerInLevel();
		// The test player always *reports* Creative, so check its real game mode underneath.
		GameType before = player.gameMode.getGameModeForPlayer();
		Vec3 start = player.position();
		CloneEntity clone = h.spawnWithNoFreeWill(CloneCraft.CLONE, 6.5F, 1.0F, 6.5F);
		CloneSpectating.start(player, clone);
		h.assertValueEqual(player.gameMode.getGameModeForPlayer(), GameType.SPECTATOR, "game mode while watching");
		h.assertTrue(player.getCamera() == clone, "The player's view should follow the clone");
		h.assertTrue(CloneSpectating.isWatching(player), "Should be watching");
		clone.kill(h.getLevel());
		h.runAfterDelay(5, () -> {
			h.assertFalse(CloneSpectating.isWatching(player), "Should have stopped watching when the clone died");
			h.assertTrue(player.getCamera() == player, "The view should be back on the player");
			h.assertValueEqual(player.gameMode.getGameModeForPlayer(), before, "game mode afterwards");
			h.assertTrue(player.position().distanceTo(start) < 0.01, "The player should be back where they started");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 300, padding = 40)
	public void dropsJunkWhenFullAndLeavesIt(final GameTestHelper h) {
		fill(h, Blocks.STONE, 0, 0);
		CloneEntity clone = progressingClone(h, 3, 1, 3, true);
		for (int i = 0; i < 34; i++) {
			clone.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
		}
		clone.getInventory().select(0);
		// Everything but the held stack is junk: it should all be thrown out, and never picked back up.
		h.runAfterDelay(60, () -> h.assertValueEqual(count(clone, Items.DIRT), 64, "dirt kept after dropping junk"));
		h.runAfterDelay(250, () -> {
			h.assertValueEqual(count(clone, Items.DIRT), 64, "dirt kept (picked junk back up?)");
			h.succeed();
		});
	}
}
