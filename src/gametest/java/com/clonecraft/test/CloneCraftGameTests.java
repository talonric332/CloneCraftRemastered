package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.block.LifeInducerBlockEntity;
import com.clonecraft.block.SterilizerBlock;
import com.clonecraft.clone.CloneInventory;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.ai.ShareGoal;
import com.clonecraft.dna.Dna;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.item.SchematicSelectorItem;
import com.clonecraft.mixin.MobAccessor;
import com.clonecraft.network.Payloads;
import com.clonecraft.network.RemoteControl;
import com.clonecraft.network.ServerNetworking;
import com.clonecraft.schematic.SchematicStore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** In-game checks for the bug fixes. Run with: gradlew runGameTest */
public class CloneCraftGameTests {
	private static final Item[] HOTBAR = {Items.DIAMOND_SWORD, Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.SAND,
		Items.GRAVEL, Items.GLASS, Items.TORCH};

	// ---------------------------------------------------------------- helpers

	private static void floor(final GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A grown-up clone that stands still (no AI goals), but still ticks: eats, picks things up, remembers chests. */
	private static CloneEntity stillClone(final GameTestHelper h, final int x, final int z) {
		CloneEntity clone = h.spawnWithNoFreeWill(CloneCraft.CLONE, x + 0.5F, 1.0F, z + 0.5F);
		clone.setGrownUp();
		return clone;
	}

	/** Fills the hotbar (a sword in the selected slot 0, then 10 of 8 different blocks) and puts 5 bread deep in the inventory. */
	private static ItemStack fillHotbarAndHideBread(final CloneEntity clone) {
		CloneInventory inv = clone.getInventory();
		ItemStack sword = new ItemStack(HOTBAR[0]);
		inv.setItem(0, sword);
		for (int i = 1; i < 9; i++) {
			inv.setItem(i, new ItemStack(HOTBAR[i], 10));
		}
		inv.select(0);
		inv.setItem(20, new ItemStack(Items.BREAD, 5));
		return sword;
	}

	private static int count(final CloneEntity clone, final Item item) {
		return clone.getInventory().count(s -> s.is(item));
	}

	/** How many inventory slots hold this exact stack object. More than 1 means the same item is in two places (a dupe). */
	private static int slotsHolding(final CloneEntity clone, final ItemStack stack) {
		int n = 0;
		for (ItemStack s : clone.getInventory().main) {
			if (s == stack) {
				n++;
			}
		}
		return n;
	}

	private static void assertNoLossOrDupe(final GameTestHelper h, final CloneEntity clone, final ItemStack sword) {
		h.assertValueEqual(count(clone, Items.DIAMOND_SWORD), 1, "swords in the clone's inventory");
		h.assertValueEqual(slotsHolding(clone, sword), 1, "slots sharing the one sword");
		for (int i = 1; i < 9; i++) {
			h.assertValueEqual(count(clone, HOTBAR[i]), 10, "count of " + HOTBAR[i]);
		}
	}

	private static int droppedItems(final GameTestHelper h, final Predicate<ItemStack> test) {
		int total = 0;
		for (ItemEntity item : h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(16))) {
			if (item.isAlive() && test.test(item.getItem())) {
				total += item.getItem().getCount();
			}
		}
		return total;
	}

	static void setField(final Object target, final Class<?> owner, final String name, final Object value) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	// ---------------------------------------------------------------- bug 1: delete + dupe with a full hotbar

	@GameTest
	public void selectFromFullHotbarKeepsEveryItem(final GameTestHelper h) {
		floor(h);
		CloneEntity clone = stillClone(h, 3, 3);
		ItemStack sword = fillHotbarAndHideBread(clone);

		clone.getInventory().selectFromInventory(20);

		h.assertTrue(clone.getMainHandItem().is(Items.BREAD), "The clone should be holding the bread it selected");
		h.assertValueEqual(count(clone, Items.BREAD), 5, "bread in the clone's inventory");
		assertNoLossOrDupe(h, clone, sword);
		h.succeed();
	}

