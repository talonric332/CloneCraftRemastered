package com.clonecraft.mixin;

import com.clonecraft.clone.progression.HomeBase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the clones' home bases when a creeper goes off, so they can see if it damaged a house. */
@Mixin(Creeper.class)
public abstract class CreeperMixin {
	@Inject(method = "explodeCreeper", at = @At("HEAD"))
	private void clonecraft$beforeExploding(final CallbackInfo ci) {
		Creeper self = (Creeper)(Object)this;
		if (self.level() instanceof ServerLevel level) {
			LivingEntity target = self.getTarget();
			HomeBase.creeperExploding(level, self.position(), target == null ? null : target.getUUID());
		}
	}
}
