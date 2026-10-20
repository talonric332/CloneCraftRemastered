package com.clonecraft;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.CloneTeam;
import com.clonecraft.clone.learning.CombatBrain;
import com.clonecraft.clone.learning.CombatBrains;
import com.clonecraft.clone.learning.CombatMove;
import com.clonecraft.clone.learning.MiningBrain;
import com.clonecraft.clone.learning.MiningMove;
import com.clonecraft.entity.CloneEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Extra shortcuts (not in the original mod):
 * /clonecraft spawn [player] [count], /clonecraft battle red blue [count], /clonecraft team, /clonecraft mode, /clonecraft killall
 */
public final class CloneCommands {
	private static final double RANGE = 64.0;

	private CloneCommands() {}

	public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("clonecraft")
			.then(Commands.literal("spawn")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> spawn(ctx, null, 1))
				.then(Commands.argument("player", StringArgumentType.word())
					.suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), b))
					.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "player"), 1))
					.then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
						.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"))))))
			.then(Commands.literal("battle")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("red", StringArgumentType.word())
					.then(Commands.argument("blue", StringArgumentType.word())
						.executes(ctx -> battle(ctx, 5))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 25))
							.executes(ctx -> battle(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
			.then(Commands.literal("team")
				.then(Commands.argument("team", StringArgumentType.word())
					.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(CloneTeam.values()).map(t -> t.displayName), b))
					.executes(CloneCommands::team)))
			.then(Commands.literal("mode")
				.then(Commands.literal("follow").executes(ctx -> mode(ctx, CloneOption.FOLLOW)))
				.then(Commands.literal("stay").executes(ctx -> mode(ctx, CloneOption.GUARD)))
				.then(Commands.literal("wander").executes(ctx -> mode(ctx, CloneOption.WANDER))))
			.then(Commands.literal("killall").executes(CloneCommands::killAll))
			// Anyone can see (and reset) what their own clones have learned about fighting.
			.then(Commands.literal("skill").executes(CloneCommands::skill)
				.then(Commands.literal("reset").executes(CloneCommands::resetSkill))));
	}

	private static int skill(final CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		CombatBrain brain = CombatBrains.get(source.getLevel(), player == null ? null : player.getUUID());
		String who = player == null ? "Unowned clones" : "Your clones";
		if (brain.fights == 0) {
			source.sendSuccess(() -> Component.literal(who + " haven't learned anything about fighting yet.").withStyle(ChatFormatting.GRAY), false);
			mining(source, player);
			return 0;
		}
		long total = Math.max(1L, Arrays.stream(brain.moveCounts).sum());
		CombatMove[] all = CombatMove.values();
		Integer[] order = new Integer[all.length];
		for (int i = 0; i < all.length; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Long.compare(brain.moveCounts[b], brain.moveCounts[a]));
		StringBuilder moves = new StringBuilder();
		for (int i : order) {
			long percent = brain.moveCounts[i] * 100 / total;
			if (percent >= 1) {
				moves.append(moves.isEmpty() ? "" : ", ").append(all[i].description).append(' ').append(percent).append('%');
			}
		}
		String text = who + "' fighting brain: " + brain.fights + " fights, " + brain.wins + " won, " + brain.deaths + " clones lost.\n"
			+ String.format(java.util.Locale.ROOT, "Lately: %.1f damage dealt and %.1f taken per fight. Still trying new moves %d%% of the time.%n",
				brain.avgDealt, brain.avgTaken, Math.round(brain.exploreChance() * 100))
			+ "Moves used: " + moves;
		source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.AQUA), false);
		mining(source, player);
		return brain.fights;
	}

	private static void mining(final CommandSourceStack source, final @Nullable ServerPlayer player) {
		MiningBrain brain = CombatBrains.MINING.get(source.getLevel(), player == null ? null : player.getUUID());
		if (brain.stretches == 0) {
			source.sendSuccess(() -> Component.literal("No mining learned yet.").withStyle(ChatFormatting.GRAY), false);
			return;
		}
		long total = Math.max(1L, Arrays.stream(brain.moveCounts).sum());
		StringBuilder moves = new StringBuilder();
		for (MiningMove move : MiningMove.values()) {
			long percent = brain.moveCounts[move.ordinal()] * 100 / total;
			if (percent >= 1) {
				moves.append(moves.isEmpty() ? "" : ", ").append(move.description).append(' ').append(percent).append('%');
			}
		}
		String text = String.format(java.util.Locale.ROOT, "Mining brain: %d stretches of tunnel. Lately %.1f ore value a minute. Still trying new ways %d%% of the time.%nWays used: %s",
			brain.stretches, brain.avgValue, Math.round(brain.exploreChance() * 100), moves);
		source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.AQUA), false);
	}

	private static int resetSkill(final CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		CombatBrains.reset(source.getLevel(), player == null ? null : player.getUUID());
		CombatBrains.MINING.reset(source.getLevel(), player == null ? null : player.getUUID());
		source.sendSuccess(() -> Component.literal("Your clones have forgotten everything they learned about fighting and mining.").withStyle(ChatFormatting.YELLOW), false);
		return 1;
	}

	private static int spawn(final CommandContext<CommandSourceStack> ctx, final @Nullable String name, final int count) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		ServerLevel level = source.getLevel();
		for (int i = 0; i < count; i++) {
			CloneEntity clone = create(level, source.getPosition(), i, count);
			if (clone == null) {
				continue;
			}
			if (name != null) {
				clone.setCloneName(name);
			} else {
				clone.setCloneOf(source.getPlayerOrException().getGameProfile());
			}
			if (player != null) {
				clone.setOwner(player);
			}
			level.addFreshEntity(clone);
		}
		String who = name != null ? name : source.getTextName();
		source.sendSuccess(() -> Component.literal("Cloned " + who + " x" + count).withStyle(ChatFormatting.GREEN), true);
		return count;
	}

	private static int battle(final CommandContext<CommandSourceStack> ctx, final int count) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		String red = StringArgumentType.getString(ctx, "red");
		String blue = StringArgumentType.getString(ctx, "blue");
		Vec3 facing = Vec3.directionFromRotation(0.0F, source.getRotation().y).scale(8.0);
		Vec3 side = new Vec3(-facing.z, 0.0, facing.x);
		army(level, source.getPosition().add(facing).add(side.scale(0.5)), red, CloneTeam.RED, count);
		army(level, source.getPosition().add(facing).subtract(side.scale(0.5)), blue, CloneTeam.BLUE, count);
		source.sendSuccess(() -> Component.literal("CLONE BATTLE! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal(red).withStyle(ChatFormatting.RED))
			.append(Component.literal(" vs ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(blue).withStyle(ChatFormatting.BLUE)), true);
		return count * 2;
	}

	private static void army(final ServerLevel level, final Vec3 pos, final String name, final CloneTeam team, final int count) {
		for (int i = 0; i < count; i++) {
			CloneEntity clone = create(level, pos, i, count);
			if (clone == null) {
				continue;
			}
			clone.setCloneName(name);
			clone.setCloneTeam(team);
			clone.setOption(CloneOption.WANDER, true);
			level.addFreshEntity(clone);
		}
	}

	private static @Nullable CloneEntity create(final ServerLevel level, final Vec3 pos, final int index, final int count) {
		CloneEntity clone = CloneCraft.CLONE.create(level, EntitySpawnReason.COMMAND);
		if (clone == null) {
			return null;
		}
		double angle = count <= 1 ? 0.0 : (Math.PI * 2.0 * index) / count;
		double radius = count <= 1 ? 0.0 : 1.5 + count * 0.15;
		clone.snapTo(pos.x + Math.cos(angle) * radius, pos.y, pos.z + Math.sin(angle) * radius, level.getRandom().nextFloat() * 360.0F, 0.0F);
		clone.setGrownUp();
		return clone;
	}

	private static List<CloneEntity> myClones(final CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		return source.getLevel().getEntitiesOfClass(CloneEntity.class, player.getBoundingBox().inflate(RANGE), c -> c.canUse(player));
	}

	private static int team(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CloneTeam team = CloneTeam.byName(StringArgumentType.getString(ctx, "team"));
		if (team == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown team. Try Red, Blue, Good, Evil or Rampant."));
			return 0;
		}
		List<CloneEntity> clones = myClones(ctx.getSource());
		clones.forEach(c -> c.setCloneTeam(team));
		ctx.getSource().sendSuccess(() -> Component.literal(clones.size() + " clone(s) joined the " + team.displayName + " team").withColor(team.color), false);
		return clones.size();
	}

	private static int mode(final CommandContext<CommandSourceStack> ctx, final CloneOption mode) throws CommandSyntaxException {
		List<CloneEntity> clones = myClones(ctx.getSource());
		for (CloneEntity c : clones) {
			c.setOption(CloneOption.FOLLOW, mode == CloneOption.FOLLOW);
			c.setOption(CloneOption.GUARD, mode == CloneOption.GUARD);
			c.setOption(CloneOption.WANDER, mode == CloneOption.WANDER);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(clones.size() + " clone(s): " + mode.label), false);
		return clones.size();
	}

	private static int killAll(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		List<CloneEntity> clones = myClones(ctx.getSource());
		clones.forEach(CloneEntity::discard);
		ctx.getSource().sendSuccess(() -> Component.literal("Removed " + clones.size() + " clone(s)"), false);
		return clones.size();
	}
}
