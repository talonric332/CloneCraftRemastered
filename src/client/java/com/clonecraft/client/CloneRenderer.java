package com.clonecraft.client;

import com.clonecraft.entity.CloneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;

/** Draws a clone exactly like a player, using the cloned player's skin (wide or slim arms). */
public class CloneRenderer extends HumanoidMobRenderer<CloneEntity, AvatarRenderState, PlayerModel> {
	private final PlayerModel wideModel;
	private final PlayerModel slimModel;
	private final PlayerSkinRenderCache skins;
	private static final java.util.Set<Integer> FEMALE = new java.util.HashSet<>();

	public CloneRenderer(final EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.skins = context.getPlayerSkinRenderCache();
		this.addLayer(new HumanoidArmorLayer<>(
			this,
			ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), part -> new PlayerModel(part, false)),
			context.getEquipmentRenderer()
		));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(final CloneEntity entity, final AvatarRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.skin = this.skins.getOrDefault(entity.getProfile()).playerSkin();
		state.id = entity.getId();
		// "Female" clones use the slim (3-pixel arm) model.
		if (entity.hasOption(com.clonecraft.clone.CloneOption.FEMALE)) {
			FEMALE.add(state.id);
		} else {
			FEMALE.remove(state.id);
		}
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(final CloneEntity mob, final HumanoidArm arm) {
		HumanoidModel.ArmPose pose = super.getArmPose(mob, arm);
		if (pose == HumanoidModel.ArmPose.EMPTY && !mob.getItemHeldByArm(arm).isEmpty()) {
			return HumanoidModel.ArmPose.ITEM;
		}
		return pose;
	}

	@Override
	public void submit(final AvatarRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
		this.model = state.skin.model() == PlayerModelType.SLIM || FEMALE.contains(state.id) ? this.slimModel : this.wideModel;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(final AvatarRenderState state) {
		return state.skin.body().texturePath();
	}

	@Override
	protected void scale(final AvatarRenderState state, final PoseStack poseStack) {
		poseStack.scale(0.9375F, 0.9375F, 0.9375F);
	}
}
