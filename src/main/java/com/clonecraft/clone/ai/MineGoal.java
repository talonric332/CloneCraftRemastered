package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mining, the way the original clones did it: with "Break Blocks" on, the clone looks for the nearest block it can see
 * that is on its "Blocks To Break" list (within 16 blocks), walks up to it, picks the best tool from its inventory and
 * digs it out at the same speed a player would. With "Farm" on it also harvests fully grown crops and replants them.
 * The building AI uses this too, to clear blocks that are in the way.
 */
public class MineGoal extends Goal {
	private static final int RADIUS = 16;
	private static final int[][] OFFSETS = buildOffsets();

	private final CloneEntity clone;
	private final List<Unbreakable> unbreakables = new ArrayList<>();

	private @Nullable BlockPos requested;
	/** Called if the requested block can't be broken (no suitable tool, or the clone can't reach it). */
	private BiConsumer<BlockPos, BlockState> onRequestFailed = (pos, state) -> {};
	/** Creative building breaks blocks instantly. */
	private boolean requestCreative;
	private @Nullable BlockPos target;
	private @Nullable BlockState targetState;
	private float progress;
	private boolean hitting;
	private int hitDelay;
	private int soundCounter;
	private int noImprovement;
	private Vec3 lastPos = Vec3.ZERO;
	private int nextSearch;

	private record Unbreakable(BlockPos pos, BlockState state) {}

