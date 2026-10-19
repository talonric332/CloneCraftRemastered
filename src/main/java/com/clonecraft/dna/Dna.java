package com.clonecraft.dna;

import com.clonecraft.CloneCraft;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The DNA stored inside a needle, test tube or spawn egg: which kind of creature it came from.
 * Players (and clones) give "Human" DNA, which hatches into a clone.
 */
public record Dna(Identifier entity) {
	public static final Codec<Dna> CODEC = Identifier.CODEC.xmap(Dna::new, Dna::entity);
	public static final StreamCodec<ByteBuf, Dna> STREAM_CODEC = Identifier.STREAM_CODEC.map(Dna::new, Dna::entity);

	public static @Nullable Dna of(final Entity entity) {
		if (entity instanceof Player || entity instanceof com.clonecraft.entity.CloneEntity) {
			return human();
		}
		if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity instanceof Mannequin) {
			return null;
		}
		return new Dna(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
	}

	public static Dna human() {
		return new Dna(CloneCraft.CLONE_KEY.identifier());
	}

	public static @Nullable Dna get(final ItemStack stack) {
		return stack.get(CloneCraft.DNA);
	}

	public boolean isHuman() {
		return this.entity.equals(CloneCraft.CLONE_KEY.identifier());
	}

	public Optional<EntityType<?>> type() {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(this.entity);
	}

	public Component displayName() {
		if (this.isHuman()) {
			return Component.translatable("clonecraft.human");
		}
		return this.type().map(EntityType::getDescription).orElse(Component.literal(this.entity.toString()));
	}

	/** Gives a stable colour for this creature, used to tint names. */
	public int color() {
		if (this.isHuman()) {
			return 0xD72A2A;
		}
		int h = this.entity.hashCode();
		float hue = (h & 0xFFFF) / 65535.0F;
		return net.minecraft.util.Mth.hsvToRgb(hue, 0.55F, 0.95F) & 0xFFFFFF;
	}
}
