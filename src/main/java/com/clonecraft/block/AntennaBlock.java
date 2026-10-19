package com.clonecraft.block;

import com.clonecraft.CloneCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Antennas catch lightning. Stack them on top of a Life Inducer. During rain or a thunderstorm, if the top of the
 * antenna is the tallest thing within 15 blocks, lightning will strike it now and then, and the charge runs down
 * into the Life Inducer. In creative mode you can right-click an antenna to charge it yourself.
 */
public class AntennaBlock extends Block {
	public static final BooleanProperty CHARGED = BooleanProperty.create("charged");
	private static final VoxelShape SHAPE = Block.box(6.4, 0, 6.4, 9.6, 16, 9.6);

	public AntennaBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CHARGED, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CHARGED);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean isRandomlyTicking(final BlockState state) {
		return !state.getValue(CHARGED);
	}

	@Override
	protected void randomTick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
		if (!level.isRaining() || random.nextInt(level.isThundering() ? 16 : 28) != 0) {
			return;
		}
		BlockPos top = pos;
		while (level.getBlockState(top.above()).is(this)) {
			top = top.above();
		}
		if (!level.isRainingAt(top.above())) {
			return;
		}
		// The antenna has to be the tallest thing around.
		int height = top.getY();
		for (int dx = -15; dx <= 15; dx++) {
			for (int dz = -15; dz <= 15; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, top.getX() + dx, top.getZ() + dz) - 1;
				if (surface >= height) {
					return;
				}
			}
		}
		LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(Vec3.atBottomCenterOf(top.above()));
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, top, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 5.0F, 1.0F);
		charge(level, top);
	}

	public static void charge(final Level level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof AntennaBlock && !state.getValue(CHARGED)) {
			level.setBlock(pos, state.setValue(CHARGED, true), Block.UPDATE_ALL);
			level.scheduleTick(pos, state.getBlock(), 2);
		}
	}

	/** The charge moves one antenna down every couple of ticks until it reaches the Life Inducer. */
	@Override
	protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
		if (!state.getValue(CHARGED)) {
			return;
		}
		level.setBlock(pos, state.setValue(CHARGED, false), Block.UPDATE_ALL);
		BlockPos below = pos.below();
		BlockState belowState = level.getBlockState(below);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.1, 0.4, 0.1, 0.05);
		if (belowState.is(this)) {
			charge(level, below);
		} else if (belowState.is(CloneCraft.LIFE_INDUCER) && level.getBlockEntity(below) instanceof LifeInducerBlockEntity be) {
			be.charge();
			level.playSound(null, below, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.5F);
		} else {
			// Nowhere for the charge to go: it blasts everything nearby.
			Vec3 middle = Vec3.atCenterOf(pos);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(10, 8, 10))) {
				double distSq = Math.max(1.0, e.position().distanceToSqr(middle));
				Vec3 push = e.position().subtract(middle).normalize().scale(2.0 / Math.sqrt(distSq));
				e.push(push.x, push.y, push.z);
				e.needsSync = true;
				e.hurtServer(level, level.damageSources().lightningBolt(), (float)(4.0 / Math.sqrt(distSq)));
			}
		}
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (player.getAbilities().instabuild) {
			if (!level.isClientSide()) {
				charge(level, pos);
				level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 1.0F, 1.5F);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Touching a charged antenna hurts. */
	@Override
	protected void entityInside(final BlockState state, final Level level, final BlockPos pos, final Entity entity, final InsideBlockEffectApplier effectApplier, final boolean isPrecise) {
		if (state.getValue(CHARGED) && level instanceof ServerLevel serverLevel && entity instanceof LivingEntity living) {
			living.hurtServer(serverLevel, level.damageSources().lightningBolt(), 4.0F);
			double dx = entity.getX() - (pos.getX() + 0.5);
			double dz = entity.getZ() - (pos.getZ() + 0.5);
			entity.push(dx * 3, 0.65, dz * 3);
			entity.needsSync = true;
		}
	}
}
