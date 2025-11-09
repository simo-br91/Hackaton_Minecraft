// package com.hackathon.ainpc.events;

// import net.minecraftforge.event.ServerChatEvent;
// import net.minecraftforge.eventbus.api.SubscribeEvent;
// import net.minecraftforge.fml.common.Mod;
// import com.hackathon.ainpc.AiNpcMod;
// import com.hackathon.ainpc.networking.AiBridgeService;
// import com.hackathon.ainpc.networking.NpcInteractionResponse;
// import net.minecraft.network.chat.Component;

// @Mod.EventBusSubscriber(modid = AiNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
// public class ChatListener {

//     @SubscribeEvent
//     public static void onPlayerChat(ServerChatEvent event) {
//         // Use getRawText() instead of getMessage() in 1.20.1
//         String message = event.getRawText();
//         String playerName = event.getUsername();

//         AiNpcMod.LOGGER.info("[ChatListener] {}: {}", playerName, message);

//         // Basic filter: only respond if player mentions "Professor"
//         if (message != null && message.toLowerCase().contains("professor")) {
//             event.getPlayer().sendSystemMessage(
//                     Component.literal("§e[Professor G]§r I'm thinking...")
//             );

//             // PHASE 3: Call the Python AI bridge with callback
//             AiBridgeService.sendToAI(
//                     playerName,
//                     "Professor G",
//                     message,
//                     // Create anonymous callback implementation with ALL 3 required methods
//                     new AiBridgeService.Callback() {
//                         @Override
//                         public void onSuccess(NpcInteractionResponse response) {
//                             // Handle successful AI response
//                             if (response == null) {
//                                 AiNpcMod.LOGGER.error("[ChatListener] Response was null!");
//                                 return;
//                             }

//                             // Display NPC response to player
//                             event.getPlayer().sendSystemMessage(
//                                     Component.literal("§e[Professor G]§r " + response.reply)
//                             );

//                             AiNpcMod.LOGGER.info("[AI Bridge] NPC said: {}", response.reply);

//                             // TODO: In Phase 4, execute the action (move, attack, emote, etc.)
//                             // This would require finding the NPC entity and calling executeAIAction()
//                         } // ✅ ADDED: Closing brace for onSuccess method

//                         @Override
//                         public void onFailure(String error) {
//                             // Handle AI error or network failure
//                             event.getPlayer().sendSystemMessage(
//                                     Component.literal("§c[Professor G]§r " + error)
//                             );

//                             AiNpcMod.LOGGER.error("[AI Bridge] Error: {}", error);
//                         } // ✅ ADDED: Closing brace for onFailure method

//                          // ✅ ADDED: Closing brace for onError method
//                     } // ✅ ADDED: Closing brace for anonymous Callback class
//             );
//         }
//     } // ✅ Closing brace for onPlayerChat method

// } // ✅ Closing brace for ChatListener class
