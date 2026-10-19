package com.clonecraft.client.screen;

import com.clonecraft.client.ClientNetworking;
import com.clonecraft.client.Outlines;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.Payloads;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * "Things To Build": pick a saved schematic, move and rotate where it goes (press Show to see the outline in the
 * world), then press Build It! Make schematics with the Schematic Selector staff.
 */
public class BuildScreen extends CloneSubScreen {
	/** Remembers where you placed each clone's build, so you can Show it and come back. */
	private static final Map<Integer, Plan> PLANS = new HashMap<>();

	private static final class Plan {
		String schematic = "";
		BlockPos origin;
		int rotation;
		boolean creative;
		int delay = 4;
	}

	private final Plan plan;
	private List<Payloads.SchematicInfo> shown = List.of();
	private EditBox search;
	private Button buildButton;
	private Button creativeButton;
	private double scroll;

	public BuildScreen(final CloneEntity clone) {
		super(clone, Component.literal("Things To Build"));
		this.plan = PLANS.computeIfAbsent(clone.getId(), id -> {
			Plan p = new Plan();
			Direction facing = clone.getDirection();
			p.origin = clone.blockPosition().relative(facing, 2);
			return p;
		});
		ClientNetworking.SCHEMATICS = null;
		ClientNetworking.send(new Payloads.CloneAction(clone.getId(), Payloads.CloneAction.REQUEST_SCHEMATICS, 0));
	}

