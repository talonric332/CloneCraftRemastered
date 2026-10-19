package com.clonecraft.item;

import com.clonecraft.dna.Dna;
import com.clonecraft.dna.DnaItems;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Test tubes hold blood. Spin blood in a Centrifuge to separate it, then draw the DNA out with a clean needle. */
public class TestTubeItem extends Item {
	public enum Kind { EMPTY, BLOOD, SEPARATED }

	public final Kind kind;

	public TestTubeItem(final Kind kind, final Item.Properties properties) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public Component getName(final ItemStack stack) {
		Dna dna = Dna.get(stack);
		return switch (this.kind) {
			case EMPTY -> Component.translatable(DnaItems.isDirty(stack) ? "item.clonecraft.test_tube.dirty" : "item.clonecraft.test_tube.empty");
			case BLOOD -> dna == null ? super.getName(stack) : Component.translatable("clonecraft.blood", dna.displayName()).withColor(dna.color());
			case SEPARATED -> dna == null ? super.getName(stack) : Component.translatable("clonecraft.separated_blood", dna.displayName()).withColor(dna.color());
		};
	}

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag flag) {
		String key = switch (this.kind) {
			case EMPTY -> DnaItems.isDirty(stack) ? "tooltip.clonecraft.test_tube.dirty" : "tooltip.clonecraft.test_tube.empty";
			case BLOOD -> "tooltip.clonecraft.test_tube.blood";
			case SEPARATED -> "tooltip.clonecraft.test_tube.separated";
		};
		builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
	}
}
