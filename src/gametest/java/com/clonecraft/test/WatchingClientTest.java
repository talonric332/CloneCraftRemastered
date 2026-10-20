package com.clonecraft.test;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.CloneSpectating;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Watching a clone in a real game: you can look around freely (the clone's head doesn't turn with you), and clones
 * back where you were keep working while you watch one far away.
 */
public class WatchingClientTest implements FabricClientGameTest {
	private static final int FAR = 64;

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			context.waitTicks(20);
			// A small simulation distance, so the far clone and the home clone can't both be simulated by the player.
			server.runOnServer(s -> {
				s.getPlayerList().setViewDistance(8);
				s.getPlayerList().setSimulationDistance(2);
			});
			server.runCommand("gamemode creative @a");

			// The far clone first (the player has to go there so its chunk exists), then back home for the walking one.
			server.runOnServer(s -> player(s).teleportTo(player(s).getX() + FAR, player(s).getY(), player(s).getZ()));
			context.waitTicks(60);
			UUID far = server.computeOnServer(s -> spawn(player(s), 0, false).getUUID());
			server.runOnServer(s -> player(s).teleportTo(player(s).getX() - FAR, player(s).getY(), player(s).getZ()));
			context.waitTicks(60);
			UUID home = server.computeOnServer(s -> spawn(player(s), 3, true).getUUID());
			context.waitTicks(40);

			// Watch the far clone.
			server.runOnServer(s -> CloneSpectating.start(player(s), (CloneEntity)s.overworld().getEntity(far)));
			context.waitTicks(40);

			// 1. Look around: the view turns, the clone's head doesn't.
			float cloneYaw = server.computeOnServer(s -> ((CloneEntity)s.overworld().getEntity(far)).getYHeadRot());
			// Turn the player (what the mouse does) to face well away from the clone.
			float lookYaw = cloneYaw + 120.0F;
			context.runOnClient(mc -> {
				mc.player.setYRot(lookYaw);
				mc.player.yRotO = lookYaw;
				mc.player.setXRot(-30.0F);
				mc.player.xRotO = -30.0F;
			});
			context.waitTicks(5);
			float viewYaw = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().yRot());
			float viewPitch = context.computeOnClient(mc -> mc.gameRenderer.mainCamera().xRot());
			float cloneYawAfter = server.computeOnServer(s -> ((CloneEntity)s.overworld().getEntity(far)).getYHeadRot());
			CloneCraft.LOGGER.info("[watch-test] looked to {}: view yaw {} pitch {}, clone head {} -> {}", lookYaw, viewYaw, viewPitch, cloneYaw, cloneYawAfter);
			if (Math.abs(viewYaw - lookYaw) > 1.0F || Math.abs(viewPitch + 30.0F) > 1.0F) {
				throw new AssertionError("The view should turn with the player: wanted " + lookYaw + "/-30, got " + viewYaw + "/" + viewPitch);
			}
			if (Math.abs(cloneYawAfter - lookYaw) < 1.0F) {
				throw new AssertionError("Looking around turned the clone's head");
			}
			context.takeScreenshot("watch_1_looking_around");

			// 2. Its hotbar, health and food on screen; E opens its whole inventory.
			server.runOnServer(s -> {
				CloneEntity clone = (CloneEntity)s.overworld().getEntity(far);
				clone.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
				clone.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 12));
				clone.getInventory().setItem(20, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 30));
				clone.getInventory().select(0);
				clone.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
			});
			context.waitTicks(20);
			String hud = context.computeOnClient(mc -> {
				CloneEntity watched = com.clonecraft.client.WatchHud.watched(mc);
				var data = watched == null ? null : com.clonecraft.client.WatchHud.inventoryOf(watched);
				if (data == null) {
					return "no inventory received for the watched clone";
				}
				boolean ok = data.items().get(0).is(net.minecraft.world.item.Items.DIAMOND_SWORD) && data.items().get(20).is(net.minecraft.world.item.Items.OAK_LOG)
					&& data.items().get(36).is(net.minecraft.world.item.Items.IRON_HELMET) && data.selected() == 0;
				return ok ? "ok" : "wrong items: " + data.items();
			});
			if (!"ok".equals(hud)) {
				throw new AssertionError("Watching should show the clone's inventory: " + hud);
			}
			context.takeScreenshot("watch_2_clone_hotbar");
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(5);
			boolean opened = context.computeOnClient(mc -> mc.gui.screen() instanceof com.clonecraft.client.screen.WatchedInventoryScreen);
			if (!opened) {
				throw new AssertionError("E should open the watched clone's inventory");
			}
			context.takeScreenshot("watch_3_clone_inventory");
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(5);
			if (context.computeOnClient(mc -> mc.gui.screen() != null)) {
				throw new AssertionError("E should close the clone's inventory again");
			}
			CloneCraft.LOGGER.info("[watch-test] hotbar and inventory work");

			// 2. The clone back home keeps wandering while we watch the far one.
			// An entity's tick counter only goes up while the game is running it, so this shows it isn't frozen
			// (watching it wander isn't reliable: it can stand still by chance).
			int ticksBefore = server.computeOnServer(s -> s.overworld().getEntity(home).tickCount);
			context.waitTicks(200);
			int ticked = server.computeOnServer(s -> s.overworld().getEntity(home).tickCount) - ticksBefore;
			CloneCraft.LOGGER.info("[watch-test] home clone ran for {} of 200 ticks while watching", ticked);
			if (ticked < 150) {
				throw new AssertionError("The clone back home froze while watching another one (ran " + ticked + " of 200 ticks)");
			}

			context.getInput().holdKeyFor(options -> options.keyShift, 10);
			context.waitTicks(10);
			CloneCraft.LOGGER.info("[watch-test] passed");
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static CloneEntity spawn(final ServerPlayer player, final int offset, final boolean wander) {
		ServerLevel level = (ServerLevel)player.level();
		int x = player.getBlockX() + offset;
		int z = player.getBlockZ() + offset;
		CloneEntity clone = CloneCraft.CLONE.create(level, EntitySpawnReason.COMMAND);
		clone.snapTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5, 0.0F, 0.0F);
		clone.setGrownUp();
		clone.setOwner(player);
		clone.setOption(CloneOption.WANDER, wander);
		level.addFreshEntity(clone);
		return clone;
	}
}
