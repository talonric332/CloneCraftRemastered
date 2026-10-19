package com.clonecraft.block;

import com.clonecraft.CloneCraft;
import com.clonecraft.menu.LifeInducerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LifeInducerBlockEntity extends BaseContainerBlockEntity {
	public static final int MAX_POWER = 160;
	private static final int CHARGE_TIME = 60;

	private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
	private final int[] charges = new int[27];
	public int power;
	private int ticks;
	private int nextChargeStart;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(final int id) {
			return LifeInducerBlockEntity.this.power;
		}

		@Override
		public void set(final int id, final int value) {
			LifeInducerBlockEntity.this.power = value;
		}

		@Override
		public int getCount() {
			return 1;
		}
	};

	public LifeInducerBlockEntity(final BlockPos pos, final BlockState state) {
		super(CloneCraft.LIFE_INDUCER_BE, pos, state);
	}

	public static boolean isValid(final ItemStack stack) {
		return stack.is(CloneCraft.SPAWN_EGG);
	}

	/** Called when lightning comes down the antenna. */
	public void charge() {
		if (this.power <= MAX_POWER - 16) {
			this.power += 16;
			this.setChanged();
		}
	}

	public void serverTick(final ServerLevel level, final BlockPos pos, final BlockState state) {
		this.ticks++;
		if (this.power > 0) {
			int added = 0;
			for (int a = 0; a < this.items.size() && added < 9; a++) {
				ItemStack stack = this.items.get(a);
				if (!isValid(stack)) {
					this.charges[a] = 0;
					continue;
				}
				if (this.charges[a] != 0 || this.ticks >= this.nextChargeStart) {
					this.charges[a]++;
					added++;
					if (this.charges[a] >= CHARGE_TIME) {
						ItemStack active = new ItemStack(CloneCraft.ACTIVE_SPAWN_EGG, stack.getCount());
						active.applyComponents(stack.getComponentsPatch());
						this.items.set(a, active);
						this.power--;
						this.charges[a] = 0;
						level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.5F, 1.6F);
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 70, 0.3, 0.3, 0.3, 0.5);
						this.setChanged();
					} else if (this.charges[a] == 1) {
						level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.3F, 1.8F);
						this.nextChargeStart = this.ticks + 1 + level.getRandom().nextInt(8);
						break;
					}
				}
			}
			this.zapNearby(level, pos);
		}
		boolean powered = this.power > 0;
		if (state.getValue(LifeInducerBlock.POWERED) != powered) {
			level.setBlock(pos, state.setValue(LifeInducerBlock.POWERED, powered), Block.UPDATE_ALL);
		}
	}

	/** A charged Life Inducer zaps the closest creature it can see, unless it's wearing leather or diamond boots. */
	private void zapNearby(final ServerLevel level, final BlockPos pos) {
		int chance = Math.max(1, 50 - this.power * 50 / MAX_POWER);
		if (this.ticks % 5 != 0 || level.getRandom().nextInt(chance) != 0) {
			return;
		}
		Vec3 middle = Vec3.atCenterOf(pos);
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(5.0))) {
			if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
				continue;
			}
			ItemStack boots = e.getItemBySlot(EquipmentSlot.FEET);
			if (boots.is(Items.LEATHER_BOOTS) || boots.is(Items.DIAMOND_BOOTS)) {
				continue;
			}
			double d = e.position().distanceToSqr(middle);
			if (d < bestDist && this.canSee(level, middle, e.getEyePosition(), pos)) {
				bestDist = d;
				best = e;
			}
		}
		if (best != null) {
			double dist = Math.sqrt(bestDist) + 0.5;
			Vec3 push = best.position().add(0, best.getBbHeight() / 2, 0).subtract(middle).normalize().scale(3.0 / dist);
			best.push(push.x, push.y, push.z);
			best.needsSync = true;
			best.hurtServer(level, level.damageSources().lightningBolt(), (float)(12.0 / dist));
			Vec3 to = best.position().add(0, best.getBbHeight() / 2, 0);
			for (int i = 0; i <= 10; i++) {
				Vec3 p = middle.lerp(to, i / 10.0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
			}
			level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.4F, 2.0F);
		}
	}

	private boolean canSee(final Level level, final Vec3 from, final Vec3 to, final BlockPos self) {
		var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(self);
	}

	/** Breaking a charged Life Inducer releases the energy all at once. */
	@Override
	public void preRemoveSideEffects(final BlockPos pos, final BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (this.power > 0 && this.level instanceof ServerLevel level) {
			double energy = this.power / 14.0;
			Vec3 middle = Vec3.atCenterOf(pos);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(16.0, 12.0, 16.0))) {
				double dist = Math.max(1.0, e.position().distanceTo(middle));
				Vec3 push = e.position().subtract(middle).normalize().scale(energy / dist);
				e.push(push.x, push.y + 0.3, push.z);
				e.needsSync = true;
				e.hurtServer(level, level.damageSources().generic(), (float)(energy / dist));
			}
			level.explode(null, middle.x, middle.y, middle.z, (float)(this.power * 6.0 / MAX_POWER), Level.ExplosionInteraction.BLOCK);
			this.power = 0;
		}
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.clonecraft.life_inducer");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(final NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return 27;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean canPlaceItem(final int slot, final ItemStack stack) {
		return isValid(stack);
	}

	@Override
	protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
		return new LifeInducerMenu(containerId, inventory, this, this.data);
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.power = input.getIntOr("power", 0);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putInt("power", this.power);
	}
}
