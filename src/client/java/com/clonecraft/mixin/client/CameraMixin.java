package com.clonecraft.mixin.client;

import com.clonecraft.entity.CloneEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * While watching a clone (Clone Remote → Watch) the camera sits at the clone's eyes and moves with it, but turns with
 * the player's own mouse instead of following where the clone looks. Only the view changes: the clone's head doesn't.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float clonecraft$lookYaw(final Entity entity, final float partialTick) {
		Entity viewer = clonecraft$viewer(entity);
		return viewer != null ? viewer.getViewYRot(partialTick) : entity.getViewYRot(partialTick);
	}

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float clonecraft$lookPitch(final Entity entity, final float partialTick) {
		Entity viewer = clonecraft$viewer(entity);
		return viewer != null ? viewer.getViewXRot(partialTick) : entity.getViewXRot(partialTick);
	}

	/** The local player, if the camera is following a clone (that's only ever because they're watching it). */
	private static Entity clonecraft$viewer(final Entity cameraEntity) {
		Minecraft mc = Minecraft.getInstance();
		return cameraEntity instanceof CloneEntity && mc.player != null && mc.player != cameraEntity ? mc.player : null;
	}
}
