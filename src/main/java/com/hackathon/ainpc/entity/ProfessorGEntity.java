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
    
    // Track current AI task
    private String currentTask = "idle";
    private LivingEntity followTarget = null;

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
        // Basic AI goals
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }
    
    /**
     * Execute an AI-determined action
     */
    public void executeAIAction(String action, String actionParams) {
        if (this.level().isClientSide) {
            return;
        }
        
        AiNpcMod.LOGGER.info("[Professor G] Executing action: {} with params: {}", action, actionParams);
        
        if (action == null) {
            return;
        }
        
        switch (action.toLowerCase()) {
            case "say":
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
     * Handle move_to action - ENHANCED VERSION
     */
    private void handleMoveToAction(String params) {
        if (params == null || params.isEmpty()) {
            sayInChat("*scratches head* Where should I go?");
            return;
        }
        
        try {
            if (params.startsWith("player:")) {
                // Move to a specific player
                String playerName = params.substring(7).trim();
                Player targetPlayer = this.level().getServer()
                    .getPlayerList()
                    .getPlayerByName(playerName);
                
                if (targetPlayer != null) {
                    double distance = this.distanceTo(targetPlayer);
                    BlockPos targetPos = targetPlayer.blockPosition();
                    
                    this.getNavigation().moveTo(targetPlayer, 1.0D);
                    this.currentTask = "moving_to_player";
                    
                    sayInChat(String.format("On my way to %s! (%.1f blocks away)", 
                        playerName, distance));
                    
                    AiNpcMod.LOGGER.info("[Professor G] Moving to player {} at {}", 
                        playerName, targetPos);
                } else {
                    sayInChat("I can't find " + playerName + "!");
                }
            } else if (params.contains(",")) {
                // Parse coordinates "x,y,z" or "x,z"
                String[] coords = params.split(",");
                
                if (coords.length >= 2) {
                    double x = Double.parseDouble(coords[0].trim());
                    double z = coords.length == 3 ? 
                        Double.parseDouble(coords[2].trim()) : 
                        Double.parseDouble(coords[1].trim());
                    double y = coords.length == 3 ? 
                        Double.parseDouble(coords[1].trim()) : 
                        this.getY();
                    
                    BlockPos targetPos = new BlockPos((int)x, (int)y, (int)z);
                    double distance = this.position().distanceTo(new Vec3(x, y, z));
                    
                    // Use pathfinding
                    boolean success = this.getNavigation().moveTo(x, y, z, 1.0D);
                    
                    if (success) {
                        this.currentTask = "moving_to_coords";
                        sayInChat(String.format("Moving to coordinates (%d, %d, %d)! (%.1f blocks)", 
                            (int)x, (int)y, (int)z, distance));
                        
                        AiNpcMod.LOGGER.info("[Professor G] Pathfinding to {}", targetPos);
                    } else {
                        sayInChat("*stumbles* I can't find a path there...");
                        AiNpcMod.LOGGER.warn("[Professor G] Pathfinding failed to {}", targetPos);
                    }
                }
            }
        } catch (Exception e) {
            AiNpcMod.LOGGER.error("[Professor G] Error in move_to action", e);
            sayInChat("*stumbles* I can't quite navigate there...");
        }
    }
    
    /**
     * Handle follow action - ENHANCED VERSION
     */
    private void handleFollowAction(String params) {
        Player targetPlayer = null;
        
        // If params specify a player name, follow that player
        if (params != null && !params.isEmpty() && !params.equals("nearest")) {
            targetPlayer = this.level().getServer()
                .getPlayerList()
                .getPlayerByName(params.trim());
        }
        
        // Otherwise find nearest player
        if (targetPlayer == null) {
            targetPlayer = this.level().getNearestPlayer(this, 50.0D);
        }
        
        if (targetPlayer != null) {
            this.followTarget = targetPlayer;
            this.currentTask = "following";
            
            // Clear existing goals and add follow goal
            this.goalSelector.getAvailableGoals().removeIf(goal -> 
                goal.getGoal() instanceof FollowMobGoal);
            
            this.goalSelector.addGoal(1, new FollowMobGoal(this, 1.0D, 3.0F, 10.0F));
            this.getNavigation().moveTo(targetPlayer, 1.0D);
            
            sayInChat("I'll follow " + targetPlayer.getName().getString() + "!");
            AiNpcMod.LOGGER.info("[Professor G] Following player: {}", 
                targetPlayer.getName().getString());
        } else {
            sayInChat("*looks around* Who should I follow?");
        }
    }
    
    /**
     * Handle attack action - ENHANCED VERSION
     */
    private void handleAttackAction(String params) {
        if (params == null || params.isEmpty()) {
            params = "pig";
        }
        
        String targetType = params.toLowerCase().trim();
        LivingEntity target = null;
        
        // Find target based on type
        if (targetType.contains("pig")) {
            target = this.level().getNearestEntity(
                Pig.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT,
                this,
                this.getX(), this.getY(), this.getZ(),
                this.getBoundingBox().inflate(20.0D)
            );
        } else if (targetType.equals("nearest") || targetType.equals("nearest_mob")) {
            // Find any nearby living entity
            target = this.level().getNearestEntity(
                LivingEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT
                    .selector(e -> !(e instanceof Player)),
                this,
                this.getX(), this.getY(), this.getZ(),
                this.getBoundingBox().inflate(20.0D)
            );
        }
        
        if (target != null) {
            this.setTarget(target);
            this.currentTask = "attacking";
            
            // Add melee attack goal if not present
            this.goalSelector.getAvailableGoals().removeIf(goal -> 
                goal.getGoal() instanceof MeleeAttackGoal);
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
            
            sayInChat("*charges* Engaging " + targetType + "!");
            AiNpcMod.LOGGER.info("[Professor G] Attacking target: {} at distance {}", 
                target.getType().getDescription().getString(), 
                this.distanceTo(target));
        } else {
            sayInChat("*looks around* I don't see any " + targetType + " nearby...");
        }
    }
    
    /**
     * Handle emote action - ENHANCED VERSION
     */
    private void handleEmoteAction(String params) {
        if (params == null || params.isEmpty()) {
            params = "happy";
        }
        
        String emote = params.toLowerCase().trim();
        
        switch (emote) {
            case "happy":
                sayInChat("*smiles warmly*");
                spawnParticles("heart");
                break;
            case "sad":
                sayInChat("*looks down sadly*");
                spawnParticles("rain");
                break;
            case "angry":
                sayInChat("*grumbles angrily*");
                spawnParticles("angry_villager");
                break;
            case "confused":
                sayInChat("*scratches head in confusion*");
                spawnParticles("smoke");
                break;
            case "excited":
                sayInChat("*jumps excitedly*");
                this.setJumping(true);
                spawnParticles("happy_villager");
                break;
            case "thinking":
                sayInChat("*strokes beard thoughtfully*");
                spawnParticles("enchant");
                break;
            default:
                sayInChat("*" + emote + "*");
        }
        
        AiNpcMod.LOGGER.info("[Professor G] Emoted: {}", emote);
    }
    
    /**
     * Spawn particles around the NPC
     */
    private void spawnParticles(String particleType) {
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        
        try {
            net.minecraft.core.particles.ParticleOptions particle = switch (particleType) {
                case "heart" -> net.minecraft.core.particles.ParticleTypes.HEART;
                case "angry_villager" -> net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER;
                case "happy_villager" -> net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
                case "smoke" -> net.minecraft.core.particles.ParticleTypes.SMOKE;
                case "rain" -> net.minecraft.core.particles.ParticleTypes.RAIN;
                case "enchant" -> net.minecraft.core.particles.ParticleTypes.ENCHANT;
                default -> net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
            };
            
            // Spawn particles around the NPC
            for (int i = 0; i < 5; i++) {
                double offsetX = (this.random.nextDouble() - 0.5) * 0.5;
                double offsetY = this.random.nextDouble() * 1.5 + 0.5;
                double offsetZ = (this.random.nextDouble() - 0.5) * 0.5;
                
                serverLevel.sendParticles(
                    particle,
                    this.getX() + offsetX,
                    this.getY() + offsetY,
                    this.getZ() + offsetZ,
                    1, 0, 0, 0, 0
                );
            }
        } catch (Exception e) {
            AiNpcMod.LOGGER.error("[Professor G] Failed to spawn particles", e);
        }
    }
    
    /**
     * Tick override to check task completion
     */
    @Override
    public void tick() {
        super.tick();
        
        if (!this.level().isClientSide) {
            // Check if we've reached our destination
            if ("moving_to_coords".equals(currentTask) || "moving_to_player".equals(currentTask)) {
                if (this.getNavigation().isDone()) {
                    sayInChat("*arrives* I've reached my destination!");
                    currentTask = "idle";
                }
            }
            
            // Check if follow target is too far
            if ("following".equals(currentTask) && followTarget != null) {
                double distance = this.distanceTo(followTarget);
                if (distance > 50.0D) {
                    sayInChat("*stops* Too far away, I'll wait here.");
                    currentTask = "idle";
                    followTarget = null;
                } else if (distance > 5.0D && this.getNavigation().isDone()) {
                    // Keep following if we stopped but target is still in range
                    this.getNavigation().moveTo(followTarget, 1.0D);
                }
            }
        }
    }
    
    /**
     * Get current task status
     */
    public String getCurrentTask() {
        return currentTask;
    }
}