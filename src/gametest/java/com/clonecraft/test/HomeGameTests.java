package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.progression.HomeBase;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The clones' shared house: building it together, sleeping in it, mending it after a creeper, and helping each other. */
public class HomeGameTests {
	/** A big flat field of grass (the test area alone is too small for a house), kept running while the test does. */
	private static void field(final GameTestHelper h) {
		BlockPos low = h.absolutePos(new BlockPos(-14, 0, -14));
		BlockPos high = h.absolutePos(new BlockPos(21, 0, 21));
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = Math.min(low.getX(), high.getX()) >> 4; cx <= Math.max(low.getX(), high.getX()) >> 4; cx++) {
			for (int cz = Math.min(low.getZ(), high.getZ()) >> 4; cz <= Math.max(low.getZ(), high.getZ()) >> 4; cz++) {
				h.getLevel().setChunkForced(cx, cz, true);
				forced.add(new ChunkPos(cx, cz));
			}
		}
		h.runBeforeTestEnd(() -> forced.forEach(c -> h.getLevel().setChunkForced(c.x(), c.z(), false)));
		h.setTime(1000);
		for (int x = -14; x <= 21; x++) {
			for (int z = -14; z <= 21; z++) {
				for (int y = -6; y <= -1; y++) {
					h.setBlock(x, y, z, y >= -2 ? Blocks.DIRT : Blocks.STONE);
				}
				h.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
				for (int y = 1; y <= 8; y++) {
					h.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}

	/** A clone of this owner on Game Progression, with stone tools, food, and (if a builder) what a house takes. */
	private static CloneEntity clone(final GameTestHelper h, final UUID owner, final int x, final int z, final boolean builder) {
		CloneEntity clone = h.spawn(CloneCraft.CLONE, x + 0.5F, 1.0F, z + 0.5F);
		clone.setGrownUp();
		clone.setOwnerId(owner);
		clone.setOption(CloneOption.GAME_PROGRESSION, true);
		clone.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
		List<ItemStack> stuff = new ArrayList<>(List.of(new ItemStack(Items.STONE_SWORD), new ItemStack(Items.BREAD, 32),
			new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.FURNACE), new ItemStack(Items.BED.white())));
		if (builder) {
			stuff.addAll(List.of(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
				new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.STICK, 32), new ItemStack(Items.COAL, 8), new ItemStack(Items.TORCH, 8)));
		}
		for (ItemStack stack : stuff) {
			clone.getInventory().add(stack);
		}
		return clone;
	}

