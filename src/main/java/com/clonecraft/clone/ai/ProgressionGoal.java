package com.clonecraft.clone.ai;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.DropClaims;
import com.clonecraft.clone.learning.MiningBrain;
import com.clonecraft.clone.learning.MiningMove;
import com.clonecraft.clone.progression.CloneStorage;
import com.clonecraft.clone.progression.CloneTalk;
import com.clonecraft.clone.progression.HomeBase;
import com.clonecraft.clone.progression.MineStairs;
import com.clonecraft.clone.progression.CloneTrading;
import com.clonecraft.clone.progression.ProgressionCrafting;
import com.clonecraft.clone.progression.ProgressionCrafting.Recipe;
import com.clonecraft.entity.CloneEntity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
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
		SURFACE(0, "Getting out of the cave"),
		HOME(0, "Building our house"),
		GO_HOME(0, "Heading home for the night"),
		DELIVER(0, "Bringing supplies"),
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
	/** Where it's heading to get out of a cave, and whether it has given up on walking there and is digging. */
	private @Nullable BlockPos surfaceGoal;
	private boolean diggingOut;
	private @Nullable BlockPos walkOutFrom;
	/** While digging out of a cave: only dig through natural ground. */
	private boolean naturalOnly;
	/** The steps of the staircases it has walked lately: never mined, so it can always walk back. */
	private final LinkedHashSet<BlockPos> steps = new LinkedHashSet<>();
	private @Nullable BlockPos lastFoothold;
	/** This mining trip has got down to its depth (after that, the mining brain decides about going deeper or higher). */
	private boolean reachedDepth;
	/** The house: when it next looks for a site or asks for blocks, spots it couldn't get to, what it's on now, and tonight's plan. */
	private int nextHomeSearch;
	private int nextHomeAsk;
	private final Map<BlockPos, Integer> homeSkipUntil = new HashMap<>();
	private @Nullable BlockPos homeTarget;
	private int homeTargetSince;
	private long decidedNight = -1L;
	private boolean goingHome;
	private @Nullable BlockPos homeWalkFrom;
	private int homeWalkStuck;
	/** Taking something to another clone. */
	private @Nullable UUID bringTo;
	private @Nullable Predicate<ItemStack> bringWhat;
	private int bringAmount;
	private int bringSince;
	/** The rest of the current stretch of mining (strip mining is a list of side tunnels), and walking into a cave. */
	private final java.util.ArrayDeque<BlockPos> stretchPlan = new java.util.ArrayDeque<>();
	private boolean walkingToCave;
	private @Nullable BlockPos caveWalkFrom;
	private int caveWalkStuck;
	/** The shared staircase it's walking down, if any, and ones it found blocked. */
	private MineStairs.@Nullable Stairs takingStairs;
	private final Set<MineStairs.Stairs> brokenStairs = new HashSet<>();
	/** While digging a staircase down: where it started, its steps so far, and the shared one it's making longer. */
	private @Nullable BlockPos stairsTop;
	private final List<BlockPos> stairsSteps = new ArrayList<>();
	private MineStairs.@Nullable Stairs extending;
	private int walkOutStuck;
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
		// And ask the other clones (now and then).
		this.askTeammates(what, wanted, 16);
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
		// On a superflat world there's nothing underground to find, so only dig for something it can sense, unless
		// it's already down in real rock (somewhere a player built up, say), where mining on makes sense.
		return !level.isFlat() || this.findBlock(level, what, 10, 10, 4) != null || this.belowGround(level);
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
		this.rememberStep(level);
		this.updateHomeNeeds(level);
		this.dropJunkIfFull(level);
		this.craftWhatWeCan();
		if (this.collectDrops(level)) {
			return;
		}
		Objective next = this.plan(level);
		// Count the ore it has finished digging out this stretch of tunnel.
		this.clone.mining.tick(level, pos -> isOre(level.getBlockState(pos)));
		if (next != this.objective) {
			// A stretch of mining ends when it stops mining (to eat, craft, go up for wood...).
			this.clone.mining.end(level);
			this.reachedDepth = false;
			this.stretchPlan.clear();
			this.walkingToCave = false;
			this.takingStairs = null;
			this.stairsTop = null;
			this.extending = null;
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

	/** Things that can only be done up on the surface (trees, animals, crops, villagers...). */
	private static final Set<Objective> NEEDS_SURFACE = EnumSet.of(Objective.WOOD, Objective.WOOL, Objective.FOOD, Objective.FIND_ANIMALS,
		Objective.PLANT, Objective.TREES_GROWING, Objective.SAPLINGS, Objective.EXPLORE, Objective.TRADE);

	private Objective plan(final ServerLevel level) {
		Objective work = this.planWork(level);
		// Down a cave (or a pit) with work to do up top: get out first, rather than wandering round underground.
		if (work == Objective.SURFACE || NEEDS_SURFACE.contains(work) && this.belowGround(level)) {
			return Objective.SURFACE;
		}
		this.surfaceGoal = null;
		this.diggingOut = false;
		return work;
	}

	private Objective planWork(final ServerLevel level) {
		boolean overworld = level.dimension() == Level.OVERWORLD;
		boolean canFight = this.clone.hasOption(CloneOption.FIGHT);

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

		// Taking something another clone asked for.
		if (this.bringTo != null) {
			return Objective.DELIVER;
		}

		// Evening, up on the surface: it might head home for the night (to sleep, or drop its things in the chest).
		// Already there with holes to fix: fix them first, then go in.
		if (this.headingHomeTonight(level)) {
			HomeBase.Home home = this.home(level);
			if (home != null && home.bounds().inflate(6.0).contains(this.clone.position()) && this.homeJob(level) != null) {
				return Objective.HOME;
			}
			return Objective.GO_HOME;
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
		// (Only with a table to go to or put down: otherwise it would wait for one forever.)
		if (this.firstAffordableTableRecipe() != null && !this.tableInReach(level)
			&& (this.has(Items.CRAFTING_TABLE) || this.findTable(level) != null)) {
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

		// Off to get stone for the house: that first, rather than pottering about the house with nothing to build with.
		if (this.gatheringForHome) {
			Objective gather = this.gatherForHome(level);
			if (gather != null) {
				return gather;
			}
		}
		// Up on the surface with what it takes: build (or repair) the house that's home base for its owner's clones.
		if (this.homeJob(level) != null) {
			return Objective.HOME;
		}
		// Its bed in the house is missing and it hasn't got one: take one from a chest before trying to make one.
		if (this.myHomeBedMissing && !this.hasBed() && this.planFetch(level, s -> s.is(ItemTags.BEDS), 1)) {
			return Objective.FETCH;
		}
		// The house needs blocks and it hasn't got any: take some from a chest (the helpers put theirs in the house's).
		if (this.homeWantsBlocks && this.home(level) != null && this.spareBuildingBlocks() < 8 && !this.belowGround(level)
			&& this.planFetch(level, ProgressionGoal::isBuildingBlock, 64)) {
			return Objective.FETCH;
		}
		// None in the chests either: go and mine some stone for it (a good load, then come back up and build).
		Objective forHome = this.gatherForHome(level);
		if (forHome != null) {
			return forHome;
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
		if (this.wantsBed() && this.count(s -> s.is(ItemTags.WOOL)) < 3 && canFight && this.findMob(level, Set.of(EntityTypes.SHEEP), 24) != null) {
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
				if (this.leaveHomeToDig(level)) {
					return;
				}
				if (this.target == null || !this.isStone(level, this.target)) {
					this.target = this.findBlock(level, s -> s.is(BlockTags.BASE_STONE_OVERWORLD), 8, 8, 2, pos -> !this.isStep(level, pos));
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
			case SURFACE -> this.getOut(level);
			case HOME -> this.buildHome(level);
			case GO_HOME -> this.goHome(level);
			case DELIVER -> this.deliver(level);
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
		// For the house: a second crafting table to leave there, and torches (both made in its hands).
		if (this.homeMissingNow.contains(HomeBase.Part.TABLE) && this.count(s -> s.is(Items.CRAFTING_TABLE)) == 1
			&& this.count(s -> s.is(ItemTags.PLANKS)) >= this.planksNeeded() + 4) {
			ProgressionCrafting.make(this.clone, ProgressionCrafting.CRAFTING_TABLE, false);
		}
		if ((this.homeMissingNow.contains(HomeBase.Part.TORCH) || this.homeMissingNow.contains(HomeBase.Part.OUTER_TORCH)) && !this.has(Items.TORCH)) {
			ProgressionCrafting.make(this.clone, ProgressionCrafting.TORCH, false);
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
		this.addHomeRecipes(wanted);
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
		if (this.wantsBed()) {
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
			return this.planksNeeded() + 4 + (this.homeWantsBlocks ? 64 : 0);
		}
		if (s.is(Items.STICK)) {
			return this.sticksNeeded() + 2;
		}
		if (s.is(ItemTags.STONE_CRAFTING_MATERIALS)) {
			// Plus plenty for the house, while it still needs building (or mending).
			return (progress ? Math.max(16, this.cobbleNeeded()) : this.cobbleNeeded()) + (this.homeWantsBlocks ? 64 : 0);
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
		this.storeInto(level, chestPos);
	}

	/** Walks to the chest and puts its spare items in. Returns true once it's done that. */
	private boolean storeInto(final ServerLevel level, final BlockPos chestPos) {
		if (this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(chestPos)) > REACH_SQ) {
			if (!this.giveUpIfStuck(chestPos)) {
				this.clone.getNavigation().moveTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, 1.0);
			}
			return false;
		}
		net.minecraft.world.Container chest = CloneStorage.at(level, chestPos);
		if (chest == null) {
			return false;
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
		return true;
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

	/**
	 * Remembers the steps of the staircases it walks: where it went up or down a block, the floor before and after
	 * the change. (Flat floor doesn't matter: digging it away leaves a hole only one block deep.)
	 */
	/**
	 * Never dug when mining: a step of one of its own staircases or a shared mine staircase (the way back), or part of
	 * a house or the ground right under or around one (no one's mine goes through a house). Building the house digs
	 * its own way, not through this.
	 */
	private boolean isStep(final ServerLevel level, final BlockPos pos) {
		return this.steps.contains(pos) || MineStairs.isStep(level, pos) || HomeBase.protects(level, pos);
	}

	private void rememberStep(final ServerLevel level) {
		if (!this.clone.onGround()) {
			return;
		}
		BlockPos foothold = this.clone.blockPosition().below().immutable();
		if (this.lastFoothold != null && foothold.getY() != this.lastFoothold.getY() && foothold.closerThan(this.lastFoothold, 3.0)) {
			for (BlockPos step : List.of(this.lastFoothold, foothold)) {
				this.steps.remove(step);
				this.steps.add(step);
				// Digging a staircase down to go mining: these are its steps, for other clones to use too.
				if (this.stairsTop != null && !this.stairsSteps.contains(step)) {
					this.stairsSteps.add(step);
				}
			}
			// Shared as it's dug (every few steps, once it's deep enough to be worth taking), not just once it's finished.
			if (this.stairsTop != null && this.stairsTop.getY() - foothold.getY() >= 6 && this.stairsSteps.size() % 4 == 0) {
				this.publishStairs(level, this.clone.blockPosition());
			}
			while (this.steps.size() > 256) {
				this.steps.remove(this.steps.iterator().next());
			}
		}
		this.lastFoothold = foothold;
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

	/**
	 * The way down at the start of a mining trip: walks down a staircase another clone (or it) dug before if there's
	 * one nearby, and otherwise shares the staircase it digs on its way down. Returns true while walking down stairs.
	 */
	private boolean headDown(final ServerLevel level, final int depth) {
		int y = Math.max(level.getMinY() + 6, depth);
		BlockPos feet = this.clone.blockPosition();
		if (this.takingStairs != null) {
			if (this.walkDownStairs(level, feet)) {
				return true;
			}
			feet = this.clone.blockPosition();
		}
		if (!this.reachedDepth && feet.getY() <= y + 2) {
			this.reachedDepth = true;
			this.finishStairs(level, feet);
		}
		if (!this.reachedDepth && this.stairsTop == null && feet.getY() > y + 8) {
			MineStairs.Stairs stairs = MineStairs.down(level, feet, y, this.brokenStairs);
			if (stairs != null) {
				this.takingStairs = stairs;
				this.clone.showMessage("Taking the mine stairs down");
				return true;
			}
			// None to use: the staircase it's about to dig is one for the others.
			this.stairsTop = feet;
			this.stairsSteps.clear();
			this.extending = null;
		}
		return false;
	}

	/**
	 * Setting off to mine from in or right by the house: walks a few blocks away from it first, so the staircase
	 * down starts out in the open, not through the floor. Returns true while doing that.
	 */
	private boolean leaveHomeToDig(final ServerLevel level) {
		HomeBase.Home home = this.home(level);
		if (home == null || this.belowGround(level)) {
			return false;
		}
		AABB near = home.bounds().inflate(4.0, 0.0, 4.0).expandTowards(0.0, -4.0, 0.0);
		if (!near.contains(this.clone.position())) {
			return false;
		}
		Vec3 centre = near.getCenter();
		Vec3 away = this.clone.position().subtract(centre);
		away = new Vec3(away.x, 0.0, away.z);
		if (away.lengthSqr() < 0.01) {
			away = new Vec3(1.0, 0.0, 0.0);
		}
		double out = Math.max(near.getXsize(), near.getZsize()) / 2.0 + 4.0;
		Vec3 to = centre.add(away.normalize().scale(out));
		if (this.clone.getNavigation().isDone() || this.clone.tickCount % 40 == 0) {
			this.clone.getNavigation().moveTo(to.x, this.clone.getY(), to.z, 1.0);
		}
		return true;
	}

	private void mineOre(final ServerLevel level, final Predicate<BlockState> ore, final int depth) {
		if (this.leaveHomeToDig(level)) {
			return;
		}
		if (this.headDown(level, depth)) {
			return;
		}
		if (this.target == null || !level.isLoaded(this.target) || !ore.test(level.getBlockState(this.target))) {
			this.target = this.findVisibleOre(level, ore);
			this.resetProgress();
		}
		if (this.target != null) {
			this.digToward(level, this.target);
		} else {
			this.branchMine(level, depth);
		}
	}

	/**
	 * The nearest ore it can actually see, like a player: within 10 blocks, with a face open to the air (in a tunnel
	 * wall, a cave, a ravine) and nothing in the way. Ore still buried in the rock is found by digging, not sensed.
	 */
	private @Nullable BlockPos findVisibleOre(final ServerLevel level, final Predicate<BlockState> ore) {
		return this.findBlock(level, ore, 10, 10, 10, pos -> !this.isStep(level, pos) && this.canSeeAFace(level, pos));
	}

	/** It can see one of the block's open faces (looking at the face, not the middle, which the blocks beside it hide). */
	private boolean canSeeAFace(final ServerLevel level, final BlockPos pos) {
		Vec3 eyes = this.clone.getEyePosition();
		for (Direction side : Direction.values()) {
			BlockPos next = pos.relative(side);
			BlockState state = level.getBlockState(next);
			if (!state.getCollisionShape(level, next).isEmpty() || !state.getFluidState().isEmpty()) {
				continue;
			}
			Vec3 face = Vec3.atCenterOf(pos).add(side.getStepX() * 0.49, side.getStepY() * 0.49, side.getStepZ() * 0.49);
			var hit = level.clip(new net.minecraft.world.level.ClipContext(eyes, face, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this.clone));
			if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(pos)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Staircases down to the depth, then mines in stretches of tunnel. When going for iron or diamonds, the clones'
	 * mining brain picks how each stretch goes (straight on, off to the side, deeper, higher, or to a cave), and
	 * learns from how much ore each turns up.
	 */
	private void branchMine(final ServerLevel level, final int depth) {
		BlockPos feet = this.clone.blockPosition();
		int y = Math.max(level.getMinY() + 6, depth);
		boolean learn = this.objective == Objective.IRON || this.objective == Objective.DIAMONDS;
		if (feet.getY() <= y + 2) {
			this.reachedDepth = true;
		}
		// Walking (not digging) into a cave: let it get there, unless it's stopped getting anywhere.
		if (this.walkingToCave) {
			if (feet.equals(this.caveWalkFrom)) {
				this.caveWalkStuck += THINK_INTERVAL;
			} else {
				this.caveWalkFrom = feet;
				this.caveWalkStuck = 0;
			}
			if (!this.clone.getNavigation().isDone() && this.caveWalkStuck < 100) {
				return;
			}
			this.walkingToCave = false;
		}
		boolean timeUp = this.clone.mining.inStretch() && this.clone.mining.stretchTicks() > STRETCH_TICKS;
		boolean arrived = this.branchGoal != null && feet.distManhattan(this.branchGoal) <= (this.stretchPlan.isEmpty() ? 2 : 1);
		if (!timeUp && arrived && !this.stretchPlan.isEmpty()) {
			// The next part of the stretch (strip mining: the next side tunnel, or back to the main one).
			this.branchGoal = this.stretchPlan.poll();
			this.resetProgress();
		} else if (timeUp || arrived || this.branchGoal == null || this.failed.contains(this.branchGoal)) {
			this.stretchPlan.clear();
			boolean descend = !this.reachedDepth && feet.getY() > y + 2 && this.clone.tickCount >= this.noDescendUntil;
			if (descend || !learn) {
				if (arrived) {
					this.digDir = this.digDir.getClockWise();
				}
				int length = descend ? Math.max(16, feet.getY() - y) : 32;
				BlockPos next = feet.relative(this.digDir, length);
				this.branchGoal = new BlockPos(next.getX(), descend ? y : feet.getY(), next.getZ());
			} else {
				this.branchGoal = this.startStretch(level, feet, depth);
				if (this.walkingToCave) {
					return;
				}
			}
			this.resetProgress();
		}
		this.digToward(level, this.branchGoal);
	}

	/**
	 * One step of walking down a shared staircase. Returns true while still on the way; once at the bottom it carries
	 * on from there (digging the staircase further down if it needs to go deeper, which makes the shared one longer).
	 */
	private boolean walkDownStairs(final ServerLevel level, final BlockPos feet) {
		MineStairs.Stairs stairs = this.takingStairs;
		if (stairs == null) {
			return false;
		}
		if (feet.closerThan(stairs.bottom(), 2.5)) {
			this.takingStairs = null;
			this.clone.getNavigation().stop();
			// Down here, each clone tunnels off its own way rather than all mining the same tunnel.
			this.digDir = Direction.from2DDataValue(Math.floorMod(this.clone.getId(), 4));
			this.branchGoal = null;
			this.stairsTop = stairs.top();
			this.stairsSteps.clear();
			this.stairsSteps.addAll(stairs.steps());
			this.extending = stairs;
			return false;
		}
		if (feet.equals(this.walkOutFrom)) {
			this.walkOutStuck += THINK_INTERVAL;
		} else {
			this.walkOutFrom = feet;
			this.walkOutStuck = 0;
		}
		if (this.clone.getNavigation().isDone()) {
			net.minecraft.world.level.pathfinder.Path path = this.clone.getNavigation().createPath(stairs.bottom(), 1);
			if (path != null && !path.canReach()) {
				// Blocked or dug away: forget it and dig its own.
				MineStairs.remove(level, stairs);
				this.takingStairs = null;
				return false;
			} else if (path != null) {
				this.clone.getNavigation().moveTo(path, 1.0);
			}
		}
		if (this.walkOutStuck >= 200) {
			this.brokenStairs.add(stairs);
			this.takingStairs = null;
			this.walkOutStuck = 0;
			return false;
		}
		return true;
	}

	/** Got down to mining depth: if it dug (or extended) a staircase on the way, the other clones can use it now. */
	private void finishStairs(final ServerLevel level, final BlockPos feet) {
		this.publishStairs(level, feet);
		this.stairsTop = null;
		this.extending = null;
		this.stairsSteps.clear();
	}

	/** Shares the staircase it's digging (down to where it is now), replacing the shorter version shared before. */
	private void publishStairs(final ServerLevel level, final BlockPos bottom) {
		if (this.stairsTop == null || this.stairsTop.getY() - bottom.getY() < 6 || this.stairsSteps.isEmpty()) {
			return;
		}
		MineStairs.Stairs stairs = new MineStairs.Stairs(this.stairsTop, bottom.immutable(), new ArrayList<>(this.stairsSteps));
		MineStairs.add(level, stairs, this.extending);
		this.extending = stairs;
	}

	/** Forgets where it was tunnelling to (after being moved somewhere else, say), so it plans afresh from here. */
	public void resetMiningPlan() {
		this.branchGoal = null;
		this.target = null;
		this.stretchPlan.clear();
		this.walkingToCave = false;
		this.resetProgress();
	}

	/** Walking down a shared mine staircase right now. */
	public boolean isTakingStairs() {
		return this.takingStairs != null;
	}

	/** A stretch of mining lasts at most this long (two minutes) before the brain picks again. */
	private static final int STRETCH_TICKS = 2400;
	private static final int STRETCH_LENGTH = 16;

	/** Asks the mining brain how to carry on, and returns where that stretch heads first. */
	private BlockPos startStretch(final ServerLevel level, final BlockPos feet, final int depth) {
		BlockPos cave = this.findCave(level, feet);
		int surface = surfaceAt(level, feet.getX(), feet.getZ());
		boolean[] allowed = new boolean[MiningMove.values().length];
		java.util.Arrays.fill(allowed, true);
		allowed[MiningMove.DEEPER.ordinal()] = feet.getY() - 8 > level.getMinY() + 6;
		allowed[MiningMove.HIGHER.ordinal()] = feet.getY() + 8 < surface - 6;
		allowed[MiningMove.CAVE.ordinal()] = cave != null;
		float[] situation = this.miningSituation(level, feet, depth, surface, cave != null);
		MiningMove move = this.clone.mining.start(level, situation, allowed);
		Direction dir = this.digDir;
		int y = feet.getY();
		switch (move) {
			case TURN -> dir = this.clone.getRandom().nextBoolean() ? dir.getClockWise() : dir.getCounterClockWise();
			case DEEPER -> y -= 8;
			case HIGHER -> y += 8;
			case CAVE -> {
				// Walk there if there's a way (it's a cave: there often is), otherwise tunnel to it.
				net.minecraft.world.level.pathfinder.Path path = this.clone.getNavigation().createPath(cave, 1);
				if (path != null && path.canReach()) {
					this.clone.getNavigation().moveTo(path, 1.0);
					this.walkingToCave = true;
					this.caveWalkFrom = null;
					this.caveWalkStuck = 0;
				}
				return cave;
			}
			case BRANCHES -> {
				// A main tunnel, with a side tunnel 5 blocks off each side every 3 blocks, coming back to the main one
				// after each (2 solid blocks between side tunnels: every block in between is seen from one or the other).
				Direction left = dir.getCounterClockWise();
				Direction right = dir.getClockWise();
				for (int i = 1; i <= 4; i++) {
					BlockPos main = feet.relative(dir, 3 * i);
					this.stretchPlan.add(main);
					this.stretchPlan.add(main.relative(left, 5));
					this.stretchPlan.add(main);
					this.stretchPlan.add(main.relative(right, 5));
					this.stretchPlan.add(main);
				}
				return this.stretchPlan.poll();
			}
			case TUNNEL -> {
			}
		}
		this.digDir = dir;
		BlockPos next = feet.relative(dir, STRETCH_LENGTH);
		return new BlockPos(next.getX(), y, next.getZ());
	}

	/** What the mining brain sees: numbers from about -1 to 1. */
	private float[] miningSituation(final ServerLevel level, final BlockPos feet, final int depth, final int surface, final boolean caveNear) {
		float[] x = new float[MiningBrain.INPUTS];
		x[0] = Math.max(-1.0F, Math.min(1.0F, (feet.getY() - depth) / 32.0F));
		x[1] = this.objective == Objective.DIAMONDS ? 1.0F : 0.0F;
		x[2] = this.objective == Objective.IRON ? 1.0F : 0.0F;
		x[3] = this.pickaxeTier() / 4.0F;
		x[4] = caveNear ? 1.0F : 0.0F;
		x[5] = this.clone.mining.lastResult();
		int lava = 0;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-4, -2, -4), feet.offset(4, 2, 4))) {
			if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA)) {
				lava++;
			}
		}
		x[6] = Math.min(1.0F, lava / 8.0F);
		x[7] = Math.max(0.0F, Math.min(1.0F, (surface - feet.getY()) / 64.0F));
		// In a cave right now (lots of open air around it), and how often its last few stretches found anything.
		int air = 0;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, 0, -2), feet.offset(2, 2, 2))) {
			if (level.getBlockState(pos).isAir()) {
				air++;
			}
		}
		x[8] = Math.max(0.0F, (air - 6) / 69.0F);
		x[9] = this.clone.mining.recentHitRate();
		return x;
	}

	/**
	 * A spot in a cave nearby: open air underground, with room to stand and plenty of space around it (so its
	 * walls are worth looking at). Checks a few dozen random spots, not every block.
	 */
	private @Nullable BlockPos findCave(final ServerLevel level, final BlockPos feet) {
		BlockPos best = null;
		for (int tries = 0; tries < 48; tries++) {
			BlockPos pos = feet.offset(this.clone.getRandom().nextInt(33) - 16, this.clone.getRandom().nextInt(13) - 6,
				this.clone.getRandom().nextInt(33) - 16);
			if (!level.isLoaded(pos) || feet.closerThan(pos, 6.0) || this.failed.contains(pos)
				|| !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
				|| level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
				|| surfaceAt(level, pos.getX(), pos.getZ()) <= pos.getY() + 3) {
				continue;
			}
			int air = 0;
			for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 2, 1))) {
				if (level.getBlockState(near).isAir()) {
					air++;
				}
			}
			if (air >= 20 && (best == null || feet.distSqr(pos) < feet.distSqr(best))) {
				best = pos.immutable();
			}
		}
		return best;
	}

	/** Any ore block. */
	private static boolean isOre(final BlockState state) {
		return state.is(ConventionalBlockTags.ORES);
	}

	/** What an ore block is worth to it right now: most for what it's after. */
	private float oreWorth(final BlockState state) {
		boolean diamonds = this.objective == Objective.DIAMONDS;
		if (state.is(ConventionalBlockTags.DIAMOND_ORES)) {
			return diamonds ? 10.0F : 3.0F;
		}
		if (state.is(ConventionalBlockTags.IRON_ORES)) {
			return diamonds ? 1.0F : 3.0F;
		}
		if (state.is(ConventionalBlockTags.COAL_ORES)) {
			return diamonds ? 0.5F : 1.0F;
		}
		return isOre(state) ? 0.5F : 0.0F;
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
		// It has become one of its own steps since it was picked: leave it, or the way back is gone.
		if (this.isStep(level, goal) && !this.passable(level, goal, goalState)) {
			this.giveUp(goal);
			return;
		}
		// Only mine the goal itself once it's in sight: mining it through stone would leave its drop sealed in a pocket.
		// And never deeper than one below its feet, or right under it: that digs a hole (and in the end a shaft) it
		// can't walk back out of. Anything lower is reached by digging a staircase down to it.
		int below = feet.getY() - goal.getY();
		boolean underFeet = goal.getX() == feet.getX() && goal.getZ() == feet.getZ() && below > 0;
		if (!this.passable(level, goal, goalState) && this.clone.getEyePosition().distanceToSqr(Vec3.atCenterOf(goal)) <= REACH_SQ
			&& below <= 1 && goal.getY() - feet.getY() <= 3 && !underFeet && this.canSee(level, goal)) {
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
			Direction aim = dir;
			if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
				aim = dx > 0 ? Direction.EAST : Direction.WEST;
			} else if (dz != 0) {
				aim = dz > 0 ? Direction.SOUTH : Direction.NORTH;
			}
			// On a staircase, never turn back on itself (that digs away the step it just stood on), and with the goal
			// almost straight below or above, keep going the same way rather than zig-zagging.
			boolean stairs = dy < -1 || dy > 1;
			boolean nearlyUnder = Math.max(Math.abs(dx), Math.abs(dz)) <= 2;
			if (!stairs || (aim != dir.getOpposite() && !nearlyUnder)) {
				dir = aim;
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
			// Never dig away a step it has stood on (that's the way back). A goal only reachable that way isn't worth
			// it: pick another straight away.
			if (this.isStep(level, pos)) {
				this.giveUp(goal);
				if (goal.equals(this.branchGoal)) {
					// A tunnel heading back over its own staircase: tunnel another way.
					this.branchGoal = null;
					this.digDir = this.digDir.getClockWise();
				}
				return;
			}
			// Nor anything but natural ground when digging out of a cave.
			if (!this.safeToMine(level, pos, state) || (this.naturalOnly && !naturalGround(state))) {
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
		// Ore counts towards how well this stretch of mining went (once it's actually dug out).
		this.clone.mining.mining(pos, this.oreWorth(level.getBlockState(pos)));
		if (level.getBlockState(pos).is(ConventionalBlockTags.DIAMOND_ORES)) {
			CloneTalk.found(this.clone, "diamonds");
		}
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

	// ---------------------------------------------------------------- home base

	/** From sunset until dawn, in the overworld: when clones might head home. */
	public static boolean isEvening(final Level level) {
		long time = level.getOverworldClockTime() % 24000L;
		return level.dimension() == Level.OVERWORLD && time >= 12000L && time < 23500L;
	}

	private HomeBase.@Nullable Home home(final ServerLevel level) {
		return HomeBase.get(level, this.clone.getOwnerId());
	}

	/** What a clone's things in the house are marked with (its own bed). */
	private String me() {
		return this.clone.getUUID().toString();
	}

	/** Building blocks: any kind of stone or brick. */
	static boolean isStoneBlock(final ItemStack s) {
		return s.is(ItemTags.STONE_CRAFTING_MATERIALS) || s.is(Items.STONE) || s.is(Items.STONE_BRICKS) || s.is(Items.DEEPSLATE_BRICKS)
			|| s.is(Items.BRICKS) || s.is(Items.SMOOTH_STONE) || s.is(Items.MOSSY_COBBLESTONE);
	}

	static boolean isBuildingBlock(final ItemStack s) {
		return isStoneBlock(s) || s.is(ItemTags.PLANKS);
	}

	/** An item of this kind it can spare (beyond what it needs for its tools and the like), or null. */
	private @Nullable Item spareItem(final Predicate<ItemStack> test) {
		int stoneSpare = this.count(ProgressionGoal::isStoneBlock) - this.cobbleNeeded();
		int planksSpare = this.count(s -> s.is(ItemTags.PLANKS)) - this.planksNeeded();
		for (ItemStack s : this.clone.getInventory().main) {
			if (s.isEmpty() || !test.test(s)) {
				continue;
			}
			if (isStoneBlock(s) && stoneSpare <= 0 || s.is(ItemTags.PLANKS) && planksSpare <= 0) {
				continue;
			}
			return s.getItem();
		}
		return null;
	}

	private int spareBuildingBlocks() {
		return Math.max(0, this.count(ProgressionGoal::isStoneBlock) - this.cobbleNeeded())
			+ Math.max(0, this.count(s -> s.is(ItemTags.PLANKS)) - this.planksNeeded());
	}

	/** Wall material: stronger where creepers have blown holes before (stone, or obsidian if there is any). */
	private @Nullable Item wallItem(final int strength) {
		if (strength >= 3 && this.has(Items.OBSIDIAN)) {
			return Items.OBSIDIAN;
		}
		Item stone = this.spareItem(ProgressionGoal::isStoneBlock);
		if (stone != null || strength >= 1) {
			return stone;
		}
		return this.spareItem(s -> s.is(ItemTags.PLANKS));
	}

	/** What it would put at this spot of the house, if it has it. */
	private @Nullable Item homeItem(final HomeBase.Home home, final HomeBase.Spot spot) {
		int strength = home.strengthAt(spot.pos());
		// Anything a creeper blew out comes back in stone (or stronger), whatever the house's style.
		if (strength > 0 && spot.part().ordinal() <= HomeBase.Part.REINFORCE.ordinal()
			&& spot.part() != HomeBase.Part.CLEAR && spot.part() != HomeBase.Part.DOOR) {
			return this.wallItem(strength);
		}
		HomeBase.Style style = home.style;
		return switch (spot.part()) {
			// Ground: dirt if it has some, then stone, and planks only if that's all there is.
			case FLOOR -> this.has(Items.DIRT) ? Items.DIRT : this.styled(HomeBase.Style.STONE);
			case WALL, REINFORCE -> this.styled(style.walls() == HomeBase.Style.MIXED
				? (home.heightOf(spot.pos()) == 0 ? HomeBase.Style.STONE : HomeBase.Style.WOOD) : style.walls());
			// Corner posts: logs if it has some to spare, otherwise the other material from the walls.
			case PILLAR -> this.spareLog() != null ? this.spareLog()
				: this.styled(style.walls() == HomeBase.Style.WOOD ? HomeBase.Style.STONE : HomeBase.Style.WOOD);
			case ROOF -> this.styled(style.roof());
			case FLOORING -> this.styled(style.floor());
			case WINDOW -> this.has(Items.GLASS_PANE) ? Items.GLASS_PANE : this.has(Items.GLASS) ? Items.GLASS : this.styled(style.walls() == HomeBase.Style.WOOD
				? HomeBase.Style.WOOD : HomeBase.Style.STONE);
			case DOOR -> this.firstItem(s -> s.is(ItemTags.WOODEN_DOORS));
			case BED -> this.firstItem(s -> s.is(ItemTags.BEDS));
			case CHEST -> this.firstItem(s -> s.is(Items.CHEST));
			// It keeps one crafting table and one furnace of its own to carry about.
			case TABLE -> this.count(s -> s.is(Items.CRAFTING_TABLE)) >= 2 ? Items.CRAFTING_TABLE : null;
			case FURNACE -> this.count(s -> s.is(Items.FURNACE)) >= 2 ? Items.FURNACE : null;
			case TORCH, OUTER_TORCH -> this.firstItem(s -> s.is(Items.TORCH));
			case CLEAR -> null;
		};
	}

	/** Wood or stone, as the house's style wants, or the other if that's all it has spare (so the house still gets built). */
	private @Nullable Item styled(final int material) {
		Item wood = this.spareItem(s -> s.is(ItemTags.PLANKS));
		Item stone = this.spareItem(ProgressionGoal::isStoneBlock);
		return material == HomeBase.Style.WOOD ? (wood != null ? wood : stone) : (stone != null ? stone : wood);
	}

	/** A log it can spare (keeping enough for the planks it needs). */
	private @Nullable Item spareLog() {
		int logs = this.count(s -> s.is(ItemTags.LOGS));
		int planksShort = Math.max(0, this.planksNeeded() - this.count(s -> s.is(ItemTags.PLANKS)));
		return logs > (planksShort + 3) / 4 + 2 ? this.firstItem(s -> s.is(ItemTags.LOGS)) : null;
	}

	private @Nullable Item firstItem(final Predicate<ItemStack> test) {
		for (ItemStack s : this.clone.getInventory().main) {
			if (!s.isEmpty() && test.test(s)) {
				return s.getItem();
			}
		}
		return null;
	}

	/** The parts of the house still to do, as of this think (so crafting and keeping stone don't check every block each time). */
	private final EnumSet<HomeBase.Part> homeMissingNow = EnumSet.noneOf(HomeBase.Part.class);
	/** It should keep stone for the house: there's no house yet, or it has holes. */
	private boolean homeWantsBlocks;

	private void updateHomeNeeds(final ServerLevel level) {
		this.homeMissingNow.clear();
		HomeBase.Home home = this.home(level);
		if (home == null) {
			this.homeWantsBlocks = level.dimension() == Level.OVERWORLD && this.clone.getOwnerId() != null;
			return;
		}
		if (!home.inside().closerThan(this.clone.blockPosition(), 96.0)) {
			this.homeWantsBlocks = false;
			return;
		}
		for (HomeBase.Spot spot : home.plan()) {
			if (!this.homeMissingNow.contains(spot.part()) && level.isLoaded(spot.pos()) && !HomeBase.done(level, home, spot)) {
				this.homeMissingNow.add(spot.part());
			}
		}
		// Its own bed in the house isn't there yet.
		boolean myBedMissing = false;
		for (HomeBase.Thing thing : home.things) {
			if (thing.part() == HomeBase.Part.BED && thing.by().equals(this.me())) {
				BlockPos foot = home.at(thing.x(), thing.y(), thing.z());
				myBedMissing = level.isLoaded(foot) && !(level.getBlockState(foot).getBlock() instanceof net.minecraft.world.level.block.BedBlock);
			}
		}
		this.myHomeBedMissing = myBedMissing;
		this.homeWantsBlocks = this.homeMissingNow.contains(HomeBase.Part.WALL) || this.homeMissingNow.contains(HomeBase.Part.PILLAR) || this.homeMissingNow.contains(HomeBase.Part.ROOF)
			|| this.homeMissingNow.contains(HomeBase.Part.FLOOR) || this.homeMissingNow.contains(HomeBase.Part.REINFORCE)
			|| this.homeMissingNow.contains(HomeBase.Part.FLOORING);
	}

	private boolean myHomeBedMissing;

	/**
	 * It wants a bed (to craft, from wool): one for its place in the house, or (with no house yet) to carry, like
	 * before. Not if it has one already.
	 */
	private boolean wantsBed() {
		if (this.hasBed()) {
			return false;
		}
		if (this.clone.level() instanceof ServerLevel level && this.home(level) != null) {
			return this.myHomeBedMissing;
		}
		return this.role == Role.PROGRESS;
	}

	/** House things to craft at a table: a door, a storage chest, a furnace to leave there. */
	private void addHomeRecipes(final List<Recipe> wanted) {
		if (this.homeMissingNow.contains(HomeBase.Part.DOOR) && this.firstItem(s -> s.is(ItemTags.WOODEN_DOORS)) == null) {
			wanted.add(ProgressionCrafting.OAK_DOOR);
		}
		if (this.homeMissingNow.contains(HomeBase.Part.CHEST) && !this.has(Items.CHEST)) {
			wanted.add(ProgressionCrafting.CHEST);
		}
		if (this.homeMissingNow.contains(HomeBase.Part.FURNACE) && this.count(s -> s.is(Items.FURNACE)) < 2 && this.pickaxeTier() >= 1) {
			wanted.add(ProgressionCrafting.FURNACE);
		}
	}

	/** Whether any spot of this part of the house still needs doing. */
	private boolean homeMissing(final ServerLevel level, final HomeBase.Home home, final HomeBase.Part part) {
		for (HomeBase.Spot spot : home.plan()) {
			if (spot.part() == part && level.isLoaded(spot.pos()) && !HomeBase.done(level, home, spot)) {
				return true;
			}
		}
		return false;
	}

	/** Getting stone for the house, and whether it went underground for it. */
	private boolean gatheringForHome;
	private boolean wentDownForHome;

	/**
	 * When the house needs blocks and it has none to spare: mines stone until it has a good load (48), then (if it
	 * went down for it) comes back up to build.
	 */
	private @Nullable Objective gatherForHome(final ServerLevel level) {
		HomeBase.Home home = this.home(level);
		int spare = this.spareBuildingBlocks();
		boolean below = this.belowGround(level);
		// Down a mine with a good load of stone, and the house needs it (or there's no house yet and it has plenty to
		// start one): come up and build. (It goes back to mining once it's used it.)
		if (below && !this.gatheringForHome && level.dimension() == Level.OVERWORLD && this.clone.getOwnerId() != null
			&& this.pickaxeTier() >= 1 && (home == null ? spare >= 64
				: this.homeWantsBlocks && spare >= 32 && home.inside().closerThan(this.clone.blockPosition(), 128.0))) {
			if (CloneTalk.say(this.clone, CloneTalk.Topic.HOUSE_ADDED, "I've got plenty of stone. I'm going up to work on the house.")) {
				this.clone.showMessage("Going up to build");
			}
			return Objective.SURFACE;
		}
		// The house needs a door and there's no wood to make one: get some.
		if (home != null && !below && this.homeMissingNow.contains(HomeBase.Part.DOOR) && this.firstItem(s -> s.is(ItemTags.WOODEN_DOORS)) == null
			&& this.count(s -> s.is(ItemTags.PLANKS)) < this.planksNeeded() + 6 && this.count(s -> s.is(ItemTags.LOGS)) == 0
			&& home.inside().closerThan(this.clone.blockPosition(), 64.0)) {
			Objective wood = this.woodObjective(level);
			if (wood != Objective.EXPLORE || !level.isFlat()) {
				return wood;
			}
		}
		if (!this.homeWantsBlocks || home == null || this.pickaxeTier() < 1 || !home.inside().closerThan(this.clone.blockPosition(), 64.0)) {
			this.gatheringForHome = false;
			return null;
		}
		if (spare < 8 && !this.gatheringForHome && !below) {
			this.gatheringForHome = true;
			CloneTalk.say(this.clone, CloneTalk.Topic.NEED, "We're out of blocks for the house. I'll go and mine some stone.");
		} else if (spare >= 48) {
			this.gatheringForHome = false;
		}
		if (this.gatheringForHome && this.worthDigging(level, IS_STONE)) {
			this.wentDownForHome = true;
			return Objective.STONE;
		}
		if (!this.gatheringForHome && this.wentDownForHome) {
			if (below) {
				return Objective.SURFACE;
			}
			this.wentDownForHome = false;
		}
		return null;
	}

	/** The house still has holes in its floor, walls or roof. */
	private boolean homeNeedsBlocks(final ServerLevel level, final HomeBase.Home home) {
		return this.homeMissing(level, home, HomeBase.Part.WALL) || this.homeMissing(level, home, HomeBase.Part.PILLAR) || this.homeMissing(level, home, HomeBase.Part.ROOF)
			|| this.homeMissing(level, home, HomeBase.Part.FLOOR) || this.homeMissing(level, home, HomeBase.Part.REINFORCE)
			|| this.homeMissing(level, home, HomeBase.Part.FLOORING);
	}

	/**
	 * What it can do for the house right now, or null. Up on the surface, it starts one if its owner's clones haven't
	 * got one yet (when it has the blocks for it), moves in (its own bed, a room for it if needed, and maybe a
	 * touch of its own), and builds or repairs whatever it has the things for.
	 */
	private HomeBase.@Nullable Spot homeJob(final ServerLevel level) {
		UUID owner = this.clone.getOwnerId();
		if (level.dimension() != Level.OVERWORLD || owner == null || this.pickaxeTier() < 1
			|| (this.hungry() && this.count(s -> s.has(DataComponents.FOOD)) == 0)) {
			return null;
		}
		HomeBase.Home home = this.home(level);
		// Not from down a mine (only from right by the house: down in a crater next to it, say).
		if (this.belowGround(level) && (home == null || !home.bounds().inflate(5.0).contains(this.clone.position()))) {
			return null;
		}
		if (home == null) {
			if (this.spareBuildingBlocks() < 32 || this.clone.tickCount < this.nextHomeSearch) {
				return null;
			}
			this.nextHomeSearch = this.clone.tickCount + 1200;
			home = this.designHome(level);
			if (home == null) {
				return null;
			}
			HomeBase.set(level, owner, home);
			CloneTalk.sayAndHearBack(this.clone, CloneTalk.Topic.HOUSE_STARTED, "I'm going to build us " + home.style.describe() + " here!",
				"Great idea, {name}! I'll help.", "Nice spot, {name}.", "I'll bring what I can.");
		}
		if (!home.inside().closerThan(this.clone.blockPosition(), 48.0)) {
			return null;
		}
		this.moveIn(level, home);
		HomeBase.Spot spot = this.nextHomeSpot(level, home);
		if (spot == null && this.homeNeedsBlocks(level, home) && this.spareBuildingBlocks() == 0 && this.clone.tickCount >= this.nextHomeAsk) {
			// Out of blocks: ask the others.
			this.nextHomeAsk = this.clone.tickCount + 2400;
			this.askTeammates("cobblestone or planks for the house", ProgressionGoal::isBuildingBlock, 32);
		}
		return spot;
	}

	/**
	 * Its place in the house: its own bed (wherever it likes, or in a room it adds on if there's no space), and the
	 * first time, maybe a touch of its own (a window or a torch). It tells the others what it's adding.
	 */
	private void moveIn(final ServerLevel level, final HomeBase.Home home) {
		if (home.hasBedFor(this.me())) {
			return;
		}
		String name = this.clone.getCloneName();
		if (home.addBed(this.me(), this.clone.getRandom())) {
			if (home.things.stream().filter(t -> t.part() == HomeBase.Part.BED).count() > 1) {
				CloneTalk.sayAndHearBack(this.clone, CloneTalk.Topic.HOUSE_ADDED, "I'll put my bed in here.", "Welcome home, {name}!", "Plenty of room.");
			}
		} else {
			// No room for its bed: add one on (at most one room per clone, plus one).
			long beds = home.things.stream().filter(t -> t.part() == HomeBase.Part.BED).count();
			if (home.rooms.size() > beds + 1) {
				return;
			}
			String side = home.addRoom(this.clone.getRandom(), pos -> this.goodGround(level, pos));
			if (side == null) {
				return;
			}
			if (!home.addBed(this.me(), this.clone.getRandom())) {
				// Still doesn't fit: forget that room.
				home.rooms.remove(home.rooms.size() - 1);
				home.redesigned();
				return;
			}
			CloneTalk.sayAndHearBack(this.clone, CloneTalk.Topic.HOUSE_ADDED, "There's no room for my bed, so I'm adding a room " + side + ".",
				"Good idea, {name}. I'll help knock through.", "A bigger house! Nice.", "I'll bring stone for it.");
		}
		// A touch of its own.
		switch (this.clone.getRandom().nextInt(3)) {
			case 0 -> home.addWindow(this.me(), this.clone.getRandom());
			case 1 -> home.addTorch(this.me(), this.clone.getRandom());
			default -> {
			}
		}
		HomeBase.changed(level);
		this.clone.showMessage(name + " moved in");
	}

	/**
	 * Designs the first room of the house, its own way: how big (bigger with more clones about), how high the walls
	 * are, which way it faces and where the door goes, then where the crafting table, chest, furnace and a torch go.
	 */
	private HomeBase.@Nullable Home designHome(final ServerLevel level) {
		net.minecraft.util.RandomSource random = this.clone.getRandom();
		int clones = CloneTalk.teammates(this.clone, 64.0).size() + 1;
		// Its own idea of a house: how it should look, and how big (bigger with more clones about; smaller if there's
		// nowhere flat enough for that).
		HomeBase.Style style = HomeBase.Style.random(random);
		int wantWidth = 5 + random.nextInt(5) + (clones > 2 ? 1 : 0);
		int wantDepth = 5 + random.nextInt(4);
		for (int attempt = 0; attempt < 6; attempt++) {
			int width = Math.max(5, wantWidth - attempt);
			int depth = Math.max(5, wantDepth - attempt / 2);
			int height = random.nextInt(3) == 0 ? 4 : 3;
			Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int doorX = 1 + random.nextInt(width - 2);
			BlockPos origin = this.findHomeSite(level, width, depth, facing);
			if (origin == null) {
				continue;
			}
			HomeBase.Home home = new HomeBase.Home(origin, facing, height, new HomeBase.Room(0, 0, width, depth), doorX);
			home.style = style;
			home.addAgainstWall(HomeBase.Part.TABLE, this.me(), random);
			home.addAgainstWall(HomeBase.Part.CHEST, this.me(), random);
			home.addAgainstWall(HomeBase.Part.FURNACE, this.me(), random);
			home.addTorch(this.me(), random);
			return home;
		}
		return null;
	}

	/**
	 * Good ground for a spot of the house, at floor level: within a block of level, on solid natural ground, with
	 * nothing but air, plants or a bump of earth where the house would go (no trees, water or anyone's building).
	 */
	private boolean goodGround(final ServerLevel level, final BlockPos floor) {
		if (!level.isLoaded(floor) || !level.isLoaded(floor.above(6))) {
			return false;
		}
		int surface = surfaceAt(level, floor.getX(), floor.getZ());
		if (Math.abs(surface - floor.getY()) > 1) {
			return false;
		}
		BlockPos groundPos = new BlockPos(floor.getX(), surface - 1, floor.getZ());
		BlockState ground = level.getBlockState(groundPos);
		if (!ground.getFluidState().isEmpty() || !(naturalGround(ground) || ground.isFaceSturdy(level, groundPos, Direction.UP))) {
			return false;
		}
		for (int y = -1; y <= 5; y++) {
			BlockPos pos = floor.above(y);
			BlockState state = level.getBlockState(pos);
			if (!state.getFluidState().isEmpty() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
				return false;
			}
			if (y >= 0 && !state.isAir() && !state.canBeReplaced() && !(y == 0 && naturalGround(state))) {
				return false;
			}
		}
		return true;
	}

	/** The nearest spot (within 12 blocks) where a room this size fits: its back left corner at floor level. */
	private @Nullable BlockPos findHomeSite(final ServerLevel level, final int width, final int depth, final Direction facing) {
		BlockPos feet = this.clone.blockPosition();
		Direction right = facing.getClockWise();
		BlockPos best = null;
		for (int dx = -12; dx <= 12; dx++) {
			for (int dz = -12; dz <= 12; dz++) {
				BlockPos corner = feet.offset(dx, 0, dz);
				BlockPos middle = corner.relative(right, width / 2).relative(facing, depth / 2);
				if (!level.isLoaded(middle)) {
					continue;
				}
				BlockPos origin = new BlockPos(corner.getX(), surfaceAt(level, middle.getX(), middle.getZ()), corner.getZ());
				if (best != null && feet.distSqr(origin) >= feet.distSqr(best)) {
					continue;
				}
				boolean fits = true;
				for (int x = 0; x < width && fits; x++) {
					for (int z = 0; z < depth && fits; z++) {
						fits = this.goodGround(level, origin.relative(right, x).relative(facing, z));
					}
				}
				// And room to walk out of the door.
				if (fits && this.goodGround(level, origin.relative(right, width / 2).relative(facing, depth))) {
					best = origin;
				}
			}
		}
		return best;
	}

	/** The next thing to do for the house that it can do now, or null. */
	private HomeBase.@Nullable Spot nextHomeSpot(final ServerLevel level, final HomeBase.Home home) {
		boolean blast = HomeBase.blastPending(home);
		for (HomeBase.Spot spot : home.plan()) {
			BlockPos pos = spot.pos();
			if (!level.isLoaded(pos) || this.homeSkipUntil.getOrDefault(pos, 0) > this.clone.tickCount || HomeBase.done(level, home, spot)) {
				continue;
			}
			if (spot.part() == HomeBase.Part.FLOORING) {
				BlockState state = level.getBlockState(pos);
				boolean solid = !state.getCollisionShape(level, pos).isEmpty();
				if (solid && !naturalGround(state)) {
					// A floor a player put down (a carpet, a slab...): leave it.
					home.skip.add(pos);
					HomeBase.changed(level);
					continue;
				}
				// Grass or dirt to dig up (only with something to put down instead), or a hole to fill.
				if (this.homeItem(home, spot) != null) {
					return spot;
				}
				continue;
			}
			if (spot.part() == HomeBase.Part.CLEAR) {
				BlockState state = level.getBlockState(pos);
				// Natural ground or plants in the way, or the wall to a new room: dig it out. Anything else a player put there.
				if (naturalGround(state) || state.is(BlockTags.LEAVES) || state.canBeReplaced() || home.placed.contains(pos)) {
					return spot;
				}
				home.skip.add(pos);
				HomeBase.changed(level);
				continue;
			}
			if (home.placed.contains(pos)) {
				// The clones built this and it's gone: a hole to fix. (A player breaking it on purpose is noted when they
				// do, and it's left alone. Right after a creeper, wait a moment for the blast to be noted, so it comes
				// back stronger.)
				if (blast) {
					continue;
				}
				home.placed.remove(pos);
				HomeBase.changed(level);
			}
			if (spot.part() == HomeBase.Part.DOOR && spot.second()) {
				continue;
			}
			// A torch on a wall that isn't up yet: later.
			if (spot.part() == HomeBase.Part.TORCH) {
				BlockPos wall = pos.relative(spot.facing().getOpposite());
				if (!level.getBlockState(wall).isFaceSturdy(level, wall, spot.facing())) {
					continue;
				}
			}
			// Each clone puts its own bed in its own place (unless whoever's it is isn't about, or hasn't got a bed to put
			// there: a creeper blew theirs up and this clone picked it up, say).
			if (spot.part() == HomeBase.Part.BED && !home.bedOwner(pos).equals(this.me())) {
				String owner = home.bedOwner(pos);
				if (CloneTalk.teammates(this.clone, 96.0).stream().anyMatch(c -> c.getUUID().toString().equals(owner)
					&& c.getInventory().find(s -> s.is(ItemTags.BEDS)) >= 0)) {
					continue;
				}
			}
			if (this.homeItem(home, spot) != null) {
				return spot;
			}
		}
		return null;
	}

	/**
	 * Somewhere to stand within reach of a spot of the house (or a crater it's filling): solid ground under it, room
	 * for its body, not on the spot itself. The nearest such place to the spot.
	 */
	private BlockPos standNear(final ServerLevel level, final HomeBase.Home home, final BlockPos target, final BlockPos alsoKeepOff) {
		BlockPos best = home.inside();
		double bestDist = Double.MAX_VALUE;
		for (BlockPos stand : BlockPos.betweenClosed(target.offset(-3, -3, -3), target.offset(3, 2, 3))) {
			if (stand.equals(target) || stand.above().equals(target) || stand.below().equals(target) || stand.equals(alsoKeepOff)
				|| !level.isLoaded(stand)) {
				continue;
			}
			if (!this.passable(level, stand, level.getBlockState(stand)) || !this.passable(level, stand.above(), level.getBlockState(stand.above()))
				|| level.getBlockState(stand.below()).getCollisionShape(level, stand.below()).isEmpty()) {
				continue;
			}
			double d = stand.distSqr(target);
			if (d < bestDist) {
				bestDist = d;
				best = stand.immutable();
			}
		}
		return best;
	}

	/** Builds the next thing for the house. */
	private void buildHome(final ServerLevel level) {
		HomeBase.Home home = this.home(level);
		HomeBase.Spot spot = home == null ? null : this.nextHomeSpot(level, home);
		if (home == null || spot == null) {
			return;
		}
		BlockPos pos = spot.pos();
		if (!pos.equals(this.homeTarget)) {
			this.homeTarget = pos;
			this.homeTargetSince = this.clone.tickCount;
		}
		// Can't get to it: leave it for a couple of minutes (or for someone else).
		if (this.clone.tickCount - this.homeTargetSince > 400) {
			this.homeTarget = null;
			// A bed that won't go where it was meant to (something in the way of its head, say): put it somewhere else.
			if (spot.part() == HomeBase.Part.BED && this.moveBed(level, home, pos)) {
				return;
			}
			this.homeSkipUntil.put(pos, this.clone.tickCount + 2400);
			return;
		}
		Vec3 centre = Vec3.atCenterOf(pos);
		// Standing in the very hole it's filling (at the bottom of a crater, say): jump and put the block under itself,
		// like a player would, which fills the hole and lifts it out.
		BlockPos feet = this.clone.blockPosition();
		if (spot.part() == HomeBase.Part.FLOOR && (feet.equals(pos) || feet.above().equals(pos))) {
			BlockPos up = feet.above(2);
			Item filler = this.homeItem(home, spot);
			if (filler != null && this.passable(level, up, level.getBlockState(up))) {
				// (A jump and a block under its feet, all at once: it only thinks every half second.)
				this.clone.getNavigation().stop();
				this.clone.setPos(this.clone.getX(), feet.getY() + 1.0, this.clone.getZ());
				if (feet.equals(pos)) {
					this.placeForHome(level, home, spot);
				} else {
					level.setBlock(feet, net.minecraft.world.level.block.Block.byItem(filler).defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
					this.clone.getInventory().consumeOne(s -> s.is(filler));
				}
				return;
			}
			// No room to jump (something right overhead): squeeze out sideways to a free spot next to it.
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos next = feet.relative(dir).above(dy);
					if (!next.equals(pos) && this.passable(level, next, level.getBlockState(next))
						&& this.passable(level, next.above(), level.getBlockState(next.above()))
						&& !level.getBlockState(next.below()).getCollisionShape(level, next.below()).isEmpty()) {
						this.clone.getNavigation().stop();
						this.clone.setPos(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
						return;
					}
				}
			}
		}
		// (A bed takes its head spot too.)
		BlockPos second = spot.part() == HomeBase.Part.BED ? pos.relative(spot.facing()) : pos;
		boolean inTheWay = spot.part() != HomeBase.Part.CLEAR && (this.clone.getBoundingBox().intersects(new AABB(pos))
			|| this.clone.getBoundingBox().intersects(new AABB(second)))
			// Not standing on the floor it's about to dig up.
			|| spot.part() == HomeBase.Part.FLOORING && this.clone.blockPosition().below().equals(pos);
		if (inTheWay || this.clone.getEyePosition().distanceToSqr(centre) > REACH_SQ - 2.0) {
			if (this.clone.getNavigation().isDone() || this.clone.tickCount % 40 == 0) {
				BlockPos stand = this.standNear(level, home, pos, second);
				if (!this.clone.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0)) {
					// No path (it's standing in a gap in the wall, say): just step there.
					this.clone.getMoveControl().setWantedPosition(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0);
				}
			}
			return;
		}
		this.clone.getNavigation().stop();
		this.clone.getLookControl().setLookAt(centre);
		if (spot.part() == HomeBase.Part.CLEAR) {
			home.placed.remove(pos);
			this.mine(level, pos);
			return;
		}
		// The grass or dirt where the floor goes comes up first.
		if (spot.part() == HomeBase.Part.FLOORING && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
			this.mine(level, pos);
			return;
		}
		this.placeForHome(level, home, spot);
	}

	private void placeForHome(final ServerLevel level, final HomeBase.Home home, final HomeBase.Spot spot) {
		Item item = this.homeItem(home, spot);
		if (item == null) {
			return;
		}
		BlockPos pos = spot.pos();
		BlockState state;
		switch (spot.part()) {
			case DOOR -> {
				state = net.minecraft.world.level.block.Block.byItem(item).defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, home.facing)
					.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
				level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
				level.setBlock(pos.above(), state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
					net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), net.minecraft.world.level.block.Block.UPDATE_ALL);
				home.placed.add(pos.above());
			}
			case BED -> {
				if (!this.passable(level, pos.relative(spot.facing()), level.getBlockState(pos.relative(spot.facing())))) {
					return;
				}
				ItemStack bed = this.clone.getInventory().main.get(this.clone.getInventory().find(s -> s.is(item)));
				state = net.minecraft.world.level.block.Block.byItem(item).defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, spot.facing());
				level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
				// The bed's own placement puts the head down.
				state.getBlock().setPlacedBy(level, pos, state, this.clone, bed);
			}
			case CHEST -> CloneStorage.place(level, pos, spot.facing());
			case FURNACE -> level.setBlock(pos, Blocks.FURNACE.defaultBlockState()
				.setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, spot.facing()), net.minecraft.world.level.block.Block.UPDATE_ALL);
			case TORCH -> {
				BlockPos wall = pos.relative(spot.facing().getOpposite());
				if (!level.getBlockState(wall).isFaceSturdy(level, wall, spot.facing())) {
					return;
				}
				level.setBlock(pos, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, spot.facing()),
					net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
			case OUTER_TORCH -> {
				if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) || !level.getBlockState(pos).canBeReplaced()) {
					// Nowhere to stand a torch there.
					home.skip.add(pos);
					HomeBase.changed(level);
					return;
				}
				level.setBlock(pos, Blocks.TORCH.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
			default -> level.setBlock(pos, net.minecraft.world.level.block.Block.byItem(item).defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
		BlockState placed = level.getBlockState(pos);
		var sound = placed.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		this.clone.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
		this.clone.getInventory().consumeOne(s -> s.is(item));
		home.placed.add(pos);
		if (home.strengthAt(pos) > 0) {
			home.repaired++;
		}
		if (!home.built && !this.homeNeedsBlocks(level, home) && !this.homeMissing(level, home, HomeBase.Part.DOOR)) {
			home.built = true;
			CloneTalk.sayAndHearBack(this.clone, CloneTalk.Topic.HOUSE_DONE, "Our house is finished!", "Home sweet home!", "Nice work, {name}!",
				"Looks great!");
		}
		HomeBase.changed(level);
	}

	/** Moves a bed that can't go where it was planned to another spot in the house. Returns false if there's nowhere else. */
	private boolean moveBed(final ServerLevel level, final HomeBase.Home home, final BlockPos foot) {
		HomeBase.Thing bed = null;
		for (HomeBase.Thing t : home.things) {
			if (t.part() == HomeBase.Part.BED && home.at(t.x(), t.y(), t.z()).equals(foot)) {
				bed = t;
			}
		}
		if (bed == null) {
			return false;
		}
		home.things.remove(bed);
		home.redesigned();
		if (!home.addBed(bed.by(), this.clone.getRandom())) {
			home.things.add(bed);
			home.redesigned();
			return false;
		}
		HomeBase.changed(level);
		return true;
	}

	/** Its bed in the house if that's free, otherwise any free bed there (where its head goes). */
	private @Nullable BlockPos freeBed(final ServerLevel level, final HomeBase.Home home) {
		BlockPos any = null;
		for (Map.Entry<BlockPos, String> bed : home.beds().entrySet()) {
			BlockState state = level.getBlockState(bed.getKey());
			if (!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
				|| state.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) {
				continue;
			}
			if (bed.getValue().equals(this.me())) {
				return bed.getKey();
			}
			any = bed.getKey();
		}
		return any;
	}

	private boolean hasThingsToStore() {
		int total = 0;
		for (int n : this.spares()) {
			total += n;
		}
		return total >= 8;
	}

	/**
	 * In the evening, up on the surface near home, each clone decides for itself whether to go home for the night:
	 * always if it has things to put away, otherwise it might stay out. Clones underground carry on.
	 */
	private boolean headingHomeTonight(final ServerLevel level) {
		HomeBase.Home home = this.home(level);
		if (home == null || !home.built || !isEvening(level) || this.belowGround(level)
			|| !home.inside().closerThan(this.clone.blockPosition(), 96.0)) {
			return false;
		}
		long night = level.getOverworldClockTime() / 24000L;
		if (night != this.decidedNight) {
			this.decidedNight = night;
			this.goingHome = this.hasThingsToStore() || this.clone.getRandom().nextFloat() < 0.6F;
			this.homeWalkFrom = null;
			this.homeWalkStuck = 0;
			if (this.goingHome) {
				CloneTalk.sayAndHearBack(this.clone, CloneTalk.Topic.GOOD_NIGHT, "It's getting dark. I'm heading home.",
					"Me too, {name}.", "Night, {name}!", "I'll stay out a bit longer.");
			}
		}
		return this.goingHome;
	}

	/** Goes home: puts its things in the chest, then sleeps in a free bed (or waits inside if there isn't one). */
	private void goHome(final ServerLevel level) {
		HomeBase.Home home = this.home(level);
		if (home == null) {
			return;
		}
		BlockPos feet = this.clone.blockPosition();
		if (!home.isInside(feet)) {
			if (feet.equals(this.homeWalkFrom)) {
				this.homeWalkStuck += THINK_INTERVAL;
			} else {
				this.homeWalkFrom = feet;
				this.homeWalkStuck = 0;
			}
			if (this.homeWalkStuck > 400) {
				// Can't get in: stay out tonight.
				this.goingHome = false;
				return;
			}
			if (this.clone.getNavigation().isDone() || this.clone.tickCount % 40 == 0) {
				BlockPos in = home.inside();
				this.clone.getNavigation().moveTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 1.0);
			}
			return;
		}
		BlockPos chest = home.chest();
		if (chest != null && this.hasThingsToStore() && CloneStorage.at(level, chest) != null) {
			this.storeInto(level, chest);
			return;
		}
		BlockPos bed = this.freeBed(level, home);
		if (bed == null) {
			this.clone.getNavigation().stop();
			return;
		}
		if (this.clone.distanceToSqr(Vec3.atCenterOf(bed)) > 2.5 * 2.5) {
			if (this.clone.getNavigation().isDone()) {
				this.clone.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 1.0);
			}
			return;
		}
		this.clone.getNavigation().stop();
		this.clone.startSleeping(bed);
	}

	// ---------------------------------------------------------------- helping each other

	/** How many of these it could hand over (what it doesn't need itself). */
	public int canSpare(final Predicate<ItemStack> what) {
		int total = 0;
		int[] spare = this.spares();
		for (int i = 0; i < spare.length; i++) {
			ItemStack s = this.clone.getInventory().main.get(i);
			if (!s.isEmpty() && what.test(s)) {
				total += spare[i];
			}
		}
		if (total == 0 && what.test(new ItemStack(Items.COBBLESTONE))) {
			// Building blocks it's keeping for the house can go to whoever's building it.
			total = this.spareBuildingBlocks();
		}
		return total;
	}

	/** Asks the other clones for something. One that can spare some says so and brings it over. */
	private void askTeammates(final String what, final Predicate<ItemStack> wanted, final int amount) {
		if (!CloneTalk.say(this.clone, CloneTalk.Topic.NEED, "Has anyone got " + what + "?")) {
			return;
		}
		CloneEntity giver = null;
		for (CloneEntity mate : CloneTalk.teammates(this.clone, 96.0)) {
			if (mate.hasOption(CloneOption.GAME_PROGRESSION) && mate.progressionGoal.canSpare(wanted) > 0) {
				giver = mate;
				break;
			}
		}
		if (giver == null) {
			List<CloneEntity> mates = CloneTalk.teammates(this.clone, 96.0);
			if (!mates.isEmpty()) {
				CloneTalk.reply(mates.get(0), "Sorry " + this.clone.getCloneName() + ", I haven't got any.", null);
			}
			return;
		}
		CloneEntity asker = this.clone;
		CloneTalk.reply(giver, "I've got some, " + asker.getCloneName() + ". I'll bring them over.",
			mate -> mate.progressionGoal.bring(asker, wanted, amount));
	}

	/** Takes some of these to another clone (it walks over and hands them over). */
	public void bring(final CloneEntity to, final Predicate<ItemStack> what, final int amount) {
		this.bringTo = to.getUUID();
		this.bringWhat = what;
		this.bringAmount = amount;
		this.bringSince = this.clone.tickCount;
	}

	/** Walking over to hand something to another clone. */
	private void deliver(final ServerLevel level) {
		Entity other = this.bringTo == null ? null : level.getEntity(this.bringTo);
		if (!(other instanceof CloneEntity to) || !to.isAlive() || this.bringWhat == null || this.clone.tickCount - this.bringSince > 1800) {
			this.bringTo = null;
			return;
		}
		if (this.clone.distanceToSqr(to) > 3.0 * 3.0) {
			if (this.clone.getNavigation().isDone() || this.clone.tickCount % 40 == 0) {
				this.clone.getNavigation().moveTo(to, 1.1);
			}
			return;
		}
		this.clone.getNavigation().stop();
		this.clone.getLookControl().setLookAt(to);
		int left = Math.min(this.bringAmount, this.canSpare(this.bringWhat));
		for (int i = 0; i < this.clone.getInventory().main.size() && left > 0; i++) {
			ItemStack s = this.clone.getInventory().main.get(i);
			if (s.isEmpty() || !this.bringWhat.test(s)) {
				continue;
			}
			ItemStack moving = s.split(Math.min(left, s.getCount()));
			int before = moving.getCount();
			to.pickupItem(moving);
			left -= before - moving.getCount();
			if (!moving.isEmpty()) {
				s.grow(moving.getCount());
				break;
			}
		}
		this.clone.getInventory().syncHeld();
		this.clone.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
		CloneTalk.reply(to, "Thanks, " + this.clone.getCloneName() + "!", null);
		this.bringTo = null;
	}

	// ---------------------------------------------------------------- getting out of caves

	/**
	 * Ground level at a column: the first free spot above the highest solid block. Leaves don't count, and nor do
	 * barriers (invisible walls and roofs in maps), which aren't ground anyone stands on.
	 */
	static int surfaceAt(final ServerLevel level, final int x, final int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x,
			level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		while (pos.getY() > level.getMinY()) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(net.minecraft.world.level.block.Blocks.BARRIER) && !state.getCollisionShape(level, pos).isEmpty()) {
				break;
			}
			pos.move(Direction.DOWN);
		}
		return pos.getY() + 1;
	}

	/** The ground the world is made of (what caves are carved out of), as opposed to anything built, or trees. */
	public static boolean naturalGround(final BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.TURNS_INTO_DIRT_PATH)
			|| state.is(BlockTags.SAND) || state.is(BlockTags.BADLANDS_TERRACOTTA) || state.is(ConventionalBlockTags.ORES)
			|| state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SANDSTONE)
			|| state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK)
			|| state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PACKED_ICE);
	}

	/** The first solid block above the clone's head, if there's one before the open sky. */
	private @Nullable BlockState roof(final ServerLevel level, final BlockPos feet) {
		int top = surfaceAt(level, feet.getX(), feet.getZ());
		for (BlockPos pos = feet.above(2); pos.getY() < top; pos = pos.above()) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(Blocks.BARRIER) && !state.getCollisionShape(level, pos).isEmpty()) {
				return state;
			}
		}
		return null;
	}

	/**
	 * Underground (natural rock or earth overhead), or at the bottom of a natural pit or ravine (ground well above
	 * it on most sides). Never inside a building or under a tree (the roof has to be natural ground), and never in a
	 * dimension with a roof (the Nether is all "underground").
	 */
	public boolean belowGround(final ServerLevel level) {
		if (level.dimensionType().hasCeiling()) {
			return false;
		}
		BlockPos feet = this.clone.blockPosition();
		if (!level.isLoaded(feet)) {
			return false;
		}
		if (surfaceAt(level, feet.getX(), feet.getZ()) > feet.getY() + 2) {
			BlockState roof = this.roof(level, feet);
			return roof != null && naturalGround(roof);
		}
		// In a hole: the ground right around it two or more higher on most sides (the top of a staircase it's dug, a
		// shaft...), so it can't just walk out.
		int walls = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos side = feet.relative(dir, 2);
			if (level.isLoaded(side) && surfaceAt(level, side.getX(), side.getZ()) > feet.getY() + 1) {
				walls++;
			}
		}
		if (walls >= 3) {
			return true;
		}
		int higher = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos side = feet.relative(dir, 8);
			if (!level.isLoaded(side)) {
				continue;
			}
			int top = surfaceAt(level, side.getX(), side.getZ());
			if (top > feet.getY() + 6 && naturalGround(level.getBlockState(new BlockPos(side.getX(), top - 1, side.getZ())))) {
				higher++;
			}
		}
		return higher >= 3;
	}

	/** A spot on the surface 10 blocks away, where the climb is shortest (and it isn't water or lava). */
	private @Nullable BlockPos pickSurfaceGoal(final ServerLevel level) {
		BlockPos feet = this.clone.blockPosition();
		BlockPos best = null;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos column = feet.relative(dir, 10);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = new BlockPos(column.getX(), surfaceAt(level, column.getX(), column.getZ()), column.getZ());
			if (top.getY() <= feet.getY() || this.failed.contains(top) || !level.getFluidState(top.below()).isEmpty()
				|| !level.getFluidState(top).isEmpty()) {
				continue;
			}
			if (best == null || top.getY() < best.getY()) {
				best = top;
			}
		}
		return best;
	}

	/**
	 * Gets out of a cave like a player would: walks out if there's a way, otherwise digs a staircase up to the
	 * surface (never into lava or water: digging turns away from them).
	 */
	private void getOut(final ServerLevel level) {
		if (this.surfaceGoal == null || this.failed.contains(this.surfaceGoal)) {
			// A mine staircase nearby is the easy way up.
			MineStairs.Stairs stairs = MineStairs.up(level, this.clone.blockPosition(), this.brokenStairs);
			this.surfaceGoal = stairs != null && !this.failed.contains(stairs.top()) ? stairs.top() : this.pickSurfaceGoal(level);
			this.diggingOut = false;
			this.walkOutFrom = null;
			this.walkOutStuck = 0;
			this.resetProgress();
			if (this.surfaceGoal == null) {
				this.explore(level);
				return;
			}
		}
		BlockPos goal = this.surfaceGoal;
		if (!this.diggingOut) {
			// Judged by moving along the way out, not by getting nearer as the crow flies: a tunnel can wind about.
			BlockPos feet = this.clone.blockPosition();
			if (this.walkOutFrom == null || !feet.closerThan(this.walkOutFrom, 1.5)) {
				this.walkOutFrom = feet;
				this.walkOutStuck = 0;
			} else {
				this.walkOutStuck += THINK_INTERVAL;
			}
			if (this.clone.getNavigation().isDone()) {
				net.minecraft.world.level.pathfinder.Path path = this.clone.getNavigation().createPath(goal, 1);
				// No path at all just means it can't plan one right now (say, it's in mid-air): try again shortly.
				// A path that doesn't reach the surface means there's no way out.
				if (path != null && !path.canReach()) {
					this.diggingOut = true;
				} else if (path != null) {
					this.clone.getNavigation().moveTo(path, 1.0);
				}
			}
			// There's meant to be a way, but it isn't getting anywhere along it.
			if (this.walkOutStuck >= 100) {
				this.diggingOut = true;
			}
			if (!this.diggingOut) {
				return;
			}
			this.clone.getNavigation().stop();
			this.resetProgress();
			this.clone.showMessage("Digging my way out");
		}
		// Only through natural ground: it turns away from anything built (or a tree's roots and logs) and digs another way.
		this.naturalOnly = true;
		try {
			this.digToward(level, goal);
		} finally {
			this.naturalOnly = false;
		}
	}

	private void explore(final ServerLevel level) {
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
		// Up on the surface, look around up there: a spot under open sky, not down a cave entrance.
		boolean onSurface = !this.belowGround(level);
		Vec3 pos = null;
		for (int tries = 0; tries < 8; tries++) {
			Vec3 candidate = DefaultRandomPos.getPos(this.clone, 20, 7);
			if (candidate == null || !level.isLoaded(BlockPos.containing(candidate))) {
				continue;
			}
			pos = candidate;
			BlockPos spot = BlockPos.containing(candidate);
			if (!onSurface || surfaceAt(level, spot.getX(), spot.getZ()) <= spot.getY() + 1) {
				break;
			}
		}
		if (pos != null) {
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
