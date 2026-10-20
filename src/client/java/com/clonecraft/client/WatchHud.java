package com.clonecraft.client;

import com.clonecraft.CloneCraft;
import com.clonecraft.client.screen.WatchedInventoryScreen;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * While watching a clone (Clone Remote → Watch): its hotbar, health and food at the bottom of the screen, and E opens
 * its whole inventory. The server sends the clone's inventory a few times a second while you watch.
 */
public final class WatchHud {
	private WatchHud() {}

	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
	private static final Identifier HOTBAR_SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");

	private static volatile Payloads.@Nullable WatchedInventory latest;

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.WatchedInventory.TYPE, (payload, context) -> latest = payload);
		// Its own layer: the vanilla hotbar (and anything attached to it) isn't drawn in spectator mode.
		HudElementRegistry.addLast(CloneCraft.id("watched_clone"), WatchHud::extract);
		// Before the game handles keys, so E opens the clone's inventory instead of (in spectator mode) nothing.
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (watched(mc) != null && mc.gui.screen() == null) {
				while (mc.options.keyInventory.consumeClick()) {
					mc.gui.setScreen(new WatchedInventoryScreen());
				}
			}
		});
	}

	/** The clone the camera is following, if the player is watching one. */
	public static @Nullable CloneEntity watched(final Minecraft mc) {
		return mc.getCameraEntity() instanceof CloneEntity clone && mc.player != null ? clone : null;
	}

	/** The latest inventory the server sent for this clone, or null if none has arrived yet. */
	public static Payloads.@Nullable WatchedInventory inventoryOf(final CloneEntity clone) {
		Payloads.WatchedInventory data = latest;
		return data != null && data.entityId() == clone.getId() ? data : null;
	}

	private static void extract(final GuiGraphicsExtractor g, final DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		CloneEntity clone = watched(mc);
		if (clone == null || mc.gui.screen() != null) {
			return;
		}
		int width = mc.getWindow().getGuiScaledWidth();
		int height = mc.getWindow().getGuiScaledHeight();
		int x = width / 2 - 91;
		int y = height - 22;
		Payloads.WatchedInventory data = inventoryOf(clone);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, x, y, 182, 22);
		if (data != null) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION, x - 1 + data.selected() * 20, y - 1, 24, 23);
			for (int i = 0; i < 9 && i < data.items().size(); i++) {
				ItemStack stack = data.items().get(i);
				if (!stack.isEmpty()) {
					g.item(stack, x + 3 + i * 20, y + 3);
					g.itemDecorations(mc.font, stack, x + 3 + i * 20, y + 3);
				}
			}
		}
		String stats = clone.getCloneName() + "   ❤ " + Math.round(clone.getHealth()) + "/" + Math.round(clone.getMaxHealth()) + "   Food " + clone.getFoodLevel() + "/20";
		g.centeredText(mc.font, stats, width / 2, y - 22, 0xFFFFFFFF);
		g.centeredText(mc.font, "E: its inventory   Sneak: stop watching", width / 2, y - 11, 0xFFAAAAAA);
	}
}
