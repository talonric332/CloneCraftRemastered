package com.clonecraft.clone.progression;

import com.clonecraft.CloneCraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The staircases clones have dug down into the ground to go mining, saved with each dimension, so other clones (and
 * the same one next time) walk down an existing staircase instead of digging another. No clone ever digs away one of
 * their steps. Server thread only.
 */
public final class MineStairs {
	/** A staircase: where it starts (up top), where it ends (down below), and the blocks that are its steps. */
	public record Stairs(BlockPos top, BlockPos bottom, List<BlockPos> steps) {
		public static final Codec<Stairs> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("top").forGetter(Stairs::top),
			BlockPos.CODEC.fieldOf("bottom").forGetter(Stairs::bottom),
			BlockPos.CODEC.listOf().fieldOf("steps").forGetter(Stairs::steps)
		).apply(i, Stairs::new));
	}

	/** At most this many staircases are remembered per dimension (the oldest is forgotten first). */
	private static final int MAX = 16;

	private static final AttachmentType<List<Stairs>> STAIRS = AttachmentRegistry.<List<Stairs>>builder()
		.persistent(Stairs.CODEC.listOf()).buildAndRegister(CloneCraft.id("mine_stairs"));

	/** Every step of every staircase, per dimension, for quick checks. Rebuilt when the staircases change. */
	private static final Map<ServerLevel, Set<BlockPos>> STEPS = new WeakHashMap<>();

	private MineStairs() {
	}

	/** Loads the class, so the saved data type is registered before any world loads. */
	public static void register() {
	}

	public static List<Stairs> all(final ServerLevel level) {
		List<Stairs> list = level.getAttached(STAIRS);
		return list == null ? List.of() : list;
	}

	private static void save(final ServerLevel level, final List<Stairs> list) {
		level.setAttached(STAIRS, List.copyOf(list));
		STEPS.remove(level);
	}

	/** Remembers a new staircase (or a longer version of one, which replaces it). */
	public static void add(final ServerLevel level, final Stairs stairs, final @Nullable Stairs replaces) {
		List<Stairs> list = new ArrayList<>(all(level));
		if (replaces != null) {
			list.remove(replaces);
		}
		list.add(new Stairs(stairs.top().immutable(), stairs.bottom().immutable(), List.copyOf(stairs.steps())));
		while (list.size() > MAX) {
			list.remove(0);
		}
		save(level, list);
	}

	/** Forgets a staircase (it's been blocked or dug away). */
	public static void remove(final ServerLevel level, final Stairs stairs) {
		List<Stairs> list = new ArrayList<>(all(level));
		if (list.remove(stairs)) {
			save(level, list);
		}
	}

	public static boolean isStep(final ServerLevel level, final BlockPos pos) {
		Set<BlockPos> steps = STEPS.computeIfAbsent(level, l -> {
			Set<BlockPos> set = new HashSet<>();
			for (Stairs stairs : all(l)) {
				set.addAll(stairs.steps());
			}
			return set;
		});
		return steps.contains(pos);
	}

	/**
	 * The nearest staircase worth taking to go down: its top within 48 blocks, and it goes at least 6 blocks lower
	 * than the clone is (not past the depth it wants).
	 */
	public static @Nullable Stairs down(final ServerLevel level, final BlockPos from, final int depth, final Set<Stairs> skip) {
		Stairs best = null;
		for (Stairs stairs : all(level)) {
			if (skip.contains(stairs) || !level.isLoaded(stairs.top()) || !level.isLoaded(stairs.bottom())
				|| !stairs.top().closerThan(from, 48.0) || stairs.bottom().getY() > from.getY() - 6 || stairs.bottom().getY() < depth - 4) {
				continue;
			}
			if (best == null || stairs.top().distSqr(from) < best.top().distSqr(from)) {
				best = stairs;
			}
		}
		return best;
	}

	/** The nearest staircase that leads up from near here: its bottom within 32 blocks. */
	public static @Nullable Stairs up(final ServerLevel level, final BlockPos from, final Set<Stairs> skip) {
		Stairs best = null;
		for (Stairs stairs : all(level)) {
			if (skip.contains(stairs) || !level.isLoaded(stairs.top()) || !level.isLoaded(stairs.bottom())
				|| !stairs.bottom().closerThan(from, 32.0) || stairs.top().getY() <= from.getY()) {
				continue;
			}
			if (best == null || stairs.bottom().distSqr(from) < best.bottom().distSqr(from)) {
				best = stairs;
			}
		}
		return best;
	}
}
