package com.hackathon.ainpc.entity;

import com.hackathon.ainpc.AiNpcMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ProfessorGEntity extends PathfinderMob {
    public static final String NPC_NAME = "Professor G";

    public ProfessorGEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal(NPC_NAME));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void registerGoals() {
        // Basic AI goals - we'll replace these with AI-driven behavior later
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }
    
    /**
     * Execute an AI-determined action
     * Called by ChatHandler when AI brain returns an action
     */
    public void executeAIAction(String action, String actionParams) {
        if (this.level().isClientSide) {
            return; // Only execute on server
        }
        
        AiNpcMod.LOGGER.info("[Professor G] Executing action: {} with params: {}", action, actionParams);
        
        if (action == null) {
            return;
        }
        
        switch (action.toLowerCase()) {
            case "say":
                // Already handled by sayInChat, but included for completeness
                if (actionParams != null && !actionParams.isEmpty()) {
                    sayInChat(actionParams);
                }
                break;
                
            case "move_to":
                handleMoveToAction(actionParams);
                break;
                
            case "follow":
                handleFollowAction(actionParams);
                break;
                
            case "attack_target":
                handleAttackAction(actionParams);
                break;
                
            case "emote":
                handleEmoteAction(actionParams);
                break;
                
            default:
                AiNpcMod.LOGGER.warn("[Professor G] Unknown action: {}", action);
                sayInChat("*looks confused* I'm not sure how to do that...");
        }
    }
    
    /**
     * Make the NPC say something in chat
     */
    public void sayInChat(String message) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§e[Professor G]§r " + message), 
                false
            );
        }
    }
    
    /**
     * Handle move_to action
     * Params can be: "player:<name>" or "x,y,z" coordinates
     */
    private void handleMoveToAction(String params) {
        if (params == null || params.isEmpty()) {
            sayInChat("*scratches head* Where should I go?");
            return;
        }
        
        try {
            if (params.startsWith("player:")) {
                String playerName = params.substring(7);
                Player targetPlayer = this.level().getPlayerByUUID(
                    this.level().getServer().getPlayerList().getPlayerByName(playerName).getUUID()
                );
                
                if (targetPlayer != null) {
                    this.getNavigation().moveTo(targetPlayer, 1.0D);
                    sayInChat("On my way to " + playerName + "!");
                }
            } else {
                // Parse coordinates "x,y,z"
                String[] coords = params.split(",");
                if (coords.length == 3) {
                    double x = Double.parseDouble(coords[0].trim());
                    double y = Double.parseDouble(coords[1].trim());
                    double z = Double.parseDouble(coords[2].trim());
                    
                    this.getNavigation().moveTo(x, y, z, 1.0D);
                    sayInChat("Moving to those coordinates!");
                }
            }
        } catch (Exception e) {
            AiNpcMod.LOGGER.error("[Professor G] Error in move_to action", e);
            sayInChat("*stumbles* I can't quite navigate there...");
        }
    }
    
    /**
     * Handle follow action
     */
    private void handleFollowAction(String params) {
        // Find nearest player
        Player nearestPlayer = this.level().getNearestPlayer(this, 50.0D);
        
        if (nearestPlayer != null) {
            this.goalSelector.addGoal(1, new FollowMobGoal(this, 1.0D, 3.0F, 10.0F));
            this.getNavigation().moveTo(nearestPlayer, 1.0D);
            sayInChat("I'll follow you!");
        } else {
            sayInChat("*looks around* Who should I follow?");
        }
    }
    
    /**
     * Handle attack action
     * Params: entity type like "pig", "zombie", etc.
     */
    private void handleAttackAction(String params) {
        if (params == null || params.isEmpty()) {
            params = "pig"; // Default target
        }
        
        String targetType = params.toLowerCase().trim();
        
        // Find nearest entity of specified type
        LivingEntity target = null;
        
        if (targetType.contains("pig")) {
            target = this.level().getNearestEntity(
                Pig.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT,
                this,
                this.getX(), this.getY(), this.getZ(),
                this.getBoundingBox().inflate(20.0D)
            );
        }
        
        if (target != null) {
            this.setTarget(target);
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
            sayInChat("*charges* Engaging target!");
        } else {
            sayInChat("*looks around* I don't see any " + targetType + " nearby...");
        }
    }
    
    /**
     * Handle emote action
     */
    private void handleEmoteAction(String params) {
        if (params == null || params.isEmpty()) {
            params = "happy";
        }
        
        String emote = params.toLowerCase().trim();
        
        switch (emote) {
            case "happy":
                sayInChat("*smiles warmly*");
                // TODO: Add particle effects
                break;
            case "sad":
                sayInChat("*looks down sadly*");
                break;
            case "angry":
                sayInChat("*grumbles angrily*");
                break;
            case "confused":
                sayInChat("*scratches head in confusion*");
                break;
            case "excited":
                sayInChat("*jumps excitedly*");
                this.setJumping(true);
                break;
            default:
                sayInChat("*" + emote + "*");
        }
    }
}