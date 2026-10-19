package com.clonecraft.dna;

import com.clonecraft.entity.CloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Hatches whatever creature a spawn egg's DNA came from. */
public final class DnaSpawner {
	private DnaSpawner() {}

	public static @Nullable Entity spawn(final ServerLevel level, final ItemStack egg, final Vec3 pos, final @Nullable Player spawnedBy) {
		Dna dna = Dna.get(egg);
		if (dna == null) {
			return null;
		}
		EntityType<?> type = dna.type().orElse(null);
		Entity entity = type == null ? null : type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (entity == null) {
			if (spawnedBy != null) {
				spawnedBy.sendSystemMessage(Component.translatable("message.clonecraft.cant_spawn", dna.displayName()).withStyle(ChatFormatting.RED));
			}
			return null;
		}

		entity.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		if (entity instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.SPAWN_ITEM_USE, null);
		}

		Component customName = egg.get(DataComponents.CUSTOM_NAME);
		if (entity instanceof CloneEntity clone) {
			if (spawnedBy != null) {
				clone.setOwner(spawnedBy);
			}
			if (customName != null) {
				clone.setCloneName(customName.getString());
			}
		} else if (customName != null) {
			entity.setCustomName(customName);
		}

		level.addFreshEntityWithPassengers(entity);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1.0F, 0.9F + level.getRandom().nextFloat() / 5.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BUBBLE_POP, SoundSource.NEUTRAL, 1.0F, 1.0F);
		return entity;
	}
}
