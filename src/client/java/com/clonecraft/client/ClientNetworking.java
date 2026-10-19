package com.clonecraft.client;

import com.clonecraft.client.screen.SaveSchematicScreen;
import com.clonecraft.network.Payloads;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientNetworking {
	private ClientNetworking() {}

	public static final Map<Integer, Payloads.CloneLists> LISTS = new HashMap<>();
	public static volatile List<Payloads.SchematicInfo> SCHEMATICS;

	public static void send(final CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.CloneLists.TYPE, (payload, context) -> LISTS.put(payload.entityId(), payload));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.SchematicList.TYPE, (payload, context) -> SCHEMATICS = payload.schematics());
		ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenSaveSchematic.TYPE,
			(payload, context) -> Minecraft.getInstance().gui.setScreen(new SaveSchematicScreen(payload.a(), payload.b())));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.Selection.TYPE,
			(payload, context) -> Outlines.setSelection(payload.a().orElse(null), payload.b().orElse(null)));
		ClientPlayNetworking.registerGlobalReceiver(Payloads.RemoteList.TYPE, (payload, context) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.gui.screen() instanceof com.clonecraft.client.screen.CloneRemoteScreen screen) {
				screen.update(payload.clones());
			} else if (payload.open()) {
				mc.gui.setScreen(new com.clonecraft.client.screen.CloneRemoteScreen(payload.clones()));
			}
		});
	}
}
