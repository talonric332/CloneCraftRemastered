package com.clonecraft.test;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A watchable run of the bug-fix game tests in a real game window: opens a world, runs each test one at a time,
 * says in chat what to look for, and points the camera at it. Run with: gradlew runClientGameTest
 */
public class WatchFixesClientTest implements FabricClientGameTest {
	private record Demo(String test, String title, String watch, int watchTicks) {}

	private static final List<Demo> DEMOS = List.of(
		new Demo("zombie_still_hunts_clones", "Warm-up: zombies still hunt clones",
			"The zombie should walk over and attack the standing clone.", 140),
		new Demo("select_from_full_hotbar_keeps_every_item", "Fix 1: full hotbar, picking an item from the backpack",
			"The clone should end up holding bread, with its sword still in its inventory (no lost or duplicated items).", 120),
		new Demo("hungry_clone_with_full_hotbar_eats", "Fix 1: hungry clone with a full hotbar",
			"Watch the clone eat 3 bread until it's full. Before the fix the bread vanished and it starved.", 330),
		new Demo("milk_bucket_turns_into_empty_bucket", "Fix 2: drinking milk",
			"The clone picks up the milk bucket, drinks it, and should be left holding an empty bucket.", 120),
		new Demo("schematic_save_only_uses_real_selection", "Fix 3: faked schematic corners",
			"The server should refuse a save with no selection or with faked far-away corners, and accept the real one.", 100),
		new Demo("sharing_with_full_partner_drops_leftovers", "Fix 5: sharing with a full clone",
			"The right clone is full, so the 5 bread it can't take should drop on the ground instead of vanishing.", 120),
		new Demo("smart_memory_does_not_load_far_chunks", "Fix 6: Smart Memory",
			"The clone remembers a chest 40,000 blocks away. It shouldn't load that chunk or forget the chest.", 140),
		new Demo("neutral_mobs_dont_hunt_clones", "Fix 7: neutral mobs",
			"Endermen and zombified piglins shouldn't get 'hunt clones'; the zombie still should.", 100),
		new Demo("broken_life_inducer_keeps_eggs", "Fix 8: breaking a charged Life Inducer",
			"It explodes, and the spawn egg inside should survive and drop on the ground.", 120),
		new Demo("boiling_sterilizer_gives_water_back", "Fix 9: emptying a boiling Sterilizer",
			"The test player empties it with a bucket and should get a water bucket back.", 100));

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			context.waitTicks(40);
			server.runCommand("gamemode spectator @a");
			server.runCommand("difficulty normal");
			server.runCommand("time set noon");
			int y = server.computeOnServer(s -> player(s).blockPosition().getY());

			say(server, "CloneCraft bug-fix tests: " + DEMOS.size() + " checks, one at a time. Results appear in chat.", "gold");
			context.waitTicks(80);

			for (int i = 0; i < DEMOS.size(); i++) {
				Demo demo = DEMOS.get(i);
				int x = 64 * (i + 1);
				server.runCommand("tp @a " + x + " " + y + " 0 0 30");
				context.waitTicks(10);
				say(server, "[" + (i + 1) + "/" + DEMOS.size() + "] " + demo.title(), "yellow");
				say(server, demo.watch(), "gray");
				context.waitTicks(60);

				// Run it from the player's position, so the test is placed next to them (not at the world origin).
				server.runCommand("execute at @a run test run clonecraft-gametest:clone_craft_game_tests_" + demo.test());
				AABB[] box = new AABB[1];
				server.waitFor(s -> (box[0] = findTest(s, demo.test())) != null, 100);
				Vec3 c = box[0].getCenter();
				// Look down at the test area from just outside it.
				server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f facing %.1f %.1f %.1f",
					c.x, box[0].minY + 9.0, box[0].minZ - 7.0, c.x, box[0].minY + 1.0, c.z));
				context.waitTicks(demo.watchTicks());
			}

			say(server, "All done! Scroll up in chat (press T) to see every result.", "green");
			context.waitTicks(300);
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static void say(final TestServerContext server, final String text, final String color) {
		server.runCommand("tellraw @a {\"text\":\"" + text.replace("\"", "'") + "\",\"color\":\"" + color + "\"}");
	}

	/** Finds the test block that /test run just placed near the player, and returns the area of that test. */
	private static @Nullable AABB findTest(final MinecraftServer server, final String test) {
		ServerPlayer player = player(server);
		BlockPos center = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-24, -12, -24), center.offset(24, 12, 24))) {
			if (player.level().getBlockEntity(pos) instanceof TestInstanceBlockEntity be
				&& be.test().map(key -> key.identifier().getPath().endsWith(test)).orElse(false)) {
				return be.getStructureBounds();
			}
		}
		return null;
	}
}
