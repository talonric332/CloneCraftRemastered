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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * With "Share Items" on, a clone standing next to a teammate hands over spare armour, weapons and tools the teammate
 * needs, and if the teammate has much less food, arrows or torches, it looks at them for a
 * moment and hands half the difference over.
 */
public class ShareGoal extends Goal {
	private static final List<Predicate<ItemStack>> SHARED = List.of(s -> s.is(ItemTags.ARROWS), s -> s.has(DataComponents.FOOD),
		s -> s.is(Items.TORCH));

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
			ItemStack gear = this.spareGearFor(other);
			if (!gear.isEmpty()) {
				this.offer = gear.copyWithCount(1);
				this.partner = other;
				return true;
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

	/**
	 * Finds a piece of gear this clone isn't using that would help the teammate: armour better than what they wear,
	 * a weapon better than their best one, or a bow or tool of a kind they don't have at all.
	 * Clones always wear their best armour, so armour in the inventory is spare. A weapon or tool only counts as spare
	 * when the clone keeps another one at least as good.
	 */
	private ItemStack spareGearFor(final CloneEntity other) {
		List<ItemStack> mine = this.clone.getInventory().main;
		for (ItemStack s : mine) {
			if (s.isEmpty() || other.getInventory().fitCount(s.copyWithCount(1)) <= 0) {
				continue;
			}
			var equippable = s.get(DataComponents.EQUIPPABLE);
			if (equippable != null && equippable.slot().isArmor() && equippable.slot() != EquipmentSlot.BODY) {
				EquipmentSlot slot = equippable.slot();
				if (CloneEntity.armorValue(s, slot) > CloneEntity.armorValue(other.getItemBySlot(slot), slot)) {
					return s;
				}
				continue;
			}
			double score = CloneEntity.weaponScore(s);
			if (score > 0 && this.keepsAnother(s, x -> CloneEntity.weaponScore(x) >= score)
				&& score > best(other.getInventory().main, CloneEntity::weaponScore)) {
				return s;
			}
			for (Predicate<ItemStack> kind : TOOL_KINDS) {
				if (kind.test(s) && this.keepsAnother(s, kind) && other.getInventory().find(kind) < 0) {
					return s;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	private static final List<Predicate<ItemStack>> TOOL_KINDS = List.of(
		s -> s.is(ItemTags.PICKAXES), s -> s.is(ItemTags.AXES), s -> s.is(ItemTags.SHOVELS), s -> s.is(ItemTags.HOES),
		s -> s.is(Items.BOW), s -> s.is(Items.CROSSBOW), s -> s.is(Items.SHEARS), s -> s.is(Items.FISHING_ROD));

	/** True if this clone has another item (not this exact stack) matching the test. */
	private boolean keepsAnother(final ItemStack stack, final Predicate<ItemStack> test) {
		for (ItemStack s : this.clone.getInventory().main) {
			if (s != stack && !s.isEmpty() && test.test(s)) {
				return true;
			}
		}
		return stack.getCount() > 1;
	}

	private static double best(final List<ItemStack> items, final java.util.function.ToDoubleFunction<ItemStack> score) {
		double best = 0;
		for (ItemStack s : items) {
			if (!s.isEmpty()) {
				best = Math.max(best, score.applyAsDouble(s));
			}
		}
		return best;
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
				// pickupItem puts armour straight on if it's better than what they wear.
				this.partner.pickupItem(template.copyWithCount(removed));
				this.clone.getInventory().syncHeld();
				this.clone.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.0F + (this.clone.getRandom().nextFloat() - 0.5F) / 10.0F);
			}
		}
		this.partner = null;
		this.offer = ItemStack.EMPTY;
	}
}
