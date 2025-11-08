package com.hackathon.ainpc.client;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.client.renderer.ProfessorGRenderer;
import com.hackathon.ainpc.registration.EntityRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AiNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegistry.PROFESSOR_G.get(), ProfessorGRenderer::new);
        AiNpcMod.LOGGER.info("[AI-NPC] Registered renderer for Professor G");
    }
}