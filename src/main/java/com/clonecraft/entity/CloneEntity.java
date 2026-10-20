package com.clonecraft.entity;

import com.clonecraft.CloneCraft;
import com.clonecraft.clone.CloneFoodData;
import com.clonecraft.clone.CloneInventory;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.CloneTeam;
import com.clonecraft.clone.NameRegistry;
import com.clonecraft.clone.Songs;
import com.clonecraft.clone.ai.AttackEnemiesGoal;
import com.clonecraft.clone.ai.BuildGoal;
import com.clonecraft.clone.ai.CommandGoal;
import com.clonecraft.clone.ai.FetchItemGoal;
import com.clonecraft.clone.ai.FollowCloneOwnerGoal;
import com.clonecraft.clone.ai.LookIdleGoal;
import com.clonecraft.clone.ai.MineGoal;
import com.clonecraft.clone.ai.ProgressionGoal;
import com.clonecraft.clone.ai.ReturnGuardGoal;
import com.clonecraft.clone.ai.ShareGoal;
import com.clonecraft.clone.ai.WalkToItemsGoal;
import com.clonecraft.clone.ai.WanderGoal;
import com.clonecraft.dna.DnaItems;
import com.clonecraft.menu.CloneMenu;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A human clone, rebuilt from the original CloneCraft: a player-like inventory, hunger, experience, growing up,
 * teams, 18 options, mining, farming, building, guarding, sharing and chat commands.
 */
