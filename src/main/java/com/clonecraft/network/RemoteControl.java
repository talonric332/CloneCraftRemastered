package com.clonecraft.network;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The server side of the Clone Remote: lists the clones a player owns (in every loaded dimension),
 * teleports them to the player, changes their mode, and opens their menu from any distance.
 */
public final class RemoteControl {
	private RemoteControl() {}

	public static boolean hasRemote(final Player player) {
		return player.getInventory().contains(stack -> stack.is(CloneCraft.CLONE_REMOTE));
	}

	/** All the living clones this player owns, in loaded chunks of every dimension. Nearest first, other dimensions last. */
	public static List<CloneEntity> ownedClones(final ServerPlayer player) {
		List<CloneEntity> clones = new ArrayList<>();
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			clones.addAll(level.getEntities(CloneCraft.CLONE, c -> c.isAlive() && c.isOwnedBy(player)));
		}
		clones.sort(Comparator.<CloneEntity>comparingInt(c -> c.level() == player.level() ? 0 : 1)
			.thenComparingDouble(c -> c.level() == player.level() ? c.distanceToSqr(player) : 0.0)
			.thenComparing(CloneEntity::getCloneName));
		return clones;
	}

	public static void sendList(final ServerPlayer player, final boolean open) {
		List<Payloads.RemoteClone> entries = new ArrayList<>();
		for (CloneEntity clone : ownedClones(player)) {
			entries.add(new Payloads.RemoteClone(clone.getUUID(), clone.getId(), clone.getCloneName(), clone.getHealth(), clone.getMaxHealth(),
				clone.level().dimension().identifier().toString(), clone.blockPosition(), clone.level() == player.level(), modeOf(clone)));
		}
		ServerPlayNetworking.send(player, new Payloads.RemoteList(entries, open));
	}

	public static int modeOf(final CloneEntity clone) {
		if (clone.hasOption(CloneOption.FOLLOW)) {
			return Payloads.RemoteClone.MODE_FOLLOW;
		}
		if (clone.hasOption(CloneOption.GUARD)) {
			return Payloads.RemoteClone.MODE_STAY;
		}
		if (clone.hasOption(CloneOption.WANDER)) {
			return Payloads.RemoteClone.MODE_WANDER;
		}
		return Payloads.RemoteClone.MODE_NONE;
	}

	public static void handle(final Payloads.RemoteAction payload, final ServerPlayer player) {
		CloneCraft.LOGGER.info("[CloneCraft] remote action {} value {} on {} from {} (has remote: {}, clone found: {})", payload.action(), payload.value(),
			payload.cloneId(), player.getGameProfile().name(), hasRemote(player), find(player, payload) != null);
		if (!hasRemote(player)) {
			return;
		}
		switch (payload.action()) {
			case Payloads.RemoteAction.TELEPORT -> {
				CloneEntity clone = find(player, payload);
				if (clone != null) {
					teleport(clone, player);
					player.sendSystemMessage(Component.literal(clone.getCloneName() + " teleported to you.").withStyle(ChatFormatting.LIGHT_PURPLE));
				}
			}
			case Payloads.RemoteAction.TELEPORT_ALL -> {
				List<CloneEntity> clones = ownedClones(player);
				for (CloneEntity clone : clones) {
					teleport(clone, player);
				}
				player.sendSystemMessage(Component.literal(clones.size() + " clone(s) teleported to you.").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			case Payloads.RemoteAction.SET_MODE -> {
				CloneEntity clone = find(player, payload);
				if (clone != null) {
					int mode = payload.value();
					clone.setOption(CloneOption.FOLLOW, mode == Payloads.RemoteClone.MODE_FOLLOW);
					clone.setOption(CloneOption.GUARD, mode == Payloads.RemoteClone.MODE_STAY);
					clone.setOption(CloneOption.WANDER, mode == Payloads.RemoteClone.MODE_WANDER);
				}
			}
			case Payloads.RemoteAction.MANAGE -> {
				CloneEntity clone = find(player, payload);
				if (clone != null && clone.level() == player.level()) {
					clone.openMenu(player);
				}
				return;
			}
			default -> {
			}
		}
		sendList(player, false);
	}

	private static @Nullable CloneEntity find(final ServerPlayer player, final Payloads.RemoteAction payload) {
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			if (level.getEntity(payload.cloneId()) instanceof CloneEntity clone && clone.isAlive() && clone.isOwnedBy(player)) {
				return clone;
			}
		}
		return null;
	}

	/** Brings the clone to the player, across dimensions if needed. A guarding clone guards its new spot. */
	private static void teleport(final CloneEntity clone, final ServerPlayer player) {
		clone.getNavigation().stop();
		clone.setGuardPos(null);
		clone.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), Set.of(), player.getYRot(), 0.0F, true);
	}
}
