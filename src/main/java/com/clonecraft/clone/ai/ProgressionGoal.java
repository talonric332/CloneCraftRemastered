package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.DropClaims;
import com.clonecraft.clone.progression.CloneStorage;
import com.clonecraft.clone.progression.CloneTrading;
import com.clonecraft.clone.progression.ProgressionCrafting;
import com.clonecraft.clone.progression.ProgressionCrafting.Recipe;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Game Progression: with the option on, an idle clone works its way through the game by itself.
 * <ol>
 * <li>Wood: chops trees, makes a crafting table, a wooden pickaxe and (if there are sheep about) a bed.</li>
 * <li>Mining: digs for stone, coal and iron (staircases down and branch tunnels), makes a furnace, smelts the iron
 * and makes full iron armour, a sword and tools.</li>
 * <li>Food: whenever it's hungry with nothing to eat, harvests ripe crops or hunts animals (and cooks the meat).</li>
 * <li>Dragon prep: diamonds for a diamond pickaxe, obsidian it finds nearby, pearls from endermen and blaze rods from
 * blazes (only if it's in the Nether), then Eyes of Ender.</li>
 * </ol>
 * It only looks at blocks and mobs in loaded chunks, never digs into lava or water, and doesn't run while the clone is
 * following, guarding or building. Fighting (including the hunting it starts) is left to the fighting AI.
 */
public class ProgressionGoal extends Goal {
	public enum Objective {
		FOOD(3, "Finding food"),
		WOOD(1, "Chopping wood"),
		WOOL(1, "Hunting sheep for a bed"),
		STONE(2, "Mining stone"),
		IRON(2, "Mining for iron and coal"),
		SMELTING(2, "Waiting for the furnace"),
		DIAMONDS(4, "Mining for diamonds"),
		OBSIDIAN(4, "Mining obsidian"),
		PEARLS(4, "Hunting endermen for pearls"),
		BLAZE_RODS(4, "Hunting blazes"),
		EXPLORE(0, "Looking around"),
		CRAFT(0, "Crafting at a crafting table"),
		PACK_TABLE(0, "Packing up the crafting table"),
		STORE(0, "Storing spare items"),
		FETCH(0, "Getting supplies from a chest"),
		WAITING(4, "Looking for endermen and blazes"),
		FIND_ANIMALS(3, "Looking for animals"),
		PLANT(1, "Planting trees"),
		TREES_GROWING(1, "Waiting for trees to grow"),
		SAPLINGS(1, "Collecting saplings"),
		NEED(0, "Needs supplies"),
		TRADE(0, "Trading"),
		SLEEP(0, "Going to bed"),
		STOCKED(3, "Food stores are full"),
		DONE(4, "Ready for the Ender Dragon!");

		public final int stage;
		public final String message;

		Objective(final int stage, final String message) {
			this.stage = stage;
			this.message = message;
		}
	}

	/**
	 * When several of a player's clones are progressing near each other, they split the work. The first (by a fixed
	 * order) goes for the dragon; the others become helpers who stock Clone Storage for it.
	 */
	public enum Role {
		PROGRESS("Progressing"),
		MINER("Miner"),
		LUMBERJACK("Lumberjack"),
		HUNTER("Hunter");

		public final String label;

		Role(final String label) {
			this.label = label;
		}
	}

	private static final Role[] HELPER_ROLES = {Role.MINER, Role.LUMBERJACK, Role.HUNTER};
	/** How far apart clones can be and still share the work. */
	public static final int TEAM_RANGE = 64;
	/** A Hunter keeps Clone Storage stocked with this much food. */
	public static final int HUNTER_FOOD_TARGET = 64;
	/** A Hunter leaves at least this many of each kind of animal nearby, so they keep breeding. */
	private static final int SPARE_ANIMALS = 3;

	private static final Predicate<ItemStack> WOOD_ITEM = s -> s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS) || isPlantable(s);
	/** How far a clone will walk to trade with a villager or wandering trader. */
	public static final int TRADE_RANGE = 24;
	private static final Predicate<BlockState> IS_STONE = s -> s.is(BlockTags.BASE_STONE_OVERWORLD);
	private static final Predicate<BlockState> IS_ORE = s -> s.is(ConventionalBlockTags.IRON_ORES) || s.is(ConventionalBlockTags.COAL_ORES);

	public static final int TARGET_OBSIDIAN = 10;
	public static final int TARGET_EYES = 12;
	private static final int IRON_DEPTH = 16;
	private static final int DIAMOND_DEPTH = -54;
	private static final int THINK_INTERVAL = 10;
	private static final double REACH_SQ = 4.5 * 4.5;
	private static final int GIVE_UP_TICKS = 600;
	/** How far a clone looks for Clone Storage chests. */
	public static final int STORAGE_RANGE = 32;
	private static final Set<EntityType<?>> FOOD_ANIMALS = Set.of(EntityTypes.COW, EntityTypes.PIG, EntityTypes.CHICKEN, EntityTypes.SHEEP,
		EntityTypes.RABBIT, EntityTypes.MOOSHROOM);

	private final CloneEntity clone;
	public final ProgressionCrafting.Furnace furnace = new ProgressionCrafting.Furnace();
	private Role role = Role.PROGRESS;
	private int nextRoleCheck;
	private @Nullable Objective objective;
	private int think;
	private @Nullable BlockPos target;
	private @Nullable BlockPos branchGoal;
	private Direction digDir = Direction.NORTH;
	private int turns;
	private int idleNearTarget;
	private double idleBestDist = Double.MAX_VALUE;
	private @Nullable BlockPos exploreFrom;
	private int exploreStill;
	/** What it's waiting for someone to bring (NEED), and the last thing it asked for (so it only asks once). */
	private String needWhat = "";
	private String askedFor = "";
	private @Nullable BlockPos openChestPos;
	private int closeChestAt;
	private @Nullable UUID tradeWith;
	private @Nullable Predicate<ItemStack> tradeWanted;
	private String tradeFor = "";
	private int tradeTicks;
	private int tradeCooldownUntil;
	private int noDescendUntil;
	private double bestDist = Double.MAX_VALUE;
	private int stuckTicks;
	private final Set<BlockPos> failed = new LinkedHashSet<>();
	private final Set<UUID> ignoredItems = new HashSet<>();
	private @Nullable ItemEntity collecting;
	/** A crafting table this clone put down, which it picks back up when it's done crafting. */
	private @Nullable BlockPos placedTable;
	private @Nullable BlockPos knownTable;
	private @Nullable BlockPos fetchFrom;
	private @Nullable Predicate<ItemStack> fetchWanted;
	private int fetchAmount;
	private int fetchCooldownUntil;
	private int collectTicks;
	private boolean announcedDone;

	public ProgressionGoal(final CloneEntity clone) {
		this.clone = clone;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public @Nullable Objective getObjective() {
		return this.objective;
	}

	public Role getRole() {
		return this.role;
	}

	/** What the clone is up to, for the Clone Remote, e.g. "Miner: Mining for iron and coal". Empty when it isn't progressing. */
	public String describe() {
		if (!this.clone.hasOption(CloneOption.GAME_PROGRESSION) || this.objective == null) {
			return "";
		}
		String message = this.objective == Objective.NEED ? "Needs " + this.needWhat
			: this.objective == Objective.TRADE ? "Trading for " + this.tradeFor : this.objective.message;
		return this.role == Role.PROGRESS ? message : this.role.label + ": " + message;
	}

	/**
	 * Can't get this itself (nothing to dig or chop here, as in a superflat world): say so once, then wait. It picks the
	 * items up as soon as they're in a nearby chest.
	 */
	private Objective need(final ServerLevel level, final String what, final Predicate<ItemStack> wanted) {
		// A trader nearby might sell it.
		Objective trade = this.planTrade(level, wanted);
		if (trade != null) {
			return trade;
		}
		this.needWhat = what;
		if (!what.equals(this.askedFor)) {
			this.askedFor = what;
			this.clone.say("I need " + what + " to keep going. Put some in a chest near me!", this.clone.getOwnerPlayer());
		}
		return Objective.NEED;
	}

	/**
	 * Is digging for this worth it here? In a normal overworld, yes. In a superflat world there's nothing underground,
	 * so only if some is in sight nearby. Never in the Nether or End.
	 */
	private boolean worthDigging(final ServerLevel level, final Predicate<BlockState> what) {
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		return !level.isFlat() || this.findBlock(level, what, 10, 10, 4) != null;
	}

	/**
	 * How to get wood: chop a tree it can reach; otherwise grow some (plant saplings, using bone meal if it has any, and
	 * wait for them); otherwise get saplings from a chest or by breaking leaves; otherwise go looking, or in a superflat
	 * world (where there are no trees to find) ask for some.
	 */
	private Objective woodObjective(final ServerLevel level) {
		if (this.findLog(level) != null) {
			return Objective.WOOD;
		}
		if (this.plantableSaplingSlot() >= 0 && this.findPlantSpot(level) != null) {
			return Objective.PLANT;
		}
		if (this.findGrowingSapling(level) != null) {
			return Objective.TREES_GROWING;
		}
		if (this.planFetch(level, s -> s.is(ItemTags.SAPLINGS) && isPlantable(s), 4)) {
			return Objective.FETCH;
		}
		Objective trade = this.planTrade(level, WOOD_ITEM);
		if (trade != null) {
			return trade;
		}
		if (this.findNaturalLeaves(level) != null) {
			return Objective.SAPLINGS;
		}
		return level.isFlat() ? this.need(level, "wood (logs, planks or saplings)", WOOD_ITEM) : Objective.EXPLORE;
	}

	// ---------------------------------------------------------------- night

	public static boolean isNight(final Level level) {
		return level.dimension() == Level.OVERWORLD && level.isDarkOutside();
	}

	/** Out under the open sky (where monsters come at night), rather than in a cave or under a roof. */
	private boolean outdoors(final ServerLevel level) {
		BlockPos feet = this.clone.blockPosition();
		return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ()) <= feet.getY() + 1;
	}

	private boolean has(final net.minecraft.tags.TagKey<Item> tag) {
		return this.count(s -> s.is(tag)) > 0;
	}

	/** A free bed (its head) nearby: its own, or any other one nobody's sleeping in. */
	private @Nullable BlockPos findBed(final ServerLevel level) {
		return this.findBlock(level, s -> s.getBlock() instanceof net.minecraft.world.level.block.BedBlock
			&& s.getValue(net.minecraft.world.level.block.AbstractBedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
			&& !s.getValue(net.minecraft.world.level.block.AbstractBedBlock.OCCUPIED), 16, 4, 4);
	}

	/** Goes to a bed (putting its own down if there isn't one about) and sleeps until morning. */
	private void goToBed(final ServerLevel level) {
		BlockPos bed = this.findBed(level);
		if (bed == null) {
			this.placeBed(level);
			return;
		}
		if (this.clone.distanceToSqr(Vec3.atCenterOf(bed)) > 2.5 * 2.5) {
			if (!this.giveUpIfStuck(bed)) {
				this.clone.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 1.0);
			}
			return;
		}
		this.clone.getNavigation().stop();
		this.clone.startSleeping(bed);
	}

	/** Puts the bed it carries down next to it: two free spots side by side on solid ground. */
	private void placeBed(final ServerLevel level) {
		int slot = this.clone.getInventory().find(s -> s.is(ItemTags.BEDS));
		if (slot < 0) {
			return;
		}
		BlockPos feet = this.clone.blockPosition();
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			for (BlockPos foot : new BlockPos[]{feet.relative(dir.getClockWise()), feet.relative(dir.getCounterClockWise())}) {
				BlockPos head = foot.relative(dir);
				if (this.bedFits(level, foot) && this.bedFits(level, head)) {
					ItemStack stack = this.clone.getInventory().main.get(slot);
					BlockState state = net.minecraft.world.level.block.Block.byItem(stack.getItem()).defaultBlockState()
						.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, dir);
					level.setBlock(foot, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
					// The bed's own placement puts the other half down.
					state.getBlock().setPlacedBy(level, foot, state, this.clone, stack);
					level.playSound(null, foot, net.minecraft.sounds.SoundEvents.WOOD_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
					this.clone.getInventory().consumeOne(s -> s.is(stack.getItem()));
					return;
				}
			}
		}
		this.makeRoom(level);
	}

	private boolean bedFits(final ServerLevel level, final BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos).getFluidState().isEmpty()
			&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
			&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			&& !this.clone.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos));
	}

	// ---------------------------------------------------------------- trading

	/** How many of the matching items the clone could part with (spares, plus junk). Tools and gear are never spare. */
	private int sellable(final Predicate<ItemStack> test) {
		int[] spare = this.spares();
		int total = 0;
		for (int i = 0; i < spare.length; i++) {
			ItemStack s = this.clone.getInventory().main.get(i);
			if (s.isEmpty() || !test.test(s)) {
				continue;
			}
			total += isJunk(s) ? s.getCount() : spare[i];
		}
		return total;
	}

	/**
	 * Looks for a villager or wandering trader nearby who sells something wanted, that the clone can pay for (with
	 * emeralds it has, or can get by selling spares to that trader). Returns TRADE if there is one; FETCH if it only
	 * needs emeralds from a nearby chest first; null otherwise.
	 */
	private @Nullable Objective planTrade(final ServerLevel level, final Predicate<ItemStack> wanted) {
		if (this.clone.tickCount < this.tradeCooldownUntil) {
			return null;
		}
		for (var merchant : CloneTrading.merchantsNear(level, this.clone, TRADE_RANGE)) {
			var offer = CloneTrading.offerFor(merchant, wanted);
			if (offer == null) {
				continue;
			}
			if (CloneTrading.affordable(this.clone, merchant, offer, this::sellable)) {
				this.tradeWith = merchant.getUUID();
				this.tradeWanted = wanted;
				this.tradeFor = offer.getResult().getHoverName().getString();
				return Objective.TRADE;
			}
			int short_ = CloneTrading.emeraldCost(offer) - this.count(s -> s.is(Items.EMERALD))
				- CloneTrading.emeraldsFromSelling(this.clone, merchant, this::sellable);
			if (short_ > 0 && this.planFetch(level, s -> s.is(Items.EMERALD), short_)) {
				return Objective.FETCH;
			}
		}
		return null;
	}

	/** Walks up to the trader, sells spares for emeralds if it has to, then buys what it wanted. */
	private void tradeWithMerchant(final ServerLevel level) {
		var merchant = this.tradeWith == null ? null : level.getEntity(this.tradeWith) instanceof net.minecraft.world.entity.npc.villager.AbstractVillager m ? m : null;
		if (merchant == null || !merchant.isAlive() || merchant.isTrading() || this.tradeWanted == null) {
			this.tradeWith = null;
			this.tradeCooldownUntil = this.clone.tickCount + 200;
			return;
		}
		this.clone.getLookControl().setLookAt(merchant);
		if (this.clone.distanceToSqr(merchant) > 3.0 * 3.0) {
			this.tradeTicks += THINK_INTERVAL;
			if (this.tradeTicks > 600) {
				// Couldn't get to it.
				this.tradeTicks = 0;
				this.tradeWith = null;
				this.tradeCooldownUntil = this.clone.tickCount + 1200;
				return;
			}
			this.clone.getNavigation().moveTo(merchant, 1.0);
			return;
		}
		this.tradeTicks = 0;
		merchant.getLookControl().setLookAt(this.clone);
		var offer = CloneTrading.offerFor(merchant, this.tradeWanted);
		if (offer != null && CloneTrading.affordable(this.clone, merchant, offer, this::sellable)) {
			CloneTrading.sellFor(this.clone, merchant, CloneTrading.emeraldCost(offer), this::sellable);
			if (CloneTrading.trade(this.clone, merchant, offer)) {
				this.clone.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
				this.tradeWith = null;
				this.tradeCooldownUntil = this.clone.tickCount + 20;
				return;
			}
		}
		this.tradeWith = null;
		this.tradeCooldownUntil = this.clone.tickCount + 1200;
	}

	// ---------------------------------------------------------------- growing trees

	/** Dark and pale oak only grow from four saplings in a square, so the clone doesn't plant those. */
	private static boolean isPlantable(final ItemStack stack) {
		return stack.is(ItemTags.SAPLINGS) && !stack.is(Items.DARK_OAK_SAPLING) && !stack.is(Items.PALE_OAK_SAPLING);
	}

	private int plantableSaplingSlot() {
		return this.clone.getInventory().find(ProgressionGoal::isPlantable);
	}

	/** Grass or dirt with room for a tree above it, not crowding another tree or sapling. */
	private @Nullable BlockPos findPlantSpot(final ServerLevel level) {
		return this.findBlock(level, s -> s.isAir(), 8, 3, 3, pos -> {
			// The game's own rule for where a sapling can grow (grass, dirt, podzol...).
			if (!level.isLoaded(pos) || !Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, pos)) {
				return false;
			}
			for (int y = 1; y <= 6; y++) {
				if (!level.getBlockState(pos.above(y)).isAir()) {
					return false;
				}
			}
			for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
				BlockState state = level.getBlockState(near);
				if (state.is(BlockTags.SAPLINGS) || state.is(BlockTags.LOGS)) {
					return false;
				}
			}
			return true;
		});
	}

	private @Nullable BlockPos findGrowingSapling(final ServerLevel level) {
		return this.findBlock(level, s -> s.is(BlockTags.SAPLINGS), 12, 4, 4);
	}

	/** Leaves that grew on a tree (not ones a player placed): breaking them sometimes drops a sapling. */
	private @Nullable BlockPos findNaturalLeaves(final ServerLevel level) {
		return this.findBlock(level, s -> s.is(BlockTags.LEAVES) && s.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
			&& !s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT), 8, 4, 6);
	}

	/** Plants a sapling at a good spot, and if it has bone meal, makes it grow. */
	private void plantTree(final ServerLevel level) {
		BlockPos spot = this.target != null && level.getBlockState(this.target).isAir() ? this.target : this.findPlantSpot(level);
		int slot = this.plantableSaplingSlot();
		if (spot == null || slot < 0) {
			return;
		}
		if (!spot.equals(this.target)) {
			this.target = spot;
			this.resetProgress();
		}
		if (this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(spot)) > REACH_SQ) {
			if (!this.giveUpIfStuck(spot)) {
				this.clone.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
			}
			return;
		}
		ItemStack sapling = this.clone.getInventory().main.get(slot);
		BlockState state = net.minecraft.world.level.block.Block.byItem(sapling.getItem()).defaultBlockState();
		if (!state.canSurvive(level, spot)) {
			this.giveUp(spot);
			return;
		}
		level.setBlock(spot, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
		level.playSound(null, spot, net.minecraft.sounds.SoundEvents.GRASS_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
		this.clone.getInventory().consumeOne(s -> s.is(sapling.getItem()));
		this.clone.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
		this.boneMeal(level, spot);
		this.target = null;
	}

	/** Uses bone meal on a sapling until it grows into a tree (or the bone meal runs out). */
	private void boneMeal(final ServerLevel level, final BlockPos sapling) {
		for (int i = 0; i < 16 && level.getBlockState(sapling).is(BlockTags.SAPLINGS); i++) {
			int slot = this.clone.getInventory().find(s -> s.is(Items.BONE_MEAL));
			if (slot < 0) {
				break;
			}
			if (net.minecraft.world.item.BoneMealItem.growCrop(this.clone.getInventory().main.get(slot), level, sapling)) {
				level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, sapling, 15);
			}
			if (this.clone.getInventory().main.get(slot).isEmpty()) {
				this.clone.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
		this.clone.getInventory().syncHeld();
	}

	/** Stays by its saplings while they grow, helping them along with bone meal if it has any. */
	private void tendTrees(final ServerLevel level) {
		BlockPos sapling = this.findGrowingSapling(level);
		if (sapling == null) {
			return;
		}
		boolean hasBoneMeal = this.has(Items.BONE_MEAL);
		double dist = this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(sapling));
		if (hasBoneMeal && dist <= REACH_SQ) {
			this.boneMeal(level, sapling);
		} else if (dist > (hasBoneMeal ? REACH_SQ : 36.0) && this.clone.getNavigation().isDone()) {
			this.clone.getNavigation().moveTo(sapling.getX() + 0.5, sapling.getY(), sapling.getZ() + 0.5, 1.0);
		}
	}

	/**
	 * Works out this clone's job: progressing clones of the same owner within range, in a fixed order (by UUID).
	 * The first goes for the dragon, the rest take turns being Miner, Lumberjack and Hunter.
	 */
	private Role computeRole(final ServerLevel level) {
		if (this.clone.getOwnerId() == null) {
			// Only a player's clones team up; an unowned clone just progresses by itself.
			return Role.PROGRESS;
		}
		List<CloneEntity> team = level.getEntitiesOfClass(CloneEntity.class, this.clone.getBoundingBox().inflate(TEAM_RANGE, TEAM_RANGE / 2.0, TEAM_RANGE),
			c -> c.isAlive() && c.hasOption(CloneOption.GAME_PROGRESSION) && !c.hasOption(CloneOption.FOLLOW) && !c.hasOption(CloneOption.GUARD)
				&& java.util.Objects.equals(c.getOwnerId(), this.clone.getOwnerId()));
		team.sort(java.util.Comparator.comparing(CloneEntity::getUUID));
		int index = team.indexOf(this.clone);
		return index <= 0 ? Role.PROGRESS : HELPER_ROLES[(index - 1) % HELPER_ROLES.length];
	}

	/** The block it's currently heading for or digging toward, if any. */
	public @Nullable BlockPos getCurrentTarget() {
		return this.target != null ? this.target : this.branchGoal;
	}

	/** Progression only runs for an idle clone: not following, guarding, building, fighting or mid-dig. */
	private boolean allowed() {
		return this.clone.hasOption(CloneOption.GAME_PROGRESSION) && !this.clone.isSleeping() && !this.clone.hasOption(CloneOption.FOLLOW)
			&& !this.clone.hasOption(CloneOption.GUARD) && !this.clone.buildGoal.isRunning() && this.clone.getTarget() == null
			&& !this.clone.mineGoal.hasRequest() && !this.clone.isUsingItem() && this.clone.level() instanceof ServerLevel;
	}

	@Override
	public boolean canUse() {
		return this.allowed();
	}

	@Override
	public boolean canContinueToUse() {
		return this.allowed();
	}

	@Override
	public void start() {
		this.think = 0;
	}

	@Override
	public void stop() {
		this.collecting = null;
		if (this.clone.level() instanceof ServerLevel level) {
			this.closeChest(level);
		}
	}

	/** Opens a chest or barrel's lid while the clone uses it; it's closed again a second later. */
	private void openChest(final ServerLevel level, final BlockPos pos) {
		if (!pos.equals(this.openChestPos)) {
			this.closeChest(level);
			CloneStorage.setOpen(level, pos, true);
			this.openChestPos = pos;
		}
		this.closeChestAt = this.clone.tickCount + 20;
	}

	private void closeChest(final ServerLevel level) {
		if (this.openChestPos != null) {
			CloneStorage.setOpen(level, this.openChestPos, false);
			this.openChestPos = null;
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (this.clone.level() instanceof ServerLevel openLevel && this.openChestPos != null && this.clone.tickCount >= this.closeChestAt) {
			this.closeChest(openLevel);
		}
		if (--this.think > 0 || !(this.clone.level() instanceof ServerLevel level)) {
			return;
		}
		this.think = THINK_INTERVAL;
		if (this.failed.size() > 200) {
			this.failed.clear();
		}
		if (this.clone.tickCount >= this.nextRoleCheck) {
			this.nextRoleCheck = this.clone.tickCount + 100;
			Role newRole = this.computeRole(level);
			if (newRole != this.role) {
				this.role = newRole;
				this.objective = null;
			}
		}
		this.dropJunkIfFull(level);
		this.craftWhatWeCan();
		if (this.collectDrops(level)) {
			return;
		}
		Objective next = this.plan(level);
		if (next != this.objective) {
			this.objective = next;
			this.target = null;
			this.branchGoal = null;
			this.resetProgress();
			String text = next == Objective.NEED ? "Needs " + this.needWhat : next == Objective.TRADE ? "Trading for " + this.tradeFor : next.message;
			String message = next.stage > 0 && this.role == Role.PROGRESS ? "Stage " + next.stage + ": " + text : text;
			this.clone.showMessage(this.role == Role.PROGRESS ? message : this.role.label + ": " + message);
		}
		this.act(level, next);
	}

	// ================================================================ what to do next

	private boolean hungry() {
		return this.clone.getFoodData().getFoodLevel() <= 14;
	}

	private Objective plan(final ServerLevel level) {
		boolean overworld = level.dimension() == Level.OVERWORLD;
		boolean canFight = this.clone.hasOption(CloneOption.FIGHT);

		// Night: with a bed (carried or nearby) and not starving, go to sleep until morning.
		if (isNight(level) && this.clone.getFoodData().getFoodLevel() > 6 && (this.has(ItemTags.BEDS) || this.findBed(level) != null)) {
			return Objective.SLEEP;
		}

		// Stage 3 comes first whenever it's needed: a hungry clone with nothing to eat finds food (from storage first).
		if (this.hungry() && this.count(s -> s.has(DataComponents.FOOD)) == 0) {
			if (this.planFetch(level, s -> s.has(DataComponents.FOOD), 16)) {
				return Objective.FETCH;
			}
			Objective foodTrade = this.planTrade(level, s -> s.has(DataComponents.FOOD) && !s.is(Items.SUSPICIOUS_STEW));
			if (foodTrade != null) {
				return foodTrade;
			}
			if (this.findCrop(level) != null || (canFight && this.findMob(level, FOOD_ANIMALS, 24) != null)) {
				return Objective.FOOD;
			}
		}

		// A better pickaxe, sword, axe or armour in a nearby chest (a bonus chest's stone pickaxe, say): take it.
		if (this.planFetch(level, this::isUpgrade, 1)) {
			return Objective.FETCH;
		}
		// ...or buy it from a villager (a toolsmith's pickaxe, an armorer's armour).
		Objective upgradeTrade = this.planTrade(level, this::isUpgrade);
		if (upgradeTrade != null) {
			return upgradeTrade;
		}

		// Tools, armour and the like need a crafting table: go to one (or put one down) when there's something to make.
		if (this.firstAffordableTableRecipe() != null && !this.tableInReach(level)) {
			return Objective.CRAFT;
		}
		// Finished with a table it put down itself: go back and pick it up.
		if (this.placedTable != null) {
			if (!level.isLoaded(this.placedTable) || !level.getBlockState(this.placedTable).is(Blocks.CRAFTING_TABLE) || this.failed.contains(this.placedTable)) {
				this.placedTable = null;
			} else if (this.firstAffordableTableRecipe() == null) {
				return Objective.PACK_TABLE;
			}
		}

		// Running out of room (or, for a helper, carrying a load): put spare items in a Clone Storage chest.
		if (this.shouldStore() && (CloneStorage.nearest(level, this.clone.blockPosition(), STORAGE_RANGE, null) != null || this.has(Items.CHEST))) {
			return Objective.STORE;
		}

		switch (this.role) {
			case LUMBERJACK -> {
				return this.woodObjective(level);
			}
			case HUNTER -> {
				if (!this.hasSword() && this.planFetch(level, s -> s.is(ItemTags.STONE_TOOL_MATERIALS), 2 - this.count(s -> s.is(ItemTags.STONE_TOOL_MATERIALS)))) {
					return Objective.FETCH;
				}
				BlockPos storage = CloneStorage.nearest(level, this.clone.blockPosition(), STORAGE_RANGE, null);
				net.minecraft.world.Container chest = storage == null ? null : CloneStorage.at(level, storage);
				int stored = chest == null ? 0 : CloneStorage.count(chest, s -> s.has(DataComponents.FOOD));
				if (stored + this.count(s -> s.has(DataComponents.FOOD)) < HUNTER_FOOD_TARGET) {
					if (this.findCrop(level) != null || (canFight && this.findHuntable(level) != null)) {
						return Objective.FOOD;
					}
					// Nothing to hunt here: go and look for animals instead of standing about.
					return Objective.FIND_ANIMALS;
				}
				return Objective.STOCKED;
			}
			case MINER -> {
				int planksShort = this.planksNeeded() - this.planksHave();
				if (planksShort > 0) {
					if (this.planFetch(level, s -> s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS), (planksShort + 3) / 4 * 4)) {
						return Objective.FETCH;
					}
					return this.woodObjective(level);
				}
				int cobbleShort = this.cobbleNeeded() - this.count(s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS));
				if (cobbleShort > 0 && this.pickaxeTier() >= 1) {
					if (this.planFetch(level, s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS), cobbleShort)) {
						return Objective.FETCH;
					}
					return this.worthDigging(level, IS_STONE) ? Objective.STONE : this.need(level, "cobblestone", s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS));
				}
				if (this.pickaxeTier() >= 2) {
					return this.worthDigging(level, IS_ORE) ? Objective.IRON : this.need(level, "coal and iron (ore, raw iron or ingots)", s -> s.is(Items.IRON_INGOT) || s.is(Items.RAW_IRON) || s.is(ItemTags.COALS));
				}
				return Objective.WAITING;
			}
			default -> {
			}
		}

		// Stage 1: wood.
		int planksShort = this.planksNeeded() - this.planksHave();
		if (planksShort > 0) {
			if (this.planFetch(level, s -> s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS), (planksShort + 3) / 4 * 4)) {
				return Objective.FETCH;
			}
			return this.woodObjective(level);
		}
		if (!this.hasBed() && this.count(s -> s.is(ItemTags.WOOL)) < 3 && canFight && this.findMob(level, Set.of(EntityTypes.SHEEP), 24) != null) {
			return Objective.WOOL;
		}

		// Stage 2: stone, then iron and coal, then smelting.
		int cobbleShort = this.cobbleNeeded() - this.count(s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS));
		if (cobbleShort > 0 && this.pickaxeTier() >= 1) {
			if (this.planFetch(level, s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS), cobbleShort)) {
				return Objective.FETCH;
			}
			return this.worthDigging(level, IS_STONE) ? Objective.STONE : this.need(level, "cobblestone", s -> s.is(ItemTags.STONE_CRAFTING_MATERIALS));
		}
		int ironNeeded = this.ironNeeded();
		int ingots = this.count(s -> s.is(Items.IRON_INGOT));
		int raw = this.count(s -> s.is(Items.RAW_IRON));
		if (ingots + raw < ironNeeded && this.planFetch(level, s -> s.is(Items.IRON_INGOT) || s.is(Items.RAW_IRON), ironNeeded - ingots - raw)) {
			return Objective.FETCH;
		}
		if (this.furnace.fuelAvailable(this.clone) < raw && this.planFetch(level, s -> s.is(ItemTags.COALS), (raw + 7) / 8)) {
			return Objective.FETCH;
		}
		if (this.pickaxeTier() >= 2 && (ingots + raw < ironNeeded || this.furnace.fuelAvailable(this.clone) < raw)) {
			return this.worthDigging(level, IS_ORE) ? Objective.IRON : this.need(level, "coal and iron (ore, raw iron or ingots)", s -> s.is(Items.IRON_INGOT) || s.is(Items.RAW_IRON) || s.is(ItemTags.COALS));
		}

		// Stage 4: diamonds, obsidian, pearls, blaze rods.
		if (this.pickaxeTier() < 4 && this.count(s -> s.is(Items.DIAMOND)) < 3) {
			if (this.planFetch(level, s -> s.is(Items.DIAMOND), 3 - this.count(s -> s.is(Items.DIAMOND)))) {
				return Objective.FETCH;
			}
			if (this.pickaxeTier() >= 3) {
				return this.worthDigging(level, s -> s.is(ConventionalBlockTags.DIAMOND_ORES)) ? Objective.DIAMONDS : this.need(level, "diamonds", s -> s.is(Items.DIAMOND));
			}
			if (raw > 0) {
				return Objective.SMELTING;
			}
		}
		if (this.pickaxeTier() >= 4 && this.count(s -> s.is(Items.OBSIDIAN)) < TARGET_OBSIDIAN && this.findObsidian(level) != null) {
			return Objective.OBSIDIAN;
		}
		int eyes = this.count(s -> s.is(Items.ENDER_EYE));
		if (canFight && eyes + this.count(s -> s.is(Items.ENDER_PEARL)) < TARGET_EYES && this.findMob(level, Set.of(EntityTypes.ENDERMAN), 32) != null) {
			return Objective.PEARLS;
		}
		int powder = this.count(s -> s.is(Items.BLAZE_POWDER)) + this.count(s -> s.is(Items.BLAZE_ROD)) * 2;
		if (canFight && eyes + powder < TARGET_EYES && this.findMob(level, Set.of(EntityTypes.BLAZE), 32) != null) {
			return Objective.BLAZE_RODS;
		}
		// No endermen about: a cleric sells Ender Pearls.
		if (this.pickaxeTier() >= 3 && eyes + this.count(s -> s.is(Items.ENDER_PEARL)) < TARGET_EYES) {
			Objective pearlTrade = this.planTrade(level, s -> s.is(Items.ENDER_PEARL));
			if (pearlTrade != null) {
				return pearlTrade;
			}
		}
		if (raw > 0) {
			return Objective.SMELTING;
		}
		boolean done = this.pickaxeTier() >= 4 && this.ironNeeded() == 0 && eyes >= TARGET_EYES;
		return done ? Objective.DONE : Objective.WAITING;
	}

	private void act(final ServerLevel level, final Objective objective) {
		switch (objective) {
			case FOOD -> {
				BlockPos crop = this.findCrop(level);
				if (crop != null) {
					this.approachAndMine(level, crop);
				} else if (this.role == Role.HUNTER) {
					LivingEntity prey = this.findHuntable(level);
					if (prey != null) {
						this.clone.setTarget(prey);
					}
				} else {
					this.hunt(level, FOOD_ANIMALS, 24);
				}
			}
			case WOOD -> this.approachAndMine(level, this.target != null && this.isLog(level, this.target) ? this.target : this.findLog(level));
			case WOOL -> this.hunt(level, Set.of(EntityTypes.SHEEP), 24);
			case STONE -> {
				if (this.target == null || !this.isStone(level, this.target)) {
					this.target = this.findBlock(level, s -> s.is(BlockTags.BASE_STONE_OVERWORLD), 8, 8, 2);
				}
				if (this.target != null) {
					this.digToward(level, this.target);
				} else {
					this.branchMine(level, this.clone.getBlockY() - 6);
				}
			}
			case IRON -> {
				if (this.role == Role.MINER) {
					// A Miner digs up every useful ore, deeper (for diamonds) once it has an iron pickaxe.
					boolean deep = this.pickaxeTier() >= 3;
					this.mineOre(level, s -> s.is(ConventionalBlockTags.IRON_ORES) || s.is(ConventionalBlockTags.COAL_ORES)
						|| (deep && s.is(ConventionalBlockTags.DIAMOND_ORES)), deep ? DIAMOND_DEPTH : IRON_DEPTH);
					return;
				}
				boolean wantCoal = this.furnace.fuelAvailable(this.clone) < this.count(s -> s.is(Items.RAW_IRON)) + 1;
				this.mineOre(level, wantCoal ? s -> s.is(ConventionalBlockTags.COAL_ORES) || s.is(ConventionalBlockTags.IRON_ORES)
					: s -> s.is(ConventionalBlockTags.IRON_ORES) || s.is(ConventionalBlockTags.COAL_ORES), IRON_DEPTH);
			}
			case DIAMONDS -> this.mineOre(level, s -> s.is(ConventionalBlockTags.DIAMOND_ORES), DIAMOND_DEPTH);
			case OBSIDIAN -> {
				BlockPos obsidian = this.findObsidian(level);
				if (obsidian != null) {
					this.digToward(level, obsidian);
				}
			}
			case PEARLS -> this.hunt(level, Set.of(EntityTypes.ENDERMAN), 32);
			case BLAZE_RODS -> this.hunt(level, Set.of(EntityTypes.BLAZE), 32);
			case EXPLORE -> this.explore(level);
			case CRAFT -> this.goToTable(level);
			case PACK_TABLE -> this.approachAndMine(level, this.placedTable);
			case STORE -> this.storeSpares(level);
			case FETCH -> this.fetchFromStorage(level);
			case DONE -> {
				if (!this.announcedDone) {
					this.announcedDone = true;
					this.clone.say("I've got everything I need to beat the Ender Dragon!", this.clone.getOwnerPlayer());
				}
			}
			case FIND_ANIMALS, WAITING -> this.explore(level);
			case PLANT -> this.plantTree(level);
			case TREES_GROWING -> this.tendTrees(level);
			case SAPLINGS -> this.approachAndMine(level, this.target != null && level.getBlockState(this.target).is(BlockTags.LEAVES) ? this.target
				: this.findNaturalLeaves(level));
			case TRADE -> this.tradeWithMerchant(level);
			case SLEEP -> this.goToBed(level);
			case NEED -> {
			}
			case SMELTING, STOCKED -> {
			}
		}
	}

	// ================================================================ crafting

	/**
	 * Makes the next things the clone needs that it can already afford, most important first. 2x2 recipes (planks,
	 * sticks, a crafting table, Eyes of Ender) work anywhere; everything else only next to a placed crafting table.
	 * When it's finished at a table it put down itself, it picks the table back up.
	 */
	public void craftWhatWeCan() {
		if (!(this.clone.level() instanceof ServerLevel level)) {
			return;
		}
		boolean atTable = this.tableInReach(level);
		if (!this.has(Items.CRAFTING_TABLE) && this.placedTable == null && !atTable && this.findTable(level) == null) {
			ProgressionCrafting.make(this.clone, ProgressionCrafting.CRAFTING_TABLE, false);
		}
		if (atTable) {
			// Keep going while it can make something: each thing made can open up the next (stone pickaxe, then furnace...).
			for (int pass = 0; pass < 12; pass++) {
				boolean made = false;
				for (Recipe recipe : this.wantedTableRecipes()) {
					made |= ProgressionCrafting.make(this.clone, recipe, true);
				}
				if (!made) {
					break;
				}
			}
		}
		int eyes = this.count(s -> s.is(Items.ENDER_EYE));
		while (eyes < TARGET_EYES && this.has(Items.ENDER_PEARL)) {
			if (!this.has(Items.BLAZE_POWDER) && !ProgressionCrafting.craft(this.clone, ProgressionCrafting.BLAZE_POWDER, false)) {
				break;
			}
			if (!ProgressionCrafting.craft(this.clone, ProgressionCrafting.EYE_OF_ENDER, false)) {
				break;
			}
			eyes++;
		}
	}

	/** What the clone still wants from a crafting table, most important first. Helpers only want the tools for their job. */
	private List<Recipe> wantedTableRecipes() {
		List<Recipe> wanted = this.gearRecipes();
		if (this.wantChest()) {
			// After the bed/tools: a chest is only made from wood that's spare after those.
			wanted.add(ProgressionCrafting.CHEST);
		}
		return wanted;
	}

	/** The tools, armour, furnace and bed it still wants (everything from a table except a storage chest). */
	private List<Recipe> gearRecipes() {
		List<Recipe> wanted = new ArrayList<>();
		boolean progress = this.role == Role.PROGRESS;
		boolean miner = this.role == Role.MINER;
		if ((progress || miner) && this.pickaxeTier() < 1) {
			wanted.add(ProgressionCrafting.WOODEN_PICKAXE);
		}
		if ((progress || miner) && this.pickaxeTier() < 2) {
			wanted.add(ProgressionCrafting.STONE_PICKAXE);
		}
		// Every clone gets a sword before going out: stone if it has the cobblestone, otherwise wood (upgraded later).
		int sword = this.swordTier();
		if (sword < 2) {
			wanted.add(ProgressionCrafting.STONE_SWORD);
		}
		if (sword < 1) {
			wanted.add(ProgressionCrafting.WOODEN_SWORD);
		}
		if ((progress || miner) && !this.has(Items.FURNACE) && this.pickaxeTier() >= 2) {
			wanted.add(ProgressionCrafting.FURNACE);
		}
		if (progress && !this.hasBed()) {
			wanted.add(ProgressionCrafting.BED);
		}
		// Iron gear one piece at a time, in order (pickaxe first), so early iron isn't spent on a shovel.
		List<Recipe> iron = this.missingIronGear();
		if (!iron.isEmpty()) {
			wanted.add(iron.get(0));
		}
		if (progress && this.pickaxeTier() < 4) {
			wanted.add(ProgressionCrafting.DIAMOND_PICKAXE);
		}
		return wanted;
	}

	/** Something it wants from a crafting table that it can already afford, or null. */
	private @Nullable Recipe firstAffordableTableRecipe() {
		for (Recipe recipe : this.wantedTableRecipes()) {
			if (ProgressionCrafting.affordable(this.clone, recipe)) {
				return recipe;
			}
		}
		return null;
	}

	/**
	 * A storage chest is worth making when there isn't one nearby: for a helper straight away (its job is to fill it),
	 * for the clone going for the dragon once its inventory is filling up.
	 */
	private boolean wantChest() {
		return (this.role != Role.PROGRESS || this.freeSlots() <= 8) && !this.has(Items.CHEST)
			&& this.planksHave() >= this.planksNeeded() + 8
			&& this.clone.level() instanceof ServerLevel level && CloneStorage.nearest(level, this.clone.blockPosition(), STORAGE_RANGE, null) == null;
	}

	private boolean tableInReach(final ServerLevel level) {
		if (this.knownTable != null && level.isLoaded(this.knownTable) && level.getBlockState(this.knownTable).is(Blocks.CRAFTING_TABLE)
			&& this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(this.knownTable)) <= REACH_SQ) {
			return true;
		}
		BlockPos near = this.findBlock(level, s -> s.is(Blocks.CRAFTING_TABLE), 4, 3, 3);
		if (near != null && this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(near)) <= REACH_SQ) {
			this.knownTable = near;
			return true;
		}
		return false;
	}

	private @Nullable BlockPos findTable(final ServerLevel level) {
		if (this.knownTable != null && level.isLoaded(this.knownTable) && level.getBlockState(this.knownTable).is(Blocks.CRAFTING_TABLE)
			&& !this.failed.contains(this.knownTable) && this.knownTable.distSqr(this.clone.blockPosition()) <= 16 * 16) {
			return this.knownTable;
		}
		this.knownTable = this.findBlock(level, s -> s.is(Blocks.CRAFTING_TABLE), 12, 4, 4);
		return this.knownTable;
	}

	/** Walks to the nearest crafting table, or puts its own down if there isn't one about. */
	private void goToTable(final ServerLevel level) {
		BlockPos table = this.findTable(level);
		if (table == null) {
			if (this.has(Items.CRAFTING_TABLE)) {
				BlockPos spot = this.findPlaceSpot(level);
				if (spot != null) {
					level.setBlock(spot, Blocks.CRAFTING_TABLE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
					level.playSound(null, spot, net.minecraft.sounds.SoundEvents.WOOD_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
					this.clone.getInventory().consumeOne(s -> s.is(Items.CRAFTING_TABLE));
					this.clone.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
					this.placedTable = spot;
					this.knownTable = spot;
				} else {
					this.makeRoom(level);
				}
			}
			return;
		}
		if (this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(table)) <= REACH_SQ) {
			this.clone.getLookControl().setLookAt(Vec3.atCenterOf(table));
			return;
		}
		if (!this.giveUpIfStuck(table)) {
			this.clone.getNavigation().moveTo(table.getX() + 0.5, table.getY(), table.getZ() + 0.5, 1.0);
		}
	}

	/**
	 * Boxed in (say, in a narrow tunnel) with nowhere to put a table or chest: dig out the block beside it, and the one
	 * above that, so there's a spot with room above it.
	 */
	private void makeRoom(final ServerLevel level) {
		BlockPos feet = this.clone.blockPosition();
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos side = feet.relative(dir);
			if (!level.isLoaded(side) || !level.getBlockState(side.below()).isFaceSturdy(level, side.below(), Direction.UP)) {
				continue;
			}
			for (BlockPos pos : new BlockPos[]{side, side.above()}) {
				BlockState state = level.getBlockState(pos);
				if (!this.passable(level, pos, state)) {
					if (this.safeToMine(level, pos, state)) {
						this.mine(level, pos);
						return;
					}
					break;
				}
			}
		}
	}

	/** An empty spot next to the clone, on solid ground with room above, to put a crafting table or chest. */
	private @Nullable BlockPos findPlaceSpot(final ServerLevel level) {
		BlockPos feet = this.clone.blockPosition();
		for (int r = 1; r <= 2; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy : new int[]{0, 1, -1}) {
						BlockPos pos = feet.offset(dx, dy, dz);
						if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
							continue;
						}
						BlockState state = level.getBlockState(pos);
						if (!state.canBeReplaced() || !state.getFluidState().isEmpty()
							|| !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
							|| !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
							|| this.clone.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))) {
							continue;
						}
						boolean nextToChest = false;
						for (Direction side : Direction.Plane.HORIZONTAL) {
							nextToChest |= level.getBlockState(pos.relative(side)).getBlock() instanceof net.minecraft.world.level.block.ChestBlock;
						}
						if (!nextToChest) {
							return pos;
						}
					}
				}
			}
		}
		return null;
	}

	// ================================================================ Clone Storage

	private int freeSlots() {
		int free = 0;
		for (ItemStack s : this.clone.getInventory().main) {
			if (s.isEmpty()) {
				free++;
			}
		}
		return free;
	}

	/**
	 * How many of this kind of item the clone wants to keep on it. The rest can go in storage. Helpers keep only what
	 * their own job needs, so everything they gather ends up in storage for the clone going for the dragon.
	 */
	private int keepLimit(final ItemStack s) {
		boolean progress = this.role == Role.PROGRESS;
		if (s.isDamageableItem() || s.has(DataComponents.EQUIPPABLE) || s.is(Items.BUCKET) || s.is(Items.WATER_BUCKET) || s.is(Items.EMERALD)) {
			return Integer.MAX_VALUE;
		}
		if (s.is(Items.OBSIDIAN) || s.is(Items.ENDER_PEARL) || s.is(Items.ENDER_EYE) || s.is(Items.BLAZE_ROD) || s.is(Items.BLAZE_POWDER)) {
			return progress ? Integer.MAX_VALUE : 0;
		}
		if (s.is(Items.RAW_IRON)) {
			return progress ? Integer.MAX_VALUE : this.role == Role.MINER && this.pickaxeTier() < 3 ? 3 : 0;
		}
		if (s.is(Items.CRAFTING_TABLE) || s.is(Items.FURNACE) || s.is(Items.CHEST) || s.is(ItemTags.BEDS)) {
			return 1;
		}
		if (s.is(ItemTags.LOGS)) {
			return this.role == Role.LUMBERJACK ? 4 : (this.planksNeeded() + 3) / 4 + 2;
		}
		if (s.is(ItemTags.PLANKS)) {
			return this.planksNeeded() + 4;
		}
		if (s.is(Items.STICK)) {
			return this.sticksNeeded() + 2;
		}
		if (s.is(ItemTags.STONE_CRAFTING_MATERIALS)) {
			return progress ? Math.max(16, this.cobbleNeeded()) : this.cobbleNeeded();
		}
		if (s.is(ItemTags.COALS)) {
			if (progress) {
				return this.count(x -> x.is(Items.RAW_IRON)) / 8 + 4;
			}
			return this.role == Role.MINER && this.pickaxeTier() < 3 ? 2 : 0;
		}
		if (s.is(Items.IRON_INGOT)) {
			return this.ironNeeded();
		}
		if (s.is(Items.DIAMOND)) {
			return progress && this.pickaxeTier() < 4 ? 3 : 0;
		}
		if (s.is(ItemTags.WOOL)) {
			return progress && !this.hasBed() ? 3 : 0;
		}
		if (s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)) {
			return progress || this.role == Role.HUNTER ? 16 : 0;
		}
		// Saplings and bone meal grow more wood (the only way to get it in a superflat world).
		if (s.is(ItemTags.SAPLINGS)) {
			return this.role == Role.HUNTER ? 0 : 16;
		}
		if (s.is(Items.BONE_MEAL)) {
			return this.role == Role.HUNTER ? 0 : 32;
		}
		return 0;
	}

	/** How many items in each slot are spare. Food is kept up to 16 in total (8 for helpers); tools and gear never are. */
	private int[] spares() {
		int[] spare = new int[com.clonecraft.clone.CloneInventory.MAIN_SIZE];
		java.util.Map<Item, Integer> kept = new java.util.HashMap<>();
		int foodBudget = this.role == Role.PROGRESS ? 16 : 8;
		int foodKept = 0;
		for (int i = 0; i < spare.length; i++) {
			ItemStack s = this.clone.getInventory().main.get(i);
			if (s.isEmpty() || isJunk(s)) {
				continue;
			}
			int keep;
			if (s.has(DataComponents.FOOD)) {
				keep = Math.min(s.getCount(), Math.max(0, foodBudget - foodKept));
				foodKept += keep;
			} else {
				int already = kept.getOrDefault(s.getItem(), 0);
				keep = (int)Math.min(s.getCount(), Math.max(0L, (long)this.keepLimit(s) - already));
				kept.put(s.getItem(), already + keep);
			}
			spare[i] = s.getCount() - keep;
		}
		return spare;
	}

	private int spareCount() {
		int total = 0;
		for (int n : this.spares()) {
			total += n;
		}
		return total;
	}

	private boolean hasSurplus() {
		return this.spareCount() > 0;
	}

	/** Time to visit storage: the clone going for the dragon when it's nearly full, a helper once it has a load to deliver. */
	private boolean shouldStore() {
		if (this.role == Role.PROGRESS) {
			return this.freeSlots() <= 8 && this.hasSurplus();
		}
		int spare = this.spareCount();
		return spare > 0 && (this.freeSlots() <= 20 || spare >= 32);
	}

	/** Puts spare items in the nearest Clone Storage chest, placing a new one first if there isn't one about. */
	private void storeSpares(final ServerLevel level) {
		BlockPos chestPos = CloneStorage.nearest(level, this.clone.blockPosition(), STORAGE_RANGE, null);
		if (chestPos == null) {
			if (this.has(Items.CHEST)) {
				BlockPos spot = this.findPlaceSpot(level);
				if (spot != null) {
					Direction facing = Direction.getApproximateNearest(this.clone.getX() - (spot.getX() + 0.5), 0, this.clone.getZ() - (spot.getZ() + 0.5));
					CloneStorage.place(level, spot, facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
					this.clone.getInventory().consumeOne(s -> s.is(Items.CHEST));
					this.clone.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
				} else {
					this.makeRoom(level);
				}
			}
			return;
		}
		if (this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(chestPos)) > REACH_SQ) {
			if (!this.giveUpIfStuck(chestPos)) {
				this.clone.getNavigation().moveTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, 1.0);
			}
			return;
		}
		net.minecraft.world.Container chest = CloneStorage.at(level, chestPos);
		if (chest == null) {
			return;
		}
		this.openChest(level, chestPos);
		this.clone.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
		int[] spare = this.spares();
		for (int i = 0; i < spare.length; i++) {
			if (spare[i] <= 0) {
				continue;
			}
			ItemStack slot = this.clone.getInventory().main.get(i);
			ItemStack moving = slot.split(spare[i]);
			CloneStorage.put(chest, moving);
			if (!moving.isEmpty()) {
				// Chest full: whatever didn't fit goes back.
				slot.grow(moving.getCount());
			}
			if (slot.isEmpty()) {
				this.clone.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		this.clone.getInventory().syncHeld();
	}

	/** Looks for a nearby chest or barrel (Clone Storage, a bonus chest, your storage...) holding what's needed. Sets up a FETCH if there is one. */
	private boolean planFetch(final ServerLevel level, final Predicate<ItemStack> wanted, final int amount) {
		if (amount <= 0 || this.clone.tickCount < this.fetchCooldownUntil || this.freeSlots() == 0) {
			return false;
		}
		BlockPos chest = CloneStorage.nearestSource(level, this.clone.blockPosition(), STORAGE_RANGE, wanted);
		if (chest == null) {
			return false;
		}
		this.fetchFrom = chest;
		this.fetchWanted = wanted;
		this.fetchAmount = amount;
		return true;
	}

	private void fetchFromStorage(final ServerLevel level) {
		BlockPos chestPos = this.fetchFrom;
		net.minecraft.world.Container chest = chestPos == null ? null : CloneStorage.sourceAt(level, chestPos);
		if (chest == null || this.fetchWanted == null) {
			this.fetchCooldownUntil = this.clone.tickCount + 200;
			return;
		}
		if (this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(chestPos)) > REACH_SQ) {
			if (this.giveUpIfStuck(chestPos)) {
				this.fetchCooldownUntil = this.clone.tickCount + 600;
			} else {
				this.clone.getNavigation().moveTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, 1.0);
			}
			return;
		}
		this.openChest(level, chestPos);
		this.clone.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
		if (CloneStorage.take(chest, this.clone, this.fetchWanted, this.fetchAmount) == 0) {
			this.fetchCooldownUntil = this.clone.tickCount + 600;
		}
		this.fetchFrom = null;
	}

	/** Is this a better pickaxe, sword, axe or piece of armour than the clone has? */
	private boolean isUpgrade(final ItemStack stack) {
		if (stack.is(ItemTags.PICKAXES)) {
			return tierOf(stack) > this.pickaxeTier();
		}
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
			double best = 0;
			for (ItemStack s : this.clone.getInventory().main) {
				if (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES)) {
					best = Math.max(best, CloneEntity.weaponScore(s));
				}
			}
			return CloneEntity.weaponScore(stack) > best;
		}
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.slot().isArmor() && equippable.slot() != EquipmentSlot.BODY) {
			return CloneEntity.armorValue(stack, equippable.slot()) > CloneEntity.armorValue(this.clone.getItemBySlot(equippable.slot()), equippable.slot());
		}
		return false;
	}

	private static int tierOf(final ItemStack pickaxe) {
		if (pickaxe.is(Items.DIAMOND_PICKAXE) || pickaxe.is(Items.NETHERITE_PICKAXE)) {
			return 4;
		}
		if (pickaxe.is(Items.IRON_PICKAXE)) {
			return 3;
		}
		if (pickaxe.is(Items.STONE_PICKAXE) || pickaxe.is(Items.COPPER_PICKAXE)) {
			return 2;
		}
		return 1;
	}

	/** 0 none, 1 wood/gold, 2 stone, 3 iron, 4 diamond/netherite. */
	public int pickaxeTier() {
		if (this.hasAny(Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
			return 4;
		}
		if (this.has(Items.IRON_PICKAXE)) {
			return 3;
		}
		if (this.hasAny(Items.STONE_PICKAXE, Items.COPPER_PICKAXE)) {
			return 2;
		}
		return this.hasAny(Items.WOODEN_PICKAXE, Items.GOLDEN_PICKAXE) ? 1 : 0;
	}

	private boolean hasSword() {
		return this.count(s -> s.is(ItemTags.SWORDS)) > 0;
	}

	/** 0 none, 1 wood/gold, 2 stone/copper, 3 iron or better. */
	private int swordTier() {
		if (this.hasAny(Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD)) {
			return 3;
		}
		if (this.hasAny(Items.STONE_SWORD, Items.COPPER_SWORD)) {
			return 2;
		}
		return this.hasSword() ? 1 : 0;
	}

	private boolean hasBed() {
		return this.count(s -> s.is(ItemTags.BEDS)) > 0;
	}

	/** The iron gear the clone doesn't have yet (or something better). A Miner only wants the pickaxe; other helpers none. */
	public List<Recipe> missingIronGear() {
		List<Recipe> missing = new ArrayList<>();
		for (Recipe recipe : ProgressionCrafting.IRON_GEAR) {
			boolean wanted = this.role == Role.PROGRESS || (this.role == Role.MINER && recipe == ProgressionCrafting.IRON_PICKAXE);
			if (wanted && !this.hasIronOrBetter(recipe.result())) {
				missing.add(recipe);
			}
		}
		return missing;
	}

	private boolean hasIronOrBetter(final Item iron) {
		Item[] options = switch (BuiltInRegistries.ITEM.getKey(iron).getPath()) {
			case "iron_pickaxe" -> new Item[]{Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE};
			case "iron_sword" -> new Item[]{Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
			case "iron_axe" -> new Item[]{Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};
			case "iron_shovel" -> new Item[]{Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL};
			case "iron_helmet" -> new Item[]{Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET};
			case "iron_chestplate" -> new Item[]{Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE};
			case "iron_leggings" -> new Item[]{Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS};
			case "iron_boots" -> new Item[]{Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS};
			default -> new Item[]{iron};
		};
		if (this.hasAny(options)) {
			return true;
		}
		for (EquipmentSlot slot : com.clonecraft.clone.CloneInventory.ARMOR_SLOTS) {
			ItemStack worn = this.clone.getItemBySlot(slot);
			for (Item option : options) {
				if (worn.is(option)) {
					return true;
				}
			}
		}
		return false;
	}

	public int ironNeeded() {
		int total = 0;
		for (Recipe recipe : this.missingIronGear()) {
			total += recipe.ingredients().get(0).count();
		}
		return total;
	}

	/** Sticks for everything the clone still wants to make. */
	private int sticksNeeded() {
		int sticks = 0;
		for (Recipe recipe : this.gearRecipes()) {
			for (ProgressionCrafting.Ingredient ingredient : recipe.ingredients()) {
				if (ingredient.kind() == ProgressionCrafting.Ingredient.Kind.STICKS) {
					sticks += ingredient.count();
				}
			}
		}
		return sticks;
	}

	/** Planks for a crafting table, everything else it still wants to make (except a storage chest) and missing sticks. */
	private int planksNeeded() {
		int planks = 0;
		if (!this.has(Items.CRAFTING_TABLE) && this.placedTable == null) {
			planks += 4;
		}
		for (Recipe recipe : this.gearRecipes()) {
			if (recipe == ProgressionCrafting.CHEST) {
				continue;
			}
			for (ProgressionCrafting.Ingredient ingredient : recipe.ingredients()) {
				if (ingredient.kind() == ProgressionCrafting.Ingredient.Kind.PLANKS) {
					planks += ingredient.count();
				}
			}
		}
		int missingSticks = this.sticksNeeded() - this.count(s -> s.is(Items.STICK));
		if (missingSticks > 0) {
			planks += 2 * ((missingSticks + 3) / 4);
		}
		return planks;
	}

	private int planksHave() {
		return this.count(s -> s.is(ItemTags.PLANKS)) + this.count(s -> s.is(ItemTags.LOGS)) * 4;
	}

	/** Cobblestone for the stone pickaxe, stone sword and furnace it still wants. */
	private int cobbleNeeded() {
		int cobble = 0;
		for (Recipe recipe : this.gearRecipes()) {
			if (recipe == ProgressionCrafting.STONE_PICKAXE || recipe == ProgressionCrafting.STONE_SWORD || recipe == ProgressionCrafting.FURNACE) {
				cobble += recipe.ingredients().get(0).count();
			}
		}
		// A furnace is next once there's a stone pickaxe: get the stone for it while mining anyway.
		if (!this.has(Items.FURNACE) && (this.role == Role.PROGRESS || this.role == Role.MINER) && this.pickaxeTier() < 2) {
			cobble += 8;
		}
		return cobble;
	}

	private int count(final Predicate<ItemStack> test) {
		return ProgressionCrafting.count(this.clone, test);
	}

	private boolean has(final Item item) {
		return ProgressionCrafting.has(this.clone, item);
	}

	private boolean hasAny(final Item... items) {
		for (Item item : items) {
			if (this.has(item)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================ finding things (loaded chunks only)

	/** The nearest matching block in loaded chunks, skipping ones the clone already gave up on. */
	private @Nullable BlockPos findBlock(final ServerLevel level, final Predicate<BlockState> test, final int radius, final int down, final int up) {
		return this.findBlock(level, test, radius, down, up, pos -> true);
	}

	/** Like findBlock, with an extra check on the position (only run for blocks that already match). */
	private @Nullable BlockPos findBlock(final ServerLevel level, final Predicate<BlockState> test, final int radius, final int down, final int up,
		final Predicate<BlockPos> where) {
		BlockPos center = this.clone.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				pos.set(center.getX() + x, center.getY(), center.getZ() + z);
				if (!level.isLoaded(pos)) {
					continue;
				}
				for (int y = -down; y <= up; y++) {
					pos.setY(center.getY() + y);
					if (!level.isInWorldBounds(pos) || !test.test(level.getBlockState(pos)) || this.failed.contains(pos)) {
						continue;
					}
					double d = pos.distSqr(center);
					if (d < bestDist && where.test(pos)) {
						bestDist = d;
						best = pos.immutable();
					}
				}
			}
		}
		return best;
	}

	private boolean isLog(final ServerLevel level, final BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.LOGS);
	}

	private boolean isStone(final ServerLevel level, final BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.BASE_STONE_OVERWORLD);
	}

	/** The nearest log it can reach standing on the ground: at most 5 blocks above the ground under it. */
	private @Nullable BlockPos findLog(final ServerLevel level) {
		return this.findBlock(level, s -> s.is(BlockTags.LOGS), 16, 4, 8, pos -> this.reachableLog(level, pos));
	}

	private boolean reachableLog(final ServerLevel level, final BlockPos log) {
		// Down through the trunk, air (where lower logs were chopped) and leaves, to the ground a clone could stand on.
		BlockPos ground = log.below();
		for (int i = 0; i < 8; i++) {
			BlockState below = level.getBlockState(ground);
			if (!below.isAir() && !below.is(BlockTags.LOGS) && !below.is(BlockTags.LEAVES)) {
				break;
			}
			ground = ground.below();
		}
		// Standing on that ground, a clone reaches about 5 blocks up.
		return log.getY() - ground.getY() <= 5;
	}

	private @Nullable BlockPos findCrop(final ServerLevel level) {
		return this.findBlock(level, MineGoal::isMatureCrop, 16, 4, 4);
	}

	private @Nullable BlockPos findObsidian(final ServerLevel level) {
		return this.findBlock(level, s -> s.is(ConventionalBlockTags.NORMAL_OBSIDIANS), 12, 12, 12);
	}

	/** An animal worth hunting for the stores: only kinds with more than a few adults nearby, so some are left to breed. */
	private @Nullable LivingEntity findHuntable(final ServerLevel level) {
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		List<LivingEntity> animals = level.getEntitiesOfClass(LivingEntity.class, this.clone.getBoundingBox().inflate(24, 12, 24),
			e -> e.isAlive() && !e.isBaby() && FOOD_ANIMALS.contains(e.getType()) && level.isLoaded(e.blockPosition()));
		for (LivingEntity e : animals) {
			long sameKind = animals.stream().filter(o -> o.getType() == e.getType()).count();
			double d = e.distanceToSqr(this.clone);
			if (sameKind > SPARE_ANIMALS && d < bestDist) {
				bestDist = d;
				best = e;
			}
		}
		return best;
	}

	private @Nullable LivingEntity findMob(final ServerLevel level, final Set<EntityType<?>> types, final double range) {
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.clone.getBoundingBox().inflate(range, range / 2, range))) {
			if (!e.isAlive() || e.isBaby() || !types.contains(e.getType()) || !level.isLoaded(e.blockPosition())) {
				continue;
			}
			double d = e.distanceToSqr(this.clone);
			if (d < bestDist) {
				bestDist = d;
				best = e;
			}
		}
		return best;
	}

	// ================================================================ doing things

	private void hunt(final ServerLevel level, final Set<EntityType<?>> types, final double range) {
		LivingEntity mob = this.findMob(level, types, range);
		if (mob != null) {
			// The fighting AI takes over from here; the drops get collected afterwards.
			this.clone.setTarget(mob);
		}
	}

	/** Walks up to a block and mines it (used for trees and crops, which are on the surface). */
	private void approachAndMine(final ServerLevel level, final @Nullable BlockPos pos) {
		if (pos == null) {
			return;
		}
		if (!pos.equals(this.target)) {
			this.target = pos;
			this.resetProgress();
		}
		if (this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH_SQ) {
			if (this.lavaNextTo(level, pos)) {
				this.giveUp(pos);
			} else {
				this.mine(level, pos);
			}
			return;
		}
		// The path ran out and it's still out of reach (leaves or a slope in the way): clear a way to it like when
		// tunnelling, and if even that gets it nowhere for a few seconds, try something else rather than standing there.
		if (this.clone.getNavigation().isDone()) {
			double dist = this.clone.distanceToSqr(Vec3.atCenterOf(pos));
			if (dist < this.idleBestDist - 0.5) {
				this.idleBestDist = dist;
				this.idleNearTarget = 0;
			}
			this.idleNearTarget += THINK_INTERVAL;
			if (this.idleNearTarget >= 160) {
				this.idleNearTarget = 0;
				this.idleBestDist = Double.MAX_VALUE;
				this.giveUp(pos);
				return;
			}
			this.digToward(level, pos);
			return;
		}
		this.idleNearTarget = 0;
		this.idleBestDist = Double.MAX_VALUE;
		if (this.giveUpIfStuck(pos)) {
			return;
		}
		this.clone.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
	}

	private void mineOre(final ServerLevel level, final Predicate<BlockState> ore, final int depth) {
		if (this.target == null || !level.isLoaded(this.target) || !ore.test(level.getBlockState(this.target))) {
			this.target = this.findBlock(level, ore, 10, 10, 10);
			this.resetProgress();
		}
		if (this.target != null) {
			this.digToward(level, this.target);
		} else {
			this.branchMine(level, depth);
		}
	}

	/** Staircases down to the depth, then tunnels in a straight line, turning every 32 blocks. */
	private void branchMine(final ServerLevel level, final int depth) {
		BlockPos feet = this.clone.blockPosition();
		int y = Math.max(level.getMinY() + 6, depth);
		if (this.branchGoal == null || this.failed.contains(this.branchGoal) || feet.distManhattan(this.branchGoal) <= 2) {
			if (this.branchGoal != null && feet.distManhattan(this.branchGoal) <= 2) {
				this.digDir = this.digDir.getClockWise();
			}
			boolean descend = feet.getY() > y + 2 && this.clone.tickCount >= this.noDescendUntil;
			int length = descend ? Math.max(16, feet.getY() - y) : 32;
			BlockPos next = feet.relative(this.digDir, length);
			this.branchGoal = new BlockPos(next.getX(), descend ? y : feet.getY(), next.getZ());
			this.resetProgress();
		}
		this.digToward(level, this.branchGoal);
	}

	/**
	 * One step of digging toward a block: mines it if it's in reach, otherwise clears the next block of a tunnel
	 * (a staircase if the goal is far above or below) and walks into it. Turns aside from lava, water and bedrock.
	 */
	private void digToward(final ServerLevel level, final BlockPos goal) {
		if (!level.isLoaded(goal) || this.giveUpIfStuck(goal)) {
			return;
		}
		BlockPos feet = this.clone.blockPosition();
		BlockState goalState = level.getBlockState(goal);
		// Only mine the goal itself once it's in sight: mining it through stone would leave its drop sealed in a pocket.
		if (!this.passable(level, goal, goalState) && this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(goal)) <= REACH_SQ
			&& Math.abs(goal.getY() - feet.getY()) <= 3 && this.canSee(level, goal)) {
			if (this.safeToMine(level, goal, goalState)) {
				this.mine(level, goal);
			} else {
				this.giveUp(goal);
			}
			return;
		}
		int dx = goal.getX() - feet.getX();
		int dy = goal.getY() - feet.getY();
		int dz = goal.getZ() - feet.getZ();
		Direction dir = this.digDir;
		if (this.turns == 0) {
			if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
				dir = dx > 0 ? Direction.EAST : Direction.WEST;
			} else if (dz != 0) {
				dir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
			}
		}
		BlockPos front = feet.relative(dir);
		List<BlockPos> toClear;
		BlockPos stand;
		if (dy < -1 || (dx == 0 && dz == 0 && dy < 0)) {
			toClear = List.of(front.above(), front, front.below());
			stand = front.below();
		} else if (dy > 1) {
			toClear = List.of(feet.above(2), front.above(), front.above(2));
			stand = front.above();
		} else {
			toClear = List.of(front, front.above());
			stand = front;
		}
		for (BlockPos pos : toClear) {
			BlockState state = level.getBlockState(pos);
			if (!state.getFluidState().isEmpty()) {
				this.turn();
				return;
			}
			if (this.passable(level, pos, state)) {
				continue;
			}
			if (!this.safeToMine(level, pos, state)) {
				this.turn();
				return;
			}
			this.mine(level, pos);
			return;
		}
		if (this.dangerBelow(level, stand)) {
			this.turn();
			return;
		}
		this.turns = 0;
		if (!this.clone.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0)) {
			this.clone.getMoveControl().setWantedPosition(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0);
		}
	}

	private boolean canSee(final ServerLevel level, final BlockPos pos) {
		var hit = level.clip(new net.minecraft.world.level.ClipContext(this.clone.getEyePosition(), Vec3.atCenterOf(pos),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this.clone));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	private boolean passable(final ServerLevel level, final BlockPos pos, final BlockState state) {
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	/** Never mine into fluids (or next to them), bedrock, unloaded chunks, or blocks the clone can't harvest. */
	private boolean safeToMine(final ServerLevel level, final BlockPos pos, final BlockState state) {
		if (!level.isLoaded(pos) || state.getDestroySpeed(level, pos) < 0 || this.failed.contains(pos)) {
			return false;
		}
		for (Direction side : Direction.values()) {
			BlockPos next = pos.relative(side);
			if (!level.isLoaded(next) || !level.getBlockState(next).getFluidState().isEmpty()) {
				return false;
			}
		}
		return this.clone.selectBestToolFor(state, false);
	}

	private boolean lavaNextTo(final ServerLevel level, final BlockPos pos) {
		for (Direction side : Direction.values()) {
			BlockPos next = pos.relative(side);
			if (!level.isLoaded(next) || level.getBlockState(next).getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	/** Stops going after a block: remembers it as failed so it isn't picked again soon. */
	private void giveUp(final BlockPos pos) {
		this.failed.add(pos.immutable());
		if (pos.equals(this.target)) {
			this.target = null;
		}
		this.resetProgress();
	}

	/** Lava or a long drop under where the clone would step. */
	private boolean dangerBelow(final ServerLevel level, final BlockPos stand) {
		for (int i = 1; i <= 4; i++) {
			BlockPos below = stand.below(i);
			if (!level.isLoaded(below)) {
				return true;
			}
			BlockState state = level.getBlockState(below);
			if (!state.getFluidState().isEmpty() && !state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
				return true;
			}
			if (!state.getCollisionShape(level, below).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private void mine(final ServerLevel level, final BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return;
		}
		this.clone.getNavigation().stop();
		this.clone.mineGoal.request(pos, false, (p, state) -> this.failed.add(p.immutable()));
	}

	private void turn() {
		this.digDir = this.digDir.getClockWise();
		this.turns++;
		if (this.turns >= 4) {
			// Boxed in on every side: give up on this goal, and stop trying to go down for a while (tunnel sideways instead).
			if (this.target != null) {
				this.failed.add(this.target);
				this.target = null;
			}
			if (this.branchGoal != null) {
				this.failed.add(this.branchGoal);
				this.branchGoal = null;
			}
			this.noDescendUntil = this.clone.tickCount + 1200;
			this.digDir = this.digDir.getClockWise();
			this.turns = 0;
		}
	}

	/** Marks the goal as failed if the clone hasn't got any closer to it for a while. */
	private boolean giveUpIfStuck(final BlockPos goal) {
		double dist = this.clone.blockPosition().distSqr(goal);
		if (dist < this.bestDist - 0.5) {
			this.bestDist = dist;
			this.stuckTicks = 0;
			return false;
		}
		this.stuckTicks += THINK_INTERVAL;
		if (this.stuckTicks < GIVE_UP_TICKS) {
			return false;
		}
		this.failed.add(goal.immutable());
		if (goal.equals(this.target)) {
			this.target = null;
		}
		if (goal.equals(this.branchGoal)) {
			this.branchGoal = null;
			this.digDir = this.digDir.getClockWise();
		}
		this.resetProgress();
		return true;
	}

	private void resetProgress() {
		this.bestDist = Double.MAX_VALUE;
		this.stuckTicks = 0;
		this.turns = 0;
	}

	private void explore(final ServerLevel level) {
		// Not out in the open at night: that's when monsters are about.
		if (isNight(level) && this.outdoors(level)) {
			this.clone.getNavigation().stop();
			return;
		}
		// Not getting anywhere (say, wandered into a tree's leaves): break the leaves around it, like a player would.
		BlockPos feet = this.clone.blockPosition();
		if (feet.equals(this.exploreFrom)) {
			this.exploreStill += THINK_INTERVAL;
		} else {
			this.exploreFrom = feet;
			this.exploreStill = 0;
		}
		if (this.exploreStill >= 60 && this.breakFreeOfLeaves(level, feet)) {
			return;
		}
		if (!this.clone.getNavigation().isDone()) {
			return;
		}
		Vec3 pos = DefaultRandomPos.getPos(this.clone, 20, 7);
		if (pos != null && level.isLoaded(BlockPos.containing(pos))) {
			this.clone.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.0);
		}
	}

	/** Mines one leaf block at or around the clone (head height first, then feet, then below). Returns false if there's none. */
	private boolean breakFreeOfLeaves(final ServerLevel level, final BlockPos feet) {
		List<BlockPos> around = new ArrayList<>(List.of(feet.above(), feet));
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			around.add(feet.relative(dir).above());
			around.add(feet.relative(dir));
		}
		around.add(feet.below());
		for (BlockPos pos : around) {
			if (level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.LEAVES) && !this.failed.contains(pos)) {
				this.mine(level, pos);
				return true;
			}
		}
		return false;
	}

	// ================================================================ items

	/** Walks to nearby dropped items (logs, ores, meat...) so they get picked up. */
	private boolean collectDrops(final ServerLevel level) {
		if (this.collecting != null) {
			this.collectTicks += THINK_INTERVAL;
			if (!this.collecting.isAlive() || this.collectTicks > 120) {
				if (this.collecting.isAlive()) {
					this.ignoredItems.add(this.collecting.getUUID());
				}
				this.collecting = null;
			}
		}
		if (this.collecting == null) {
			if (this.ignoredItems.size() > 100) {
				this.ignoredItems.clear();
			}
			double bestDist = Double.MAX_VALUE;
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.clone.getBoundingBox().inflate(8, 4, 8))) {
				if (!item.isAlive() || item.getOwner() == this.clone || !DropClaims.mayTake(item, this.clone) || this.ignoredItems.contains(item.getUUID())
					|| !level.isLoaded(item.blockPosition())
					|| this.clone.getInventory().fitCount(item.getItem()) <= 0 || isJunk(item.getItem())) {
					continue;
				}
				double d = item.distanceToSqr(this.clone);
				if (d < bestDist) {
					bestDist = d;
					this.collecting = item;
				}
			}
			this.collectTicks = 0;
		}
		if (this.collecting == null) {
			return false;
		}
		this.clone.getNavigation().moveTo(this.collecting, 1.0);
		return true;
	}

	/** Blocks dug up along the way that a progressing clone has no use for. */
	public static boolean isJunk(final ItemStack stack) {
		return stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT) || stack.is(Items.GRAVEL) || stack.is(Items.SAND) || stack.is(Items.RED_SAND)
			|| stack.is(Items.ANDESITE) || stack.is(Items.DIORITE) || stack.is(Items.GRANITE) || stack.is(Items.TUFF) || stack.is(Items.CALCITE)
			|| stack.is(Items.NETHERRACK) || stack.is(Items.ROTTEN_FLESH) || stack.is(Items.POISONOUS_POTATO) || stack.is(Items.FLINT);
	}

	/** With 3 or fewer free slots, throws away junk and stone beyond what the clone still needs. */
	private void dropJunkIfFull(final ServerLevel level) {
		int free = 0;
		for (ItemStack s : this.clone.getInventory().main) {
			if (s.isEmpty()) {
				free++;
			}
		}
		if (free > 3) {
			return;
		}
		int stoneKept = 0;
		// Extra stone only gets thrown away when there's no Clone Storage to put it in.
		int stoneWanted = CloneStorage.nearest(level, this.clone.blockPosition(), STORAGE_RANGE, null) != null ? Integer.MAX_VALUE
			: Math.max(16, this.cobbleNeeded());
		for (int i = 0; i < com.clonecraft.clone.CloneInventory.MAIN_SIZE; i++) {
			ItemStack s = this.clone.getInventory().main.get(i);
			if (s.isEmpty() || i == this.clone.getInventory().selected) {
				continue;
			}
			boolean drop = isJunk(s);
			if (!drop && s.is(ItemTags.STONE_CRAFTING_MATERIALS)) {
				drop = stoneKept >= stoneWanted;
				stoneKept += s.getCount();
			}
			if (drop) {
				this.clone.getInventory().setItem(i, ItemStack.EMPTY);
				ItemEntity thrown = this.clone.spawnAtLocation(level, s);
				if (thrown != null) {
					thrown.setThrower(this.clone);
					thrown.setPickUpDelay(40);
				}
			}
		}
	}

	// ================================================================ saving

	public void save(final ValueOutput output) {
		this.furnace.save(output);
		output.putInt("ProgressionDigDir", this.digDir.get3DDataValue());
		output.putBoolean("ProgressionDone", this.announcedDone);
		if (this.placedTable != null) {
			output.store("ProgressionTable", BlockPos.CODEC, this.placedTable);
		}
	}

	public void load(final ValueInput input) {
		this.furnace.load(input);
		Direction dir = Direction.from3DDataValue(input.getIntOr("ProgressionDigDir", Direction.NORTH.get3DDataValue()));
		this.digDir = dir.getAxis().isHorizontal() ? dir : Direction.NORTH;
		this.announcedDone = input.getBooleanOr("ProgressionDone", false);
		this.placedTable = input.read("ProgressionTable", BlockPos.CODEC).orElse(null);
	}
}
