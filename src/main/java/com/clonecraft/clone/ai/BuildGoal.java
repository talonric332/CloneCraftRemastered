package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.schematic.Schematic;
import com.clonecraft.schematic.SchematicStore;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Builds a saved schematic block by block, in four stages like the original: solid blocks, then other blocks,
 * then redstone and fire, then a final pass. Uses blocks from the clone's inventory (fetching them from chests it
 * remembers) unless "Creative Mode" building was chosen. Blocks in the way are mined out first.
 */
public class BuildGoal extends Goal {
	private final CloneEntity clone;
	private @Nullable Schematic schematic;
	private String schematicName = "";
	private BlockPos origin = BlockPos.ZERO;
	private Rotation rotation = Rotation.NONE;
	private int index;
	private int stage;
	private boolean running;
	private boolean creative;
	private int delay = 4;
	private int cooldown;
	private @Nullable Item waitingFor;
	private int waitTicks;
	private final Set<Integer> skip = new HashSet<>();

	public BuildGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public boolean isRunning() {
		return this.running;
	}

	public boolean isCreative() {
		return this.creative;
	}

	public void setDelay(final int delay) {
		this.delay = Math.max(0, Math.min(19, delay));
	}

	public void startBuilding(final Schematic schematic, final BlockPos origin, final int rotation, final boolean creative) {
		this.schematic = schematic;
		this.schematicName = schematic.name;
		this.origin = origin;
		this.rotation = Rotation.values()[Math.floorMod(rotation, 4)];
		this.index = 0;
		this.stage = 0;
		this.creative = creative;
		this.running = true;
		this.skip.clear();
		this.waitingFor = null;
		this.sync();
	}

	public void stopBuilding() {
		this.running = false;
		this.clone.mineGoal.cancelRequest();
		this.clone.setBuildProgress(0, 0, -1);
	}

	private void sync() {
		this.clone.setBuildProgress(this.index, this.schematic == null ? 0 : this.schematic.size(), this.running ? this.stage : -1);
	}

	/** Called by the mining AI when it can't clear a block. */
	public void cantBreak(final BlockPos pos, final BlockState state) {
		if (this.schematic == null) {
			return;
		}
		this.skip.add(this.index);
		this.clone.say("I can't break the " + state.getBlock().getName().getString() + " in the way, so I'll skip it.", this.clone.getOwnerPlayer());
	}

