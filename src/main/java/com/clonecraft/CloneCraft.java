package com.clonecraft;

import com.clonecraft.block.AntennaBlock;
import com.clonecraft.block.CentrifugeBlock;
import com.clonecraft.block.CentrifugeBlockEntity;
import com.clonecraft.block.LifeInducerBlock;
import com.clonecraft.block.LifeInducerBlockEntity;
import com.clonecraft.block.SterilizerBlock;
import com.clonecraft.block.SterilizerBlockEntity;
import com.clonecraft.clone.CloneOption;
import com.clonecraft.dna.Dna;
import com.clonecraft.dna.DnaItems;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.entity.CloneSpawnEggEntity;
import com.clonecraft.item.CloneSpawnEggItem;
import com.clonecraft.item.NeedleItem;
import com.clonecraft.item.SchematicSelectorItem;
import com.clonecraft.item.TestTubeItem;
import com.clonecraft.menu.CentrifugeMenu;
import com.clonecraft.menu.CloneMenu;
import com.clonecraft.menu.LifeInducerMenu;
import com.clonecraft.mixin.MobAccessor;
import com.clonecraft.network.ServerNetworking;
import com.clonecraft.recipe.DnaRecipe;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CloneCraft Remastered: a rebuild of Jamezo97's CloneCraft for Minecraft 26.3.
 * <p>
 * How to make a clone, the original way:
 * <ol>
 * <li>Craft a Needle, take some blood (right-click yourself in the air for human blood, or hit a creature).</li>
 * <li>Craft the blood needle with a Test Tube. Spin the test tube of blood in a Centrifuge.</li>
 * <li>Craft a clean Needle with the separated blood to get DNA, then craft the DNA needle with an Empty Egg.</li>
 * <li>Charge the unactivated spawn egg in a Life Inducer, powered by lightning through Antennas.</li>
 * <li>Throw or place the spawn egg. Right-click your clone to control it.</li>
 * </ol>
 * Used needles and test tubes come back dirty. Clean them in a Sterilizer (water + fire underneath).
 */
public class CloneCraft implements ModInitializer {
	public static final String MOD_ID = "clonecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// ---------------------------------------------------------------- data components

