package com.clonecraft.entity;

import com.clonecraft.CloneCraft;
import com.clonecraft.dna.DnaSpawner;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A thrown, activated spawn egg. It hatches its creature where it lands. */
public class CloneSpawnEggEntity extends ThrowableItemProjectile {
	public CloneSpawnEggEntity(final EntityType<? extends CloneSpawnEggEntity> type, final Level level) {
		super(type, level);
	}

	public CloneSpawnEggEntity(final Level level, final LivingEntity owner, final ItemStack stack) {
		super(CloneCraft.SPAWN_EGG_ENTITY, owner, level, stack);
	}

	@Override
	public void handleEntityEvent(final byte id) {
		if (id == 3) {
			ItemStack item = this.getItem();
			if (!item.isEmpty()) {
				ItemParticleOption crumbs = new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(item));
				for (int i = 0; i < 8; i++) {
					this.level().addParticle(crumbs, this.getX(), this.getY(), this.getZ(),
						(this.random.nextFloat() - 0.5) * 0.08, (this.random.nextFloat() - 0.5) * 0.08, (this.random.nextFloat() - 0.5) * 0.08);
				}
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void onHit(final HitResult hitResult) {
		super.onHit(hitResult);
		if (this.level() instanceof ServerLevel level) {
			Vec3 pos = hitResult.getLocation();
			if (hitResult instanceof BlockHitResult blockHit) {
				pos = Vec3.atBottomCenterOf(blockHit.getBlockPos().relative(blockHit.getDirection()));
			}
			DnaSpawner.spawn(level, this.getItem(), pos, this.getOwner() instanceof Player p ? p : null);
			level.broadcastEntityEvent(this, (byte)3);
			this.discard();
		}
	}

	@Override
	protected Item getDefaultItem() {
		return CloneCraft.ACTIVE_SPAWN_EGG;
	}
}