	private static String state(final GameTestHelper h, final HomeBase.Home home, final CloneEntity... clones) {
		StringBuilder s = new StringBuilder();
		if (home != null) {
			s.append(home.rooms.size()).append(" room(s), ").append(home.things.size()).append(" things, built=").append(home.built)
				.append(", blasts=").append(home.blasts).append(", still to do: ");
			int shown = 0;
			for (HomeBase.Spot spot : home.plan()) {
				if (!HomeBase.done(h.getLevel(), home, spot) && shown++ < 6) {
					s.append(spot.part()).append('@').append(h.relativePos(spot.pos())).append('=')
						.append(h.getLevel().getBlockState(spot.pos()).getBlock().getName().getString()).append(' ');
				}
			}
		}
		for (CloneEntity c : clones) {
			s.append("; ").append(c.getCloneName()).append(" \"").append(c.progressionGoal.describe()).append("\" at ").append(h.relativePos(c.blockPosition()))
				.append(" planks=").append(c.getInventory().main.stream().filter(i -> i.is(net.minecraft.tags.ItemTags.PLANKS)).mapToInt(ItemStack::getCount).sum())
				.append(" stone=").append(c.getInventory().main.stream().filter(i -> i.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum());
		}
		return s.toString();
	}

	/**
	 * Two clones of one owner live together: one starts the house (in wood: that's all they have), the other moves
	 * in (its own bed, and a room added if there's no space). Then a creeper blows a hole in the back wall: they say
	 * so, and rebuild it in stone (they've been given some), with a torch outside on that side. At night they go home
	 * and sleep, and in the morning they get up. (All one test, since day and night are the same for every test.)
	 */
	@GameTest(maxTicks = 24000, padding = 60)
	public void clonesLiveTogether(final GameTestHelper h) {
		field(h);
		UUID owner = UUID.randomUUID();
		CloneEntity first = clone(h, owner, 2, 2, true);
		CloneEntity second = clone(h, owner, 5, 2, true);
		for (CloneEntity c : List.of(first, second)) {
			c.getInventory().main.replaceAll(s -> s.is(Items.COBBLESTONE) ? ItemStack.EMPTY : s);
			c.getInventory().add(new ItemStack(Items.OAK_PLANKS, 64));
			c.getInventory().add(new ItemStack(Items.OAK_PLANKS, 64));
		}
		List<BlockPos> lost = new ArrayList<>();
		String[] stage = {"building"};
		h.onEachTick(() -> {
			HomeBase.Home home = HomeBase.get(h.getLevel(), owner);
			if (!stage[0].startsWith("night") && !stage[0].equals("morning") && h.getTick() % 1000 == 0) {
				h.setTime(1000);
			}
			if (home == null) {
				return;
			}
			switch (stage[0]) {
				case "building" -> {
					if (home.built && home.hasBedFor(first.getUUID().toString()) && home.hasBedFor(second.getUUID().toString())
						&& home.beds().keySet().stream().allMatch(b -> h.getLevel().getBlockState(b).getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
						stage[0] = "creeper";
						BlockPos back = home.at(home.rooms.get(0).width() / 2, 0, -1);
						Creeper creeper = EntityTypes.CREEPER.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
						creeper.snapTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5, 0.0F, 0.0F);
						h.getLevel().addFreshEntity(creeper);
						// The clones would kill it first given the chance (they fight creepers): this one gets to go off.
						creeper.setPermanentlyInvulnerable(true);
						// (Monsters vanish with no player near, and tests have none.)
						creeper.setPersistenceRequired();
						creeper.ignite();
						for (CloneEntity c : List.of(first, second)) {
							c.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
						}
					}
				}
				case "creeper" -> {
					if (home.blasts > 0) {
						stage[0] = "repairing";
						lost.addAll(home.strength.keySet());
					}
				}
				case "repairing" -> {
					// The walls, roof and floors (a bed blown to bits needs wool for a new one, which there isn't here).
					java.util.Set<BlockPos> furniture = new java.util.HashSet<>();
					for (HomeBase.Spot s : home.plan()) {
						if (s.part().ordinal() >= HomeBase.Part.TABLE.ordinal()) {
							furniture.add(s.pos());
						}
					}
					boolean mended = lost.stream().allMatch(p -> furniture.contains(p) || home.skip.contains(p) || !h.getLevel().getBlockState(p).isAir());
					boolean torch = home.outerTorches.stream().anyMatch(t -> h.getLevel().getBlockState(t).getLightEmission() > 0 || home.skip.contains(t));
					if (mended && torch) {
						for (BlockPos p : lost) {
							h.assertFalse(h.getLevel().getBlockState(p).is(net.minecraft.tags.BlockTags.PLANKS),
								"Rebuilt in planks, not stone, at " + h.relativePos(p));
						}
						stage[0] = "night";
						h.setTime(13000);
					}
				}
				case "night" -> {
					// (Another test may have set it back to day.)
					if (h.getTick() % 200 == 0 && !com.clonecraft.clone.ai.ProgressionGoal.isEvening(h.getLevel())) {
						h.setTime(13000);
					}
					if (first.isSleeping() || second.isSleeping()) {
						stage[0] = "morning";
						h.setTime(1000);
					}
				}
				case "morning" -> {
					if (!first.isSleeping() && !second.isSleeping()) {
						stage[0] = "done";
					}
				}
				default -> {
				}
			}
		});
		h.runBeforeTestEnd(() -> h.setTime(1000));
		h.succeedWhen(() -> {
			HomeBase.Home home = HomeBase.get(h.getLevel(), owner);
			h.assertTrue(stage[0].equals("done"), "Stuck at \"" + stage[0] + "\": " + state(h, home, first, second));
			h.assertTrue(home.chest() != null && h.getLevel().getBlockState(home.chest()).is(Blocks.CHEST), "No chest in the house");
		});
	}

	/**
	 * The house design on its own: a first room with the basics, beds for clone after clone (rooms added when there's
	 * no space), and everyone can still walk everywhere inside.
	 */
	@GameTest
	public void houseDesignFitsEveryonesBed(final GameTestHelper h) {
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(5L);
		for (int trial = 0; trial < 40; trial++) {
			int width = 5 + random.nextInt(3);
			int depth = 5 + random.nextInt(2);
			HomeBase.Home home = new HomeBase.Home(BlockPos.ZERO, net.minecraft.core.Direction.SOUTH, 3, new HomeBase.Room(0, 0, width, depth),
				1 + random.nextInt(width - 2));
			home.addAgainstWall(HomeBase.Part.TABLE, "a", random);
			home.addAgainstWall(HomeBase.Part.CHEST, "a", random);
			home.addAgainstWall(HomeBase.Part.FURNACE, "a", random);
			home.addTorch("a", random);
			for (int clone = 0; clone < 4; clone++) {
				String me = "clone" + clone;
				if (!home.addBed(me, random)) {
					h.assertTrue(home.addRoom(random, pos -> true) != null, "Couldn't add a room (trial " + trial + ", clone " + clone + ")");
					h.assertTrue(home.addBed(me, random), "No room for a bed even in a new room (trial " + trial + ", clone " + clone + ", "
						+ width + "x" + depth + ", rooms " + home.rooms + ", things " + home.things + ")");
				}
			}
		}
		h.succeed();
	}

	/** Puts a whole house up at once (for tests about what happens to a house once it's there). */
	static HomeBase.Home instantHouse(final GameTestHelper h, final UUID owner, final CloneEntity resident) {
		HomeBase.Home home = new HomeBase.Home(h.absolutePos(new BlockPos(0, 1, 0)), net.minecraft.core.Direction.SOUTH, 3,
			new HomeBase.Room(0, 0, 7, 5), 3);
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(1L);
		home.addAgainstWall(HomeBase.Part.TABLE, resident.getUUID().toString(), random);
		home.addAgainstWall(HomeBase.Part.CHEST, resident.getUUID().toString(), random);
		home.addBed(resident.getUUID().toString(), random);
		var level = h.getLevel();
		for (HomeBase.Spot spot : home.plan()) {
			var block = switch (spot.part()) {
				case WALL, ROOF, FLOOR -> Blocks.OAK_PLANKS.defaultBlockState();
				case TABLE -> Blocks.CRAFTING_TABLE.defaultBlockState();
				case CHEST -> Blocks.CHEST.defaultBlockState();
				default -> null;
			};
			if (spot.part() == HomeBase.Part.DOOR && !spot.second()) {
				var door = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, home.facing);
				level.setBlock(spot.pos(), door, 3);
				level.setBlock(spot.pos().above(), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
					net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
				home.placed.add(spot.pos());
				home.placed.add(spot.pos().above());
			} else if (spot.part() == HomeBase.Part.BED) {
				var bed = net.minecraft.world.level.block.Block.byItem(Items.BED.white()).defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, spot.facing());
				level.setBlock(spot.pos(), bed, 3);
				bed.getBlock().setPlacedBy(level, spot.pos(), bed, resident, new ItemStack(Items.BED.white()));
				home.placed.add(spot.pos());
			} else if (block != null) {
				level.setBlock(spot.pos(), block, 3);
				home.placed.add(spot.pos());
			}
		}
		home.built = true;
		HomeBase.set(level, owner, home);
		return home;
	}

