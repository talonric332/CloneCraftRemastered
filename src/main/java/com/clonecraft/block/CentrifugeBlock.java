package com.clonecraft.block;

import com.clonecraft.CloneCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Centrifuge spins test tubes of blood until the blood separates, which lets you draw out the DNA.
 * Put up to 8 tubes of blood around the edge and fuel (like coal) in the middle, then press Spin
 * (or give it a redstone pulse). A full spin takes 80 seconds.
 */
public class CentrifugeBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty SPINNING = BooleanProperty.create("spinning");
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	private static final VoxelShape SHAPE_NS = Block.box(1, 0, 3, 15, 9, 13);
	private static final VoxelShape SHAPE_EW = Block.box(3, 0, 1, 13, 9, 15);

	public CentrifugeBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SPINNING, false).setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SPINNING, POWERED);
	}

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new CentrifugeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
		return level instanceof ServerLevel serverLevel
			? createTickerHelper(type, CloneCraft.CENTRIFUGE_BE, (l, pos, s, be) -> be.serverTick(serverLevel, pos, s))
			: null;
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CentrifugeBlockEntity be) {
			player.openMenu(be);
		}
		return InteractionResult.SUCCESS;
	}

	/** A redstone pulse starts (or stops) the spin, like the original. */
	@Override
	protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos, final Block block, final @Nullable Orientation orientation, final boolean movedByPiston) {
		boolean powered = level.hasNeighborSignal(pos);
		if (powered != state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
			if (powered && level.getBlockEntity(pos) instanceof CentrifugeBlockEntity be) {
				be.toggleSpin();
			}
		}
	}

	@Override
	protected boolean hasAnalogOutputSignal(final BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(final BlockState state, final Level level, final BlockPos pos, final Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (state.getValue(SPINNING)) {
			double x = pos.getX() + 0.5 + (random.nextFloat() - 0.5) * 0.6;
			double z = pos.getZ() + 0.5 + (random.nextFloat() - 0.5) * 0.6;
			level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 0.65, z, 0.0, 0.2, 0.0);
			if (random.nextBoolean()) {
				level.addParticle(ParticleTypes.FLAME, x, pos.getY() + 0.65, z, 0.0, 0.09, 0.0);
			}
			if (random.nextInt(8) == 0) {
				level.playLocalSound(pos, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, 0.3F, 1.6F + random.nextFloat() * 0.3F, false);
			}
		}
	}
}
