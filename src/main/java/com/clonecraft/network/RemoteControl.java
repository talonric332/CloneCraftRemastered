package com.clonecraft.network;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.CloneTeam;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

	/** Options the bulk editor never touches: they're part of who the clone is (Female also picks its name), not how it behaves. */
	public static final int BULK_LOCKED = CloneOption.FEMALE.bit();

	/** Applies the same option, team and list changes to many owned clones. Names are never changed here. */
	public static void bulkEdit(final Payloads.BulkEdit payload, final ServerPlayer player) {
		if (!hasRemote(player)) {
			return;
		}
		int clear = payload.clearMask() & ~BULK_LOCKED;
		int set = payload.setMask() & ~BULK_LOCKED & ~clear;
		CloneTeam team = payload.team() >= 0 && payload.team() < CloneTeam.values().length ? CloneTeam.values()[payload.team()] : null;
		CloneEntity source = payload.listSource().map(id -> find(player, id)).orElse(null);
		if (payload.listSource().isPresent() && source == null) {
			player.sendSystemMessage(Component.literal("Couldn't find the clone to copy lists from.").withStyle(ChatFormatting.RED));
		}
		List<Identifier> attack = source == null ? null : List.copyOf(source.attackTypes);
		List<Identifier> blocks = source == null ? null : List.copyOf(source.breakBlocks);
		Payloads.ListEdit attackEdit = valid(payload.attackEdit(), BuiltInRegistries.ENTITY_TYPE);
		Payloads.ListEdit breakEdit = valid(payload.breakEdit(), BuiltInRegistries.BLOCK);

		int changed = 0;
		for (UUID id : new LinkedHashSet<>(payload.clones())) {
			CloneEntity clone = find(player, id);
			if (clone == null) {
				continue;
			}
			if (set != 0 || clear != 0) {
				clone.setOptions((clone.getOptions() & ~clear) | set);
			}
			if (team != null) {
				clone.setCloneTeam(team);
			}
			if (source != null && clone != source) {
				clone.attackTypes.clear();
				clone.attackTypes.addAll(attack);
				clone.breakBlocks.clear();
				clone.breakBlocks.addAll(blocks);
			}
			applyListEdit(clone.attackTypes, attackEdit);
			applyListEdit(clone.breakBlocks, breakEdit);
			changed++;
		}
		player.sendSystemMessage(Component.literal("Updated " + changed + " clone" + (changed == 1 ? "" : "s") + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
		sendList(player, false);
	}

	/** Drops any names that aren't real mobs (or blocks), so a bad client can't fill clones' lists with junk. */
	private static Payloads.ListEdit valid(final Payloads.ListEdit edit, final Registry<?> registry) {
		return new Payloads.ListEdit(edit.add().stream().filter(registry::containsKey).distinct().toList(),
			edit.remove().stream().filter(registry::containsKey).distinct().toList(), edit.replace());
	}

	public static void applyListEdit(final Set<Identifier> list, final Payloads.ListEdit edit) {
		if (edit.replace()) {
			list.clear();
		} else {
			edit.remove().forEach(list::remove);
		}
		list.addAll(edit.add());
	}

	private static @Nullable CloneEntity find(final ServerPlayer player, final Payloads.RemoteAction payload) {
		return find(player, payload.cloneId());
	}

	private static @Nullable CloneEntity find(final ServerPlayer player, final UUID id) {
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			if (level.getEntity(id) instanceof CloneEntity clone && clone.isAlive() && clone.isOwnedBy(player)) {
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
