package com.clonecraft.item;

import com.clonecraft.dna.Dna;
import com.clonecraft.dna.DnaSpawner;
import com.clonecraft.entity.CloneSpawnEggEntity;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A spawn egg made from DNA. It starts "unactivated" and has to be charged with lightning in a Life Inducer.
 * Once active, place it on the ground or throw it to hatch the creature. Rename it in an anvil to name your clone
 * (the name is also the player whose skin the clone wears).
 */
public class CloneSpawnEggItem extends Item {
	public final boolean activated;

	public CloneSpawnEggItem(final boolean activated, final Item.Properties properties) {
		super(properties);
		this.activated = activated;
	}

	@Override
	public Component getName(final ItemStack stack) {
		Dna dna = Dna.get(stack);
		Component creature = dna == null ? Component.literal("?") : dna.displayName().copy().withColor(dna.color());
		return Component.translatable(this.activated ? "item.clonecraft.spawn_egg.active" : "item.clonecraft.spawn_egg.unactivated", creature);
	}

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag flag) {
		builder.accept(Component.translatable(this.activated ? "tooltip.clonecraft.spawn_egg.active" : "tooltip.clonecraft.spawn_egg.unactivated")
			.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public boolean isFoil(final ItemStack stack) {
		return !this.activated;
	}

	@Override
	public InteractionResult useOn(final UseOnContext context) {
		if (!this.activated) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		if (level instanceof ServerLevel serverLevel) {
			BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
			ItemStack stack = context.getItemInHand();
			if (DnaSpawner.spawn(serverLevel, stack, Vec3.atBottomCenterOf(pos), context.getPlayer()) != null) {
				if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
					stack.shrink(1);
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (!this.activated) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
		if (level instanceof ServerLevel serverLevel) {
			Projectile.spawnProjectileFromRotation(CloneSpawnEggEntity::new, serverLevel, stack, player, 0.0F, 1.5F, 1.0F);
		}
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
