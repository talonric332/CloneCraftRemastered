package com.clonecraft.block;

import com.clonecraft.CloneCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Life Inducer brings unactivated spawn eggs to life. Put the eggs inside and stack Antennas on top.
 * When lightning strikes the antenna (it needs to be the tallest thing around during a storm), the charge
 * flows down into the Life Inducer and powers up the eggs. A charged Life Inducer zaps anything nearby,
 * unless you wear leather or diamond boots, and explodes if you break it while it is charged!
 */
public class LifeInducerBlock extends BaseEntityBlock {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public LifeInducerBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new LifeInducerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
		return level instanceof ServerLevel serverLevel
			? createTickerHelper(type, CloneCraft.LIFE_INDUCER_BE, (l, pos, s, be) -> be.serverTick(serverLevel, pos, s))
			: null;
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LifeInducerBlockEntity be) {
			player.openMenu(be);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (state.getValue(POWERED)) {
			for (int i = 0; i < 4; i++) {
				double x = pos.getX() + (random.nextBoolean() ? 0.0 : 1.0);
				double y = pos.getY() + random.nextFloat();
				double z = pos.getZ() + random.nextFloat();
				if (random.nextBoolean()) {
					double t = x;
					x = pos.getX() + random.nextFloat();
					z = pos.getZ() + (t - pos.getX());
				}
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0);
			}
			if (random.nextInt(20) == 0) {
				level.playLocalSound(pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6F, 1.8F, false);
			}
		}
	}
}
