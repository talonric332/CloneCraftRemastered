package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.CloneTeam;
import com.clonecraft.entity.CloneEntity;
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
				}
				return sb.toString();
			});
			if (!problems.isEmpty()) {
				throw new AssertionError("Bulk Edit didn't apply: " + problems);
			}
			CloneCraft.LOGGER.info("[bulk-e2e] all clones updated correctly");
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}
}