public class CloneEntity extends PathfinderMob {
	private static final EntityDataAccessor<ResolvableProfile> DATA_PROFILE = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.RESOLVABLE_PROFILE);
	private static final EntityDataAccessor<String> DATA_NAME = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_TEAM = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_OPTIONS = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FOOD = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_XP_LEVEL = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_XP_PROGRESS = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<String> DATA_OWNER_NAME = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> DATA_MESSAGE = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_SHRINK_COOLDOWN = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BUILD_INDEX = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BUILD_TOTAL = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BUILD_STAGE = SynchedEntityData.defineId(CloneEntity.class, EntityDataSerializers.INT);

	private static final Pattern VALID_PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Identifier SPRINT_SPEED = CloneCraft.id("sprint");
	private static final Identifier CRIT_DAMAGE = CloneCraft.id("jump_crit");

	public static final float DEFAULT_SCALE = 1.0F;
	public static final float MAX_SCALE = 1.25F;
	private static final float GROWTH_PER_TICK = 0.000020833F; // a baby grows up in about 20 minutes

	private final CloneInventory inventory = new CloneInventory(this);
	private final CloneFoodData foodData = new CloneFoodData();
	public final Set<Identifier> attackTypes = new HashSet<>();
	public final Set<Identifier> breakBlocks = new HashSet<>();
	public final List<BlockPos> knownContainers = new ArrayList<>();

	private @Nullable UUID ownerId;
	private @Nullable BlockPos guardPos;
	private int totalXp;
	private int xpLevel;
	private float xpProgress;

	private float scale = 0.5F;
	private double aimScale = 0.5;
	private float growth;
	private int shrinkCooldown;
	private float appliedScale = -1.0F;

	private int messageTicks;
	private int attackTimer;
	private int songIndex;
	private final int songId;

	public MineGoal mineGoal;
	public BuildGoal buildGoal;
	public FetchItemGoal fetchGoal;
	public CommandGoal commandGoal;
	public ShareGoal shareGoal;
	public ProgressionGoal progressionGoal;

	public CloneEntity(final EntityType<? extends CloneEntity> type, final Level level) {
		super(type, level);
		this.setCanPickUpLoot(false);
		this.getNavigation().setCanOpenDoors(true);
		// Players walk and swim through water happily, so clones shouldn't go far out of their way to avoid it.
		this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 2.0F);
		this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER, 0.0F);
		this.songId = this.random.nextInt(Songs.count());
		this.setPersistenceRequired();
		if (!level.isClientSide()) {
			this.setOption(CloneOption.FEMALE, this.random.nextBoolean());
			this.setCloneName(this.hasOption(CloneOption.FEMALE) ? NameRegistry.girlName(this.random) : NameRegistry.boyName(this.random));
		}
		this.applyScale(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.ATTACK_DAMAGE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 56.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_PROFILE, ResolvableProfile.Static.EMPTY);
		entityData.define(DATA_NAME, "Steve");
		entityData.define(DATA_TEAM, CloneTeam.GOOD.ordinal());
		entityData.define(DATA_OPTIONS, CloneOption.defaults());
		entityData.define(DATA_FOOD, 20);
		entityData.define(DATA_XP_LEVEL, 0);
		entityData.define(DATA_XP_PROGRESS, 0.0F);
		entityData.define(DATA_OWNER_NAME, "");
		entityData.define(DATA_MESSAGE, "");
		entityData.define(DATA_SHRINK_COOLDOWN, 0);
		entityData.define(DATA_BUILD_INDEX, 0);
		entityData.define(DATA_BUILD_TOTAL, 0);
		entityData.define(DATA_BUILD_STAGE, -1);
	}

	@Override
	protected void registerGoals() {
		this.mineGoal = new MineGoal(this);
		this.buildGoal = new BuildGoal(this);
		this.fetchGoal = new FetchItemGoal(this);
		this.commandGoal = new CommandGoal(this);
		this.shareGoal = new ShareGoal(this);
		this.progressionGoal = new ProgressionGoal(this);

		this.goalSelector.addGoal(0, new FloatGoal(this));
		// Above fighting: a badly hurt clone on Game Progression runs instead of fighting to the death.
		this.goalSelector.addGoal(0, new com.clonecraft.clone.ai.FleeGoal(this));
		this.goalSelector.addGoal(1, new AttackEnemiesGoal(this));
		this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
		this.goalSelector.addGoal(3, new FollowCloneOwnerGoal(this));
		this.goalSelector.addGoal(4, this.commandGoal);
		this.goalSelector.addGoal(5, this.fetchGoal);
		this.goalSelector.addGoal(6, this.mineGoal);
		this.goalSelector.addGoal(7, this.buildGoal);
		// Below fighting, following, commands and mining (which it asks to dig for it); it doesn't run while building.
		this.goalSelector.addGoal(7, this.progressionGoal);
		this.goalSelector.addGoal(8, new WalkToItemsGoal(this));
		this.goalSelector.addGoal(9, new ReturnGuardGoal(this));
		this.goalSelector.addGoal(18, this.shareGoal);
		this.goalSelector.addGoal(19, new LookIdleGoal(this));
		this.goalSelector.addGoal(20, new WanderGoal(this));
	}

	// ================================================================ basic data

	public CloneInventory getInventory() {
		return this.inventory;
	}

	public CloneFoodData getFoodData() {
		return this.foodData;
	}

	public int getFoodLevel() {
		return this.entityData.get(DATA_FOOD);
	}

	public int getXpLevel() {
		return this.entityData.get(DATA_XP_LEVEL);
	}

	public float getXpProgress() {
		return this.entityData.get(DATA_XP_PROGRESS);
	}

	public int getShrinkCooldown() {
		return this.entityData.get(DATA_SHRINK_COOLDOWN);
	}

	public int getBuildIndex() {
		return this.entityData.get(DATA_BUILD_INDEX);
	}

	public int getBuildTotal() {
		return this.entityData.get(DATA_BUILD_TOTAL);
	}

	/** -1 when not building, otherwise the build stage (0-3). */
	public int getBuildStage() {
		return this.entityData.get(DATA_BUILD_STAGE);
	}

	public void setBuildProgress(final int index, final int total, final int stage) {
		this.entityData.set(DATA_BUILD_INDEX, index);
		this.entityData.set(DATA_BUILD_TOTAL, total);
		this.entityData.set(DATA_BUILD_STAGE, stage);
	}

	// ---------------------------------------------------------------- options

	public int getOptions() {
		return this.entityData.get(DATA_OPTIONS);
	}

	public void setOptions(final int options) {
		int old = this.getOptions();
		this.entityData.set(DATA_OPTIONS, options);
		if ((old & CloneOption.GUARD.bit()) != 0 && (options & CloneOption.GUARD.bit()) == 0) {
			this.guardPos = null;
		}
		if ((old & CloneOption.FEMALE.bit()) != (options & CloneOption.FEMALE.bit())) {
			this.refreshName();
		}
	}

	public boolean hasOption(final CloneOption option) {
		return (this.getOptions() & option.bit()) != 0;
	}

	public void setOption(final CloneOption option, final boolean value) {
		this.setOptions(value ? this.getOptions() | option.bit() : this.getOptions() & ~option.bit());
	}

	// ---------------------------------------------------------------- name & skin

	public String getCloneName() {
		return this.entityData.get(DATA_NAME);
	}

	/** Sets the clone's name, which is also the username of the skin it wears. */
	public void setCloneName(final String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.isEmpty()) {
			name = "Steve";
		}
		if (name.length() > 32) {
			name = name.substring(0, 32);
		}
		this.entityData.set(DATA_NAME, name);
		this.entityData.set(DATA_PROFILE, VALID_PLAYER_NAME.matcher(name).matches() ? ResolvableProfile.createUnresolved(name) : ResolvableProfile.Static.EMPTY);
		this.refreshName();
	}

	public void setCloneOf(final GameProfile profile) {
		this.entityData.set(DATA_NAME, profile.name());
		this.entityData.set(DATA_PROFILE, ResolvableProfile.createResolved(profile));
		this.refreshName();
	}

	public ResolvableProfile getProfile() {
		return this.entityData.get(DATA_PROFILE);
	}

	private void refreshName() {
		super.setCustomName(Component.literal(this.getCloneName()).withColor(this.getCloneTeam().color));
		this.setCustomNameVisible(true);
	}

	/** Name tags rename the clone (and so change its skin). */
	@Override
	public void setCustomName(final @Nullable Component name) {
		if (name != null && !name.getString().equals(this.getCloneName()) && !this.level().isClientSide()) {
			this.setCloneName(name.getString());
		} else {
			super.setCustomName(name);
		}
	}

	// ---------------------------------------------------------------- team

	public CloneTeam getCloneTeam() {
		return CloneTeam.byId(this.entityData.get(DATA_TEAM));
	}

	public void setCloneTeam(final CloneTeam team) {
		if (team != this.getCloneTeam()) {
			this.setTarget(null);
		}
		this.entityData.set(DATA_TEAM, team.ordinal());
		this.refreshName();
	}

	/** Evil and Rampant clones do their own thing. */
	public boolean isIndependent() {
		return this.getCloneTeam() == CloneTeam.EVIL || this.getCloneTeam() == CloneTeam.RAMPANT;
	}

	// ---------------------------------------------------------------- owner

	public void setOwner(final @Nullable Player player) {
		this.ownerId = player == null ? null : player.getUUID();
		this.entityData.set(DATA_OWNER_NAME, player == null ? "" : player.getGameProfile().name());
	}

	public boolean hasOwner() {
		return this.ownerId != null;
	}

	public @Nullable UUID getOwnerId() {
		return this.ownerId;
	}

	public String getOwnerName() {
		return this.entityData.get(DATA_OWNER_NAME);
	}

	public @Nullable Player getOwnerPlayer() {
		return this.ownerId == null ? null : this.level().getPlayerByUUID(this.ownerId);
	}

	public boolean isOwnedBy(final Entity entity) {
		return this.ownerId != null && this.ownerId.equals(entity.getUUID());
	}

	/** The owner can manage the clone from any distance (in the same dimension) while carrying a Clone Remote. Server side only. */
	public boolean hasRemoteAccess(final Player player) {
		return !this.level().isClientSide() && this.isOwnedBy(player) && player.level() == this.level()
			&& com.clonecraft.network.RemoteControl.hasRemote(player);
	}

	/** Only the owner (or anyone in creative mode) may control a clone. */
	public boolean canUse(final Player player) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		if (this.level().isClientSide()) {
			String owner = this.getOwnerName();
			return owner.isEmpty() || owner.equals(player.getGameProfile().name());
		}
		return this.ownerId == null || this.ownerId.equals(player.getUUID());
	}

	// ---------------------------------------------------------------- guard position

	public @Nullable BlockPos getGuardPos() {
		return this.guardPos;
	}

	public void setGuardPos(final @Nullable BlockPos pos) {
		this.guardPos = pos;
	}

	// ================================================================ ticking

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			this.inventory.syncHeld();
			if (this.isAlive()) {
				this.pickupNearby(level);
			}
			this.foodData.tick(this, level);
			this.entityData.set(DATA_FOOD, this.foodData.getFoodLevel());
			this.checkHunger();
			this.updateBow(level);
			this.updateSprint();
			this.updateScale();
			this.swim();
			if (this.hasOption(CloneOption.GAME_PROGRESSION)) {
				this.progressionGoal.furnace.tick(this, this.foodData.getFoodLevel() <= 14);
			}
			// Asleep in a bed: stay put, and get up in the morning (or if Game Progression is turned off).
			if (this.isSleeping()) {
				this.getNavigation().stop();
				if (!ProgressionGoal.isNight(level) || !this.hasOption(CloneOption.GAME_PROGRESSION)) {
					this.stopSleeping();
				}
			}
			// Only pick a guard spot once standing on solid ground (not mid-fall or swimming).
			if (this.hasOption(CloneOption.GUARD) && this.guardPos == null && this.onGround() && !this.isInWater()) {
				this.guardPos = this.blockPosition().below();
			}
			if (this.hasOption(CloneOption.SMART_MEMORY) && this.tickCount % 40 == 0) {
				this.scanForContainers(level);
			}
			if (this.isIndependent()) {
				this.setOption(CloneOption.SPRINT, this.getTarget() != null);
			}
			if (this.messageTicks > 0 && --this.messageTicks == 0) {
				this.entityData.set(DATA_MESSAGE, "");
			}
			if (this.attackTimer > 0) {
				this.attackTimer--;
			}
			if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL && this.tickCount % 240 == 0 && this.getHealth() < this.getMaxHealth()) {
				this.heal(1.0F);
			}
			// Crouch under low ceilings, like the original.
			this.setShiftKeyDown(!level.getBlockState(BlockPos.containing(this.getX(), this.getY() + this.getBbHeight() + 0.1, this.getZ())).isAir()
				&& level.getBlockState(BlockPos.containing(this.getX(), this.getY() + this.getBbHeight() + 0.1, this.getZ())).isCollisionShapeFullBlock(level, BlockPos.containing(this.getX(), this.getY() + this.getBbHeight() + 0.1, this.getZ())));
		}
	}

	private Vec3 lastPos = Vec3.ZERO;

	/**
	 * Mobs swim very slowly and struggle to get out of water. In water, push the clone along its path like a
	 * swimming player (faster when sprinting), help it up onto ledges, and come up for air when running out.
	 */
	private void swim() {
		if (!this.isInWater() || this.isPassenger()) {
			return;
		}
		Vec3 push = Vec3.ZERO;
		var path = this.getNavigation().getPath();
		if (path != null && !path.isDone()) {
			Vec3 to = path.getNextEntityPos(this).subtract(this.position());
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			if (flat > 0.1) {
				double speed = this.isSprinting() ? 0.045 : 0.03;
				push = push.add(to.x / flat * speed, 0.0, to.z / flat * speed);
			}
			if (to.y > 0.3 || this.horizontalCollision) {
				push = push.add(0.0, 0.04, 0.0);
			}
			if (to.y < -0.5 && this.getAirSupply() > this.getMaxAirSupply() / 2) {
				push = push.add(0.0, -0.03, 0.0);
			}
		}
		if (this.isUnderWater() && this.getAirSupply() < this.getMaxAirSupply() / 3) {
			push = push.add(0.0, 0.05, 0.0);
		}
		if (push != Vec3.ZERO) {
			this.setDeltaMovement(this.getDeltaMovement().add(push));
		}
	}

	private void updateSprint() {
		boolean moving = !this.getNavigation().isDone();
		boolean sprint = this.hasOption(CloneOption.SPRINT) && moving;
		this.setSprinting(sprint);
		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			if (sprint && !speed.hasModifier(SPRINT_SPEED)) {
				speed.addTransientModifier(new AttributeModifier(SPRINT_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			} else if (!sprint && speed.hasModifier(SPRINT_SPEED)) {
				speed.removeModifier(SPRINT_SPEED);
			}
		}
		Vec3 now = this.position();
		double moved = Math.sqrt(now.distanceToSqr(this.lastPos));
		if (moved > 0.0 && moved < 2.0 && this.onGround()) {
			this.foodData.addExhaustion((float)(moved * (sprint ? 0.1 : 0.01)));
		}
		this.lastPos = now;
	}

	/**
	 * Like a player, a clone on Game Progression will jump down a few blocks (say, off a giant mushroom or out of a
	 * tree) rather than stay stranded, as long as the fall costs at most about a third of its health.
	 */
	@Override
	public int getMaxFallDistance() {
		if (this.hasOption(CloneOption.GAME_PROGRESSION) && this.getTarget() == null) {
			return 3 + Math.max(0, (int)(this.getHealth() / 3.0F));
		}
		return super.getMaxFallDistance();
	}

	@Override
	public void jumpFromGround() {
		super.jumpFromGround();
		this.foodData.addExhaustion(this.isSprinting() ? 0.2F : 0.05F);
	}

	// ================================================================ growing up (scale)

	/** Called when hit with a Stem Cell needle. Makes a baby grow faster, or a grown clone grow into a giant for a while. */
	public void injectStemCells(final boolean increaseHeight) {
		if (this.aimScale < MAX_SCALE) {
			if (this.aimScale >= DEFAULT_SCALE) {
				if (this.aimScale == DEFAULT_SCALE || increaseHeight) {
					this.aimScale += 0.05;
					this.shrinkCooldown += 300;
				} else {
					this.shrinkCooldown += 1200;
				}
			} else {
				this.aimScale += 0.1;
			}
		} else {
			this.shrinkCooldown += 1200;
		}
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEE_STING, SoundSource.NEUTRAL, 1.0F, 1.6F);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + this.getBbHeight() / 2, this.getZ(), 12, 0.3, 0.5, 0.3, 0.0);
		}
	}

	/** A baby clone hit by a thrown Glistering Melon Slice eats one and grows a step, like a Stem Cell needle. */
	private void eatThrownMelons(final ServerLevel level) {
		if (this.aimScale >= DEFAULT_SCALE) {
			return;
		}
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(0.5))) {
			ItemStack stack = itemEntity.getItem();
			// Only thrown slices count (they have a thrower), so melons lying around don't get eaten.
			if (!itemEntity.isAlive() || !stack.is(Items.GLISTERING_MELON_SLICE) || itemEntity.getOwner() == null) {
				continue;
			}
			stack.shrink(1);
			if (stack.isEmpty()) {
				itemEntity.discard();
			} else {
				itemEntity.setItem(stack);
			}
			this.aimScale = Math.min(DEFAULT_SCALE, this.aimScale + 0.1);
			this.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.2F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + this.getBbHeight() / 2, this.getZ(), 12, 0.3, 0.5, 0.3, 0.0);
			return;
		}
	}

	/** Spawns the clone fully grown (used by commands and copies). */
	public void setGrownUp() {
		this.scale = DEFAULT_SCALE;
		this.aimScale = DEFAULT_SCALE;
		this.applyScale(true);
	}

	public float getCloneScale() {
		return this.scale;
	}

	private void updateScale() {
		if (this.shrinkCooldown > 0) {
			this.shrinkCooldown--;
			if (this.aimScale < DEFAULT_SCALE) {
				this.shrinkCooldown = 0;
			}
		}
		if (this.aimScale > MAX_SCALE) {
			this.aimScale = MAX_SCALE;
		}
		if (this.scale < DEFAULT_SCALE) {
			this.aimScale += GROWTH_PER_TICK;
		}
		if (this.aimScale > DEFAULT_SCALE && this.shrinkCooldown == 0) {
			this.aimScale += (DEFAULT_SCALE - this.aimScale) / 2000.0 - 0.000005;
			if (this.aimScale <= DEFAULT_SCALE) {
				this.aimScale = DEFAULT_SCALE;
			}
		}
		double diff = this.aimScale - this.scale;
		if (Math.abs(diff) < 0.0004) {
			this.scale = (float)this.aimScale;
			this.growth = 0;
		} else {
			this.growth = (float)(diff / 50.0 + Math.signum(diff) * 0.00005);
		}
		this.scale = Math.min(MAX_SCALE, this.scale + this.growth);
		this.applyScale(false);
		this.entityData.set(DATA_SHRINK_COOLDOWN, this.shrinkCooldown);
	}

	private void applyScale(final boolean force) {
		if (!force && Math.abs(this.appliedScale - this.scale) < 0.005F) {
			return;
		}
		AttributeInstance scaleAttr = this.getAttribute(Attributes.SCALE);
		if (scaleAttr == null) {
			return;
		}
		this.appliedScale = this.scale;
		scaleAttr.setBaseValue(this.scale);
		double oldMax = this.getMaxHealth();
		double newMax = Math.max(1, Math.round(20.0 * this.scale));
		if (oldMax != newMax) {
			this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMax);
			this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3 + 0.1 * (this.scale - 1.0));
			if (newMax > oldMax) {
				this.heal((float)(newMax - oldMax));
			}
			if (this.getHealth() > newMax) {
				this.setHealth((float)newMax);
			}
		}
	}

	// ================================================================ items, eating & experience

	private void pickupNearby(final ServerLevel level) {
		this.eatThrownMelons(level);
		// Game Progression needs the things it mines and hunts, so it always picks up.
		if (this.hasOption(CloneOption.PICKUP) || this.hasOption(CloneOption.GAME_PROGRESSION)) {
			for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(1.0, 0.5, 1.0))) {
				// Don't pick straight back up what this clone threw away.
				// Nor what another clone just mined or killed: that's theirs for a while.
				if (!itemEntity.isAlive() || itemEntity.hasPickUpDelay() || itemEntity.getOwner() == this
					|| !com.clonecraft.clone.DropClaims.mayTake(itemEntity, this)) {
					continue;
				}
				ItemStack stack = itemEntity.getItem();
				int before = stack.getCount();
				ItemStack copy = stack.copy();
				this.pickupItem(stack);
				int taken = before - stack.getCount();
				if (taken > 0) {
					this.take(itemEntity, taken);
					this.mineGoal.onPickedUp(copy);
					this.playSound(SoundEvents.ITEM_PICKUP, 0.2F, ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
					if (copy.is(Items.MILK_BUCKET) && (this.shrinkCooldown > 0 || this.aimScale > DEFAULT_SCALE)) {
						this.drinkMilk();
					}
				}
				if (stack.isEmpty()) {
					itemEntity.discard();
				}
			}
		}
		for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, this.getBoundingBox().inflate(1.5))) {
			if (orb.isAlive()) {
				this.addXp(orb.getValue());
				orb.discard();
				this.playNextNote();
			}
		}
	}

	/** Takes as much of a stack as possible, wearing armour if it is better. */
	public void pickupItem(final ItemStack stack) {
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.slot().isArmor() && equippable.slot() != EquipmentSlot.BODY) {
			EquipmentSlot slot = equippable.slot();
			ItemStack worn = this.getItemBySlot(slot);
			if (worn.isEmpty()) {
				this.setItemSlot(slot, stack.split(1));
				return;
			}
			if (armorValue(stack, slot) > armorValue(worn, slot) && this.inventory.fitCount(worn) > 0) {
				this.inventory.add(worn.copy());
				this.setItemSlot(slot, stack.split(1));
				return;
			}
		}
		this.inventory.add(stack);
	}

	public static double armorValue(final ItemStack stack, final EquipmentSlot slot) {
		double[] total = {0};
		stack.forEachModifier(slot, (attribute, modifier) -> {
			if (attribute.is(Attributes.ARMOR) || attribute.is(Attributes.ARMOR_TOUGHNESS)) {
				total[0] += modifier.amount();
			}
		});
		return total[0] + stack.getEnchantments().size() * 0.5;
	}

	private void drinkMilk() {
		int slot = this.inventory.find(s -> s.is(Items.MILK_BUCKET));
		if (slot >= 0) {
			// setItem also updates the hand when the milk is being held.
			this.inventory.setItem(slot, new ItemStack(Items.BUCKET));
			this.aimScale = DEFAULT_SCALE;
			this.shrinkCooldown = 0;
			this.playSound(SoundEvents.GENERIC_DRINK.value(), 1.0F, 1.0F);
		}
	}

	private void checkHunger() {
		if (this.isUsingItem() || this.foodData.getFoodLevel() >= 20) {
			return;
		}
		if (this.getTarget() != null && this.foodData.getFoodLevel() >= 5) {
			return;
		}
		int slot = this.inventory.find(s -> s.has(DataComponents.FOOD) && s.getUseAnimation() == ItemUseAnimation.EAT);
		if (slot >= 0) {
			this.inventory.selectFromInventory(slot);
			this.startUsingItem(InteractionHand.MAIN_HAND);
		}
	}

	@Override
	protected void completeUsingItem() {
		ItemStack using = this.getUseItem();
		FoodProperties food = using.get(DataComponents.FOOD);
		if (food != null && !this.level().isClientSide()) {
			this.foodData.eat(food.nutrition(), food.saturation() / Math.max(1, food.nutrition()) / 2.0F);
		}
		super.completeUsingItem();
		this.inventory.syncHeld();
	}

	public void addXp(final int amount) {
		this.totalXp += amount;
		this.xpProgress += (float)amount / xpNeeded(this.xpLevel);
		while (this.xpProgress >= 1.0F) {
			this.xpProgress = (this.xpProgress - 1.0F) * xpNeeded(this.xpLevel);
			this.xpLevel++;
			this.xpProgress /= xpNeeded(this.xpLevel);
		}
		this.entityData.set(DATA_XP_LEVEL, this.xpLevel);
		this.entityData.set(DATA_XP_PROGRESS, this.xpProgress);
	}

	private static int xpNeeded(final int level) {
		return level >= 30 ? 112 + (level - 30) * 9 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	/** Gives all of the clone's experience to the player. */
	public void transferXp(final Player player) {
		if (this.totalXp > 0) {
			player.giveExperiencePoints(this.totalXp);
			this.totalXp = 0;
			this.xpLevel = 0;
			this.xpProgress = 0;
			this.entityData.set(DATA_XP_LEVEL, 0);
			this.entityData.set(DATA_XP_PROGRESS, 0.0F);
			this.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.0F);
		}
	}

	/** Picking up experience plays the next note of the clone's song, like the original. */
	private void playNextNote() {
		float pitch = Songs.pitch(this.songId, this.songIndex++);
		this.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.1F, pitch);
	}

	// ================================================================ fighting

	/** Should the clone go out of its way to attack this? (teams, chosen mobs) */
	public boolean shouldProvokeAttack(final @Nullable LivingEntity entity) {
		if (entity == null || entity == this || !entity.isAlive()) {
			return false;
		}
		if (entity instanceof CloneEntity other) {
			return this.getCloneTeam().attacksTeam(other.getCloneTeam()) && other.hasOption(CloneOption.FIGHT);
		}
		if (entity instanceof Player player) {
			return this.isIndependent() && !player.getAbilities().instabuild && !player.isSpectator();
		}
		Identifier typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (this.getCloneTeam() == CloneTeam.RAMPANT && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
			return true;
		}
		return this.attackTypes.contains(typeId);
	}

	/** Is the clone allowed to attack this at all? (used when it is attacked first) */
	public boolean canAttackEntity(final @Nullable LivingEntity entity) {
		if (entity == null || !entity.isAlive() || entity == this) {
			return false;
		}
		if (entity instanceof CloneEntity other) {
			return this.getCloneTeam().attacksTeam(other.getCloneTeam());
		}
		if (entity instanceof Player player) {
			return this.getCloneTeam() != CloneTeam.GOOD && !player.getAbilities().instabuild && !player.isSpectator();
		}
		return true;
	}

	@Override
	public boolean canAttack(final LivingEntity target) {
		if (this.isOwnedBy(target) && !this.isIndependent()) {
			return false;
		}
		return this.canAttackEntity(target) && super.canAttack(target);
	}

	/** Called every tick while chasing a target: picks the best weapon and swings or shoots. */
	public void attackWithBestWeapon(final LivingEntity target) {
		if (this.foodData.getFoodLevel() < 5 && this.isUsingItem()) {
			return;
		}
		if (!this.hasLineOfSight(target)) {
			this.selectBestWeapon();
			return;
		}
		double distSq = this.distanceToSqr(target);
		if (this.isWithinMeleeAttackRange(target)) {
			if (this.isUsingItem()) {
				this.stopUsingItem();
			}
			this.selectBestWeapon();
			if (this.hasOption(CloneOption.JUMP) && this.onGround() && this.random.nextInt(3) == 0) {
				this.jumpFromGround();
			}
			if (this.attackTimer == 0 && this.level() instanceof ServerLevel level) {
				this.swingForAttack(InteractionHand.MAIN_HAND);
				this.doHurtTarget(level, target);
				this.attackTimer = this.random.nextInt(10) + 8;
			}
		} else if (distSq < 400.0 && this.selectBow()) {
			if (!this.isUsingItem()) {
				this.startUsingItem(InteractionHand.MAIN_HAND);
			}
		}
	}

	@Override
	public boolean doHurtTarget(final ServerLevel level, final Entity target) {
		boolean crit = this.fallDistance > 0.0F && !this.onGround() && !this.onClimbable() && !this.isInWater();
		AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (crit && damage != null) {
			damage.addTransientModifier(new AttributeModifier(CRIT_DAMAGE, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		boolean hit = super.doHurtTarget(level, target);
		if (damage != null) {
			damage.removeModifier(CRIT_DAMAGE);
		}
		if (hit) {
			this.foodData.addExhaustion(0.3F);
			if (crit) {
				level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
			}
		}
		return hit;
	}

	public void selectBestWeapon() {
		int best = -1;
		double bestScore = 0;
		for (int i = 0; i < CloneInventory.MAIN_SIZE; i++) {
			ItemStack s = this.inventory.main.get(i);
			if (s.isEmpty()) {
				continue;
			}
			double score = weaponScore(s);
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		if (best >= 0 && best != this.inventory.selected) {
			this.inventory.selectFromInventory(best);
		}
	}

	public static double weaponScore(final ItemStack stack) {
		double[] dmg = {0};
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
				dmg[0] += modifier.amount();
			}
		});
		return dmg[0] <= 0 ? 0 : dmg[0] + stack.getEnchantments().size() * 1.5;
	}

	private boolean selectBow() {
		if (this.inventory.find(s -> s.is(net.minecraft.tags.ItemTags.ARROWS)) < 0) {
			return false;
		}
		for (int i = 0; i < 9; i++) {
			if (this.inventory.main.get(i).is(Items.BOW)) {
				if (this.inventory.selected != i) {
					this.inventory.select(i);
				}
				return true;
			}
		}
		return false;
	}

	private void updateBow(final ServerLevel level) {
		if (!this.isUsingItem() || !this.getUseItem().is(Items.BOW)) {
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			this.stopUsingItem();
			return;
		}
		if (this.getTicksUsingItem() >= 20) {
			int arrowSlot = this.inventory.find(s -> s.is(net.minecraft.tags.ItemTags.ARROWS));
			if (arrowSlot >= 0) {
				ItemStack bow = this.getMainHandItem();
				ItemStack arrowStack = this.inventory.main.get(arrowSlot).copyWithCount(1);
				AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, 1.0F, bow);
				double xd = target.getX() - this.getX();
				double yd = target.getY(0.3333) - arrow.getY();
				double zd = target.getZ() - this.getZ();
				double horizontal = Math.sqrt(xd * xd + zd * zd);
				Projectile.spawnProjectileUsingShoot(arrow, level, arrowStack, xd, yd + horizontal * 0.2, zd, 1.6F, 2.0F);
				this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.random.nextFloat() * 0.4F + 0.8F));
				this.inventory.main.get(arrowSlot).shrink(1);
				if (this.inventory.main.get(arrowSlot).isEmpty()) {
					this.inventory.main.set(arrowSlot, ItemStack.EMPTY);
				}
				bow.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
			}
			this.stopUsingItem();
		}
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		Entity attacker = source.getEntity();
		// Stem cell needles inject instead of hurting.
		if (attacker instanceof Player player && player.getMainHandItem().is(CloneCraft.STEM_CELL_NEEDLE)) {
			if (this.canUse(player)) {
				this.injectStemCells(!player.isShiftKeyDown());
				if (!player.getAbilities().instabuild) {
					DnaItems.exchangeOne(player.getMainHandItem(), player, DnaItems.dirty(CloneCraft.NEEDLE), InteractionHand.MAIN_HAND);
				}
			}
			return false;
		}
		if (this.scale > DEFAULT_SCALE && source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
			return false;
		}
		// Woken up by being hit.
		if (this.isSleeping()) {
			this.stopSleeping();
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && attacker instanceof LivingEntity living && this.hasOption(CloneOption.RETALIATE) && this.hasOption(CloneOption.FIGHT)) {
			boolean friendly = living instanceof Player p && (p.getAbilities().instabuild || this.isOwnedBy(p));
			if (!friendly && this.canAttackEntity(living)) {
				this.setTarget(living);
				for (CloneEntity other : level.getEntitiesOfClass(CloneEntity.class, this.getBoundingBox().inflate(8.0))) {
					if (other != this && other.getTarget() == null && other.hasOption(CloneOption.RETALIATE)
						&& other.getCloneTeam() == this.getCloneTeam() && other.canAttackEntity(living)) {
						other.setTarget(living);
					}
				}
			}
		}
		return hurt;
	}

	// ================================================================ interaction

	@Override
	protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.NAME_TAG) || held.is(CloneCraft.STEM_CELL_NEEDLE) || held.getItem() instanceof com.clonecraft.item.NeedleItem) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown() || !this.canUse(player)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			if (!this.hasOwner()) {
				this.setOwner(player);
				player.sendSystemMessage(Component.literal("You now own " + this.getCloneName()).withStyle(ChatFormatting.GOLD));
			}
			this.openMenu(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	public void openMenu(final ServerPlayer player) {
		CloneEntity clone = this;
		player.openMenu(new ExtendedMenuProvider<Integer>() {
			@Override
			public Integer getScreenOpeningData(final ServerPlayer p) {
				return clone.getId();
			}

			@Override
			public Component getDisplayName() {
				return Component.literal(clone.getCloneName());
			}

			@Override
			public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player p) {
				return new CloneMenu(containerId, inventory, clone);
			}
		});
	}

	// ================================================================ talking

	/** The clone says something in chat (to the given players, or everyone nearby) and above its head. */
	public void say(final String text, final Player... to) {
		Component line = Component.literal("<").append(Component.literal(this.getCloneName()).withColor(this.getCloneTeam().color)).append("> " + text);
		if (to == null || to.length == 0 || to[0] == null) {
			if (this.level() instanceof ServerLevel level) {
				for (ServerPlayer p : level.players()) {
					if (p.distanceToSqr(this) < 32 * 32) {
						p.sendSystemMessage(line);
					}
				}
			}
		} else {
			for (Player p : to) {
				if (p != null) {
					p.sendSystemMessage(line);
				}
			}
		}
		this.showMessage(text);
	}

	public void showMessage(final String text) {
		this.entityData.set(DATA_MESSAGE, text.length() > 60 ? text.substring(0, 57) + "..." : text);
		this.messageTicks = 100;
	}

	/** Shows the clone's message, or its health and hunger, under its name. */
	@Override
	public @Nullable Component belowNameDisplay() {
		String message = this.entityData.get(DATA_MESSAGE);
		if (!message.isEmpty()) {
			return Component.literal(message).withStyle(ChatFormatting.YELLOW);
		}
		if (this.hasOption(CloneOption.STATS)) {
			return Component.literal("❤ " + Math.round(this.getHealth()) + "/" + Math.round(this.getMaxHealth())).withStyle(ChatFormatting.RED)
				.append(Component.literal("   Food " + this.getFoodLevel() + "/20").withStyle(ChatFormatting.GOLD));
		}
		return null;
	}

	// ================================================================ smart memory

	private void scanForContainers(final ServerLevel level) {
		// Only check containers in loaded chunks. Looking up a far-away one would force its chunk to load.
		this.knownContainers.removeIf(pos -> level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof net.minecraft.world.Container));
		BlockPos center = this.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
			if (this.knownContainers.size() >= 64) {
				break;
			}
			if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container && !this.knownContainers.contains(pos)) {
				this.knownContainers.add(pos.immutable());
			}
		}
	}

	// ================================================================ mining helpers

	/** Picks the best tool for a block. Returns false if nothing in the inventory can break it properly. */
	public boolean selectBestToolFor(final BlockState state, final boolean select) {
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		int best = -1;
		float bestSpeed = 1.0F;
		for (int i = 0; i < CloneInventory.MAIN_SIZE; i++) {
			ItemStack s = this.inventory.main.get(i);
			if (s.isEmpty()) {
				continue;
			}
			float speed = s.getDestroySpeed(state);
			boolean correct = !state.requiresCorrectToolForDrops() || s.isCorrectToolForDrops(state);
			if (correct && speed > bestSpeed) {
				bestSpeed = speed;
				best = i;
			}
		}
		if (best >= 0) {
			if (select) {
				this.inventory.selectFromInventory(best);
			}
			return true;
		}
		if (!state.requiresCorrectToolForDrops()) {
			if (select) {
				this.inventory.selectEmptyHand();
			}
			return true;
		}
		return false;
	}

	public void addExhaustion(final float amount) {
		this.foodData.addExhaustion(amount);
	}

	// ================================================================ death, saving

	@Override
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
		this.inventory.dropAll();
		for (EquipmentSlot slot : CloneInventory.ARMOR_SLOTS) {
			ItemStack worn = this.getItemBySlot(slot);
			if (!worn.isEmpty()) {
				this.spawnAtLocation(level, worn);
				this.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	@Override
	protected int getBaseExperienceReward(final ServerLevel level) {
		return this.totalXp + (this.isIndependent() ? (int)((this.scale + 0.5) * 50) : 0);
	}

	@Override
	public void die(final DamageSource source) {
		if (!this.level().isClientSide()) {
			Player owner = this.getOwnerPlayer();
			if (owner != null) {
				owner.sendSystemMessage(Component.literal("Your clone " + this.getCloneName() + " has died!").withStyle(ChatFormatting.RED));
			}
		}
		super.die(source);
	}

	@Override
	public boolean removeWhenFarAway(final double distSqr) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("CloneName", this.getCloneName());
		output.store("profile", ResolvableProfile.CODEC, this.getProfile());
		output.putInt("CloneTeam", this.getCloneTeam().ordinal());
		output.putInt("CloneOptions", this.getOptions());
		if (this.ownerId != null) {
			output.store("OwnerUUID", net.minecraft.core.UUIDUtil.CODEC, this.ownerId);
			output.putString("OwnerName", this.getOwnerName());
		}
		this.inventory.save(output);
		this.foodData.save(output);
		output.putInt("XpTotal", this.totalXp);
		output.putInt("XpLevel", this.xpLevel);
		output.putFloat("XpP", this.xpProgress);
		output.putFloat("scale", this.scale);
		output.putDouble("scaleAim", this.aimScale);
		output.putInt("shrinkCooldown", this.shrinkCooldown);
		if (this.guardPos != null) {
			output.store("guardPosition", BlockPos.CODEC, this.guardPos);
		}
		output.store("AttackTypes", Identifier.CODEC.listOf(), new ArrayList<>(this.attackTypes));
		output.store("BreakBlocks", Identifier.CODEC.listOf(), new ArrayList<>(this.breakBlocks));
		output.store("KnownContainers", BlockPos.CODEC.listOf(), new ArrayList<>(this.knownContainers));
		this.buildGoal.save(output);
		this.progressionGoal.save(output);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_OPTIONS, input.getIntOr("CloneOptions", CloneOption.defaults()));
		this.entityData.set(DATA_TEAM, input.getIntOr("CloneTeam", CloneTeam.GOOD.ordinal()));
		String name = input.getStringOr("CloneName", "Steve");
		ResolvableProfile profile = input.read("profile", ResolvableProfile.CODEC).orElse(null);
		this.setCloneName(name);
		if (profile != null && profile.name().map(n -> n.equals(name)).orElse(false)) {
			this.entityData.set(DATA_PROFILE, profile);
		}
		this.ownerId = input.read("OwnerUUID", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		this.entityData.set(DATA_OWNER_NAME, input.getStringOr("OwnerName", ""));
		this.inventory.load(input);
		this.foodData.load(input);
		this.totalXp = input.getIntOr("XpTotal", 0);
		this.xpLevel = input.getIntOr("XpLevel", 0);
		this.xpProgress = input.getFloatOr("XpP", 0.0F);
		this.entityData.set(DATA_XP_LEVEL, this.xpLevel);
		this.entityData.set(DATA_XP_PROGRESS, this.xpProgress);
		this.scale = input.getFloatOr("scale", 0.5F);
		this.aimScale = input.getDoubleOr("scaleAim", this.scale);
		this.shrinkCooldown = input.getIntOr("shrinkCooldown", 0);
		this.applyScale(true);
		this.guardPos = input.read("guardPosition", BlockPos.CODEC).orElse(null);
		this.attackTypes.clear();
		this.attackTypes.addAll(input.read("AttackTypes", Identifier.CODEC.listOf()).orElse(List.of()));
		this.breakBlocks.clear();
		this.breakBlocks.addAll(input.read("BreakBlocks", Identifier.CODEC.listOf()).orElse(List.of()));
		this.knownContainers.clear();
		this.knownContainers.addAll(input.read("KnownContainers", BlockPos.CODEC.listOf()).orElse(List.of()));
		this.buildGoal.load(input);
		this.progressionGoal.load(input);
		this.refreshName();
	}

	/** Copies everything about another clone into this one (used by "Copy Clone"). */
	public void copyFrom(final CloneEntity other) {
		this.setCloneName(other.getCloneName());
		this.entityData.set(DATA_PROFILE, other.getProfile());
		this.setCloneTeam(other.getCloneTeam());
		this.setOptions(other.getOptions());
		this.ownerId = other.ownerId;
		this.entityData.set(DATA_OWNER_NAME, other.getOwnerName());
		other.inventory.syncHeld();
		for (int i = 0; i < CloneInventory.MAIN_SIZE; i++) {
			this.inventory.main.set(i, other.inventory.main.get(i).copy());
		}
		this.inventory.select(other.inventory.selected);
		for (EquipmentSlot slot : CloneInventory.ARMOR_SLOTS) {
			this.setItemSlot(slot, other.getItemBySlot(slot).copy());
		}
		this.attackTypes.addAll(other.attackTypes);
		this.breakBlocks.addAll(other.breakBlocks);
		this.scale = other.scale;
		this.aimScale = other.aimScale;
		this.applyScale(true);
		this.foodData.setFoodLevel(other.foodData.getFoodLevel());
	}
}