	public static final DataComponentType<Dna> DNA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("dna"),
		DataComponentType.<Dna>builder().persistent(Dna.CODEC).networkSynchronized(Dna.STREAM_CODEC).build());
	public static final DataComponentType<Unit> DIRTY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("dirty"),
		DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(Unit.INSTANCE)).build());

	// ---------------------------------------------------------------- entities

	public static final ResourceKey<EntityType<?>> CLONE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("clone"));
	public static final EntityType<CloneEntity> CLONE = Registry.register(BuiltInRegistries.ENTITY_TYPE, CLONE_KEY,
		EntityType.Builder.<CloneEntity>of(CloneEntity::new, MobCategory.MISC).noLootTable().sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build(CLONE_KEY));

	public static final ResourceKey<EntityType<?>> SPAWN_EGG_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("spawn_egg"));
	public static final EntityType<CloneSpawnEggEntity> SPAWN_EGG_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, SPAWN_EGG_ENTITY_KEY,
		EntityType.Builder.<CloneSpawnEggEntity>of(CloneSpawnEggEntity::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10)
			.build(SPAWN_EGG_ENTITY_KEY));

	// ---------------------------------------------------------------- blocks

	public static final Block STERILIZER = block("sterilizer", SterilizerBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).noOcclusion());
	public static final Block CENTRIFUGE = block("centrifuge", CentrifugeBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).noOcclusion());
	public static final Block LIFE_INDUCER = block("life_inducer", LifeInducerBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(LifeInducerBlock.POWERED) ? 7 : 0));
	public static final Block ANTENNA = block("antenna", AntennaBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).noOcclusion().randomTicks()
			.lightLevel(s -> s.getValue(AntennaBlock.CHARGED) ? 12 : 0));

	public static final BlockEntityType<SterilizerBlockEntity> STERILIZER_BE = blockEntity("sterilizer", SterilizerBlockEntity::new, STERILIZER);
	public static final BlockEntityType<CentrifugeBlockEntity> CENTRIFUGE_BE = blockEntity("centrifuge", CentrifugeBlockEntity::new, CENTRIFUGE);
	public static final BlockEntityType<LifeInducerBlockEntity> LIFE_INDUCER_BE = blockEntity("life_inducer", LifeInducerBlockEntity::new, LIFE_INDUCER);

	// ---------------------------------------------------------------- items

	public static final Item NEEDLE = item("needle", p -> new NeedleItem(NeedleItem.Kind.EMPTY, p), new Item.Properties());
	public static final Item BLOOD_NEEDLE = item("blood_needle", p -> new NeedleItem(NeedleItem.Kind.BLOOD, p), new Item.Properties().stacksTo(1));
	public static final Item DNA_NEEDLE = item("dna_needle", p -> new NeedleItem(NeedleItem.Kind.DNA, p), new Item.Properties().stacksTo(1));
	public static final Item STEM_CELL_NEEDLE = item("stem_cell_needle", p -> new NeedleItem(NeedleItem.Kind.STEM_CELLS, p), new Item.Properties().stacksTo(16));
	public static final Item TEST_TUBE = item("test_tube", p -> new TestTubeItem(TestTubeItem.Kind.EMPTY, p), new Item.Properties());
	public static final Item BLOOD_TEST_TUBE = item("blood_test_tube", p -> new TestTubeItem(TestTubeItem.Kind.BLOOD, p), new Item.Properties().stacksTo(1));
	public static final Item SEPARATED_TEST_TUBE = item("separated_test_tube", p -> new TestTubeItem(TestTubeItem.Kind.SEPARATED, p), new Item.Properties().stacksTo(1));
	public static final Item EMPTY_EGG = item("empty_egg", Item::new, new Item.Properties());
	public static final Item SPAWN_EGG = item("unactivated_spawn_egg", p -> new CloneSpawnEggItem(false, p), new Item.Properties());
	public static final Item ACTIVE_SPAWN_EGG = item("spawn_egg", p -> new CloneSpawnEggItem(true, p), new Item.Properties());
	public static final Item SCHEMATIC_SELECTOR = item("schematic_selector", SchematicSelectorItem::new, new Item.Properties().stacksTo(1));

	public static final Item STERILIZER_ITEM = blockItem(STERILIZER);
	public static final Item CENTRIFUGE_ITEM = blockItem(CENTRIFUGE);
	public static final Item LIFE_INDUCER_ITEM = blockItem(LIFE_INDUCER);
	public static final Item ANTENNA_ITEM = blockItem(ANTENNA);

	// ---------------------------------------------------------------- menus

	public static final MenuType<CentrifugeMenu> CENTRIFUGE_MENU = Registry.register(BuiltInRegistries.MENU, id("centrifuge"),
		new MenuType<>(CentrifugeMenu::new, FeatureFlags.VANILLA_SET));
	public static final MenuType<LifeInducerMenu> LIFE_INDUCER_MENU = Registry.register(BuiltInRegistries.MENU, id("life_inducer"),
		new MenuType<>(LifeInducerMenu::new, FeatureFlags.VANILLA_SET));
	public static final ExtendedMenuType<CloneMenu, Integer> CLONE_MENU = Registry.register(BuiltInRegistries.MENU, id("clone"),
		new ExtendedMenuType<>(CloneMenu::fromNetwork, ByteBufCodecs.VAR_INT));

	// ---------------------------------------------------------------- creative tab

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("clonecraft"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.clonecraft"))
			.icon(() -> DnaItems.filled(ACTIVE_SPAWN_EGG, Dna.human()))
			.displayItems((params, output) -> {
				output.accept(NEEDLE);
				output.accept(DnaItems.dirty(NEEDLE));
				output.accept(TEST_TUBE);
				output.accept(DnaItems.dirty(TEST_TUBE));
				output.accept(EMPTY_EGG);
				output.accept(STERILIZER_ITEM);
				output.accept(CENTRIFUGE_ITEM);
				output.accept(LIFE_INDUCER_ITEM);
				output.accept(ANTENNA_ITEM);
				output.accept(SCHEMATIC_SELECTOR);
				output.accept(DnaItems.dirty(STEM_CELL_NEEDLE));
				Dna human = Dna.human();
				output.accept(DnaItems.filled(BLOOD_NEEDLE, human));
				output.accept(DnaItems.filled(BLOOD_TEST_TUBE, human));
				output.accept(DnaItems.filled(SEPARATED_TEST_TUBE, human));
				output.accept(DnaItems.filled(DNA_NEEDLE, human));
				output.accept(eggOf(SPAWN_EGG, human));
				output.accept(eggOf(ACTIVE_SPAWN_EGG, human));
				ItemStack dan = eggOf(ACTIVE_SPAWN_EGG, human);
				dan.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("DanTDM"));
				output.accept(dan);
				ItemStack tray = eggOf(ACTIVE_SPAWN_EGG, human);
				tray.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Trayaurus"));
				output.accept(tray);
				// "CloneCraft All": spawn eggs for every creature.
				for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
					if (DefaultAttributes.hasSupplier(type) && type != net.minecraft.world.entity.EntityTypes.PLAYER && type != net.minecraft.world.entity.EntityTypes.ARMOR_STAND && type != net.minecraft.world.entity.EntityTypes.MANNEQUIN && type != CLONE) {
						output.accept(eggOf(ACTIVE_SPAWN_EGG, new Dna(BuiltInRegistries.ENTITY_TYPE.getKey(type))));
					}
				}
			})
			.build());

	private static ItemStack eggOf(final Item egg, final Dna dna) {
		ItemStack stack = new ItemStack(egg);
		stack.set(DNA, dna);
		return stack;
	}

	// ---------------------------------------------------------------- setup

	@Override
	public void onInitialize() {
		for (var entry : DnaRecipe.SERIALIZERS.entrySet()) {
			Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("dna_" + entry.getKey().name().toLowerCase(java.util.Locale.ROOT)), entry.getValue());
		}

		FabricDefaultAttributeRegistry.register(CLONE, CloneEntity.createAttributes());
		ServerNetworking.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> CloneCommands.register(dispatcher));

		// Talk to clones with "\" in chat.
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> !ChatCommands.handle(sender, message.signedContent()));

		// Hostile mobs attack clones that are willing to fight, just like they attack players.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Monster monster) {
				var targets = ((MobAccessor)monster).clonecraft$getTargetSelector();
				boolean already = targets.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof CloneTargetGoal);
				if (!already) {
					targets.addGoal(3, new CloneTargetGoal(monster));
				}
			}
		});

		LOGGER.info("CloneCraft Remastered loaded - time to clone yourself!");
	}

	/** Makes a monster go after nearby clones that have "Fight" turned on. */
	public static class CloneTargetGoal extends NearestAttackableTargetGoal<CloneEntity> {
		public CloneTargetGoal(final Monster monster) {
			super(monster, CloneEntity.class, true, (target, level) -> target instanceof CloneEntity c && c.hasOption(CloneOption.FIGHT));
		}
	}

	// ---------------------------------------------------------------- helpers

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static Block block(final String name, final Function<BlockBehaviour.Properties, Block> factory, final BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item item(final String name, final Function<Item.Properties, Item> factory, final Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item blockItem(final Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> blockEntity(
		final String name, final BlockEntityType.BlockEntitySupplier<T> factory, final Block block) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id(name), new BlockEntityType<>(factory, Set.of(block)));
	}
}
