package com.hackathon.ainpc.handler;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.networking.AiBridgeService;
import com.hackathon.ainpc.networking.NpcInteractionResponse;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Listens to player chat and triggers AI responses
 */
@Mod.EventBusSubscriber(modid = AiNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChatHandler {

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getRawText();
        String playerName = event.getUsername();

        AiNpcMod.LOGGER.info("[ChatHandler] {}: {}", playerName, message);

        // Only respond if player mentions "professor"
        if (!message.toLowerCase().contains("professor")) {
            return;
        }

        // Show thinking message
        player.sendSystemMessage(
            Component.literal("🧠 ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Professor G is thinking...").withStyle(ChatFormatting.ITALIC))
        );

        // Send to AI brain asynchronously
        AiBridgeService.sendToAI(
            playerName, 
            "Professor G", 
            message, 
            new AiBridgeService.Callback() {
                @Override
                public void onSuccess(NpcInteractionResponse response) {
                    // Must run on server thread
                    player.getServer().execute(() -> {
                        NpcActionDispatcher.dispatchAction(player.getServer(), response);
                    });
                }

                @Override
                public void onFailure(String error) {
                    player.getServer().execute(() -> {
                        player.sendSystemMessage(
                            Component.literal("❌ [AI Error] ")
                                .withStyle(ChatFormatting.RED)
                                .append(Component.literal(error).withStyle(ChatFormatting.WHITE))
                        );
                    });
                }
            }
        );
    }
}