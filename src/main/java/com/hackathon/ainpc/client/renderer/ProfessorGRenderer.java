package com.hackathon.ainpc.client.renderer;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ProfessorGEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class ProfessorGRenderer extends MobRenderer<ProfessorGEntity, VillagerModel<ProfessorGEntity>> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/villager/villager.png");

    public ProfessorGRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ProfessorGEntity entity) {
        return TEXTURE;
    } // ✅ FIXED: Added closing brace for method
} // ✅ FIXED: Added closing brace for class