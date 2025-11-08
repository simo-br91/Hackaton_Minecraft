package com.hackathon.ainpc.events;

import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.hackathon.ainpc.AiNpcMod;
import net.minecraft.network.chat.Component;

@Mod.EventBusSubscriber(modid = AiNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChatListener {

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        // Use getRawText() instead of getMessage() in 1.20.1
        String message = event.getRawText();
        String playerName = event.getUsername();

        AiNpcMod.LOGGER.info("[ChatListener] {}: {}", playerName, message);

        // Basic filter: only respond if player mentions "Professor"
        if (message.toLowerCase().contains("professor")) {
            event.getPlayer().sendSystemMessage(
                    Component.literal("§e[Professor G]§r I'm listening...")
            );
            
            // TODO: In Phase 3, this is where we'll call the Python AI bridge
            // sendToAIBridge(playerName, message, event.getPlayer());
        }
    }
}