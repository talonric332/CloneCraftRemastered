package com.clonecraft.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets CloneCraft teach hostile mobs to attack clones, like they attack players. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("targetSelector")
	GoalSelector clonecraft$getTargetSelector();
}
