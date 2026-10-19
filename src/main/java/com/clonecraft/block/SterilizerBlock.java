package com.clonecraft.block;

import com.clonecraft.CloneCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Sterilizer cleans dirty needles and test tubes. Fill it with a water bucket and light a fire underneath,
 * then throw dirty items in. After 32 items the water gets dirty: empty it with a bucket and refill it.
 */
public class SterilizerBlock extends BaseEntityBlock {
	/** 0 = empty, 1 = clean water, 2 = dirty water, 3 = boiling items. */
	public static final IntegerProperty WATER = IntegerProperty.create("water", 0, 3);
	private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Block.box(2, 4, 2, 14, 16, 14), net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);

	public SterilizerBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(WATER, 0));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WATER);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new SterilizerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
		return level instanceof ServerLevel serverLevel
			? createTickerHelper(type, CloneCraft.STERILIZER_BE, (l, pos, s, be) -> be.serverTick(serverLevel, pos, s))
			: null;
	}

	@Override
	protected InteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player,
		final InteractionHand hand, final BlockHitResult hit) {
		int water = state.getValue(WATER);
		if (stack.is(Items.WATER_BUCKET) && water == 0) {
			if (!level.isClientSide()) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
				level.setBlock(pos, state.setValue(WATER, 1), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.BUCKET) && water > 0) {
			if (!level.isClientSide()) {
				if (level.getBlockEntity(pos) instanceof SterilizerBlockEntity be) {
					be.dropItems(false);
					be.dirtiness = 0;
				}
				level.setBlock(pos, state.setValue(WATER, 0), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
				// 3 is clean water with items boiling in it, so it's still clean water.
				if (water == 1 || water == 3) {
					player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected void affectNeighborsAfterRemoval(final BlockState state, final ServerLevel level, final BlockPos pos, final boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		int water = state.getValue(WATER);
		if ((water == 1 || water == 3) && SterilizerBlockEntity.hasFire(level, pos)) {
			int count = water == 3 ? 3 : 1;
			for (int i = 0; i < count; i++) {
				level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.2 + random.nextFloat() * 0.6, pos.getY() + 0.95, pos.getZ() + 0.2 + random.nextFloat() * 0.6, 0, 0.05, 0);
			}
			if (random.nextInt(water == 3 ? 6 : 40) == 0) {
				level.playLocalSound(pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.4F, 1.0F + random.nextFloat() / 20.0F, false);
			}
		}
	}
}
