package com.hackathon.ainpc.handler;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ProfessorGEntity;
import com.hackathon.ainpc.networking.AiBridgeService;
import com.hackathon.ainpc.networking.NpcInteractionResponse;
import com.hackathon.ainpc.networking.NPCStateResponse;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Phase 5+: Enhanced Chat Handler with Multiple NPCs Support
 */
@Mod.EventBusSubscriber(modid = AiNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChatHandler {

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        String message = event.getRawText();
        String playerName = event.getUsername();

        if (message == null || message.isEmpty()) {
            return;
        }

        // Check if message mentions "professor" (case insensitive)
        String lowerMessage = message.toLowerCase();
        if (!lowerMessage.contains("professor")) {
            return;
        }

        ServerLevel level = (ServerLevel) event.getPlayer().level();
        
        // Find the nearest NPC to the player
        ProfessorGEntity nearestNPC = findNearestProfessorG(level, event.getPlayer());

        if (nearestNPC == null) {
            event.getPlayer().sendSystemMessage(
                    Component.literal("§c[Error]§r No Professor nearby!")
            );
            return;
        }

        // Show thinking indicator with NPC name
        event.getPlayer().sendSystemMessage(
                Component.literal("§7[" + nearestNPC.getNpcName() + " is thinking...]§r")
        );

        // Use unique NPC ID for memory storage
        final String npcId = nearestNPC.getNpcId();
        final String npcName = nearestNPC.getNpcName();

        // Send to AI with unique NPC ID
        AiBridgeService.sendToAI(playerName, npcId, message, new AiBridgeService.Callback() {
            @Override
            public void onSuccess(NpcInteractionResponse response) {
                level.getServer().execute(() -> {
                    AiNpcMod.LOGGER.info("[ChatHandler] AI Response for {}: {}", npcName, response);

                    if (response != null && response.action != null) {
                        // Get chat response
                        String chatResponse = response.action.chatResponse;
                        if (chatResponse != null && !chatResponse.isEmpty()) {
                            nearestNPC.sayInChat(chatResponse);
                        }

                        // Update NPC emotion if changed
                        if (response.newState != null && response.newState.emotion != null) {
                            nearestNPC.setEmotion(response.newState.emotion);
                            AiNpcMod.LOGGER.info("[ChatHandler] {} emotion updated to: {}",
                                    npcName, response.newState.emotion);
                        }

                        // Execute action
                        String actionType = response.action.actionType;
                        if (actionType != null && !actionType.equals("say") && !actionType.equals("respond_chat")) {
                            String params = response.getActionParams();
                            nearestNPC.executeAIAction(actionType, params);
                        }

                        // Update NPC internal state
                        if (response.newState != null) {
                            nearestNPC.updateFromState(response.newState);
                        }
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                AiNpcMod.LOGGER.error("[ChatHandler] AI call failed for {}: {}", npcName, error);
                level.getServer().execute(() -> {
                    nearestNPC.sayInChat("*confused* My circuits seem scrambled...");
                    event.getPlayer().sendSystemMessage(
                            Component.literal("§c[AI Error]§r " + error)
                    );
                });
            }
        });
    }

    /**
     * Periodic state sync - called every 60 seconds
     * This keeps NPC in sync with Python backend state
     */
    public static void syncNPCState(ProfessorGEntity npc) {
        // Use unique NPC ID
        AiBridgeService.pollState(npc.getNpcId(), new AiBridgeService.StateCallback() {
            @Override
            public void onSuccess(NPCStateResponse state) {
                if (npc.level() instanceof ServerLevel serverLevel) {
                    serverLevel.getServer().execute(() -> {
                        AiNpcMod.LOGGER.debug("[ChatHandler] State sync for {}: emotion={}, objective={}",
                                npc.getNpcName(), state.emotion, state.currentObjective);

                        // Update NPC emotion
                        if (state.emotion != null) {
                            npc.setEmotion(state.emotion);
                        }

                        // Update NPC objective
                        if (state.currentObjective != null) {
                            npc.setCurrentObjective(state.currentObjective);
                        }
                    });
                }
            }

            @Override
            public void onFailure(String error) {
                AiNpcMod.LOGGER.debug("[ChatHandler] State sync failed for {}: {}", 
                        npc.getNpcName(), error);
                // Silent failure - not critical
            }
        });
    }

    /**
     * Find nearest Professor NPC to the player
     */
    private static ProfessorGEntity findNearestProfessorG(ServerLevel level, ServerPlayer player) {
        double nearestDistance = Double.MAX_VALUE;
        ProfessorGEntity nearest = null;

        // Search within 32 blocks
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
    }
}