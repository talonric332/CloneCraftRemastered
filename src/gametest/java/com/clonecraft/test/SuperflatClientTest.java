package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Game Progression in a real superflat world with a bonus chest: the clones should get going from the bonus chest
 * (wood, saplings) instead of wandering about or digging to bedrock, and say what they need when they're stuck.
 */
public class SuperflatClientTest implements FabricClientGameTest {
	private static final int MINUTES = 3;

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(s -> s.setBonusChest(true)).create()) {
			var server = world.getServer();
			context.waitTicks(40);
			server.runCommand("gamemode creative @a");
			server.runCommand("time set day");
			List<UUID> ids = server.computeOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				ServerLevel level = (ServerLevel)player.level();
				CloneCraft.LOGGER.info("[flat] world is superflat: {}", level.isFlat());
				List<UUID> spawned = new ArrayList<>();
				for (int i = 0; i < 3; i++) {
					int x = player.getBlockX() + 2 + i;
					int z = player.getBlockZ() + 2;
					CloneEntity clone = CloneCraft.CLONE.create(level, EntitySpawnReason.COMMAND);
					clone.snapTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5, 0.0F, 0.0F);
					clone.setGrownUp();
					clone.setOwner(player);
					clone.setOption(CloneOption.GAME_PROGRESSION, true);
					level.addFreshEntity(clone);
					spawned.add(clone.getUUID());
				}
				return spawned;
			});
			boolean gotWood = false;
			for (int minute = 1; minute <= MINUTES; minute++) {
				context.waitTicks(1200);
				final int m = minute;
				gotWood |= server.computeOnServer(s -> {
					boolean wood = false;
					for (UUID id : ids) {
						if (s.overworld().getEntity(id) instanceof CloneEntity clone) {
							CloneCraft.LOGGER.info("[flat] minute {} {} at y={}: {} | {}", m, clone.getCloneName(), clone.getBlockY(), clone.progressionGoal.describe(),
								clone.getInventory().main.stream().filter(x -> !x.isEmpty()).map(x -> x.getCount() + " " + x.getItem()).toList());
							wood |= clone.getInventory().count(x -> x.is(ItemTags.LOGS) || x.is(ItemTags.PLANKS)) > 0
								|| clone.getInventory().count(x -> x.is(net.minecraft.world.item.Items.CRAFTING_TABLE)) > 0
								|| clone.progressionGoal.pickaxeTier() > 0;
						} else {
							CloneCraft.LOGGER.info("[flat] minute {} clone {} is gone", m, id);
						}
					}
					return wood;
				});
			}
			if (!gotWood) {
				throw new AssertionError("No clone got any wood in a superflat world with a bonus chest");
			}
			CloneCraft.LOGGER.info("[flat] passed");
		}
	}
}
