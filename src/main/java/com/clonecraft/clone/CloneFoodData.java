package com.clonecraft.clone;

import com.clonecraft.entity.CloneEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Clones get hungry just like players, and heal when well fed. */
public class CloneFoodData {
	private int foodLevel = 20;
	private float saturation = 5.0F;
	private float exhaustion;
	private int timer;

	public int getFoodLevel() {
		return this.foodLevel;
	}

	public void setFoodLevel(final int foodLevel) {
		this.foodLevel = foodLevel;
	}

	public void eat(final int nutrition, final float saturationModifier) {
		this.foodLevel = Math.min(this.foodLevel + nutrition, 20);
		this.saturation = Math.min(this.saturation + nutrition * saturationModifier * 2.0F, this.foodLevel);
	}

	public void addExhaustion(final float amount) {
		this.exhaustion = Math.min(this.exhaustion + amount, 40.0F);
	}

	public void tick(final CloneEntity clone, final ServerLevel level) {
		Difficulty difficulty = level.getDifficulty();
		if (this.exhaustion > 4.0F) {
			this.exhaustion -= 4.0F;
			if (this.saturation > 0.0F) {
				this.saturation = Math.max(this.saturation - 1.0F, 0.0F);
			} else if (difficulty != Difficulty.PEACEFUL) {
				this.foodLevel = Math.max(this.foodLevel - 1, 0);
			}
		}

		boolean regen = level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
		if (regen && this.foodLevel >= 18 && clone.getHealth() > 0.0F && clone.getHealth() < clone.getMaxHealth()) {
			if (++this.timer >= 80) {
				clone.heal(1.0F);
				this.addExhaustion(3.0F);
				this.timer = 0;
			}
		} else if (this.foodLevel <= 0) {
			if (++this.timer >= 80) {
				if (clone.getHealth() > 10.0F || difficulty == Difficulty.HARD || clone.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
					clone.hurtServer(level, level.damageSources().starve(), 1.0F);
				}
				this.timer = 0;
			}
		} else {
			this.timer = 0;
		}
	}

	public void save(final ValueOutput output) {
		output.putInt("foodLevel", this.foodLevel);
		output.putInt("foodTickTimer", this.timer);
		output.putFloat("foodSaturationLevel", this.saturation);
		output.putFloat("foodExhaustionLevel", this.exhaustion);
	}

	public void load(final ValueInput input) {
		this.foodLevel = input.getIntOr("foodLevel", 20);
		this.timer = input.getIntOr("foodTickTimer", 0);
		this.saturation = input.getFloatOr("foodSaturationLevel", 5.0F);
		this.exhaustion = input.getFloatOr("foodExhaustionLevel", 0.0F);
	}
}
