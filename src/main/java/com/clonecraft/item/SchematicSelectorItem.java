package com.clonecraft.item;

import com.clonecraft.network.Payloads;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Schematic Selector (the wooden staff). Right-click a block to pick corner 1, sneak + right-click a block for
 * corner 2, then right-click the air to save everything in between as a schematic your clones can build.
 * Sneak + right-click the air to clear the selection.
 */
public class SchematicSelectorItem extends Item {
	private static final Map<UUID, BlockPos[]> SELECTIONS = new HashMap<>();

	public SchematicSelectorItem(final Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder, final TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.clonecraft.schematic_selector.1").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.clonecraft.schematic_selector.2").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.clonecraft.schematic_selector.3").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public InteractionResult useOn(final UseOnContext context) {
		Player player = context.getPlayer();
		if (player instanceof ServerPlayer serverPlayer) {
			BlockPos[] sel = SELECTIONS.computeIfAbsent(player.getUUID(), k -> new BlockPos[2]);
			BlockPos pos = context.getClickedPos();
			int corner = player.isShiftKeyDown() ? 1 : 0;
			sel[corner] = pos;
			player.sendSystemMessage(Component.literal("Corner " + (corner + 1) + " Selected (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ").")
				.withStyle(ChatFormatting.DARK_GREEN));
			if (corner == 0 && sel[1] == null) {
				player.sendSystemMessage(Component.literal("Now select the second corner by sneaking and right-clicking a block.").withStyle(ChatFormatting.GRAY));
			}
			sync(serverPlayer, sel);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			BlockPos[] sel = SELECTIONS.computeIfAbsent(player.getUUID(), k -> new BlockPos[2]);
			if (player.isShiftKeyDown()) {
				sel[0] = null;
				sel[1] = null;
				player.sendSystemMessage(Component.literal("Cleared Selection").withStyle(ChatFormatting.DARK_GREEN));
				sync(serverPlayer, sel);
			} else if (sel[0] == null) {
				player.sendSystemMessage(Component.literal("You must select the first corner!").withStyle(ChatFormatting.RED));
			} else if (sel[1] == null) {
				player.sendSystemMessage(Component.literal("You must select the second corner!").withStyle(ChatFormatting.RED));
			} else {
				ServerPlayNetworking.send(serverPlayer, new Payloads.OpenSaveSchematic(sel[0], sel[1]));
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** The two corners this player picked, or null if they haven't picked both. */
	public static BlockPos @org.jspecify.annotations.Nullable [] getSelection(final UUID player) {
		BlockPos[] sel = SELECTIONS.get(player);
		return sel == null || sel[0] == null || sel[1] == null ? null : sel.clone();
	}

	public static void clearSelections() {
		SELECTIONS.clear();
	}

	private static void sync(final ServerPlayer player, final BlockPos[] sel) {
		ServerPlayNetworking.send(player, new Payloads.Selection(Optional.ofNullable(sel[0]), Optional.ofNullable(sel[1])));
	}
}
