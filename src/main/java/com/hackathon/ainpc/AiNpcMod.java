package com.hackathon.ainpc;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.hackathon.ainpc.registration.EntityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AiNpcMod.MOD_ID)
public class AiNpcMod {
    public static final String MOD_ID = "ainpc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public AiNpcMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        EntityRegistry.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[AI-NPC] Mod setup initialized!");
    }
}