	@GameTest(maxTicks = 400)
	public void hungryCloneWithFullHotbarEats(final GameTestHelper h) {
		floor(h);
		CloneEntity clone = stillClone(h, 3, 3);
		ItemStack sword = fillHotbarAndHideBread(clone);
		clone.getFoodData().setFoodLevel(5);

		// Bread gives 5 food, so getting from 5 to 20 takes 3 bread, leaving 2.
		h.succeedWhen(() -> {
			h.assertValueEqual(clone.getFoodData().getFoodLevel(), 20, "food level");
			h.assertValueEqual(count(clone, Items.BREAD), 2, "bread left");
			assertNoLossOrDupe(h, clone, sword);
		});
	}

	// ---------------------------------------------------------------- bug 2: milk bucket never used up

	@GameTest(maxTicks = 60)
	public void milkBucketTurnsIntoEmptyBucket(final GameTestHelper h) {
		floor(h);
		CloneEntity clone = stillClone(h, 3, 3);
		clone.injectStemCells(true); // grows past normal size, so the clone wants to drink milk
		h.spawnItem(Items.MILK_BUCKET, 3.5F, 1.0F, 3.5F);

		h.succeedWhen(() -> {
			h.assertValueEqual(count(clone, Items.MILK_BUCKET), 0, "milk buckets left");
			h.assertValueEqual(count(clone, Items.BUCKET), 1, "empty buckets");
			h.assertTrue(clone.getMainHandItem().is(Items.BUCKET), "The clone should be holding the empty bucket");
		});
	}

	// ---------------------------------------------------------------- bug 3: spoofed schematic corners

	@GameTest
	public void schematicSaveOnlyUsesRealSelection(final GameTestHelper h) {
		floor(h);
		ServerPlayer player = h.makeMockServerPlayerInLevel();
		BlockPos a = h.absolutePos(new BlockPos(1, 1, 1));
		player.snapTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0.0F, 0.0F);
		var server = h.getLevel().getServer();
		String name = "cc_gametest_" + UUID.randomUUID().toString().substring(0, 8);
		BlockPos realB = a.offset(2, 2, 2);
		BlockPos farB = a.offset(999_999, 0, 0);

