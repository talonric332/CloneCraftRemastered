package com.clonecraft.network;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneTeam;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.item.SchematicSelectorItem;
import com.clonecraft.schematic.Schematic;
import com.clonecraft.schematic.SchematicStore;
import java.util.ArrayList;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.Nullable;

public final class ServerNetworking {
	private ServerNetworking() {}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Payloads.CloneAction.TYPE, Payloads.CloneAction.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Payloads.SetName.TYPE, Payloads.SetName.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Payloads.SetList.TYPE, Payloads.SetList.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Payloads.Build.TYPE, Payloads.Build.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Payloads.SaveSchematic.TYPE, Payloads.SaveSchematic.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.CloneLists.TYPE, Payloads.CloneLists.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.SchematicList.TYPE, Payloads.SchematicList.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenSaveSchematic.TYPE, Payloads.OpenSaveSchematic.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.Selection.TYPE, Payloads.Selection.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Payloads.RemoteAction.TYPE, Payloads.RemoteAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.RemoteList.TYPE, Payloads.RemoteList.CODEC);

		PayloadTypeRegistry.serverboundPlay().register(Payloads.BulkEdit.TYPE, Payloads.BulkEdit.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Payloads.RemoteAction.TYPE, (payload, context) -> RemoteControl.handle(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.BulkEdit.TYPE, (payload, context) -> RemoteControl.bulkEdit(payload, context.player()));

		ServerPlayNetworking.registerGlobalReceiver(Payloads.CloneAction.TYPE, (payload, context) -> handleAction(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SetName.TYPE, (payload, context) -> {
			CloneEntity clone = usable(context.player(), payload.entityId());
			if (clone != null) {
				clone.setCloneName(payload.name());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SetList.TYPE, (payload, context) -> {
			CloneEntity clone = usable(context.player(), payload.entityId());
			if (clone != null) {
				var set = payload.list() == 0 ? clone.attackTypes : clone.breakBlocks;
				set.clear();
				set.addAll(payload.ids());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Build.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			CloneEntity clone = usable(player, payload.entityId());
			if (clone == null) {
				return;
			}
			Schematic schematic = SchematicStore.load(player.level().getServer(), payload.schematic());
			if (schematic == null) {
				player.sendSystemMessage(Component.literal("Couldn't find the schematic '" + payload.schematic() + "'.").withStyle(ChatFormatting.RED));
				return;
			}
			if (payload.origin().distSqr(clone.blockPosition()) > 128 * 128) {
				return;
			}
			clone.buildGoal.setDelay(payload.delay());
			clone.buildGoal.startBuilding(schematic, payload.origin(), payload.rotation(), payload.creative() && player.getAbilities().instabuild);
			player.sendSystemMessage(Component.literal("Start Building!").withStyle(ChatFormatting.GOLD));
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SaveSchematic.TYPE, (payload, context) -> saveSchematic(payload, context.player()));
	}

	private static @Nullable CloneEntity usable(final ServerPlayer player, final int entityId) {
		if (player.level().getEntity(entityId) instanceof CloneEntity clone && clone.isAlive() && clone.canUse(player)
			&& (clone.distanceToSqr(player) < 64 * 64 || clone.hasRemoteAccess(player))) {
			return clone;
		}
		return null;
	}

	private static void handleAction(final Payloads.CloneAction payload, final ServerPlayer player) {
		CloneEntity clone = usable(player, payload.entityId());
		CloneCraft.LOGGER.info("[CloneCraft] clone action {} on entity {} from {}: {}", payload.action(), payload.entityId(),
			player.getGameProfile().name(), clone == null ? "REJECTED (not usable)" : "ok");
		if (clone == null) {
			return;
		}
		ServerLevel level = (ServerLevel)player.level();
		switch (payload.action()) {
			case Payloads.CloneAction.TRANSFER_XP -> clone.transferXp(player);
			case Payloads.CloneAction.KILL -> {
				player.closeContainer();
				clone.kill(level);
				CloneCraft.LOGGER.info("[CloneCraft] kill {}: alive afterwards = {}, health = {}", clone.getCloneName(), clone.isAlive(), clone.getHealth());
			}
			case Payloads.CloneAction.CLAIM -> {
				clone.setOwner(player);
				player.sendSystemMessage(Component.literal("You now own " + clone.getCloneName()).withStyle(ChatFormatting.GOLD));
			}
			case Payloads.CloneAction.COPY -> {
				if (player.getAbilities().instabuild) {
					int amount = Math.max(1, Math.min(50, payload.value()));
					for (int i = 0; i < amount; i++) {
						CloneEntity copy = CloneCraft.CLONE.create(level, EntitySpawnReason.COMMAND);
						if (copy != null) {
							copy.copyFrom(clone);
							copy.snapTo(clone.getX() + (level.getRandom().nextFloat() - 0.5) / 10.0, clone.getY(),
								clone.getZ() + (level.getRandom().nextFloat() - 0.5) / 10.0, clone.getYRot(), 0.0F);
							level.addFreshEntity(copy);
						}
					}
				}
			}
			case Payloads.CloneAction.SET_TEAM -> clone.setCloneTeam(CloneTeam.byId(payload.value()));
			case Payloads.CloneAction.SET_OPTIONS -> clone.setOptions(payload.value());
			case Payloads.CloneAction.OPEN_MAIN -> clone.openMenu(player);
			case Payloads.CloneAction.REQUEST_LISTS -> ServerPlayNetworking.send(player,
				new Payloads.CloneLists(clone.getId(), new ArrayList<>(clone.attackTypes), new ArrayList<>(clone.breakBlocks)));
			case Payloads.CloneAction.STOP_BUILDING -> clone.buildGoal.stopBuilding();
			case Payloads.CloneAction.REQUEST_SCHEMATICS -> {
				var list = new ArrayList<Payloads.SchematicInfo>();
				for (SchematicStore.Entry e : SchematicStore.list(level.getServer())) {
					list.add(new Payloads.SchematicInfo(e.name(), e.sizeX(), e.sizeY(), e.sizeZ(), e.blocks()));
				}
				ServerPlayNetworking.send(player, new Payloads.SchematicList(clone.getId(), list));
			}
			default -> {
			}
		}
	}

	private static void saveSchematic(final Payloads.SaveSchematic payload, final ServerPlayer player) {
		String name = SchematicStore.cleanName(payload.name());
		if (name.isEmpty()) {
			player.sendSystemMessage(Component.literal("Give the schematic a name first.").withStyle(ChatFormatting.RED));
			return;
		}
		// Use the corners the player actually picked with the Schematic Selector, never the ones the client sends,
		// so a modified client can't make the server load or generate huge areas.
		BlockPos[] sel = SchematicSelectorItem.getSelection(player.getUUID());
		if (sel == null || !sel[0].equals(payload.a()) || !sel[1].equals(payload.b())) {
			player.sendSystemMessage(Component.literal("Select both corners with the Schematic Selector first.").withStyle(ChatFormatting.RED));
			return;
		}
		BlockPos a = sel[0];
		BlockPos b = sel[1];
		long sx = Math.abs(a.getX() - b.getX()) + 1L;
		long sy = Math.abs(a.getY() - b.getY()) + 1L;
		long sz = Math.abs(a.getZ() - b.getZ()) + 1L;
		if (sx * sy * sz > 1_000_000L) {
			player.sendSystemMessage(Component.literal("That selection is too big (max 1,000,000 blocks).").withStyle(ChatFormatting.RED));
			return;
		}
		if (a.distSqr(player.blockPosition()) > 256 * 256 || b.distSqr(player.blockPosition()) > 256 * 256) {
			player.sendSystemMessage(Component.literal("You're too far from the selection to save it.").withStyle(ChatFormatting.RED));
			return;
		}
		BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
			for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
				if (!player.level().isLoaded(new BlockPos(cx << 4, min.getY(), cz << 4))) {
					player.sendSystemMessage(Component.literal("Part of the selection isn't loaded. Move closer and try again.").withStyle(ChatFormatting.RED));
					return;
				}
			}
		}
		var server = player.level().getServer();
		if (SchematicStore.exists(server, name) && !payload.overwrite()) {
			player.sendSystemMessage(Component.literal("A schematic called '" + name + "' already exists.").withStyle(ChatFormatting.RED));
			return;
		}
		Schematic schematic = Schematic.capture(player.level(), name, a, b);
		if (SchematicStore.save(server, schematic)) {
			player.sendSystemMessage(Component.literal("Schematic '" + name + "' saved!").withStyle(ChatFormatting.GREEN));
		} else {
			player.sendSystemMessage(Component.literal("Couldn't save the schematic.").withStyle(ChatFormatting.RED));
		}
	}
}
