package com.hackathon.ainpc.handler;

import com.google.gson.JsonObject;
import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ProfessorGEntity;
import com.hackathon.ainpc.networking.AiBridgeService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enhanced Event Handler for Phase 5+
 * Records combat and social events, handles NPC death
 */
@Mod.EventBusSubscriber(modid = AiNpcMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnhancedEventHandler {

    /**
     * Track when NPCs are attacked
     */
    @SubscribeEvent
    public static void onNPCAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ProfessorGEntity npc)) {
            return;
        }

        if (npc.level().isClientSide) {
            return;
        }

        if (event.isCanceled() || event.getAmount() <= 0) {
            return;
        }

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        
        if (attacker == null) {
            return;
        }

        String attackerName = attacker.getName().getString();
        String attackerType = attacker instanceof Player ? "player" : "mob";
        float damage = event.getAmount();
        String weapon = "unknown";
        
        if (attacker instanceof Player player) {
            if (!player.getMainHandItem().isEmpty()) {
                weapon = player.getMainHandItem().getHoverName().getString();
            } else {
                weapon = "fist";
            }
        }

        AiNpcMod.LOGGER.info("[Enhanced] {} attacked by {} ({}) for {} damage with {}",
                npc.getNpcName(), attackerName, attackerType, damage, weapon);

        // Send combat event to Python (use unique NPC ID)
        recordCombatEvent(
                npc.getNpcId(),
                "attacked_by",
                attackerName,
                attackerType,
                damage,
                weapon
        );

        // Make NPC react immediately
        if (npc.level().getServer() != null) {
            npc.level().getServer().execute(() -> {
                checkRelationshipAndReact(npc, attackerName, damage);
            });
        }
    }

    /**
     * Track when entities die near the NPC AND when NPCs themselves die
     */
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity deadEntity = event.getEntity();
        
        // Check if the dead entity is one of our NPCs
        if (deadEntity instanceof ProfessorGEntity deadNPC) {
            handleNPCDeath(deadNPC);
            return;
        }
        
        // Find nearby Professor G NPCs to witness the death
        var nearbyNPCs = deadEntity.level().getEntitiesOfClass(
                ProfessorGEntity.class,
                deadEntity.getBoundingBox().inflate(20.0)
        );

        for (ProfessorGEntity npc : nearbyNPCs) {
            String deadEntityName = deadEntity.getName().getString();
            String deadEntityType = deadEntity instanceof Player ? "player" : "mob";

            AiNpcMod.LOGGER.info("[Enhanced] {} witnessed death of {}",
                    npc.getNpcName(), deadEntityName);

            // Record witnessed death (use unique NPC ID)
            recordCombatEvent(
                    npc.getNpcId(),
                    "witnessed_death",
                    deadEntityName,
                    deadEntityType,
                    0.0f,
                    null
            );

            // React emotionally
            npc.sayInChat("*gasps* " + deadEntityName + "!");
            npc.setEmotion("sad");
        }
    }

    /**
     * Handle NPC death - clear its memory on backend
     */
    private static void handleNPCDeath(ProfessorGEntity npc) {
        String npcId = npc.getNpcId();
        String npcName = npc.getNpcName();
        
        AiNpcMod.LOGGER.info("[Enhanced] {} has died - clearing memory", npcName);
        
        // Send delete request to Python backend
        JsonObject payload = new JsonObject();
        payload.addProperty("npc_id", npcId);
        
        AiBridgeService.deleteNPCMemory(npcId, new AiBridgeService.DeleteCallback() {
            @Override
            public void onSuccess() {
                AiNpcMod.LOGGER.info("[Enhanced] Successfully cleared memory for {}", npcName);
            }

            @Override
            public void onFailure(String error) {
                AiNpcMod.LOGGER.warn("[Enhanced] Failed to clear memory for {}: {}", npcName, error);
            }
        });
    }

    /**
     * Track when players give items to NPC
     */
    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ProfessorGEntity npc)) {
            return;
        }

        if (event.getEntity().level().isClientSide) {
            return;
        }

        Player player = event.getEntity();
        
        if (!player.getMainHandItem().isEmpty()) {
            String itemName = player.getMainHandItem().getHoverName().getString();
            
            AiNpcMod.LOGGER.info("[Enhanced] {} offered item '{}' to {}",
                    player.getName().getString(), itemName, npc.getNpcName());

            // Record social event (use unique NPC ID)
            recordSocialEvent(
                    npc.getNpcId(),
                    "gift_received",
                    player.getName().getString(),
                    itemName,
                    null
            );
        }
    }

    /**
     * Send combat event to Python backend
     */
    private static void recordCombatEvent(
            String npcId,
            String eventType,
            String entityName,
            String entityType,
            float damage,
            String weapon
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("npc_id", npcId);
        payload.addProperty("event_type", "combat");

        JsonObject data = new JsonObject();
        data.addProperty("event_type", eventType);
        data.addProperty("entity_name", entityName);
        data.addProperty("entity_type", entityType);
        
        if (damage > 0) {
            data.addProperty("damage", damage);
        }
        if (weapon != null) {
            data.addProperty("weapon", weapon);
        }

        payload.add("data", data);

        AiBridgeService.sendEvent(payload, new AiBridgeService.EventCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                AiNpcMod.LOGGER.info("[Enhanced] Combat event recorded successfully for {}", npcId);
                
                if (response.has("relationship")) {
                    JsonObject rel = response.getAsJsonObject("relationship");
                    boolean shouldAttack = rel.get("should_attack").getAsBoolean();
                    boolean shouldAvoid = rel.get("should_avoid").getAsBoolean();
                    
                    AiNpcMod.LOGGER.info("[Enhanced] Relationship update: attack={}, avoid={}",
                            shouldAttack, shouldAvoid);
                }
            }

            @Override
            public void onFailure(String error) {
                AiNpcMod.LOGGER.warn("[Enhanced] Failed to record combat event: {}", error);
            }
        });
    }

    /**
     * Send social event to Python backend
     */
    private static void recordSocialEvent(
            String npcId,
            String eventType,
            String entityName,
            String item,
            String message
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("npc_id", npcId);
        payload.addProperty("event_type", "social");

        JsonObject data = new JsonObject();
        data.addProperty("event_type", eventType);
        data.addProperty("entity_name", entityName);
        
        if (item != null) {
            data.addProperty("item", item);
        }
        if (message != null) {
            data.addProperty("message", message);
        }

        payload.add("data", data);

        AiBridgeService.sendEvent(payload, new AiBridgeService.EventCallback() {
            @Override
            public void onSuccess(JsonObject response) {
                AiNpcMod.LOGGER.info("[Enhanced] Social event recorded successfully for {}", npcId);
            }

            @Override
            public void onFailure(String error) {
                AiNpcMod.LOGGER.warn("[Enhanced] Failed to record social event: {}", error);
            }
        });
    }

    /**
     * Check relationship and make NPC react to attacks
     */
    private static void checkRelationshipAndReact(ProfessorGEntity npc, String attackerName, float damage) {
        if (npc == null || npc.level().isClientSide || npc.level().getServer() == null) {
            return;
        }

        // Query relationship status from Python (use unique NPC ID)
        AiBridgeService.getRelationship(npc.getNpcId(), attackerName, 
                new AiBridgeService.RelationshipCallback() {
            @Override
            public void onSuccess(JsonObject relationship) {
                String status = relationship.get("status").getAsString();
                boolean shouldAttack = relationship.getAsJsonObject("recommendations")
                        .get("should_attack").getAsBoolean();
                boolean shouldAvoid = relationship.getAsJsonObject("recommendations")
                        .get("should_avoid").getAsBoolean();

                AiNpcMod.LOGGER.info("[Enhanced] {} relationship with {}: {} (attack={}, avoid={})",
                        npc.getNpcName(), attackerName, status, shouldAttack, shouldAvoid);

                // React based on relationship
                npc.level().getServer().execute(() -> {
                    if (shouldAttack) {
                        npc.sayInChat("*enraged* " + attackerName + ", you've gone too far!");
                        npc.setEmotion("angry");
                        
                        Player attacker = npc.level().getServer()
                                .getPlayerList()
                                .getPlayerByName(attackerName);
                        
                        if (attacker != null) {
                            npc.executeAIAction("attack_target", attackerName);
                        }
                        
                    } else if (shouldAvoid) {
                        npc.sayInChat("*frightened* Stay away from me, " + attackerName + "!");
                        npc.setEmotion("afraid");
                        
                    } else if (damage > 3.0f) {
                        npc.sayInChat("Ow! " + attackerName + ", why would you do that?!");
                        npc.setEmotion("sad");
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                AiNpcMod.LOGGER.error("[Enhanced] Failed to get relationship: {}", error);
            }
        });
    }
}