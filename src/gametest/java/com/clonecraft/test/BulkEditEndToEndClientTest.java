package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.CloneTeam;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.CloneSpectating;
import com.clonecraft.network.RemoteControl;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/**
 * Bulk Edit the way a player uses it: own some clones, open the Clone Remote, press Bulk Edit, pick mobs and blocks,
 * press Apply, then check every clone's lists on the server.
 */
public class BulkEditEndToEndClientTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			context.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				player.getInventory().add(new ItemStack(CloneCraft.CLONE_REMOTE));
				for (int i = 0; i < 3; i++) {
					CloneEntity clone = CloneCraft.CLONE.create((ServerLevel)player.level(), EntitySpawnReason.COMMAND);
					clone.snapTo(player.getX() + 2 + i, player.getY(), player.getZ() + 2, 0.0F, 0.0F);
					clone.setGrownUp();
					clone.setOwner(player);
					player.level().addFreshEntity(clone);
				}
			});
			context.waitTicks(10);
			server.runOnServer(s -> RemoteControl.sendList(player(s), true));
			context.waitTicks(10);
			context.takeScreenshot("e2e_1_remote");

			context.clickScreenButton("Bulk Edit");
			context.waitTicks(3);
			context.clickScreenButton("Entities To Attack...");
			context.waitTicks(3);
			context.clickScreenButton("+ Monsters");
			context.clickScreenButton("Done");
			context.waitTicks(3);
			context.clickScreenButton("Blocks To Break...");
			context.waitTicks(3);
			context.clickScreenButton("Add All");
			context.clickScreenButton("Others: Keep");
			context.clickScreenButton("Done");
			context.waitTicks(3);
			context.clickScreenButton("Options...");
			context.waitTicks(3);
			context.clickScreenButton("Follow: Keep");
			context.clickScreenButton("Team: Keep");
			context.clickScreenButton("Game Progression: Keep");
			context.waitTicks(3);
			context.takeScreenshot("e2e_2_options");
			context.clickScreenButton("Done");
			context.waitTicks(3);
			context.takeScreenshot("e2e_3_before_apply");
			context.clickScreenButton("Apply");
			context.waitTicks(20);
			context.takeScreenshot("e2e_4_after_apply");

			String problems = server.computeOnServer(s -> {
				StringBuilder sb = new StringBuilder();
				List<CloneEntity> clones = RemoteControl.ownedClones(player(s));
				if (clones.size() != 3) {
					sb.append("expected 3 clones, found ").append(clones.size()).append("; ");
				}
				for (CloneEntity clone : clones) {
					CloneCraft.LOGGER.info("[bulk-e2e] {}: attack={} break={} follow={} team={}", clone.getCloneName(), clone.attackTypes.size(),
						clone.breakBlocks.size(), clone.hasOption(CloneOption.FOLLOW), clone.getCloneTeam());
					if (!clone.attackTypes.contains(Identifier.withDefaultNamespace("zombie"))) {
						sb.append(clone.getCloneName()).append(" isn't attacking zombies; ");
					}
					if (!clone.breakBlocks.contains(Identifier.withDefaultNamespace("diamond_ore"))) {
						sb.append(clone.getCloneName()).append(" isn't breaking diamond ore; ");
					}
					if (!clone.hasOption(CloneOption.FOLLOW)) {
						sb.append(clone.getCloneName()).append(" isn't following; ");
					}
					if (clone.getCloneTeam() != CloneTeam.RED) {
						sb.append(clone.getCloneName()).append(" isn't on the Red team; ");
					}
					if (!clone.hasOption(CloneOption.GAME_PROGRESSION)) {
						sb.append(clone.getCloneName()).append(" doesn't have Game Progression on; ");
					}
				}
				return sb.toString();
			});
			if (!problems.isEmpty()) {
				throw new AssertionError("Bulk Edit didn't apply: " + problems);
			}
			CloneCraft.LOGGER.info("[bulk-e2e] all clones updated correctly");

			// A single clone's Options screen, to check the Game Progression button fits with the others.
			CloneEntity shown = context.computeOnClient(mc -> {
				for (var entity : mc.level.entitiesForRendering()) {
					if (entity instanceof CloneEntity clone) {
						return clone;
					}
				}
				throw new AssertionError("No clone on the client");
			});
			context.setScreen(() -> new com.clonecraft.client.screen.CloneOptionsScreen(shown));
			context.waitTicks(5);
			context.takeScreenshot("e2e_5_clone_options");
			context.setScreen(() -> null);

			// Let them progress (they don't while following) so they pick jobs, then look at the Clone Remote.
			server.runOnServer(s -> RemoteControl.ownedClones(player(s)).forEach(c -> c.setOption(CloneOption.FOLLOW, false)));
			context.waitTicks(140);
			server.runOnServer(s -> RemoteControl.sendList(player(s), true));
			context.waitTicks(10);
			context.takeScreenshot("e2e_6_remote_with_tasks");

			// Watch the first clone, then press Sneak to come back.
			var before = server.computeOnServer(s -> new Object[]{player(s).gameMode.getGameModeForPlayer(), player(s).position()});
			context.clickScreenButton("Watch");
			context.waitTicks(20);
			context.takeScreenshot("e2e_7_watching");
			String watching = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				return p.getCamera() instanceof CloneEntity c && CloneSpectating.isWatching(p)
					&& p.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR ? "ok" : "camera=" + p.getCamera() + " mode=" + p.gameMode();
			});
			if (!"ok".equals(watching)) {
				throw new AssertionError("Watch didn't work: " + watching);
			}
			context.getInput().holdKeyFor(options -> options.keyShift, 10);
			context.waitTicks(10);
			String back = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				boolean ok = !CloneSpectating.isWatching(p) && p.getCamera() == p && p.gameMode.getGameModeForPlayer() == before[0]
					&& p.position().distanceTo((net.minecraft.world.phys.Vec3)before[1]) < 0.5;
				return ok ? "ok" : "watching=" + CloneSpectating.isWatching(p) + " mode=" + p.gameMode.getGameModeForPlayer() + " pos=" + p.position();
			});
			if (!"ok".equals(back)) {
				throw new AssertionError("Sneak didn't bring the player back: " + back);
			}
			context.takeScreenshot("e2e_8_back");
			CloneCraft.LOGGER.info("[bulk-e2e] watching works");
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}
}
