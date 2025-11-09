// package com.hackathon.ainpc.handler;

// import com.hackathon.ainpc.AiNpcMod;
// import com.hackathon.ainpc.entity.ProfessorGEntity;
// import com.hackathon.ainpc.networking.ActionPayload;
// import com.hackathon.ainpc.networking.NpcInteractionResponse;
// import net.minecraft.ChatFormatting;
// import net.minecraft.core.BlockPos;
// import net.minecraft.network.chat.Component;
// import net.minecraft.server.MinecraftServer;
// import net.minecraft.server.level.ServerLevel;
// import net.minecraft.world.entity.Entity;
// import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
// import net.minecraft.world.level.block.state.BlockState;
// import net.minecraft.world.phys.Vec3;

// /**
//  * Dispatches AI actions to the NPC entity
//  */
// public class NpcActionDispatcher {

//     public static void dispatchAction(MinecraftServer server, NpcInteractionResponse response) {
//         server.execute(() -> {
//             // Find Professor G in the world
//             ProfessorGEntity npc = findProfessorG(server);
            
//             if (npc == null) {
//                 AiNpcMod.LOGGER.warn("[Dispatcher] Professor G not found in world!");
//                 server.getPlayerList().broadcastSystemMessage(
//                     Component.literal("⚠️ Professor G is not in the world. Summon him first!")
//                         .withStyle(ChatFormatting.YELLOW), 
//                     false
//                 );
//                 return;
//             }

//             // Extract action data
//             ActionPayload action = response.action;
//             String actionType = action != null ? action.actionType : "idle";
//             String chatResponse = action != null ? action.chatResponse : null;
            
//             AiNpcMod.LOGGER.info("[Dispatcher] Processing action: {}", actionType);

//             // Broadcast chat response if present
//             if (chatResponse != null && !chatResponse.isEmpty()) {
//                 server.getPlayerList().broadcastSystemMessage(
//                     Component.literal("[Professor G] ")
//                         .withStyle(ChatFormatting.GREEN)
//                         .append(Component.literal(chatResponse).withStyle(ChatFormatting.YELLOW)), 
//                     false
//                 );
//             }

//             // Dispatch specific action
//             switch (actionType.toLowerCase()) {
//                 case "move_to" -> handleMoveTo(npc, action);
//                 case "follow_player" -> handleFollowPlayer(npc);
//                 case "attack" -> handleAttack(npc, action.targetName);
//                 case "mine_block" -> handleMineBlock(npc, action.targetName);
//                 case "emote" -> handleEmote(npc, response.newState.emotion);
//                 case "respond_chat" -> {} // Already handled above
//                 case "idle" -> handleIdle(npc);
//                 default -> {
//                     AiNpcMod.LOGGER.warn("[Dispatcher] Unknown action type: {}", actionType);
//                     npc.sayInChat("I don't know how to do that yet!");
//                 }
//             }
//         });
//     }

//     /**
//      * Find Professor G entity in any loaded dimension
//      */
//     private static ProfessorGEntity findProfessorG(MinecraftServer server) {
//         for (ServerLevel level : server.getAllLevels()) {
//             for (Entity entity : level.getAllEntities()) {
//                 if (entity instanceof ProfessorGEntity professorG) {
//                     return professorG;
//                 }
//             }
//         }
//         return null;
//     }

//     /**
//      * Handle move_to action
//      */
//     private static void handleMoveTo(ProfessorGEntity npc, ActionPayload action) {
//         if (action.x == null || action.z == null) {
//             AiNpcMod.LOGGER.warn("[Dispatcher] move_to action missing coordinates");
//             npc.sayInChat("I need coordinates to move!");
//             return;
//         }
        
//         int targetX = action.x;
//         int targetZ = action.z;
//         int targetY = (int) npc.getY(); // Use current Y for now
        
//         AiNpcMod.LOGGER.info("[Dispatcher] Moving to ({}, {}, {})", targetX, targetY, targetZ);
//         npc.sayInChat(String.format("Moving to coordinates (%d, %d)...", targetX, targetZ));
        
//         // Simple pathfinding - will be improved in Phase 4
//         Vec3 targetPos = new Vec3(targetX, targetY, targetZ);
//         npc.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
//     }

//     /**
//      * Handle follow_player action
//      */
//     private static void handleFollowPlayer(ProfessorGEntity npc) {
//         AiNpcMod.LOGGER.info("[Dispatcher] Following player");
//         npc.sayInChat("I'll follow you!");
//         // Will be implemented in Phase 4 with proper AI goals
//     }

//     /**
//      * Handle attack action
//      */
//     private static void handleAttack(ProfessorGEntity npc, String targetName) {
//         if (targetName == null || targetName.isEmpty()) {
//             npc.sayInChat("What should I attack?");
//             return;
//         }
        
//         AiNpcMod.LOGGER.info("[Dispatcher] Attacking: {}", targetName);
//         npc.sayInChat("Attacking " + targetName + "!");
//         // Will be implemented in Phase 4 with combat AI
//     }

//     /**
//      * Handle mine_block action
//      */
//     private static void handleMineBlock(ProfessorGEntity npc, String blockName) {
//         if (blockName == null || blockName.isEmpty()) {
//             npc.sayInChat("What should I mine?");
//             return;
//         }
        
//         AiNpcMod.LOGGER.info("[Dispatcher] Mining: {}", blockName);
//         npc.sayInChat("I'll mine some " + blockName + "!");
//         // Will be implemented in Phase 4 with block breaking AI
//     }

//     /**
//      * Handle emote action
//      */
//     private static void handleEmote(ProfessorGEntity npc, String emotion) {
//         AiNpcMod.LOGGER.info("[Dispatcher] Emoting: {}", emotion);
        
//         // Simple emote based on emotion
//         switch (emotion.toLowerCase()) {
//             case "happy" -> npc.sayInChat("*smiles*");
//             case "sad" -> npc.sayInChat("*sighs*");
//             case "angry" -> npc.sayInChat("*frowns*");
//             case "excited" -> npc.sayInChat("*jumps excitedly*");
//             case "curious" -> npc.sayInChat("*looks around curiously*");
//             default -> npc.sayInChat("*emotes*");
//         }
//     }

//     /**
//      * Handle idle action
//      */
//     private static void handleIdle(ProfessorGEntity npc) {
//         AiNpcMod.LOGGER.info("[Dispatcher] NPC idling");
//         // Do nothing - NPC continues normal behavior
//     }
// }