	@Override
	protected void init() {
		this.search = new EditBox(this.font, 10, 30, 190, 20, Component.literal("Search"));
		this.search.setHint(Component.literal("Search..."));
		this.search.setResponder(t -> this.filter());
		this.addRenderableWidget(this.search);

		int rx = 215;
		int ry = 60;
		this.addRenderableWidget(Button.builder(Component.literal("← X"), b -> this.move(-1, 0, 0)).bounds(rx, ry, 45, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("X →"), b -> this.move(1, 0, 0)).bounds(rx + 50, ry, 45, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("↓ Y"), b -> this.move(0, -1, 0)).bounds(rx, ry + 24, 45, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Y ↑"), b -> this.move(0, 1, 0)).bounds(rx + 50, ry + 24, 45, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("← Z"), b -> this.move(0, 0, -1)).bounds(rx, ry + 48, 45, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Z →"), b -> this.move(0, 0, 1)).bounds(rx + 50, ry + 48, 45, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Rotate"), b -> {
			this.plan.rotation = (this.plan.rotation + 1) % 4;
		}).bounds(rx, ry + 72, 95, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Here (at me)"), b -> {
			this.plan.origin = this.minecraft.player.blockPosition();
		}).bounds(rx, ry + 96, 95, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Show"), b -> {
			Payloads.SchematicInfo info = this.selectedInfo();
			if (info != null) {
				Outlines.showBuild(this.plan.origin, this.rotatedEnd(info), 200);
				this.onClose();
			}
		}).bounds(rx, ry + 120, 95, 20).build());

		if (this.minecraft.player.getAbilities().instabuild) {
			this.creativeButton = this.addRenderableWidget(Button.builder(this.creativeLabel(), b -> {
				this.plan.creative = !this.plan.creative;
				b.setMessage(this.creativeLabel());
			}).bounds(rx, ry + 150, 150, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("Delay -"), b -> this.plan.delay = Math.max(0, this.plan.delay - 1)).bounds(rx, ry + 174, 60, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("Delay +"), b -> this.plan.delay = Math.min(19, this.plan.delay + 1)).bounds(rx + 90, ry + 174, 60, 20).build());
		}

		this.buildButton = this.addRenderableWidget(Button.builder(Component.literal("Build It!"), b -> {
			if (this.clone.getBuildStage() >= 0) {
				ClientNetworking.send(new Payloads.CloneAction(this.clone.getId(), Payloads.CloneAction.STOP_BUILDING, 0));
			} else if (!this.plan.schematic.isEmpty()) {
				ClientNetworking.send(new Payloads.Build(this.clone.getId(), this.plan.schematic, this.plan.origin, this.plan.rotation, this.plan.creative, this.plan.delay));
				this.minecraft.gui.setScreen(null);
			}
		}).bounds(rx, 30, 150, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(this.width - 90, this.height - 26, 80, 20).build());
		this.filter();
	}

	private Component creativeLabel() {
		return Component.literal("Creative Mode: ").append(Component.literal(this.plan.creative ? "ON" : "OFF").withStyle(this.plan.creative ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	private void move(final int dx, final int dy, final int dz) {
		this.plan.origin = this.plan.origin.offset(dx, dy, dz);
	}

	private void filter() {
		List<Payloads.SchematicInfo> all = ClientNetworking.SCHEMATICS == null ? List.of() : ClientNetworking.SCHEMATICS;
		String q = this.search == null ? "" : this.search.getValue().toLowerCase(Locale.ROOT);
		this.shown = all.stream().filter(s -> s.name().toLowerCase(Locale.ROOT).contains(q)).toList();
	}

	private Payloads.SchematicInfo selectedInfo() {
		for (Payloads.SchematicInfo info : this.shown) {
			if (info.name().equals(this.plan.schematic)) {
				return info;
			}
		}
		return null;
	}

	private BlockPos rotatedEnd(final Payloads.SchematicInfo info) {
		BlockPos far = new BlockPos(info.sizeX() - 1, info.sizeY() - 1, info.sizeZ() - 1);
		return this.plan.origin.offset(StructureTemplate.transform(far, Mirror.NONE, Rotation.values()[this.plan.rotation], BlockPos.ZERO));
	}

	private List<Payloads.SchematicInfo> lastSource;

	@Override
	public void tick() {
		super.tick();
		if (ClientNetworking.SCHEMATICS != this.lastSource) {
			this.lastSource = ClientNetworking.SCHEMATICS;
			this.filter();
		}
		boolean building = this.clone.getBuildStage() >= 0;
		this.buildButton.setMessage(Component.literal(building ? "Stop Building" : "Build It!"));
		this.buildButton.active = building || !this.plan.schematic.isEmpty();
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		this.scroll = Math.max(0, Math.min(Math.max(0, this.shown.size() * 24 - (this.height - 90)), this.scroll - scrollY * 24));
		return true;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (event.x() >= 10 && event.x() < 200 && event.y() >= 56 && event.y() < this.height - 30) {
			int index = (int)((event.y() - 56 + this.scroll) / 24);
			if (index >= 0 && index < this.shown.size()) {
				this.plan.schematic = this.shown.get(index).name();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		this.title(g, "Things To Build - " + this.clone.getCloneName());
		g.fill(8, 54, 202, this.height - 28, 0x88000000);
		g.enableScissor(10, 56, 200, this.height - 30);
		for (int i = 0; i < this.shown.size(); i++) {
			int y = 56 + i * 24 - (int)this.scroll;
			Payloads.SchematicInfo s = this.shown.get(i);
			boolean sel = s.name().equals(this.plan.schematic);
			g.fill(10, y, 200, y + 23, sel ? 0x6633AA33 : 0x22FFFFFF);
			g.text(this.font, s.name(), 14, y + 3, sel ? 0xFF88FF88 : 0xFFFFFFFF);
			g.text(this.font, s.sizeX() + "x" + s.sizeY() + "x" + s.sizeZ() + ", " + s.blocks() + " blocks", 14, y + 13, 0xFFAAAAAA);
		}
		g.disableScissor();
		if (ClientNetworking.SCHEMATICS == null) {
			g.text(this.font, "Loading...", 14, 60, 0xFFFFFF55);
		} else if (this.shown.isEmpty()) {
			g.textWithWordWrap(this.font, Component.literal("No schematics yet. Use the Schematic Selector staff to save one."), 14, 60, 180, 0xFFFFFF55);
		}

		int rx = 320;
		int y = 64;
		g.text(this.font, "Position: " + this.plan.origin.getX() + ", " + this.plan.origin.getY() + ", " + this.plan.origin.getZ(), rx, y, 0xFFFFFFFF);
		g.text(this.font, "Rotation: " + this.plan.rotation * 90 + "°", rx, y + 12, 0xFFFFFFFF);
		if (this.minecraft.player.getAbilities().instabuild) {
			g.text(this.font, "Build Delay: " + this.plan.delay + " ticks", rx, y + 24, 0xFFFFFFFF);
		}
		int stage = this.clone.getBuildStage();
		if (stage >= 0) {
			String[] stages = {"Solid Blocks", "Non-solid Blocks", "Fire & Redstone", "Finalizing"};
			g.text(this.font, "Currently Building", rx, y + 44, 0xFF77EE77);
			g.text(this.font, "Stage " + (stage + 1) + " of 4: " + stages[Math.min(3, stage)], rx, y + 56, 0xFFFFFFFF);
			int total = Math.max(1, this.clone.getBuildTotal());
			Ui.bar(g, rx, y + 70, 150, 8, this.clone.getBuildIndex() / (float)total, 0xFF33CC33);
		}
	}
}
