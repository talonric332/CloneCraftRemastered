package com.clonecraft.client.screen;

import com.clonecraft.CloneCraft;
import com.clonecraft.client.ClientNetworking;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.Payloads;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * "Entities To Attack" and "Blocks To Break": a searchable list where you tick what the clone should go after.
 * Quick buttons select everything, all monsters / animals, or all ores / trees, like the original.
 */
public class ChooseListScreen extends CloneSubScreen {
	public enum Kind { ENTITIES, BLOCKS }

	/** One mob or block in the list. group is "mob"/"animal"/"other" for mobs and "ore"/"tree"/"other" for blocks. */
	record Entry(Identifier id, Component name, ItemStack icon, String group) {}

	static final int ROW = 20;

	private final Kind kind;
	private final List<Entry> all;
	private List<Entry> shown;
	private final Set<Identifier> selected = new HashSet<>();
	private boolean loaded;
	private boolean dirty;
	private double scroll;
	private EditBox search;
	private int listTop;
	private int listBottom;

	public ChooseListScreen(final CloneEntity clone, final Kind kind) {
		super(clone, Component.literal(kind == Kind.ENTITIES ? "Entities To Attack" : "Blocks To Break"));
		this.kind = kind;
		this.all = entries(kind);
		this.shown = new ArrayList<>(this.all);
		ClientNetworking.LISTS.remove(clone.getId());
		ClientNetworking.send(new Payloads.CloneAction(clone.getId(), Payloads.CloneAction.REQUEST_LISTS, 0));
	}

	/** Every mob (or block) a clone can be told to attack (or break), sorted by name. Shared with the bulk editor. */
	static List<Entry> entries(final Kind kind) {
		List<Entry> all = new ArrayList<>();
		if (kind == Kind.ENTITIES) {
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				if (!DefaultAttributes.hasSupplier(type) || type == EntityTypes.PLAYER || type == EntityTypes.ARMOR_STAND || type == EntityTypes.MANNEQUIN
					|| type == CloneCraft.CLONE) {
					continue;
				}
				String group = type.getCategory() == MobCategory.MONSTER ? "mob" : (type.getCategory() == MobCategory.MISC ? "other" : "animal");
				all.add(new Entry(BuiltInRegistries.ENTITY_TYPE.getKey(type), type.getDescription(), ItemStack.EMPTY, group));
			}
		} else {
			for (Block block : BuiltInRegistries.BLOCK) {
				var item = block.asItem();
				// Skip blocks with no item, unbreakable blocks, and wall variants (wall signs, wall torches...) that share an item.
				if (item == Items.AIR || block.defaultDestroyTime() < 0 || !(item instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() == block)) {
					continue;
				}
				var state = block.defaultBlockState();
				String group = state.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES) ? "ore" : state.is(BlockTags.LOGS) ? "tree" : "other";
				all.add(new Entry(BuiltInRegistries.BLOCK.getKey(block), block.getName(), new ItemStack(item), group));
			}
		}
		all.sort((a, b) -> a.name.getString().compareToIgnoreCase(b.name.getString()));
		return all;
	}

	@Override
	protected void init() {
		this.listTop = 58;
		this.listBottom = this.height - 34;
		this.search = new EditBox(this.font, this.width / 2 - 100, 30, 200, 20, Component.literal("Search"));
		this.search.setHint(Component.literal("Search..."));
		this.search.setResponder(text -> {
			String q = text.toLowerCase(Locale.ROOT);
			this.shown = this.all.stream().filter(e -> e.name.getString().toLowerCase(Locale.ROOT).contains(q) || e.id.toString().contains(q)).toList();
			this.scroll = 0;
		});
		this.addRenderableWidget(this.search);

		int y = this.height - 26;
		int x = this.width / 2 - 205;
		this.addRenderableWidget(Button.builder(Component.literal("Select All"), b -> this.selectGroup(null)).bounds(x, y, 80, 20).build());
		if (this.kind == Kind.ENTITIES) {
			this.addRenderableWidget(Button.builder(Component.literal("Monsters"), b -> this.selectGroup("mob")).bounds(x + 84, y, 80, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("Animals"), b -> this.selectGroup("animal")).bounds(x + 168, y, 80, 20).build());
		} else {
			this.addRenderableWidget(Button.builder(Component.literal("Ores"), b -> this.selectGroup("ore")).bounds(x + 84, y, 80, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("Trees"), b -> this.selectGroup("tree")).bounds(x + 168, y, 80, 20).build());
		}
		this.addRenderableWidget(Button.builder(Component.literal("Select None"), b -> {
			this.selected.clear();
			this.dirty = true;
		}).bounds(x + 252, y, 80, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(x + 336, y, 74, 20).build());
	}

	private void selectGroup(final String group) {
		for (Entry e : this.all) {
			if (group == null || e.group.equals(group)) {
				this.selected.add(e.id);
			}
		}
		this.dirty = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.loaded) {
			Payloads.CloneLists lists = ClientNetworking.LISTS.get(this.clone.getId());
			if (lists != null) {
				this.selected.addAll(this.kind == Kind.ENTITIES ? lists.attack() : lists.breakBlocks());
				this.loaded = true;
			}
		}
	}

	@Override
	protected void save() {
		if (this.dirty) {
			ClientNetworking.send(new Payloads.SetList(this.clone.getId(), this.kind == Kind.ENTITIES ? 0 : 1, new ArrayList<>(this.selected)));
		}
	}

	private int listLeft() {
		return this.width / 2 - 150;
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		int max = Math.max(0, this.shown.size() * ROW - (this.listBottom - this.listTop));
		this.scroll = Math.max(0, Math.min(max, this.scroll - scrollY * ROW * 2));
		return true;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (mx >= this.listLeft() && mx < this.listLeft() + 300 && my >= this.listTop && my < this.listBottom) {
			int index = (int)((my - this.listTop + this.scroll) / ROW);
			if (index >= 0 && index < this.shown.size()) {
				Identifier id = this.shown.get(index).id;
				if (!this.selected.remove(id)) {
					this.selected.add(id);
				}
				this.dirty = true;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		this.title(g, this.title.getString() + " - " + this.clone.getCloneName() + " (" + this.selected.size() + " selected)");
		int left = this.listLeft();
		g.fill(left - 2, this.listTop - 2, left + 302, this.listBottom + 2, 0x88000000);
		g.enableScissor(left, this.listTop, left + 300, this.listBottom);
		int first = (int)(this.scroll / ROW);
		for (int i = first; i < this.shown.size(); i++) {
			int y = this.listTop + i * ROW - (int)this.scroll;
			if (y > this.listBottom) {
				break;
			}
			Entry e = this.shown.get(i);
			boolean on = this.selected.contains(e.id);
			boolean hover = mouseX >= left && mouseX < left + 300 && mouseY >= y && mouseY < y + ROW && mouseY >= this.listTop && mouseY < this.listBottom;
			g.fill(left, y, left + 300, y + ROW - 1, on ? 0x6633AA33 : (hover ? 0x44FFFFFF : 0x22FFFFFF));
			int textX = left + 6;
			if (!e.icon.isEmpty()) {
				g.item(e.icon, left + 2, y + 1);
				textX = left + 24;
			}
			g.text(this.font, e.name, textX, y + 6, on ? 0xFF88FF88 : 0xFFFFFFFF);
			String mark = on ? "✔" : "";
			g.text(this.font, mark, left + 286, y + 6, 0xFF55FF55);
		}
		g.disableScissor();
		if (!this.loaded) {
			g.centeredText(this.font, "Loading...", this.width / 2, this.listTop + 10, 0xFFFFFF55);
		}
	}
}
