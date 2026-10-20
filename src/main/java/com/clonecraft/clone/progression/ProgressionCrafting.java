package com.clonecraft.clone.progression;

import com.clonecraft.clone.CloneInventory;
import com.clonecraft.entity.CloneEntity;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The recipes a clone on Game Progression knows, made from its inventory. Anything bigger than a 2x2 grid (tools, armour,
 * furnace, bed, chest) needs a placed crafting table within reach, like a player. Smelting needs a furnace in the
 * inventory and real fuel, and takes as long as a furnace.
 */
public final class ProgressionCrafting {
	private ProgressionCrafting() {}

	/** One ingredient. Planks and sticks are made from logs on the spot when a recipe needs them. */
	public record Ingredient(Predicate<ItemStack> test, int count, Kind kind) {
		public enum Kind { OTHER, PLANKS, STICKS }

		static Ingredient of(final Item item, final int count) {
			return new Ingredient(s -> s.is(item), count, item == Items.STICK ? Kind.STICKS : Kind.OTHER);
		}

		static Ingredient of(final TagKey<Item> tag, final int count) {
			return new Ingredient(s -> s.is(tag), count, tag == ItemTags.PLANKS ? Kind.PLANKS : Kind.OTHER);
		}
	}

	public record Recipe(Item result, int resultCount, boolean needsTable, List<Ingredient> ingredients) {}

	private static final Ingredient STICKS_1 = Ingredient.of(Items.STICK, 1);
	private static final Ingredient STICKS_2 = Ingredient.of(Items.STICK, 2);

	public static final Recipe STICKS = new Recipe(Items.STICK, 4, false, List.of(Ingredient.of(ItemTags.PLANKS, 2)));
	public static final Recipe CRAFTING_TABLE = new Recipe(Items.CRAFTING_TABLE, 1, false, List.of(Ingredient.of(ItemTags.PLANKS, 4)));
	public static final Recipe WOODEN_PICKAXE = new Recipe(Items.WOODEN_PICKAXE, 1, true, List.of(Ingredient.of(ItemTags.PLANKS, 3), STICKS_2));
	public static final Recipe BED = new Recipe(Items.BED.white(), 1, true, List.of(Ingredient.of(ItemTags.WOOL, 3), Ingredient.of(ItemTags.PLANKS, 3)));
	public static final Recipe WOODEN_SWORD = new Recipe(Items.WOODEN_SWORD, 1, true, List.of(Ingredient.of(ItemTags.PLANKS, 2), STICKS_1));
	public static final Recipe STONE_PICKAXE =new Recipe(Items.STONE_PICKAXE, 1, true, List.of(Ingredient.of(ItemTags.STONE_TOOL_MATERIALS, 3), STICKS_2));
	public static final Recipe STONE_SWORD = new Recipe(Items.STONE_SWORD, 1, true, List.of(Ingredient.of(ItemTags.STONE_TOOL_MATERIALS, 2), STICKS_1));
	public static final Recipe FURNACE = new Recipe(Items.FURNACE, 1, true, List.of(Ingredient.of(ItemTags.STONE_CRAFTING_MATERIALS, 8)));
	public static final Recipe IRON_PICKAXE = iron(Items.IRON_PICKAXE, 3, STICKS_2);
	public static final Recipe IRON_SWORD = iron(Items.IRON_SWORD, 2, STICKS_1);
	public static final Recipe IRON_AXE = iron(Items.IRON_AXE, 3, STICKS_2);
	public static final Recipe IRON_SHOVEL = iron(Items.IRON_SHOVEL, 1, STICKS_2);
	public static final Recipe IRON_HELMET = iron(Items.IRON_HELMET, 5, null);
	public static final Recipe IRON_CHESTPLATE = iron(Items.IRON_CHESTPLATE, 8, null);
	public static final Recipe IRON_LEGGINGS = iron(Items.IRON_LEGGINGS, 7, null);
	public static final Recipe IRON_BOOTS = iron(Items.IRON_BOOTS, 4, null);
	public static final Recipe DIAMOND_PICKAXE = new Recipe(Items.DIAMOND_PICKAXE, 1, true, List.of(Ingredient.of(Items.DIAMOND, 3), STICKS_2));
	public static final Recipe CHEST = new Recipe(Items.CHEST, 1, true, List.of(Ingredient.of(ItemTags.PLANKS, 8)));
	public static final Recipe BLAZE_POWDER =new Recipe(Items.BLAZE_POWDER, 2, false, List.of(Ingredient.of(Items.BLAZE_ROD, 1)));
	public static final Recipe EYE_OF_ENDER = new Recipe(Items.ENDER_EYE, 1, false, List.of(Ingredient.of(Items.ENDER_PEARL, 1), Ingredient.of(Items.BLAZE_POWDER, 1)));

