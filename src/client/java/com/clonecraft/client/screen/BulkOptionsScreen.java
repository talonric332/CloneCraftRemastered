package com.clonecraft.client.screen;

import com.clonecraft.clone.CloneOption;
import com.clonecraft.clone.CloneTeam;
import com.clonecraft.network.RemoteControl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Bulk Edit's Options screen: every clone option cycles Keep / ON / OFF, and Team can be set.
 * Anything left on Keep isn't touched. Female isn't here, since it also picks the clone's name.
 */
public class BulkOptionsScreen extends Screen {
	/** What Bulk Edit will do to the options and team. Kept by the Bulk Edit screen, so it survives going back and forth. */
	public static class Changes {
		static final int KEEP = 0;
		static final int ON = 1;
		static final int OFF = 2;

		final List<CloneOption> options = new ArrayList<>();
		final int[] state;
		int team = -1;

		Changes() {
			for (CloneOption option : CloneOption.values()) {
				if ((option.bit() & RemoteControl.BULK_LOCKED) == 0) {
					this.options.add(option);
				}
			}
			this.state = new int[this.options.size()];
		}

		int changedOptions() {
			return (int)Arrays.stream(this.state).filter(s -> s != KEEP).count();
		}

		boolean isEmpty() {
			return this.team < 0 && this.changedOptions() == 0;
		}

		void reset() {
			Arrays.fill(this.state, KEEP);
			this.team = -1;
		}

		int setMask() {
			return this.mask(ON);
		}

		int clearMask() {
			return this.mask(OFF);
		}

		private int mask(final int wanted) {
			int mask = 0;
			for (int i = 0; i < this.options.size(); i++) {
				if (this.state[i] == wanted) {
					mask |= this.options.get(i).bit();
				}
			}
			return mask;
		}

		/** One line for the Bulk Edit screen, e.g. "No changes" or "2 options, team Red". */
		Component summary() {
			if (this.isEmpty()) {
				return Component.literal("No changes").withStyle(ChatFormatting.GRAY);
			}
			List<String> parts = new ArrayList<>();
			int n = this.changedOptions();
			if (n > 0) {
				parts.add(n + " option" + (n == 1 ? "" : "s"));
			}
			if (this.team >= 0) {
				parts.add("team " + CloneTeam.values()[this.team].displayName);
			}
			return Component.literal(String.join(", ", parts)).withStyle(ChatFormatting.GOLD);
		}
	}

	private static final int TOP = 34;

	private final Screen parent;
	private final Changes changes;
	private final List<Button> optionButtons = new ArrayList<>();
	private Button teamButton;

	public BulkOptionsScreen(final Screen parent, final Changes changes) {
		super(Component.literal("Bulk Edit: Options"));
		this.parent = parent;
		this.changes = changes;
	}

	@Override
	protected void init() {
		int count = this.changes.options.size() + 1;
		int areaW = Math.min(this.width - 20, 480);
		int columns = areaW >= 420 ? 3 : 2;
		int rows = (int)Math.ceil(count / (double)columns);
		int gap = 4;
		int avail = this.height - 34 - TOP;
		int buttonH = Math.max(12, Math.min(20, avail / rows - gap));
		int buttonW = (areaW - gap * (columns - 1)) / columns;
		int x0 = (this.width - areaW) / 2;

		this.optionButtons.clear();
		for (int i = 0; i < count; i++) {
			int x = x0 + (i % columns) * (buttonW + gap);
			int y = TOP + (i / columns) * (buttonH + gap);
			if (i < this.changes.options.size()) {
				int index = i;
				this.optionButtons.add(this.addRenderableWidget(Button.builder(this.optionLabel(index), b -> {
					this.changes.state[index] = (this.changes.state[index] + 1) % 3;
					b.setMessage(this.optionLabel(index));
				}).bounds(x, y, buttonW, buttonH).build()));
			} else {
				this.teamButton = this.addRenderableWidget(Button.builder(this.teamLabel(), b -> {
					this.changes.team = this.changes.team + 1 >= CloneTeam.values().length ? -1 : this.changes.team + 1;
					b.setMessage(this.teamLabel());
				}).bounds(x, y, buttonW, buttonH).build());
			}
		}

		int by = this.height - 26;
		this.addRenderableWidget(Button.builder(Component.literal("Reset Options"), b -> {
			this.changes.reset();
			this.rebuildWidgets();
		}).bounds(this.width / 2 - 104, by, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(this.width / 2 + 4, by, 100, 20).build());
	}

	private Component optionLabel(final int index) {
		return Component.literal(this.changes.options.get(index).label + ": ").append(switch (this.changes.state[index]) {
			case Changes.ON -> Component.literal("ON").withStyle(ChatFormatting.GREEN);
			case Changes.OFF -> Component.literal("OFF").withStyle(ChatFormatting.RED);
			default -> Component.literal("Keep").withStyle(ChatFormatting.GRAY);
		});
	}

	private Component teamLabel() {
		if (this.changes.team < 0) {
			return Component.literal("Team: ").append(Component.literal("Keep").withStyle(ChatFormatting.GRAY));
		}
		CloneTeam t = CloneTeam.values()[this.changes.team];
		return Component.literal("Team: ").append(Component.literal(t.displayName).withColor(t.color));
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, this.title.getString(), this.width / 2, 8, 0xFFFFFFFF);
		String hint = "Click to cycle Keep / ON / OFF. Keep leaves each clone's own setting alone.";
		for (int i = 0; i < this.optionButtons.size(); i++) {
			if (this.optionButtons.get(i).isHovered()) {
				hint = this.changes.options.get(i).description;
			}
		}
		if (this.teamButton != null && this.teamButton.isHovered()) {
			hint = this.changes.team < 0 ? "Click to choose a team for every selected clone." : CloneTeam.values()[this.changes.team].info();
		}
		g.centeredText(this.font, this.font.plainSubstrByWidth(hint, this.width - 20), this.width / 2, 20, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
