package com.hackathon.ainpc.handler;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.command.TestNPCCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AiNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandRegistration {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TestNPCCommand.register(event.getDispatcher());
        AiNpcMod.LOGGER.info("[AI-NPC] Registered test commands");
    }
}