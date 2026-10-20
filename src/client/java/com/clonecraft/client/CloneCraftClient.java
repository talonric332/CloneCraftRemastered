package com.clonecraft.client;

import com.clonecraft.CloneCraft;
import com.clonecraft.client.screen.CentrifugeScreen;
import com.clonecraft.client.screen.CloneScreen;
import com.clonecraft.client.screen.LifeInducerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class CloneCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(CloneCraft.CLONE, CloneRenderer::new);
		EntityRendererRegistry.register(CloneCraft.SPAWN_EGG_ENTITY, ThrownItemRenderer::new);
		MenuScreens.register(CloneCraft.CLONE_MENU, CloneScreen::new);
		MenuScreens.register(CloneCraft.CENTRIFUGE_MENU, CentrifugeScreen::new);
		MenuScreens.register(CloneCraft.LIFE_INDUCER_MENU, LifeInducerScreen::new);
		ClientNetworking.register();
		WatchHud.register();
		ClientTickEvents.END_CLIENT_TICK.register(Outlines::tick);
	}
}