	public MineGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private static int[][] buildOffsets() {
		List<int[]> list = new ArrayList<>();
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int y = -RADIUS; y <= RADIUS; y++) {
				for (int z = -RADIUS; z <= RADIUS; z++) {
					list.add(new int[]{x, y, z});
				}
			}
		}
		list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2]));
		return list.toArray(new int[0][]);
	}

	/** Used by the build AI to clear a block. */
	public void request(final BlockPos pos) {
		this.request(pos, this.clone.buildGoal.isCreative(), this.clone.buildGoal::cantBreak);
	}

	/** Asks the clone to break one block. onFailed hears about it if the block can't be broken. */
	public void request(final BlockPos pos, final boolean creative, final BiConsumer<BlockPos, BlockState> onFailed) {
		this.requested = pos.immutable();
		this.requestCreative = creative;
		this.onRequestFailed = onFailed;
	}

	public boolean hasRequest() {
		return this.requested != null;
	}

	public boolean isRequested(final BlockPos pos) {
		return pos.equals(this.requested);
	}

	public void cancelRequest() {
		this.requested = null;
	}

	/** Picking up a new tool may make blocks the clone gave up on breakable again. */
	public void onPickedUp(final ItemStack stack) {
		this.unbreakables.removeIf(u -> !u.state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(u.state));
	}

	@Override
	public boolean canUse() {
		if (this.clone.isUsingItem() || this.clone.getTarget() != null) {
			return false;
		}
		if (this.requested != null) {
			BlockState state = this.clone.level().getBlockState(this.requested);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				this.requested = null;
				return false;
			}
			if (!this.requestCreative && !this.clone.selectBestToolFor(state, true)) {
				BlockPos failed = this.requested;
				this.requested = null;
				this.onRequestFailed.accept(failed, state);
				return false;
			}
			this.target = this.requested;
			this.targetState = state;
			return true;
		}
		if (!this.clone.hasOption(CloneOption.BREAK_BLOCKS) && !this.clone.hasOption(CloneOption.FARM)) {
			return false;
		}
		if (this.clone.tickCount < this.nextSearch) {
			return false;
		}
		this.nextSearch = this.clone.tickCount + 10;
		if (this.findBlock()) {
			return true;
		}
		this.nextSearch = this.clone.tickCount + 40;
		return false;
	}

	private boolean findBlock() {
		this.unbreakables.removeIf(u -> !this.clone.level().getBlockState(u.pos).equals(u.state));
		BlockPos origin = this.clone.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int tries = 0;
		for (int[] o : OFFSETS) {
			pos.set(origin.getX() + o[0], origin.getY() + o[1], origin.getZ() + o[2]);
			if (!this.clone.level().isInWorldBounds(pos)) {
				continue;
			}
			BlockState state = this.clone.level().getBlockState(pos);
			if (!this.wantsToBreak(state) || this.isUnbreakable(pos, state) || !this.canSee(pos)) {
				continue;
			}
			if (this.clone.selectBestToolFor(state, true)) {
				this.target = pos.immutable();
				this.targetState = state;
				return true;
			}
			this.unbreakables.add(new Unbreakable(pos.immutable(), state));
			if (this.unbreakables.size() > 30) {
				this.unbreakables.remove(0);
			}
			if (++tries >= 5) {
				return false;
			}
		}
		return false;
	}

	private boolean wantsToBreak(final BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(this.clone.level(), BlockPos.ZERO) < 0) {
			return false;
		}
		if (this.clone.hasOption(CloneOption.FARM) && isMatureCrop(state)) {
			return true;
		}
		return this.clone.hasOption(CloneOption.BREAK_BLOCKS) && this.clone.breakBlocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}

	public static boolean isMatureCrop(final BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (block instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= 3;
		}
		if (block instanceof CocoaBlock) {
			return state.getValue(CocoaBlock.AGE) >= 2;
		}
		return false;
	}

	private boolean isUnbreakable(final BlockPos pos, final BlockState state) {
		for (Unbreakable u : this.unbreakables) {
			if (u.pos.equals(pos) && u.state.equals(state)) {
				return true;
			}
		}
		return false;
	}

	private boolean canSee(final BlockPos pos) {
		Vec3 eye = this.clone.getEyePosition();
		Vec3 center = Vec3.atCenterOf(pos);
		BlockHitResult hit = this.clone.level().clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.clone));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	@Override
	public boolean canContinueToUse() {
		return this.target != null && this.targetState != null && this.clone.getTarget() == null;
	}

	@Override
	public void start() {
		this.hitting = false;
		this.progress = 0;
		this.noImprovement = 0;
		this.lastPos = this.clone.position();
		if (this.target != null) {
			this.clone.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, 1.0);
		}
	}

	@Override
	public void stop() {
		this.stopBreaking();
		this.target = null;
		this.targetState = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (this.target == null || this.targetState == null || !(this.clone.level() instanceof ServerLevel level)) {
			return;
		}
		BlockState now = level.getBlockState(this.target);
		if (!now.equals(this.targetState)) {
			this.finish(false);
			return;
		}
		this.clone.getLookControl().setLookAt(this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5, 10.0F, this.clone.getMaxHeadXRot());

		boolean close = this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(this.target)) <= 25.0;
		if (!close) {
			if (this.hitting) {
				this.stopBreaking();
			}
			if (this.clone.tickCount % 5 == 0) {
				this.clone.getNavigation().moveTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, 1.0);
				if (this.clone.position().distanceToSqr(this.lastPos) < 25.0) {
					this.noImprovement++;
				} else {
					this.noImprovement = 0;
					this.lastPos = this.clone.position();
				}
				if (this.noImprovement > 20) {
					this.giveUp();
				}
			}
			return;
		}

		this.clone.getNavigation().stop();
		if (!this.hitting) {
			this.hitting = true;
			this.progress = 0;
			this.soundCounter = 0;
		}
		if (this.hitDelay > 0) {
			this.hitDelay--;
			return;
		}

		ItemStack tool = this.clone.getMainHandItem();
		boolean creative = this.requested != null && this.requestCreative;
		float hardness = now.getDestroySpeed(level, this.target);
		if (hardness < 0) {
			this.giveUp();
			return;
		}
		boolean canHarvest = !now.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(now);
		float speed = tool.getDestroySpeed(now);
		if (!this.clone.onGround()) {
			speed /= 5.0F;
		}
		if (this.clone.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
			speed /= 5.0F;
		}
		this.progress += hardness == 0 || creative ? 1.0F : speed / hardness / (canHarvest ? 30.0F : 100.0F);

		if (this.soundCounter++ % 4 == 0) {
			this.clone.swingForAttack(InteractionHand.MAIN_HAND);
			var sound = now.getSoundType();
			level.playSound(null, this.target, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F);
		}
		if (this.progress >= 1.0F) {
			this.harvest(level, now, tool, canHarvest);
			this.finish(true);
			this.hitDelay = 5;
		} else {
			level.destroyBlockProgress(this.clone.getId(), this.target, (int)(this.progress * 10.0F) - 1);
		}
	}

	private void harvest(final ServerLevel level, final BlockState state, final ItemStack tool, final boolean canHarvest) {
		BlockPos pos = this.target;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		level.levelEvent(2001, pos, Block.getId(state));
		if (!level.removeBlock(pos, false)) {
			return;
		}
		state.getBlock().destroy(level, pos, state);
		if (canHarvest) {
			Block.dropResources(state, level, pos, blockEntity, this.clone, tool);
			// Keep the drops for this clone, so other clones nearby don't grab them first.
			com.clonecraft.clone.DropClaims.claimNew(level, new net.minecraft.world.phys.AABB(pos).inflate(1.0), this.clone);
		}
		if (tool.isDamageableItem() && state.getDestroySpeed(level, pos) != 0.0F) {
			tool.hurtAndBreak(1, this.clone, EquipmentSlot.MAINHAND);
		}
		this.clone.addExhaustion(0.005F);
		if ((this.clone.hasOption(CloneOption.FARM) || this.clone.hasOption(CloneOption.GAME_PROGRESSION)) && isMatureCrop(state)) {
			this.replant(level, pos, state);
		}
	}

	/** Puts a seed back in after harvesting a crop, if the clone has one. */
	private void replant(final ServerLevel level, final BlockPos pos, final BlockState harvested) {
		Item seed = harvested.getBlock().asItem();
		if (seed == net.minecraft.world.item.Items.AIR) {
			return;
		}
		if (this.clone.getInventory().consumeOne(s -> s.is(seed))) {
			BlockState planted = harvested.getBlock().defaultBlockState();
			if (harvested.getBlock() instanceof CocoaBlock) {
				planted = planted.setValue(CocoaBlock.FACING, harvested.getValue(CocoaBlock.FACING));
			}
			if (planted.canSurvive(level, pos)) {
				level.setBlock(pos, planted, Block.UPDATE_ALL);
				this.clone.swingForAttack(InteractionHand.MAIN_HAND);
			}
		}
	}

	private void giveUp() {
		if (this.target != null && this.targetState != null) {
			if (this.requested != null) {
				BlockPos failed = this.target;
				BlockState failedState = this.targetState;
				this.requested = null;
				this.onRequestFailed.accept(failed, failedState);
			} else {
				this.unbreakables.add(new Unbreakable(this.target, this.targetState));
			}
		}
		this.finish(false);
	}

	private void finish(final boolean broke) {
		if (this.requested != null && this.requested.equals(this.target)) {
			this.requested = null;
		}
		this.stopBreaking();
		this.target = null;
		this.targetState = null;
		if (broke) {
			this.clone.getInventory().select(0);
		}
	}

	private void stopBreaking() {
		if (this.target != null && this.hitting) {
			this.clone.level().destroyBlockProgress(this.clone.getId(), this.target, -1);
		}
		this.hitting = false;
		this.progress = 0;
	}
}
