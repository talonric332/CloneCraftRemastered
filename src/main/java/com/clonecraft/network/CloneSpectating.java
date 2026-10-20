package com.clonecraft.network;

import com.clonecraft.CloneCraft;
import com.clonecraft.entity.CloneEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Watching a clone from the Clone Remote. The player is put in spectator mode with their view locked to the clone;
 * pressing Sneak (or the clone dying or leaving) puts them back exactly where they were, in their old game mode.
 * Where they were is saved on the player, so even logging out or a server restart mid-watch brings them back.
 */
public final class CloneSpectating {
	private CloneSpectating() {}

	/** Where the player was before they started watching. */
	public record Saved(ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot, GameType gameMode) {
		public static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION).fieldOf("dimension").forGetter(Saved::dimension),
			Vec3.CODEC.fieldOf("pos").forGetter(Saved::pos),
			Codec.FLOAT.fieldOf("yRot").forGetter(Saved::yRot),
			Codec.FLOAT.fieldOf("xRot").forGetter(Saved::xRot),
			GameType.CODEC.fieldOf("gameMode").forGetter(Saved::gameMode)).apply(i, Saved::new));
	}

	public static final AttachmentType<Saved> WATCHING = AttachmentRegistry.<Saved>builder().persistent(Saved.CODEC)
		.buildAndRegister(CloneCraft.id("watching_clone"));

	/**
	 * While a player watches a clone, their body moves with the camera, so the place they left would stop being
	 * simulated and every other clone there would freeze. This ticket keeps that place running as if they were still
	 * standing there. It isn't saved, so nothing stays loaded after a restart.
	 */
	private static final TicketType STAY_ACTIVE = Registry.register(BuiltInRegistries.TICKET_TYPE, CloneCraft.id("watching_clone"),
		new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

	/** Where each watching player's ticket is, so it can be taken away again. Server thread only. */
	private record Held(ServerLevel level, ChunkPos chunk, int radius) {}

	private static final Map<UUID, Held> HELD = new HashMap<>();

	private static void keepActive(final ServerPlayer player, final ServerLevel level, final Vec3 pos) {
		release(player);
		// Entity-ticking reaches 2 chunks less than a ticket's radius: match the player's own simulation distance.
		int radius = Math.min(level.getServer().getPlayerList().getSimulationDistance(), 10) + 2;
		ChunkPos chunk = ChunkPos.containing(BlockPos.containing(pos));
		level.getChunkSource().addTicketWithRadius(STAY_ACTIVE, chunk, radius);
		HELD.put(player.getUUID(), new Held(level, chunk, radius));
	}

	private static void release(final ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held != null) {
			held.level().getChunkSource().removeTicketWithRadius(STAY_ACTIVE, held.chunk(), held.radius());
		}
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CloneSpectating::tick);
		// Logging back in mid-watch (after a crash, say) puts the player back where they were.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (handler.player.hasAttached(WATCHING)) {
				stop(handler.player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.player.hasAttached(WATCHING)) {
				stop(handler.player);
			}
		});
	}

	public static boolean isWatching(final ServerPlayer player) {
		return player.hasAttached(WATCHING);
	}

	/** Starts watching the clone (or switches to it if already watching another one). */
	public static void start(final ServerPlayer player, final CloneEntity clone) {
		if (!player.hasAttached(WATCHING)) {
			player.setAttached(WATCHING, new Saved(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.gameMode()));
			keepActive(player, player.level(), player.position());
		}
		player.setGameMode(GameType.SPECTATOR);
		if (clone.level() != player.level() && clone.level() instanceof ServerLevel level) {
			player.teleportTo(level, clone.getX(), clone.getY(), clone.getZ(), Set.of(), player.getYRot(), player.getXRot(), true);
		}
		player.setCamera(clone);
		player.sendOverlayMessage(Component.literal("Watching " + clone.getCloneName() + ". Press Sneak to stop.").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** Stops watching: back to where the player was, in the game mode they had. */
	public static void stop(final ServerPlayer player) {
		Saved saved = player.removeAttached(WATCHING);
		release(player);
		if (saved == null) {
			return;
		}
		player.setCamera(player);
		ServerLevel level = player.level().getServer().getLevel(saved.dimension());
		if (level == null) {
			level = player.level().getServer().overworld();
		}
		player.teleportTo(level, saved.pos().x, saved.pos().y, saved.pos().z, Set.of(), saved.yRot(), saved.xRot(), true);
		player.setGameMode(saved.gameMode());
		player.sendOverlayMessage(Component.literal("Stopped watching.").withStyle(ChatFormatting.GRAY));
	}

	/** Sends the watched clone's inventory, hotbar and armour to the watching player, for their screen. */
	private static void sendInventory(final ServerPlayer player, final CloneEntity clone) {
		clone.getInventory().syncHeld();
		java.util.List<net.minecraft.world.item.ItemStack> items = new java.util.ArrayList<>();
		for (net.minecraft.world.item.ItemStack stack : clone.getInventory().main) {
			items.add(stack.copy());
		}
		for (net.minecraft.world.entity.EquipmentSlot slot : com.clonecraft.clone.CloneInventory.ARMOR_SLOTS) {
			items.add(clone.getItemBySlot(slot).copy());
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
			new Payloads.WatchedInventory(clone.getId(), clone.getInventory().selected, items));
	}

	/** Sneaking (vanilla switches the camera back) or the clone dying or leaving ends the watch. */
	private static void tick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.hasAttached(WATCHING)) {
				continue;
			}
			if (!(player.getCamera() instanceof CloneEntity clone) || !clone.isAlive() || clone.isRemoved()) {
				stop(player);
			} else if (server.getTickCount() % 5 == 0) {
				sendInventory(player, clone);
			}
		}
	}
}
