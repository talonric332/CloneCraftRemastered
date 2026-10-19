package com.clonecraft.recipe;

import com.clonecraft.CloneCraft;
import com.clonecraft.dna.Dna;
import com.clonecraft.dna.DnaItems;
import com.clonecraft.item.NeedleItem;
import com.clonecraft.item.TestTubeItem;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * The original CloneCraft crafting steps that carry DNA from one item to the next:
 * <ul>
 * <li>Blood needle + clean empty test tube = test tube of blood</li>
 * <li>Clean empty needle + separated blood = DNA needle</li>
 * <li>DNA needle + empty egg = unactivated spawn egg</li>
 * <li>Clean empty needle + separated human blood + bone meal = stem cells</li>
 * <li>Any filled needle or tube on its own = emptied (dirty) again</li>
 * </ul>
 * The used needle or tube is handed back empty and dirty, so it has to be sterilized.
 */
public class DnaRecipe extends CustomRecipe {
	public enum Type { NEEDLE_TO_TUBE, TUBE_TO_NEEDLE, EGG, STEM_CELLS, CLEAR }

	public static final Map<Type, RecipeSerializer<DnaRecipe>> SERIALIZERS = new EnumMap<>(Type.class);

	static {
		for (Type type : Type.values()) {
			DnaRecipe instance = new DnaRecipe(type);
			SERIALIZERS.put(type, new RecipeSerializer<>(MapCodec.unit(instance), StreamCodec.<RegistryFriendlyByteBuf, DnaRecipe>unit(instance)));
		}
	}

	private final Type type;

	public DnaRecipe(final Type type) {
		this.type = type;
	}

	private static List<ItemStack> items(final CraftingInput input) {
		List<ItemStack> list = new ArrayList<>();
		for (int i = 0; i < input.size(); i++) {
			if (!input.getItem(i).isEmpty()) {
				list.add(input.getItem(i));
			}
		}
		return list;
	}

	private static ItemStack find(final List<ItemStack> list, final net.minecraft.world.item.Item item) {
		for (ItemStack s : list) {
			if (s.is(item)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean matches(final CraftingInput input, final Level level) {
		return !this.assemble(input).isEmpty();
	}

	@Override
	public ItemStack assemble(final CraftingInput input) {
		List<ItemStack> list = items(input);
		switch (this.type) {
			case NEEDLE_TO_TUBE -> {
				ItemStack needle = find(list, CloneCraft.BLOOD_NEEDLE);
				ItemStack tube = find(list, CloneCraft.TEST_TUBE);
				if (list.size() == 2 && !needle.isEmpty() && !tube.isEmpty() && !DnaItems.isDirty(tube) && Dna.get(needle) != null) {
					return DnaItems.filled(CloneCraft.BLOOD_TEST_TUBE, Dna.get(needle));
				}
			}
			case TUBE_TO_NEEDLE -> {
				ItemStack needle = find(list, CloneCraft.NEEDLE);
				ItemStack tube = find(list, CloneCraft.SEPARATED_TEST_TUBE);
				if (list.size() == 2 && !needle.isEmpty() && !tube.isEmpty() && !DnaItems.isDirty(needle) && Dna.get(tube) != null) {
					return DnaItems.filled(CloneCraft.DNA_NEEDLE, Dna.get(tube));
				}
			}
			case EGG -> {
				ItemStack needle = find(list, CloneCraft.DNA_NEEDLE);
				ItemStack egg = find(list, CloneCraft.EMPTY_EGG);
				if (list.size() == 2 && !needle.isEmpty() && !egg.isEmpty() && Dna.get(needle) != null) {
					ItemStack result = new ItemStack(CloneCraft.SPAWN_EGG);
					result.set(CloneCraft.DNA, Dna.get(needle));
					return result;
				}
			}
			case STEM_CELLS -> {
				ItemStack needle = find(list, CloneCraft.NEEDLE);
				ItemStack tube = find(list, CloneCraft.SEPARATED_TEST_TUBE);
				ItemStack boneMeal = find(list, Items.BONE_MEAL);
				Dna dna = tube.isEmpty() ? null : Dna.get(tube);
				if (list.size() == 3 && !needle.isEmpty() && !boneMeal.isEmpty() && dna != null && dna.isHuman() && !DnaItems.isDirty(needle)) {
					return DnaItems.dirty(CloneCraft.STEM_CELL_NEEDLE);
				}
			}
			case CLEAR -> {
				if (list.size() == 1) {
					ItemStack s = list.get(0);
					boolean filledNeedle = s.getItem() instanceof NeedleItem n && n.kind != NeedleItem.Kind.EMPTY;
					boolean filledTube = s.getItem() instanceof TestTubeItem t && t.kind != TestTubeItem.Kind.EMPTY;
					if (filledNeedle || filledTube) {
						return DnaItems.emptied(s);
					}
				}
			}
		}
		return ItemStack.EMPTY;
	}

	/** Hands back the needle or tube whose contents were used, now dirty. */
	@Override
	public NonNullList<ItemStack> getRemainingItems(final CraftingInput input) {
		NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		for (int i = 0; i < input.size(); i++) {
			ItemStack s = input.getItem(i);
			boolean giveBack = switch (this.type) {
				case NEEDLE_TO_TUBE -> s.is(CloneCraft.BLOOD_NEEDLE);
				case TUBE_TO_NEEDLE, STEM_CELLS -> s.is(CloneCraft.SEPARATED_TEST_TUBE);
				case EGG -> s.is(CloneCraft.DNA_NEEDLE);
				case CLEAR -> false;
			};
			if (giveBack) {
				result.set(i, DnaItems.emptied(s));
			}
		}
		return result;
	}

	@Override
	public RecipeSerializer<DnaRecipe> getSerializer() {
		return SERIALIZERS.get(this.type);
	}
}
