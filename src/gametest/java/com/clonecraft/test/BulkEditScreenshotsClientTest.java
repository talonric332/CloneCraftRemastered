package com.clonecraft.test;

import com.clonecraft.client.screen.CloneBulkEditScreen;
import com.clonecraft.network.Payloads;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;

/**
 * Opens the Bulk Edit screens with some made-up clones and takes screenshots (build/run/clientGameTest/screenshots),
 * to check the layout. Nothing is sent to a server: the screens are only looked at, never applied.
 */
public class BulkEditScreenshotsClientTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		List<Payloads.RemoteClone> clones = new ArrayList<>();
		String[] names = {"Steve", "Alex", "Notch", "Jeb", "Dinnerbone", "Grumm"};
		for (int i = 0; i < names.length; i++) {
			clones.add(new Payloads.RemoteClone(UUID.randomUUID(), i, names[i], 20.0F, 20.0F, "minecraft:overworld", BlockPos.ZERO, true,
				Payloads.RemoteClone.MODE_FOLLOW, ""));
		}
		// Block icons need a loaded world, like when a player opens Bulk Edit from the Clone Remote.
		try (var world = context.worldBuilder().create()) {
			context.waitTicks(20);
			this.screenshots(context, clones);
		}
	}

	private void screenshots(final ClientGameTestContext context, final List<Payloads.RemoteClone> clones) {
		context.setScreen(() -> new CloneBulkEditScreen(null, clones));
		context.waitTicks(5);
		context.takeScreenshot("bulk_edit_1_main");

		context.clickScreenButton("Entities To Attack...");
		context.waitTicks(5);
		context.takeScreenshot("bulk_edit_2_attack_list");

		context.clickScreenButton("+ Monsters");
		context.waitTicks(5);
		context.takeScreenshot("bulk_edit_3_monsters_added");

		context.clickScreenButton("Done");
		context.waitTicks(5);
		context.takeScreenshot("bulk_edit_4_back_with_changes");

		context.clickScreenButton("Blocks To Break...");
		context.waitTicks(2);
		context.clickScreenButton("+ Ores");
		context.clickScreenButton("Others: Keep");
		context.waitTicks(5);
		context.takeScreenshot("bulk_edit_5_break_replace");
		context.clickScreenButton("Done");
		context.waitTicks(5);
		context.takeScreenshot("bulk_edit_6_back_with_lists");
		context.clickScreenButton("Options...");
		context.waitTicks(2);
		context.clickScreenButton("Sprint: Keep");
		context.clickScreenButton("Wander: Keep");
		context.clickScreenButton("Wander: ON");
		context.waitTicks(5);
		context.takeScreenshot("bulk_edit_7_options");
		context.clickScreenButton("Done");
		context.waitTicks(5);
		context.takeScreenshot("bulk_edit_8_final");
		context.setScreen(() -> null);
	}
}