	@Override
	public boolean canUse() {
		if (this.running && this.schematic == null && this.clone.level() instanceof ServerLevel level) {
			this.schematic = SchematicStore.load(level.getServer(), this.schematicName);
			if (this.schematic == null) {
				this.running = false;
			}
		}
		return this.running && this.schematic != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private BlockPos worldPos(final int i) {
		BlockPos local = this.schematic.localPos(i);
		return this.origin.offset(StructureTemplate.transform(local, Mirror.NONE, this.rotation, BlockPos.ZERO));
	}

	private static boolean isLate(final BlockState state) {
		Block b = state.getBlock();
		return b instanceof RedstoneWireBlock || b instanceof BaseFireBlock || state.is(BlockTags.RAILS) || b == Blocks.REDSTONE_TORCH
			|| b == Blocks.REDSTONE_WALL_TORCH || b == Blocks.REPEATER || b == Blocks.COMPARATOR || b == Blocks.LEVER || state.is(BlockTags.BUTTONS);
	}

	private boolean belongsToStage(final BlockState target) {
		return switch (this.stage) {
			case 0 -> !isLate(target) && target.isCollisionShapeFullBlock(this.clone.level(), BlockPos.ZERO);
			case 1 -> !isLate(target) && !target.isCollisionShapeFullBlock(this.clone.level(), BlockPos.ZERO);
			case 2 -> isLate(target);
			default -> false;
		};
	}

	@Override
	public void tick() {
		if (this.schematic == null || !(this.clone.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.cooldown > 0) {
			this.cooldown--;
			return;
		}
		if (this.clone.mineGoal.isRequested(this.worldPos(Math.min(this.index, this.schematic.size() - 1)))) {
			return;
		}
		if (this.clone.fetchGoal.isFetching()) {
			return;
		}

		for (int tries = 0; tries < 1000; tries++) {
			if (this.stage >= 4) {
				this.running = false;
				this.clone.setBuildProgress(this.schematic.size(), this.schematic.size(), -1);
				this.clone.say("I'm finished building " + this.schematicName + "!", this.clone.getOwnerPlayer());
				return;
			}
			if (this.index >= this.schematic.size()) {
				this.index = 0;
				this.stage++;
				this.sync();
				continue;
			}
			if (this.skip.contains(this.index)) {
				this.index++;
				continue;
			}
			BlockPos pos = this.worldPos(this.index);
			if (!level.isInWorldBounds(pos)) {
				this.index++;
				continue;
			}
			BlockState target = this.schematic.get(this.index).rotate(this.rotation);
			BlockState current = level.getBlockState(pos);

			if (target.isAir()) {
				boolean clear = this.stage == 0 && !this.clone.hasOption(CloneOption.SKIP_AIR) && !current.isAir() && current.getFluidState().isEmpty();
				if (!clear) {
					this.index++;
					continue;
				}
				if (this.removeInTheWay(level, pos, current)) {
					this.index++;
					continue;
				}
				return;
			}
			if (!this.belongsToStage(target)) {
				this.index++;
				continue;
			}
			if (current.equals(target)) {
				this.index++;
				continue;
			}
			if (current.is(target.getBlock())) {
				// Same block facing a different way: just turn it.
				level.setBlock(pos, target, Block.UPDATE_ALL);
				this.index++;
				continue;
			}
			if ((target.is(Blocks.DIRT) || target.is(Blocks.GRASS_BLOCK)) && (current.is(Blocks.DIRT) || current.is(Blocks.GRASS_BLOCK) || current.is(Blocks.FARMLAND))) {
				this.index++;
				continue;
			}
			if (current.is(Blocks.BEDROCK)) {
				this.index++;
				continue;
			}
			if (!current.isAir() && current.getFluidState().isEmpty() && !current.canBeReplaced()) {
				if (this.stage != 0) {
					this.index++;
					continue;
				}
				if (this.removeInTheWay(level, pos, current)) {
					continue;
				}
				return;
			}
			if (!target.getFluidState().isEmpty() && !this.creative) {
				this.index++;
				continue;
			}

			Item item = target.getBlock().asItem();
			if (!this.creative) {
				if (item == Items.AIR) {
					this.index++;
					continue;
				}
				if (this.clone.getInventory().find(s -> s.is(item)) < 0) {
					this.waitForItem(item);
					return;
				}
			}
			this.waitingFor = null;

			if (this.clone.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 36.0) {
				if (this.clone.tickCount % 10 == 0) {
					this.clone.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
				}
				return;
			}
			if (this.clone.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos)) && target.isCollisionShapeFullBlock(level, pos)) {
				this.clone.getJumpControl().jump();
				return;
			}

			this.clone.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			level.setBlock(pos, target, Block.UPDATE_ALL);
			var sound = target.getSoundType();
			level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			this.clone.swingForAttack(InteractionHand.MAIN_HAND);
			if (!this.creative) {
				this.clone.getInventory().consumeOne(s -> s.is(item));
			}
			this.index++;
			this.cooldown = this.creative ? this.delay : 4;
			this.sync();
			return;
		}
		this.sync();
	}

	/** Clears a block that's in the way. Returns true if it's gone already, false if the clone has to mine it first. */
	private boolean removeInTheWay(final ServerLevel level, final BlockPos pos, final BlockState current) {
		if (this.creative && !this.clone.hasOption(CloneOption.BREAK_BLOCKS)) {
			level.levelEvent(2001, pos, Block.getId(current));
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			return true;
		}
		this.clone.mineGoal.request(pos);
		return false;
	}

	private void waitForItem(final Item item) {
		if (item != this.waitingFor) {
			this.waitingFor = item;
			this.waitTicks = 0;
			if (!this.clone.fetchGoal.fetch(s -> s.is(item))) {
				this.clone.say("I need some " + new net.minecraft.world.item.ItemStack(item).getHoverName().getString() + " to keep building!", this.clone.getOwnerPlayer());
			}
		} else if (++this.waitTicks % 200 == 0) {
			if (!this.clone.fetchGoal.fetch(s -> s.is(item))) {
				this.clone.showMessage("I need " + new net.minecraft.world.item.ItemStack(item).getHoverName().getString());
			}
		}
	}

	public void save(final ValueOutput output) {
		output.putBoolean("Building", this.running);
		output.putString("BuildSchematic", this.schematicName);
		output.store("BuildOrigin", BlockPos.CODEC, this.origin);
		output.putInt("BuildRotation", this.rotation.ordinal());
		output.putInt("BuildIndex", this.index);
		output.putInt("BuildStage", this.stage);
		output.putBoolean("BuildCreative", this.creative);
		output.putInt("BuildDelay", this.delay);
	}

	public void load(final ValueInput input) {
		this.running = input.getBooleanOr("Building", false);
		this.schematicName = input.getStringOr("BuildSchematic", "");
		this.origin = input.read("BuildOrigin", BlockPos.CODEC).orElse(BlockPos.ZERO);
		this.rotation = Rotation.values()[Math.floorMod(input.getIntOr("BuildRotation", 0), 4)];
		this.index = input.getIntOr("BuildIndex", 0);
		this.stage = input.getIntOr("BuildStage", 0);
		this.creative = input.getBooleanOr("BuildCreative", false);
		this.delay = input.getIntOr("BuildDelay", 4);
		this.schematic = null;
	}
}