	/**
	 * A creeper goes off right by the house: the clones fill in the crater around it (so nobody can fall in and get
	 * stuck), as well as mending the house.
	 */
	@GameTest(maxTicks = 8000, padding = 60)
	public void craterIsFilledIn(final GameTestHelper h) {
		field(h);
		// Daytime throughout (another test may make it night, and the clones would go in for the night).
		h.onEachTick(() -> {
			if (h.getTick() % 200 == 0 && com.clonecraft.clone.ai.ProgressionGoal.isEvening(h.getLevel())) {
				h.setTime(1000);
			}
		});
		UUID owner = UUID.randomUUID();
		CloneEntity clone = clone(h, owner, 3, 7, true);
		HomeBase.Home home = instantHouse(h, owner, clone);
		BlockPos side = home.at(7, 0, 2);
		// A real explosion next to the right-hand wall (as a creeper's: so the clones know it was one).
		HomeBase.creeperExploding(h.getLevel(), net.minecraft.world.phys.Vec3.atCenterOf(side), null);
		h.getLevel().explode(null, side.getX() + 0.5, side.getY(), side.getZ() + 0.5, 3.0F, net.minecraft.world.level.Level.ExplosionInteraction.MOB);
		BlockPos ground = side.below();
		h.succeedWhen(() -> {
			h.assertTrue(home.blasts > 0, "The blast wasn't noticed");
			StringBuilder holes = new StringBuilder();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos pos = ground.offset(dx, 0, dz);
					if (h.getLevel().getBlockState(pos).getCollisionShape(h.getLevel(), pos).isEmpty()) {
						holes.append(h.relativePos(pos)).append(' ');
					}
				}
			}
			h.assertTrue(holes.isEmpty(), "Holes left in the ground: " + holes + "(fill list " + home.fill.size() + ", "
				+ home.fill.stream().filter(p -> !home.skip.contains(p)).count() + " not skipped); " + state(h, home, clone));
			for (HomeBase.Spot spot : home.plan()) {
				h.assertTrue(HomeBase.done(h.getLevel(), home, spot), "House not mended: " + state(h, home, clone));
			}
		});
	}

	/** A creeper going off a few blocks from the house, without hitting it: the crater is still filled in. */
	@GameTest(maxTicks = 8000, padding = 60)
	public void craterNearTheHouseIsFilledEvenIfTheHouseIsntHit(final GameTestHelper h) {
		field(h);
		// Daytime throughout (another test may make it night, and the clones would go in for the night).
		h.onEachTick(() -> {
			if (h.getTick() % 200 == 0 && com.clonecraft.clone.ai.ProgressionGoal.isEvening(h.getLevel())) {
				h.setTime(1000);
			}
		});
		UUID owner = UUID.randomUUID();
		CloneEntity clone = clone(h, owner, 3, 9, true);
		HomeBase.Home home = instantHouse(h, owner, clone);
		BlockPos spot = home.at(14, 0, 2);
		HomeBase.creeperExploding(h.getLevel(), net.minecraft.world.phys.Vec3.atCenterOf(spot), null);
		h.getLevel().explode(null, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 3.0F, net.minecraft.world.level.Level.ExplosionInteraction.MOB);
		BlockPos ground = spot.below();
		h.succeedWhen(() -> {
			h.assertTrue(home.blasts == 0, "The house shouldn't have been hit");
			StringBuilder holes = new StringBuilder();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos pos = ground.offset(dx, 0, dz);
					if (h.getLevel().getBlockState(pos).getCollisionShape(h.getLevel(), pos).isEmpty()) {
						holes.append(h.relativePos(pos)).append(' ');
					}
				}
			}
			h.assertTrue(holes.isEmpty(), "Holes left in the ground: " + holes + "(fill list " + home.fill.size() + "); " + state(h, home, clone));
		});
	}

	/** A hole in the house (not a player's doing) gets fixed. */
	@GameTest(maxTicks = 2400, padding = 60)
	public void holesInTheHouseAreFixed(final GameTestHelper h) {
		field(h);
		UUID owner = UUID.randomUUID();
		CloneEntity clone = clone(h, owner, 3, 8, true);
		HomeBase.Home home = instantHouse(h, owner, clone);
		BlockPos hole = home.at(0, 1, 2);
		h.getLevel().setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
		h.succeedWhen(() -> h.assertFalse(h.getLevel().getBlockState(hole).isAir(), "The hole in the wall hasn't been fixed: " + state(h, home, clone)));
	}

	/** A block of the house a player breaks on purpose is left as they made it. */
	@GameTest(maxTicks = 800, padding = 60)
	@SuppressWarnings("removal")
	public void aPlayersChangeIsLeftAlone(final GameTestHelper h) {
		field(h);
		UUID owner = UUID.randomUUID();
		CloneEntity clone = clone(h, owner, 3, 8, true);
		HomeBase.Home home = instantHouse(h, owner, clone);
		BlockPos window = home.at(0, 1, 2);
		var player = h.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		player.gameMode.destroyBlock(window);
		h.runAtTickTime(700, () -> {
			h.assertTrue(h.getLevel().getBlockState(window).isAir(), "The clones filled in what the player took out");
			h.assertTrue(home.skip.contains(window), "It should be noted as the player's change");
			h.succeed();
		});
	}

	/** A clone setting off to dig for stone from inside the house never digs through the house (or the ground under it). */
	@GameTest(maxTicks = 3000, padding = 60)
	public void minesDontGoThroughTheHouse(final GameTestHelper h) {
		field(h);
		UUID owner = UUID.randomUUID();
		CloneEntity clone = clone(h, owner, 3, 8, false);
		HomeBase.Home home = instantHouse(h, owner, clone);
		// Inside, with a wooden pickaxe and wood but no stone: it'll go and dig for stone.
		clone.teleportTo(home.inside().getX() + 0.5, home.inside().getY(), home.inside().getZ() + 0.5);
		clone.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
		clone.getInventory().add(new ItemStack(Items.OAK_PLANKS, 16));
		clone.getInventory().add(new ItemStack(Items.STICK, 8));
		List<BlockPos> house = new ArrayList<>();
		AABB box = home.bounds();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - 3, box.minZ), BlockPos.containing(box.maxX - 1, box.minY, box.maxZ - 1))) {
			if (!h.getLevel().getBlockState(pos).isAir()) {
				house.add(pos.immutable());
			}
		}
		h.succeedWhen(() -> {
			for (BlockPos pos : house) {
				h.assertFalse(h.getLevel().getBlockState(pos).isAir(), "Something under or in the house was dug out at " + h.relativePos(pos));
			}
			h.assertTrue(clone.getInventory().find(s -> s.is(Items.COBBLESTONE)) >= 0, "It hasn't dug any stone yet: " + state(h, home, clone));
		});
	}

	/** A clone down a mine with plenty of stone comes up to mend the house. */
	@GameTest(maxTicks = 4000, padding = 60)
	public void minerComesUpToMendTheHouse(final GameTestHelper h) {
		field(h);
		UUID owner = UUID.randomUUID();
		CloneEntity clone = clone(h, owner, 3, 8, true);
		HomeBase.Home home = instantHouse(h, owner, clone);
		BlockPos hole = home.at(0, 1, 2);
		h.getLevel().setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
		// A little room dug in the stone under the field, well away from the house, with the clone in it.
		for (int x = 12; x <= 14; x++) {
			for (int z = 12; z <= 14; z++) {
				h.setBlock(x, -5, z, Blocks.AIR);
				h.setBlock(x, -4, z, Blocks.AIR);
			}
		}
		clone.teleportTo(h.absolutePos(new BlockPos(13, -5, 13)).getX() + 0.5, h.absolutePos(new BlockPos(13, -5, 13)).getY(),
			h.absolutePos(new BlockPos(13, -5, 13)).getZ() + 0.5);
		h.succeedWhen(() -> h.assertFalse(h.getLevel().getBlockState(hole).isAir(), "The miner hasn't come up to fix the house: " + state(h, home, clone)));
	}

	/** Houses come in different styles: different materials, flat or peaked roofs (and a peaked one really is). */
	@GameTest
	public void housesComeInDifferentStyles(final GameTestHelper h) {
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(3L);
		java.util.Set<HomeBase.Style> styles = new java.util.HashSet<>();
		for (int k = 0; k < 40; k++) {
			styles.add(HomeBase.Style.random(random));
		}
		h.assertTrue(styles.size() >= 10, "Only " + styles.size() + " different styles in 40 houses");
		HomeBase.Home home = new HomeBase.Home(BlockPos.ZERO, net.minecraft.core.Direction.NORTH, 3, new HomeBase.Room(0, 0, 7, 5), 3);
		home.style = new HomeBase.Style(HomeBase.Style.WOOD, HomeBase.Style.STONE, true, HomeBase.Style.WOOD, true);
		int top = home.plan().stream().filter(s -> s.part() == HomeBase.Part.ROOF).mapToInt(s -> s.pos().getY()).max().orElse(0);
		h.assertTrue(top >= 5, "A peaked roof should rise above the flat top of the walls (it tops out at y=" + top + ")");
		h.assertTrue(home.plan().stream().anyMatch(s -> s.part() == HomeBase.Part.PILLAR), "No corner posts");
		h.succeed();
	}

	/** A clone on Game Progression fights a creeper that comes for it, even though creepers aren't on its list. */
	@GameTest(maxTicks = 600, padding = 60)
	public void cloneFightsACreeper(final GameTestHelper h) {
		field(h);
		CloneEntity clone = clone(h, UUID.randomUUID(), 2, 2, false);
		Creeper creeper = h.spawn(EntityTypes.CREEPER, 9, 1, 9);
		h.succeedWhen(() -> h.assertTrue(creeper.getHealth() < creeper.getMaxHealth() || !creeper.isAlive(),
			"The clone hasn't hit the creeper: " + state(h, null, clone)));
	}

	/** A clone that needs cobblestone asks the others; one that has some brings it over. */
	@GameTest(maxTicks = 2400, padding = 60)
	public void aCloneBringsWhatAnotherAskedFor(final GameTestHelper h) {
		field(h);
		UUID owner = UUID.randomUUID();
		CloneEntity needy = clone(h, owner, 0, 0, false);
		// Wood for its tools, but no stone.
		needy.getInventory().add(new ItemStack(Items.OAK_PLANKS, 12));
		needy.getInventory().add(new ItemStack(Items.STICK, 16));
		needy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
		CloneEntity giver = clone(h, owner, 8, 8, false);
		giver.getInventory().add(new ItemStack(Items.OAK_PLANKS, 8));
		giver.getInventory().add(new ItemStack(Items.STICK, 16));
		giver.getInventory().add(new ItemStack(Items.COBBLESTONE, 20));
		h.succeedWhen(() -> h.assertTrue(needy.getInventory().find(s -> s.is(Items.COBBLESTONE)) >= 0,
			"The clone that needed cobblestone hasn't been given any: " + state(h, null, needy, giver)));
	}
}