	/** Full iron gear, in the order the clone makes it: pickaxe first (it needs it for diamonds), then weapon, armour, the rest. */
	public static final List<Recipe> IRON_GEAR = List.of(IRON_PICKAXE, IRON_SWORD, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_HELMET, IRON_BOOTS, IRON_AXE,
		IRON_SHOVEL);

	private static Recipe iron(final Item result, final int ingots, final Ingredient sticks) {
		Ingredient iron = Ingredient.of(Items.IRON_INGOT, ingots);
		return new Recipe(result, 1, true, sticks == null ? List.of(iron) : List.of(iron, sticks));
	}

	// ---------------------------------------------------------------- crafting

	public static int count(final CloneEntity clone, final Predicate<ItemStack> test) {
		return clone.getInventory().count(test);
	}

	public static boolean has(final CloneEntity clone, final Item item) {
		return count(clone, s -> s.is(item)) > 0;
	}

	/** Has everything for the recipe right now (planks and sticks already made), and a crafting table if it needs one. */
	public static boolean canCraft(final CloneEntity clone, final Recipe recipe, final boolean atTable) {
		if (recipe.needsTable() && !atTable) {
			return false;
		}
		for (Ingredient ingredient : recipe.ingredients()) {
			if (count(clone, ingredient.test()) < ingredient.count()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Could make the recipe from what it carries, counting logs it can still turn into planks and sticks. Doesn't check
	 * for a crafting table: that's how the clone knows it's worth walking to one.
	 */
	public static boolean affordable(final CloneEntity clone, final Recipe recipe) {
		int planksWanted = 0;
		int sticksWanted = 0;
		for (Ingredient ingredient : recipe.ingredients()) {
			switch (ingredient.kind()) {
				case PLANKS -> planksWanted += ingredient.count();
				case STICKS -> sticksWanted += ingredient.count();
				case OTHER -> {
					if (count(clone, ingredient.test()) < ingredient.count()) {
						return false;
					}
				}
			}
		}
		int sticksShort = Math.max(0, sticksWanted - count(clone, s -> s.is(Items.STICK)));
		planksWanted += 2 * ((sticksShort + 3) / 4);
		return count(clone, s -> s.is(ItemTags.PLANKS)) + count(clone, s -> s.is(ItemTags.LOGS)) * 4 >= planksWanted;
	}

	/** Makes the item if the clone has everything for it right now. Returns true if it did. */
	public static boolean craft(final CloneEntity clone, final Recipe recipe, final boolean atTable) {
		if (!canCraft(clone, recipe, atTable)) {
			return false;
		}
		for (Ingredient ingredient : recipe.ingredients()) {
			for (int i = 0; i < ingredient.count(); i++) {
				clone.getInventory().consumeOne(ingredient.test());
			}
		}
		give(clone, new ItemStack(recipe.result(), recipe.resultCount()));
		return true;
	}

	/**
	 * Makes the item, first turning logs into the planks and sticks it needs, but only if it can afford the whole thing
	 * (so wood isn't used up on sticks for a tool the clone can't make yet). Recipes bigger than 2x2 need atTable: a
	 * placed crafting table within reach. Returns true if it made the item.
	 */
	public static boolean make(final CloneEntity clone, final Recipe recipe, final boolean atTable) {
		if ((recipe.needsTable() && !atTable) || !affordable(clone, recipe)) {
			return false;
		}
		for (Ingredient ingredient : recipe.ingredients()) {
			if (ingredient.kind() == Ingredient.Kind.STICKS) {
				while (count(clone, ingredient.test()) < ingredient.count()) {
					while (count(clone, s -> s.is(ItemTags.PLANKS)) < 2 && makePlanks(clone)) {
						// one log at a time
					}
					if (!craft(clone, STICKS, false)) {
						return false;
					}
				}
			}
		}
		int planksWanted = 0;
		for (Ingredient ingredient : recipe.ingredients()) {
			if (ingredient.kind() == Ingredient.Kind.PLANKS) {
				planksWanted += ingredient.count();
			}
		}
		while (count(clone, s -> s.is(ItemTags.PLANKS)) < planksWanted && makePlanks(clone)) {
			// one log at a time
		}
		return craft(clone, recipe, atTable);
	}

	/** Turns one log into four planks of the same wood. Returns false if the clone has no logs. */
	public static boolean makePlanks(final CloneEntity clone) {
		int slot = clone.getInventory().find(s -> s.is(ItemTags.LOGS));
		if (slot < 0) {
			return false;
		}
		Item planks = planksFor(clone.getInventory().main.get(slot).getItem());
		clone.getInventory().consumeOne(s -> s.is(ItemTags.LOGS));
		give(clone, new ItemStack(planks, 4));
		return true;
	}

	/** oak_log, stripped_oak_wood, crimson_stem... become oak_planks / crimson_planks. Anything unusual becomes oak planks. */
	static Item planksFor(final Item log) {
		String path = BuiltInRegistries.ITEM.getKey(log).getPath().replace("stripped_", "");
		for (String suffix : new String[]{"_log", "_wood", "_stem", "_hyphae"}) {
			if (path.endsWith(suffix)) {
				Identifier id = BuiltInRegistries.ITEM.getKey(log).withPath(path.substring(0, path.length() - suffix.length()) + "_planks");
				Item planks = BuiltInRegistries.ITEM.getValue(id);
				if (planks != Items.AIR && new ItemStack(planks).is(ItemTags.PLANKS)) {
					return planks;
				}
			}
		}
		return Items.OAK_PLANKS;
	}

	/** Puts an item in the clone's inventory (wearing armour if it's better), dropping whatever doesn't fit. */
	public static void give(final CloneEntity clone, final ItemStack stack) {
		clone.pickupItem(stack);
		if (!stack.isEmpty() && clone.level() instanceof ServerLevel level) {
			clone.spawnAtLocation(level, stack);
		}
	}

	// ---------------------------------------------------------------- smelting

	public static final int SMELT_TIME = 200;

	private static final Map<Item, Item> SMELTS = Map.of(
		Items.RAW_IRON, Items.IRON_INGOT,
		Items.BEEF, Items.COOKED_BEEF,
		Items.PORKCHOP, Items.COOKED_PORKCHOP,
		Items.CHICKEN, Items.COOKED_CHICKEN,
		Items.MUTTON, Items.COOKED_MUTTON,
		Items.RABBIT, Items.COOKED_RABBIT,
		Items.COD, Items.COOKED_COD,
		Items.SALMON, Items.COOKED_SALMON);

	/** Planks and logs kept back from the furnace, so there's always wood for sticks and tools. */
	private static final int WOOD_RESERVE = 8;

	public static boolean isSmeltable(final ItemStack stack) {
		return SMELTS.containsKey(stack.getItem());
	}

	/** The clone's portable furnace: how much fuel is burning, and how far along the current item is. */
	public static class Furnace {
		/** Items that the fuel already burning can still smelt. */
		float fuel;
		int progress;

		/** Called every tick. Smelts one item every 10 seconds while the clone has a furnace, something to smelt and fuel. */
		public void tick(final CloneEntity clone, final boolean hungry) {
			if (!has(clone, Items.FURNACE)) {
				this.progress = 0;
				return;
			}
			Predicate<ItemStack> input = hungry
				? s -> isSmeltable(s) && s.has(net.minecraft.core.component.DataComponents.FOOD)
				: ProgressionCrafting::isSmeltable;
			int slot = clone.getInventory().find(input);
			if (slot < 0 && hungry) {
				input = ProgressionCrafting::isSmeltable;
				slot = clone.getInventory().find(input);
			}
			if (slot < 0) {
				this.progress = 0;
				return;
			}
			if (this.fuel <= 0.0F && !this.burnFuel(clone)) {
				return;
			}
			if (++this.progress < SMELT_TIME) {
				return;
			}
			this.progress = 0;
			this.fuel -= 1.0F;
			Item result = SMELTS.get(clone.getInventory().main.get(slot).getItem());
			Predicate<ItemStack> from = input;
			Item raw = clone.getInventory().main.get(slot).getItem();
			clone.getInventory().consumeOne(s -> s.is(raw) && from.test(s));
			give(clone, new ItemStack(result));
		}

		/** Puts one fuel item in. Coal first; wood only beyond what the clone keeps back for tools. */
		private boolean burnFuel(final CloneEntity clone) {
			CloneInventory inv = clone.getInventory();
			if (inv.consumeOne(s -> s.is(ItemTags.COALS))) {
				this.fuel += 8.0F;
				return true;
			}
			if (count(clone, s -> s.is(ItemTags.LOGS)) > 0 && count(clone, s -> s.is(ItemTags.PLANKS)) + count(clone, s -> s.is(ItemTags.LOGS)) * 4 > WOOD_RESERVE
				&& inv.consumeOne(s -> s.is(ItemTags.LOGS))) {
				this.fuel += 1.5F;
				return true;
			}
			if (count(clone, s -> s.is(ItemTags.PLANKS)) > WOOD_RESERVE && inv.consumeOne(s -> s.is(ItemTags.PLANKS))) {
				this.fuel += 1.5F;
				return true;
			}
			return false;
		}

		/** How many more items the clone could smelt with the fuel it carries (and what's already burning). */
		public float fuelAvailable(final CloneEntity clone) {
			return this.fuel + count(clone, s -> s.is(ItemTags.COALS)) * 8.0F;
		}

		public void save(final ValueOutput output) {
			output.putFloat("ProgressionFuel", this.fuel);
			output.putInt("ProgressionSmelt", this.progress);
		}

		public void load(final ValueInput input) {
			this.fuel = input.getFloatOr("ProgressionFuel", 0.0F);
			this.progress = input.getIntOr("ProgressionSmelt", 0);
		}
	}
}
