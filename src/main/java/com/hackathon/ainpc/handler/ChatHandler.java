package com.hackathon.ainpc.handler;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ProfessorGEntity;
import com.hackathon.ainpc.networking.AiBridgeService;
import com.hackathon.ainpc.networking.NpcInteractionResponse;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AiNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChatHandler {

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        String message = event.getRawText();
        String playerName = event.getUsername();

        // Only react if the player mentions the NPC name
        if (message == null || !message.toLowerCase().contains("professor")) {
            return;
        }

        ServerLevel level = (ServerLevel) event.getPlayer().level();
        ProfessorGEntity nearestNPC = findNearestProfessorG(level, event.getPlayer());

        if (nearestNPC == null) {
            event.getPlayer().sendSystemMessage(
                    Component.literal("§c[Error]§r No Professor G nearby!")
            );
            return;
        }

        event.getPlayer().sendSystemMessage(
                Component.literal("§e[Professor G]§r I'm listening...")
        );

        event.getPlayer().sendSystemMessage(
                Component.literal("§7[Thinking]§r Professor G is thinking...")
        );

        // Call the AI bridge
        AiBridgeService.sendToAI(playerName, "professor_g", message, new AiBridgeService.Callback() {
            @Override
            public void onSuccess(NpcInteractionResponse response) {
                // Run back on the server thread
                level.getServer().execute(() -> {
                    AiNpcMod.LOGGER.info("[ChatHandler] AI Response: {}", response);

                    if (response != null) {
                        if (response.reply != null && !response.reply.isEmpty()) {
                            nearestNPC.sayInChat(response.reply);
                        }

                        if (response.action != null && !response.action.equals("say")) {
                            nearestNPC.executeAIAction(response.action, response.action_params);
                        }
                    }
                });
            } // ✅ ADDED: Closing brace for onSuccess method

            @Override
            public void onFailure(String error) {
                // Handle failure
                AiNpcMod.LOGGER.error("[ChatHandler] AI call failed: {}", error);
                level.getServer().execute(() -> {
                    nearestNPC.sayInChat("*confused* My thoughts seem scattered right now...");
                    event.getPlayer().sendSystemMessage(
                            Component.literal("§c[AI Error]§r " + error)
                    );
                });
            } // ✅ ADDED: Closing brace for onFailure method

         // ✅ ADDED: Closing brace for onError method
        }); // ✅ ADDED: Closing brace for anonymous Callback class
    } // ✅ Closing brace for onPlayerChat method

    private static ProfessorGEntity findNearestProfessorG(ServerLevel level, ServerPlayer player) {
        double nearestDistance = Double.MAX_VALUE;
        ProfessorGEntity nearest = null;

        for (Entity e : level.getEntities(null, player.getBoundingBox().inflate(32))) {
            if (e instanceof ProfessorGEntity npc) {
                double distance = npc.distanceToSqr(player);
                if (distance < nearestDistance) {
                    nearest = npc;
                    nearestDistance = distance;
                }
            }
        }

        return nearest;
    } // ✅ Closing brace for findNearestProfessorG method

} // ✅ Closing brace for ChatHandler class
