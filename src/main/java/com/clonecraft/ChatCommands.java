package com.clonecraft;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * Talk to your clones in chat, starting the message with a backslash, like the original mod:
 * <pre>
 * \hello            \follow me        \stop following
 * \stay here        \guard 10 64 -20  \stop staying
 * \come here        \kill 5 zombies   \stop
 * \kill yourself please
 * </pre>
 * Say a clone's name to talk to that clone, or "everyone" to talk to all of them. Otherwise the closest one answers.
 */
public final class ChatCommands {
	private ChatCommands() {}

	private static final String[] HELLO_REPLIES = {
		"Top of the morning to ye @PLAYER", "G'Day @PLAYER", "Yeha?", "Whaddya want @PLAYER?", "Leave me alone", "What can I do yer for?",
		"Hello there @PLAYER. Lovely weather today isn't it?", "Howsit going @PLAYER?", "Rack off mate. I'm doing just fine here",
		"I'm bored @PLAYER", "Do I look stupid to you? Don't answer that!", "How much wood could a wood chuck chop if a wood chuck could chop wood?",
		"Howdy", "You smell lovely today. Not that.. I was trying to.. smell you..", "Bless you too kind person"
	};
	private static final String[] FOLLOW_REPLIES = {
		"Okay!", "Okay @PLAYER!", "Okey Dokey!", "Sure can do", "I'll follow you anywhere.", "Fiine. If I have to", "Okay then. Let's go!", "Sure thing.", "Let's go @PLAYER"
	};
	private static final String[] NUMBERS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve"};

	/** Returns true if the message was meant for clones (so it shouldn't be shown as normal chat). */
	public static boolean handle(final ServerPlayer player, final String raw) {
		if (!raw.startsWith("\\")) {
			return false;
		}
		String message = raw.substring(1).trim();
		String lower = message.toLowerCase(Locale.ROOT);
		String[] words = lower.replace("!", " ").replace("?", " ").replace(",", " ").replace(".", " ").split("\\s+");
		ServerLevel level = (ServerLevel)player.level();

		List<CloneEntity> clones = level.getEntitiesOfClass(CloneEntity.class, player.getBoundingBox().inflate(64, 48, 64),
			c -> c.isAlive() && c.hasOption(CloneOption.COMMAND) && c.canUse(player));
		if (clones.isEmpty()) {
			return true;
		}

		boolean everyone = lower.contains("everyone") || lower.contains("every one") || lower.contains("everybody") || lower.contains("all of you");
		List<CloneEntity> targets = new ArrayList<>();
		if (everyone) {
			targets.addAll(clones);
		} else {
			CloneEntity named = null;
			for (CloneEntity c : clones) {
				if (hasWord(words, c.getCloneName().toLowerCase(Locale.ROOT)) && (named == null || c.distanceToSqr(player) < named.distanceToSqr(player))) {
					named = c;
				}
			}
			if (named == null) {
				for (CloneEntity c : clones) {
					if (named == null || c.distanceToSqr(player) < named.distanceToSqr(player)) {
						named = c;
					}
				}
			}
			targets.add(named);
		}

		for (CloneEntity clone : targets) {
			run(clone, player, lower, words);
		}
		return true;
	}

