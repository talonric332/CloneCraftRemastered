package com.clonecraft.item;

import com.clonecraft.CloneCraft;
import com.clonecraft.dna.Dna;
import com.clonecraft.dna.DnaItems;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The needle. Empty needles take blood from whatever you hit (or from yourself when you use it in the air).
 * Blood needles fill test tubes, DNA needles fill empty eggs, and stem cell needles make clones grow.
 */
public class NeedleItem extends Item {
	public enum Kind { EMPTY, BLOOD, DNA, STEM_CELLS }

	public final Kind kind;

	public NeedleItem(final Kind kind, final Item.Properties properties) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public Component getName(final ItemStack stack) {
		Dna dna = Dna.get(stack);
		return switch (this.kind) {
			case EMPTY -> Component.translatable(DnaItems.isDirty(stack) ? "item.clonecraft.needle.dirty" : "item.clonecraft.needle.empty");
			case BLOOD -> dna == null ? super.getName(stack) : Component.translatable("clonecraft.blood", dna.displayName()).withColor(dna.color());
			case DNA -> dna == null ? super.getName(stack) : Component.translatable("clonecraft.dna", dna.displayName()).withColor(dna.color());
			case STEM_CELLS -> Component.translatable("item.clonecraft.stem_cell_needle").withColor(0x9933EE);
		};
	}

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag flag) {
		String key = switch (this.kind) {
			case EMPTY -> DnaItems.isDirty(stack) ? "tooltip.clonecraft.needle.dirty" : "tooltip.clonecraft.needle.empty";
			case BLOOD -> "tooltip.clonecraft.needle.blood";
			case DNA -> "tooltip.clonecraft.needle.dna";
			case STEM_CELLS -> "tooltip.clonecraft.needle.stem_cells";
		};
		builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
	}

	/** Use in the air: take your own blood. */
	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (this.kind == Kind.EMPTY && tryFill(stack, player, player, hand)) {
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Right-click a creature: take its blood. */
	@Override
	public InteractionResult interactLivingEntity(final ItemStack stack, final Player player, final LivingEntity target, final InteractionHand hand) {
		if (this.kind == Kind.EMPTY && tryFill(stack, target, player, hand)) {
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Hitting a creature with an empty needle also takes its blood, like the original mod. */
	@Override
	public void hurtEnemy(final ItemStack stack, final LivingEntity mob, final LivingEntity attacker) {
		if (this.kind == Kind.EMPTY && attacker instanceof Player player) {
			tryFill(stack, mob, player, InteractionHand.MAIN_HAND);
		}
	}

	public static boolean tryFill(final ItemStack stack, final LivingEntity source, final Player user, final InteractionHand hand) {
		if (DnaItems.isDirty(stack)) {
			if (!user.level().isClientSide()) {
				user.sendOverlayMessage(Component.translatable("message.clonecraft.needle_dirty").withStyle(ChatFormatting.RED));
			}
			return false;
		}
		Dna dna = Dna.of(source);
		if (dna == null) {
			return false;
		}
		Level level = user.level();
		level.playSound(null, source.getX(), source.getY(), source.getZ(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 1.0F, 1.4F);
		if (!level.isClientSide()) {
			if (!(source instanceof Player p && p.getAbilities().instabuild)) {
				if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
					source.hurtServer(serverLevel, level.damageSources().generic(), 2.0F);
				}
				source.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 400));
				source.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400));
			}
			DnaItems.exchangeOne(stack, user, DnaItems.filled(CloneCraft.BLOOD_NEEDLE, dna), hand);
		}
		return true;
	}

	@Override
	public boolean isFoil(final ItemStack stack) {
		return this.kind == Kind.STEM_CELLS;
	}

	public static boolean hasDna(final DataComponentGetter getter) {
		return getter.get(CloneCraft.DNA) != null;
	}
}
