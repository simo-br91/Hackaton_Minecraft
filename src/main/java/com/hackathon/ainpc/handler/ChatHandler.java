package com.hackathon.ainpc.handler;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ProfessorGEntity;
import com.hackathon.ainpc.networking.AiBridgeService;
import com.hackathon.ainpc.networking.NpcInteractionResponse;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
        
        AiNpcMod.LOGGER.info("[ChatHandler] {}: {}", playerName, message);
        
        // Only respond if player mentions "professor"
        if (!message.toLowerCase().contains("professor")) {
            return;
        }
        
        // Show "thinking" message
        event.getPlayer().sendSystemMessage(
            Component.literal("§7[Thinking]§r Professor G is thinking...")
        );
        
        // Find nearest Professor G entity
        ServerLevel level = (ServerLevel) event.getPlayer().level();
        ProfessorGEntity nearestNPC = findNearestProfessorG(level, event.getPlayer());
        
        if (nearestNPC == null) {
            event.getPlayer().sendSystemMessage(
                Component.literal("§c[Error]§r No Professor G nearby!")
            );
            return;
        }
        
        // Call AI via OkHttp bridge
        AiBridgeService.sendToAI(playerName, "professor_g", message, new AiBridgeService.Callback() {
            @Override
            public void onSuccess(NpcInteractionResponse response) {
                // Execute on server thread
                level.getServer().execute(() -> {
                    AiNpcMod.LOGGER.info("[ChatHandler] AI Response: {}", response);
                    
                    // Say the reply
                    if (response.reply != null && !response.reply.isEmpty()) {
                        nearestNPC.sayInChat(response.reply);
                    }
                    
                    // Execute action
                    if (response.action != null && !response.action.equals("say")) {
                        nearestNPC.executeAIAction(response.action, response.action_params);
                    }
                });
            }
            
            @Override
            public void onFailure(String error) {
                AiNpcMod.LOGGER.error("[ChatHandler] AI call failed: {}", error);
                
                // Show fallback on server thread
                level.getServer().execute(() -> {
                    nearestNPC.sayInChat("*confused* My thoughts seem scattered right now...");
                    event.getPlayer().sendSystemMessage(
                        Component.literal("§c[AI Error]§r " + error)
                    );
                });
            }
        });
    }
    
    private static ProfessorGEntity findNearestProfessorG(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        double searchRadius = 50.0;
        ProfessorGEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ProfessorGEntity professorG) {
                double distance = entity.distanceTo(player);
                if (distance < searchRadius && distance < nearestDistance) {
                    nearest = professorG;
                    nearestDistance = distance;
                }
            }
        }
        
        return nearest;
    }
}
// ```

// ---

// ### **4. Remove Old AIBridgeHandler.java**

// Delete `src/main/java/com/hackathon/ainpc/handler/AIBridgeHandler.java` (the one using Java's HttpClient) since we're now using OkHttp properly.

// ---

// ## 📋 **COMPLETE FILE STRUCTURE:**
// ```
// src/main/java/com/hackathon/ainpc/
// ├── AiNpcMod.java
// ├── client/
// │   ├── ClientModEvents.java
// │   └── renderer/
// │       └── ProfessorGRenderer.java
// ├── entity/
// │   └── ProfessorGEntity.java
// ├── events/
// │   └── ChatListener.java
// ├── handler/
// │   └── ChatHandler.java              ← UPDATED
// ├── networking/
// │   ├── AiBridgeService.java         ← YOUR FILE (good!)
// │   ├── NpcInteractionRequest.java   ← NEW!
// │   └── NpcInteractionResponse.java  ← NEW!
// └── registration/
//     └── EntityRegistry.java

// src/main/resources/META-INF/
// └── mods.toml