	private static boolean hasWord(final String[] words, final String... options) {
		for (String w : words) {
			for (String o : options) {
				if (w.equals(o)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String pick(final CloneEntity clone, final String[] options, final ServerPlayer player) {
		return options[clone.getRandom().nextInt(options.length)].replace("@PLAYER", player.getGameProfile().name());
	}

	private static void run(final CloneEntity clone, final ServerPlayer player, final String lower, final String[] words) {
		boolean stop = hasWord(words, "stop", "cancel", "forget", "nevermind", "don't", "dont", "quit");

		if (hasWord(words, "kill", "attack", "destroy", "suicide", "maim")) {
			kill(clone, player, lower, words);
		} else if (hasWord(words, "follow", "following", "track", "tracking", "lead")) {
			if (stop) {
				clone.setOption(CloneOption.FOLLOW, false);
				clone.say("Okay", player);
			} else {
				clone.setOwner(player);
				clone.setOption(CloneOption.FOLLOW, true);
				clone.setOption(CloneOption.GUARD, false);
				clone.say(pick(clone, FOLLOW_REPLIES, player), player);
			}
		} else if (hasWord(words, "stay", "staying", "remain", "still", "guard", "guarding", "defend", "defending")) {
			if (stop) {
				clone.setOption(CloneOption.GUARD, false);
				clone.say("Okay", player);
			} else {
				clone.setOption(CloneOption.FOLLOW, false);
				clone.setOption(CloneOption.WANDER, false);
				clone.setOption(CloneOption.GUARD, true);
				BlockPos pos = readPosition(words);
				if (pos != null) {
					clone.setGuardPos(pos);
					clone.say("Okay, I'll stay at the position (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")", player);
				} else {
					clone.setGuardPos(clone.blockPosition().below());
					clone.say("Okay, I'll guard my position here.", player);
				}
			}
		} else if (hasWord(words, "come")) {
			if (clone.distanceToSqr(player) > 400) {
				clone.snapTo(player.getX(), player.getY(), player.getZ(), clone.getYRot(), clone.getXRot());
				clone.getNavigation().stop();
			} else {
				clone.getNavigation().moveTo(player, 1.0);
			}
			clone.say("Coming!", player);
		} else if (stop) {
			clone.commandGoal.clear();
			clone.setTarget(null);
			clone.getNavigation().stop();
			clone.say("Okay, I'll stop.", player);
		} else if (hasWord(words, "hello", "hey", "hi", "yo", "wassup", "sup", "howdy")) {
			clone.say(pick(clone, HELLO_REPLIES, player), player);
			if (clone.onGround()) {
				clone.getJumpControl().jump();
			}
		} else {
			clone.say("Huh? I don't understand.", player);
		}
	}

	private static void kill(final CloneEntity clone, final ServerPlayer player, final String lower, final String[] words) {
		if (hasWord(words, "yourself", "suicide", "self")) {
			if (hasWord(words, "please", "pls", "plz")) {
				clone.say("Okay then!", player);
				clone.kill((ServerLevel)clone.level());
			} else {
				clone.say("Say please!", player);
			}
			return;
		}
		Set<EntityType<?>> types = new HashSet<>();
		String typeName = null;
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (!DefaultAttributes.hasSupplier(type) || type == net.minecraft.world.entity.EntityTypes.PLAYER) {
				continue;
			}
			String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
			String name = type.getDescription().getString().toLowerCase(Locale.ROOT);
			String joined = name.replace(" ", "_");
			for (String w : words) {
				String single = w.endsWith("es") && w.length() > 3 ? w.substring(0, w.length() - 2) : w;
				String singleS = w.endsWith("s") && w.length() > 2 ? w.substring(0, w.length() - 1) : w;
				if (w.equals(path) || w.equals(joined) || singleS.equals(path) || singleS.equals(joined) || single.equals(path) || lower.contains(name)) {
					types.add(type);
					typeName = type.getDescription().getString();
				}
			}
		}
		if (types.isEmpty()) {
			clone.say("Kill what?", player);
			return;
		}
		int amount = 1;
		for (String w : words) {
			try {
				amount = Integer.parseInt(w);
				break;
			} catch (NumberFormatException ignored) {
				for (int i = 1; i < NUMBERS.length; i++) {
					if (NUMBERS[i].equals(w)) {
						amount = i;
					}
				}
			}
		}
		amount = Math.max(1, Math.min(100, amount));
		clone.commandGoal.setKillTask(types, amount, player);
		clone.say("Okay, I'll try and kill " + amount + " " + (types.size() == 1 ? typeName : "of those") + (amount > 1 && types.size() == 1 ? "s" : "") + "!", player);
	}

	private static BlockPos readPosition(final String[] words) {
		List<Integer> numbers = new ArrayList<>();
		for (String w : words) {
			try {
				numbers.add(Integer.parseInt(w));
			} catch (NumberFormatException ignored) {
			}
		}
		return numbers.size() >= 3 ? new BlockPos(numbers.get(0), numbers.get(1) - 1, numbers.get(2)) : null;
	}

	@SuppressWarnings("unused")
	private static boolean isLiving(final LivingEntity e) {
		return true;
	}
}
