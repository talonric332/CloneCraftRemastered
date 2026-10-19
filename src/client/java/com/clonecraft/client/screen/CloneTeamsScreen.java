package com.clonecraft.client.screen;

import com.clonecraft.client.ClientNetworking;
import com.clonecraft.clone.CloneTeam;
import com.clonecraft.entity.CloneEntity;
import com.clonecraft.network.Payloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Pick the clone's team. Coloured teams fight each other, Good fights only Evil, Evil fights everyone, Rampant fights everything. */
public class CloneTeamsScreen extends CloneSubScreen {
	private final List<Button> teamButtons = new ArrayList<>();
	private CloneTeam selected;

	public CloneTeamsScreen(final CloneEntity clone) {
		super(clone, Component.literal("Teams"));
		this.selected = clone.getCloneTeam();
	}

	@Override
	protected void init() {
		this.teamButtons.clear();
		CloneTeam[] teams = CloneTeam.values();
		int w = 120;
		int x0 = this.width / 2 - w - 5;
		for (int i = 0; i < teams.length; i++) {
			CloneTeam team = teams[i];
			int x = i < 8 ? x0 + (i % 2) * (w + 10) : this.width / 2 - w / 2;
			int y = i < 8 ? 40 + (i / 2) * 24 : 140 + (i - 8) * 24;
			Button b = Button.builder(this.label(team), btn -> {
				this.selected = team;
				ClientNetworking.send(new Payloads.CloneAction(this.clone.getId(), Payloads.CloneAction.SET_TEAM, team.ordinal()));
				for (int j = 0; j < this.teamButtons.size(); j++) {
					this.teamButtons.get(j).setMessage(this.label(CloneTeam.values()[j]));
				}
			}).bounds(x, y, w, 20).build();
			this.teamButtons.add(b);
			this.addRenderableWidget(b);
		}
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose()).bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
	}

	private Component label(final CloneTeam team) {
		String text = team == this.selected ? "> " + team.displayName + " <" : team.displayName;
		return Component.literal(text).withColor(team.color == 0xFFFFFF ? 0xFFFFFF : team.color);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		this.title(g, "Choose a team for " + this.clone.getCloneName());
		CloneTeam shown = this.selected;
		for (int i = 0; i < this.teamButtons.size(); i++) {
			if (this.teamButtons.get(i).isHovered()) {
				shown = CloneTeam.values()[i];
			}
		}
		g.textWithWordWrap(this.font, Component.literal(shown.info()), this.width / 2 - 150, 22, 300, 0xFFDDDDDD);
	}
}
