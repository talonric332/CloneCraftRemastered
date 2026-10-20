package com.clonecraft.clone.progression;

import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Clones talking to each other (in chat, so their owner can follow along): planning the house together, asking each
 * other for things, calling for help, sharing news, saying good night. What one says can get a reply from a teammate
 * (another clone with the same owner, nearby), and some replies come with doing something: bringing what was asked
 * for, or coming to help. Each clone only says each kind of thing every so often, so chat doesn't fill up.
 * Server thread only.
 */
public final class CloneTalk {
	public enum Topic {
		HOUSE_STARTED(0),
		HOUSE_ADDED(0),
		HOUSE_DONE(0),
		HOUSE_BLOWN_UP(600),
		NEED(6000),
		HELP(600),
		FOUND(1200),
		GOOD_NIGHT(6000),
		GOOD_MORNING(6000);

		/** How long (in ticks) before the same clone says this kind of thing again. */
		final int cooldown;

		Topic(final int cooldown) {
			this.cooldown = cooldown;
		}
	}

	/** A reply waiting to be said (a moment after what it answers, like a real conversation). */
	private record Pending(UUID speaker, ServerLevel level, long at, String text, @Nullable Consumer<CloneEntity> then) {}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<UUID, Map<Topic, Long>> LAST = new HashMap<>();

	private CloneTalk() {
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(CloneTalk::tick);
		// Nothing carries over from one world to the next.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			LAST.clear();
		});
	}

	/** Whether this clone can say this kind of thing now (and if so, it counts as said). */
	private static boolean mayTalk(final CloneEntity clone, final Topic topic) {
		long now = clone.level().getGameTime();
		Map<Topic, Long> last = LAST.computeIfAbsent(clone.getUUID(), k -> new HashMap<>());
		Long before = last.get(topic);
		if (before != null && now - before < topic.cooldown) {
			return false;
		}
		last.put(topic, now);
		return true;
	}

	/** Says something to the others (and its owner). Returns false if it said this kind of thing too recently. */
	public static boolean say(final CloneEntity clone, final Topic topic, final String text) {
		if (!mayTalk(clone, topic)) {
			return false;
		}
		clone.say(text, clone.getOwnerPlayer());
		return true;
	}

	/** Its teammates nearby: the other clones with the same owner, within this many blocks. */
	public static List<CloneEntity> teammates(final CloneEntity clone, final double range) {
		UUID owner = clone.getOwnerId();
		if (owner == null) {
			return List.of();
		}
		return clone.level().getEntitiesOfClass(CloneEntity.class, clone.getBoundingBox().inflate(range),
			c -> c != clone && c.isAlive() && owner.equals(c.getOwnerId()));
	}

	/** A teammate answers after a moment (and then maybe does something about it). */
	public static void reply(final CloneEntity who, final String text, final @Nullable Consumer<CloneEntity> then) {
		if (who.level() instanceof ServerLevel level) {
			PENDING.add(new Pending(who.getUUID(), level, level.getGameTime() + 30 + who.getRandom().nextInt(30), text, then));
		}
	}

	/** Says something, and the nearest teammate (if any) answers with one of these. */
	public static void sayAndHearBack(final CloneEntity clone, final Topic topic, final String text, final String... answers) {
		if (!say(clone, topic, text) || answers.length == 0) {
			return;
		}
		List<CloneEntity> mates = teammates(clone, 64.0);
		if (mates.isEmpty()) {
			return;
		}
		CloneEntity mate = mates.get(clone.getRandom().nextInt(mates.size()));
		reply(mate, answers[clone.getRandom().nextInt(answers.length)].replace("{name}", clone.getCloneName()), null);
	}

	/** Calls for help; teammates nearby who'll fight answer and come for the attacker. */
	public static void callForHelp(final CloneEntity clone, final net.minecraft.world.entity.LivingEntity attacker) {
		if (!say(clone, Topic.HELP, "Help! I'm being attacked!")) {
			return;
		}
		boolean answered = false;
		for (CloneEntity mate : teammates(clone, 24.0)) {
			if (!mate.hasOption(com.clonecraft.clone.CloneOption.FIGHT) || mate.getTarget() != null || !mate.canAttackEntity(attacker)) {
				continue;
			}
			// The first one says so; they all come.
			mate.setTarget(attacker);
			if (!answered) {
				answered = true;
				reply(mate, "Hang on, " + clone.getCloneName() + ", I'm coming!", null);
			}
		}
	}

	/** Shares a find with the others. */
	public static void found(final CloneEntity clone, final String what) {
		sayAndHearBack(clone, Topic.FOUND, "I found " + what + "!", "Nice one, {name}!", "Great find!", "Brilliant!");
	}

	private static void tick(final MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Pending> due = new ArrayList<>();
		for (Pending p : PENDING) {
			if (p.level().getGameTime() >= p.at()) {
				due.add(p);
			}
		}
		PENDING.removeAll(due);
		for (Pending p : due) {
			Entity entity = p.level().getEntity(p.speaker());
			if (entity instanceof CloneEntity clone && clone.isAlive()) {
				clone.say(p.text(), clone.getOwnerPlayer());
				if (p.then() != null) {
					p.then().accept(clone);
				}
			}
		}
	}

	/** Forgets a clone that's gone (so this doesn't grow forever). */
	public static void forget(final UUID clone) {
		LAST.remove(clone);
	}
}