		try {
			Method save = ServerNetworking.class.getDeclaredMethod("saveSchematic", Payloads.SaveSchematic.class, ServerPlayer.class);
			save.setAccessible(true);

			// No selection made: refused, even with corners that look fine.
			save.invoke(null, new Payloads.SaveSchematic(name, a, realB, false), player);
			h.assertFalse(SchematicStore.exists(server, name), "Saved without any selection");

			// Real selection, but the client claims a huge far-away corner: refused.
			Field field = SchematicSelectorItem.class.getDeclaredField("SELECTIONS");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<UUID, BlockPos[]> selections = (Map<UUID, BlockPos[]>)field.get(null);
			selections.put(player.getUUID(), new BlockPos[]{a, realB});
			save.invoke(null, new Payloads.SaveSchematic(name, a, farB, false), player);
			h.assertFalse(SchematicStore.exists(server, name), "Saved with corners that don't match the selection");

			// Matching the real selection: saved.
			save.invoke(null, new Payloads.SaveSchematic(name, a, realB, false), player);
			h.assertTrue(SchematicStore.exists(server, name), "Didn't save a valid selection");

			selections.remove(player.getUUID());
			Files.deleteIfExists(server.getWorldPath(new LevelResource("clonecraft_schematics")).resolve(name + ".nbt"));
			SchematicStore.clearCache();
		} catch (ReflectiveOperationException | java.io.IOException e) {
			throw new RuntimeException(e);
		}
		h.succeed();
	}

	// ---------------------------------------------------------------- Bulk Edit: attack / break lists

	@GameTest
	public void bulkEditChangesAttackAndBreakLists(final GameTestHelper h) {
		floor(h);
		ServerPlayer player = h.makeMockServerPlayerInLevel();
		player.getInventory().add(new ItemStack(CloneCraft.CLONE_REMOTE));
		CloneEntity first = stillClone(h, 1, 1);
		CloneEntity second = stillClone(h, 3, 1);
		CloneEntity notChosen = stillClone(h, 5, 1);
		for (CloneEntity clone : List.of(first, second, notChosen)) {
			clone.setOwner(player);
		}
		Identifier zombie = Identifier.withDefaultNamespace("zombie");
		Identifier cow = Identifier.withDefaultNamespace("cow");
		Identifier skeleton = Identifier.withDefaultNamespace("skeleton");
		Identifier stone = Identifier.withDefaultNamespace("stone");
		Identifier diamondOre = Identifier.withDefaultNamespace("diamond_ore");
		Identifier junk = Identifier.withDefaultNamespace("not_a_real_thing");
		first.attackTypes.addAll(List.of(zombie, cow));
		first.breakBlocks.add(stone);
		notChosen.attackTypes.add(cow);
		notChosen.breakBlocks.add(stone);

		// Attack: add skeletons, remove cows. Break: replace the whole list with diamond ore. Junk names must be ignored.
		RemoteControl.bulkEdit(new Payloads.BulkEdit(List.of(first.getUUID(), second.getUUID()), 0, 0, -1,
			new Payloads.ListEdit(List.of(skeleton, junk), List.of(cow), false),
			new Payloads.ListEdit(List.of(diamondOre, junk), List.of(), true)), player);

		h.assertValueEqual(Set.copyOf(first.attackTypes), Set.of(zombie, skeleton), "first clone's attack list");
		h.assertValueEqual(Set.copyOf(second.attackTypes), Set.of(skeleton), "second clone's attack list");
		h.assertValueEqual(Set.copyOf(first.breakBlocks), Set.of(diamondOre), "first clone's break list");
		h.assertValueEqual(Set.copyOf(second.breakBlocks), Set.of(diamondOre), "second clone's break list");
		h.assertValueEqual(Set.copyOf(notChosen.attackTypes), Set.of(cow), "unchosen clone's attack list");
		h.assertValueEqual(Set.copyOf(notChosen.breakBlocks), Set.of(stone), "unchosen clone's break list");
		h.succeed();
	}

	// ---------------------------------------------------------------- bug 5: sharing with a full partner

	@GameTest
	public void sharingWithFullPartnerDropsLeftovers(final GameTestHelper h) {
		floor(h);
		CloneEntity giver = stillClone(h, 2, 3);
		CloneEntity partner = stillClone(h, 4, 3);
		giver.setOption(CloneOption.PICKUP, false);
		partner.setOption(CloneOption.PICKUP, false);
		giver.getInventory().setItem(0, new ItemStack(Items.BREAD, 10));
		for (int i = 0; i < CloneInventory.MAIN_SIZE; i++) {
			partner.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
		}

		// Act as if the goal had already picked this partner and offer, then the partner filled up.
		setField(giver.shareGoal, ShareGoal.class, "partner", partner);
		setField(giver.shareGoal, ShareGoal.class, "offer", new ItemStack(Items.BREAD, 5));
		giver.shareGoal.stop();

		h.assertValueEqual(count(giver, Items.BREAD), 5, "bread the giver kept");
		h.assertValueEqual(droppedItems(h, s -> s.is(Items.BREAD)), 5, "bread dropped on the ground");
		h.succeed();
	}

	// ---------------------------------------------------------------- bug 6: Smart Memory loading far chunks

	@GameTest(maxTicks = 100)
	public void smartMemoryDoesNotLoadFarChunks(final GameTestHelper h) {
		floor(h);
		CloneEntity clone = stillClone(h, 3, 3);
		clone.setOption(CloneOption.SMART_MEMORY, true);
		BlockPos far = h.absolutePos(new BlockPos(0, 1, 0)).offset(40_000, 0, 40_000);
		var level = h.getLevel();
		h.assertFalse(level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "The far chunk was already loaded before the test");
		clone.knownContainers.add(far);

		// The clone rescans its remembered containers every 40 ticks.
		h.runAfterDelay(90, () -> {
			h.assertFalse(level.hasChunk(far.getX() >> 4, far.getZ() >> 4), "The clone loaded a far-away chunk");
			h.assertTrue(clone.knownContainers.contains(far), "The clone forgot a container in an unloaded chunk");
			h.succeed();
		});
	}

	// ---------------------------------------------------------------- bug 7: neutral mobs hunting clones

	@GameTest(maxTicks = 200)
	public void zombieStillHuntsClones(final GameTestHelper h) {
		floor(h);
		// A husk doesn't burn in daylight, so there's no need to make it night (which would affect the other tests).
		CloneEntity clone = stillClone(h, 1, 1);
		net.minecraft.world.entity.monster.zombie.Zombie zombie = h.spawn(EntityTypes.HUSK, 6, 1, 6);
		h.succeedWhen(() -> h.assertTrue(zombie.getTarget() == clone, "The zombie should go after the clone"));
	}

	private static boolean huntsClones(final Mob mob) {
		return ((MobAccessor)mob).clonecraft$getTargetSelector().getAvailableGoals().stream()
			.anyMatch(g -> g.getGoal() instanceof CloneCraft.CloneTargetGoal);
	}

	/**
	 * Endermen teleport away too fast to watch them pick targets, so this checks the behaviour itself:
	 * neutral mobs mustn't be given "hunt clones", while hostile ones still are.
	 */
	@GameTest
	public void neutralMobsDontHuntClones(final GameTestHelper h) {
		floor(h);
		Enderman enderman = h.spawnWithNoFreeWill(EntityTypes.ENDERMAN, 2, 1, 2);
		ZombifiedPiglin piglin = h.spawnWithNoFreeWill(EntityTypes.ZOMBIFIED_PIGLIN, 4, 1, 4);
		Zombie zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 6, 1, 6);
		h.assertFalse(huntsClones(enderman), "Endermen shouldn't hunt clones unprovoked");
		h.assertFalse(huntsClones(piglin), "Zombified piglins shouldn't hunt clones unprovoked");
		h.assertTrue(huntsClones(zombie), "Zombies should still hunt clones");
		h.succeed();
	}

	// ---------------------------------------------------------------- bug 8: charged Life Inducer destroying its eggs

	@GameTest(maxTicks = 60)
	public void brokenLifeInducerKeepsEggs(final GameTestHelper h) {
		floor(h);
		BlockPos pos = new BlockPos(4, 1, 4);
		h.setBlock(pos, CloneCraft.LIFE_INDUCER);
		LifeInducerBlockEntity inducer = h.getBlockEntity(pos, LifeInducerBlockEntity.class);
		ItemStack egg = new ItemStack(CloneCraft.SPAWN_EGG);
		egg.set(CloneCraft.DNA, Dna.human());
		inducer.setItem(0, egg);
		inducer.power = 100;

		h.destroyBlock(pos);

		h.succeedWhen(() -> h.assertValueEqual(droppedItems(h, s -> s.is(CloneCraft.SPAWN_EGG) || s.is(CloneCraft.ACTIVE_SPAWN_EGG)), 1,
			"spawn eggs dropped by the broken Life Inducer"));
	}

	// ---------------------------------------------------------------- bug 9: Sterilizer keeps the water

	@GameTest
	public void boilingSterilizerGivesWaterBack(final GameTestHelper h) {
		floor(h);
		BlockPos pos = new BlockPos(3, 1, 3);
		h.setBlock(pos, CloneCraft.STERILIZER.defaultBlockState().setValue(SterilizerBlock.WATER, 3));
		ServerPlayer player = h.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		ItemStack bucket = new ItemStack(Items.BUCKET);
		player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

		BlockPos abs = h.absolutePos(pos);
		BlockState state = h.getBlockState(pos);
		state.useItemOn(bucket, h.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));

		h.assertTrue(player.getInventory().contains(s -> s.is(Items.WATER_BUCKET)), "Emptying a boiling Sterilizer should give a water bucket");
		h.assertBlockProperty(pos, SterilizerBlock.WATER, 0);
		h.succeed();
	}
}
