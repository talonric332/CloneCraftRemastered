package com.clonecraft.item;

import com.clonecraft.network.RemoteControl;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Clone Remote. Right-click to see every clone you own, manage them from any distance,
 * change what they're doing, and teleport one or all of them to you.
 */
public class CloneRemoteItem extends Item {
	public CloneRemoteItem(final Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.clonecraft.clone_remote.1").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.clonecraft.clone_remote.2").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			RemoteControl.sendList(serverPlayer, true);
		}
		return InteractionResult.SUCCESS;
	}
}
