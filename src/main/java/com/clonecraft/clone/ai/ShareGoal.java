package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * With "Share Items" on, a clone standing next to a teammate who has much less food or arrows looks at them for a
 * moment and hands half the difference over.
 */
public class ShareGoal extends Goal {
	private static final List<Predicate<ItemStack>> SHARED = List.of(s -> s.is(ItemTags.ARROWS), s -> s.has(DataComponents.FOOD));

	private final CloneEntity clone;
	private @Nullable CloneEntity partner;
	private ItemStack offer = ItemStack.EMPTY;
	private int countDown;

	public ShareGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.LOOK));
	}

	public ItemStack getOffer() {
		return this.offer;
	}

	@Override
	public boolean canUse() {
		if (!this.clone.hasOption(CloneOption.SHARE) || this.clone.tickCount % 10 != 0 || this.clone.getRandom().nextFloat() > 0.1F) {
			return false;
		}
		List<CloneEntity> near = this.clone.level().getEntitiesOfClass(CloneEntity.class, this.clone.getBoundingBox().inflate(2, 1, 2));
		near.sort(Comparator.comparingDouble(c -> c.distanceToSqr(this.clone)));
		for (CloneEntity other : near) {
			if (other == this.clone || other.getCloneTeam() != this.clone.getCloneTeam() || !other.hasOption(CloneOption.SHARE)) {
				continue;
			}
			for (Predicate<ItemStack> type : SHARED) {
				int mine = this.clone.getInventory().count(type);
				int theirs = other.getInventory().count(type);
				if (mine - theirs < 2) {
					continue;
				}
				int slot = this.clone.getInventory().find(type);
				if (slot < 0) {
					continue;
				}
				ItemStack stack = this.clone.getInventory().main.get(slot);
				int give = Math.min(Math.min((mine - theirs) / 2, 64), stack.getCount());
				int fit = other.getInventory().fitCount(stack.copyWithCount(give));
				if (fit > 0) {
					this.offer = stack.copyWithCount(fit);
					this.partner = other;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void start() {
		this.countDown = 30;
	}

	@Override
	public boolean canContinueToUse() {
		return this.countDown > 0 && this.partner != null && this.partner.isAlive();
	}

	@Override
	public void tick() {
		this.countDown--;
		if (this.partner != null) {
			this.clone.getLookControl().setLookAt(this.partner);
			this.partner.getLookControl().setLookAt(this.clone);
		}
	}

	@Override
	public void stop() {
		if (this.partner != null && this.partner.isAlive() && this.partner.distanceToSqr(this.clone) < 16 && !this.offer.isEmpty()) {
			ItemStack template = this.offer;
			int removed = 0;
			for (int i = 0; i < this.clone.getInventory().main.size() && removed < template.getCount(); i++) {
				ItemStack s = this.clone.getInventory().main.get(i);
				if (ItemStack.isSameItemSameComponents(s, template)) {
					int take = Math.min(s.getCount(), template.getCount() - removed);
					s.shrink(take);
					removed += take;
				}
			}
			if (removed > 0) {
				this.partner.getInventory().add(template.copyWithCount(removed));
				this.clone.getInventory().syncHeld();
				this.clone.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.0F + (this.clone.getRandom().nextFloat() - 0.5F) / 10.0F);
			}
		}
		this.partner = null;
		this.offer = ItemStack.EMPTY;
	}
}
