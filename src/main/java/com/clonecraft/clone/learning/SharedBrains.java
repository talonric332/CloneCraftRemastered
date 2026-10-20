package com.clonecraft.clone.learning;

import com.clonecraft.CloneCraft;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * One kind of brain for every player's clones (all the clones a player owns share one), saved with the world on the
 * overworld. Clones nobody owns share one too. Server thread only.
 */
public final class SharedBrains<T> {
	/** Clones nobody owns learn together too. */
	public static final UUID UNOWNED = new UUID(0L, 0L);

	private final AttachmentType<Map<UUID, T>> type;
	private final Function<UUID, T> fresh;

	public SharedBrains(final String name, final Codec<T> codec, final Function<UUID, T> fresh) {
		this.type = AttachmentRegistry.<Map<UUID, T>>builder().persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, codec))
			.buildAndRegister(CloneCraft.id(name));
		this.fresh = fresh;
	}

	private Map<UUID, T> all(final ServerLevel anyLevel) {
		ServerLevel overworld = anyLevel.getServer().overworld();
		Map<UUID, T> map = overworld.getAttached(this.type);
		// Loaded maps can't be changed: swap in one that can.
		if (!(map instanceof HashMap<UUID, T>)) {
			map = map == null ? new HashMap<>() : new HashMap<>(map);
			overworld.setAttached(this.type, map);
		}
		return map;
	}

	public T get(final ServerLevel level, final @Nullable UUID owner) {
		return this.all(level).computeIfAbsent(owner == null ? UNOWNED : owner, this.fresh);
	}

	/** Forgets what that player's clones have learned. */
	public void reset(final ServerLevel level, final @Nullable UUID owner) {
		this.all(level).remove(owner == null ? UNOWNED : owner);
		this.changed(level);
	}

	/** Brains are changed in place, so tell the world it has something new to save. */
	public void changed(final ServerLevel level) {
		level.getServer().overworld().setAttached(this.type, new HashMap<>(this.all(level)));
	}
}
