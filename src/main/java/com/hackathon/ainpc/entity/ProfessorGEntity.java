package com.hackathon.ainpc.entity;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ai.FollowPlayerGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ProfessorGEntity extends PathfinderMob {
    public static final String NPC_NAME = "Professor G";
    
    // Track current AI task
    private String currentTask = "idle";
    private LivingEntity followTarget = null;
    private FollowPlayerGoal followGoal = null;
    private MeleeAttackGoal attackGoal = null;

    public ProfessorGEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal(NPC_NAME));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)  // Slightly slower for better control
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D);  // Add knockback for attacks
    }

    @Override
    protected void registerGoals() {
        // Basic AI goals - priority 5 and below for base behaviors
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
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
        
        // Clear previous dynamic goals before executing new action
        clearDynamicGoals();
        
        switch (action.toLowerCase()) {
            case "say":
            case "respond_chat":
                if (actionParams != null && !actionParams.isEmpty()) {
                    sayInChat(actionParams);
                }
                break;
                
            case "move_to":
                handleMoveToAction(actionParams);
                break;
                
            case "follow":
            case "follow_player":
                handleFollowAction(actionParams);
                break;
                
            case "attack":
            case "attack_target":
                handleAttackAction(actionParams);
                break;
                
            case "mine_block":
                handleMineBlockAction(actionParams);
                break;
                
            case "emote":
                handleEmoteAction(actionParams);
                break;
                
            case "give_item":
                handleGiveItemAction(actionParams);
                break;
                
            case "pickup_item":
                handlePickupItemAction(actionParams);
                break;
                
            case "idle":
                handleIdleAction();
                break;
                
            default:
                AiNpcMod.LOGGER.warn("[Professor G] Unknown action: {}", action);
                sayInChat("*looks confused* I'm not sure how to do that...");
        }
    }
    
    /**
     * Clear all dynamically added goals (follow, attack)
     */
    private void clearDynamicGoals() {
        if (followGoal != null) {
            this.goalSelector.removeGoal(followGoal);
            followGoal = null;
        }
        if (attackGoal != null) {
            this.goalSelector.removeGoal(attackGoal);
            this.targetSelector.getAvailableGoals().clear();  // Clear target selector too
            attackGoal = null;
        }
        this.setTarget(null);
        this.getNavigation().stop();
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
            AiNpcMod.LOGGER.info("[Professor G] Said: {}", message);
        }
    }
    
    /**
     * Handle move_to action - FIXED VERSION with proper Y coordinate handling
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
                    
                    // FIX: Use the actual target position, not just the entity
                    boolean success = this.getNavigation().moveTo(
                        targetPos.getX() + 0.5, 
                        targetPos.getY(), 
                        targetPos.getZ() + 0.5, 
                        1.0D
                    );
                    
                    this.currentTask = "moving_to_player";
                    
                    if (success) {
                        sayInChat(String.format("On my way to %s! (%.1f blocks away)", 
                            playerName, distance));
                        
                        AiNpcMod.LOGGER.info("[Professor G] Moving to player {} at {}", 
                            playerName, targetPos);
                    } else {
                        sayInChat("I can't find a path to " + playerName + "!");
                        AiNpcMod.LOGGER.warn("[Professor G] Pathfinding failed - no valid path");
                    }
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
                    
                    // FIX: Find valid Y coordinate at target location
                    double y = coords.length == 3 ? 
                        Double.parseDouble(coords[1].trim()) : 
                        findGroundLevel(x, z);
                    
                    BlockPos targetPos = new BlockPos((int)x, (int)y, (int)z);
                    double distance = this.position().distanceTo(new Vec3(x, y, z));
                    
                    // FIX: Ensure we're pathfinding to a valid position
                    // Center of the block for better pathfinding
                    boolean success = this.getNavigation().moveTo(
                        x + 0.5, 
                        y, 
                        z + 0.5, 
                        1.0D
                    );
                    
                    if (success) {
                        this.currentTask = "moving_to_coords";
                        sayInChat(String.format("Moving to (%d, %d, %d)! That's %.1f blocks away.", 
                            (int)x, (int)y, (int)z, distance));
                        
                        AiNpcMod.LOGGER.info("[Professor G] Pathfinding to {} - Path valid: {}", 
                            targetPos, this.getNavigation().getPath() != null);
                    } else {
                        sayInChat("*stumbles* I can't find a path there...");
                        AiNpcMod.LOGGER.warn("[Professor G] Pathfinding failed to {} - Target may be unreachable", targetPos);
                    }
                }
            }
        } catch (Exception e) {
            AiNpcMod.LOGGER.error("[Professor G] Error in move_to action", e);
            sayInChat("*stumbles* I can't quite navigate there...");
        }
    }
    
    /**
     * Find ground level at given X, Z coordinates
     */
    private double findGroundLevel(double x, double z) {
        BlockPos checkPos = new BlockPos((int)x, (int)this.getY(), (int)z);
        
        // Search down for solid ground
        for (int dy = 0; dy >= -10; dy--) {
            BlockPos testPos = checkPos.offset(0, dy, 0);
            if (!this.level().getBlockState(testPos).isAir() && 
                this.level().getBlockState(testPos.above()).isAir()) {
                return testPos.getY() + 1;  // Stand on top of the block
            }
        }
        
        // Search up for ground
        for (int dy = 1; dy <= 10; dy++) {
            BlockPos testPos = checkPos.offset(0, dy, 0);
            if (!this.level().getBlockState(testPos).isAir() && 
                this.level().getBlockState(testPos.above()).isAir()) {
                return testPos.getY() + 1;
            }
        }
        
        return this.getY();  // Fallback to current Y
    }
    
    /**
     * Handle follow action - FIXED VERSION
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
            
            // Add follow goal with high priority
            followGoal = new FollowPlayerGoal(this, 1.0D, 3.0F, 10.0F);
            followGoal.setTargetPlayer(targetPlayer);
            this.goalSelector.addGoal(1, followGoal);
            
            sayInChat("I'll follow " + targetPlayer.getName().getString() + "!");
            AiNpcMod.LOGGER.info("[Professor G] Following player: {}", 
                targetPlayer.getName().getString());
        } else {
            sayInChat("*looks around* Who should I follow?");
        }
    }
    
    /**
     * Handle attack action - COMPLETELY FIXED VERSION
     */
    private void handleAttackAction(String params) {
        if (params == null || params.isEmpty()) {
            params = "nearest";
        }
        
        String targetType = params.toLowerCase().trim();
        final LivingEntity target;  // FIXED: Made final for lambda
        
        AiNpcMod.LOGGER.info("[Professor G] Searching for attack target: {}", targetType);
        
        // Find target based on type
        if (targetType.contains("pig")) {
            target = this.level().getNearestEntity(
                Pig.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT,
                this,
                this.getX(), this.getY(), this.getZ(),
                this.getBoundingBox().inflate(20.0D)
            );
        } else if (targetType.contains("zombie") || targetType.contains("skeleton") || 
                   targetType.contains("creeper") || targetType.contains("spider")) {
            // Find specific monster type
            final String finalTargetType = targetType;  // FIXED: Made final for lambda
            target = this.level().getNearestEntity(
                Monster.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT
                    .selector(e -> e.getType().toString().toLowerCase().contains(finalTargetType)),
                this,
                this.getX(), this.getY(), this.getZ(),
                this.getBoundingBox().inflate(20.0D)
            );
        } else {
            // Find any nearby living entity (excluding players) - for "nearest" or "nearest_mob"
            target = this.level().getNearestEntity(
                LivingEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT
                    .selector(e -> !(e instanceof Player) && !(e instanceof ProfessorGEntity)),
                this,
                this.getX(), this.getY(), this.getZ(),
                this.getBoundingBox().inflate(20.0D)
            );
        }
        
        if (target != null) {
            // FIX: Set target FIRST before adding attack goal
            this.setTarget(target);
            this.currentTask = "attacking";
            
            // FIX: Add BOTH attack goal and target selector goal
            // MeleeAttackGoal needs the target selector to work properly
            attackGoal = new MeleeAttackGoal(this, 1.2D, true);  // true = follow even when not seeing
            this.goalSelector.addGoal(2, attackGoal);
            
            // CRITICAL FIX: Add target selector goal to maintain target
            NearestAttackableTargetGoal<LivingEntity> targetGoal = 
                new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, 
                    e -> e == target);  // Only target the specific entity
            this.targetSelector.addGoal(1, targetGoal);
            
            sayInChat("*charges* Engaging " + target.getType().getDescription().getString() + "!");
            AiNpcMod.LOGGER.info("[Professor G] Attacking {} at distance {} - Target set: {}", 
                target.getType().getDescription().getString(), 
                this.distanceTo(target),
                this.getTarget() != null);
        } else {
            sayInChat("*looks around* I don't see any " + targetType + " nearby...");
            AiNpcMod.LOGGER.warn("[Professor G] No target found for: {}", targetType);
        }
    }
    
    /**
     * OVERRIDE: Make Professor G actually able to attack
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        // Call parent attack method
        boolean success = super.doHurtTarget(target);
        
        if (success) {
            // Play swing animation
            this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            AiNpcMod.LOGGER.info("[Professor G] Successfully attacked target!");
        }
        
        return success;
    }
    
    /**
     * Handle mine_block action - ENHANCED VERSION
     */
    private void handleMineBlockAction(String params) {
        if (params == null || params.isEmpty()) {
            sayInChat("What should I mine?");
            return;
        }
        
        String blockType = params.toLowerCase().trim();
        AiNpcMod.LOGGER.info("[Professor G] Looking for block to mine: {}", blockType);
        
        // Search for block in nearby area
        BlockPos nearestBlock = findNearbyBlock(blockType, 16);
        
        if (nearestBlock != null) {
            double distance = this.position().distanceTo(Vec3.atCenterOf(nearestBlock));
            this.currentTask = "mining";
            
            // Move to block
            boolean success = this.getNavigation().moveTo(
                nearestBlock.getX() + 0.5, 
                nearestBlock.getY(), 
                nearestBlock.getZ() + 0.5, 
                1.0D
            );
            
            if (success) {
                sayInChat(String.format("I'll mine that %s! It's %.1f blocks away.", 
                    blockType, distance));
                
                // Schedule block breaking after reaching it
                scheduleBlockBreak(nearestBlock, 100); // Break after 100 ticks (5 seconds)
            } else {
                sayInChat("I can't reach that " + blockType + "!");
            }
        } else {
            sayInChat("I don't see any " + blockType + " nearby...");
            AiNpcMod.LOGGER.warn("[Professor G] No {} found nearby", blockType);
        }
    }
    
    /**
     * Find nearby block of specific type
     */
    private BlockPos findNearbyBlock(String blockType, int radius) {
        BlockPos npcPos = this.blockPosition();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = npcPos.offset(x, y, z);
                    BlockState state = this.level().getBlockState(checkPos);
                    Block block = state.getBlock();
                    
                    String blockName = block.getDescriptionId().toLowerCase();
                    
                    if (blockName.contains(blockType) || 
                        block.toString().toLowerCase().contains(blockType)) {
                        return checkPos;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Schedule block breaking after delay
     */
    private void scheduleBlockBreak(BlockPos pos, int delayTicks) {
        // This is a simple implementation - in production you'd want proper animation
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> {
                try {
                    Thread.sleep(delayTicks * 50); // Convert ticks to milliseconds
                    
                    if (this.distanceToSqr(Vec3.atCenterOf(pos)) < 16.0D) {
                        serverLevel.destroyBlock(pos, true);
                        sayInChat("*mines successfully* Got it!");
                        this.currentTask = "idle";
                    } else {
                        sayInChat("*too far* I couldn't reach it in time...");
                    }
                } catch (InterruptedException e) {
                    AiNpcMod.LOGGER.error("Block break interrupted", e);
                }
            });
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
            case "curious":
                sayInChat("*looks around curiously*");
                spawnParticles("note");
                break;
            case "determined":
                sayInChat("*nods with determination*");
                spawnParticles("flame");
                break;
            default:
                sayInChat("*" + emote + "*");
        }
        
        AiNpcMod.LOGGER.info("[Professor G] Emoted: {}", emote);
    }
    
    /**
     * Handle give_item action
     */
    private void handleGiveItemAction(String params) {
        sayInChat("*reaches into pocket* I would give you " + params + ", but I don't have my inventory system yet!");
        AiNpcMod.LOGGER.info("[Professor G] Give item not yet implemented: {}", params);
    }
    
    /**
     * Handle pickup_item action
     */
    private void handlePickupItemAction(String params) {
        sayInChat("*looks at ground* I would pick up items, but that's not implemented yet!");
        AiNpcMod.LOGGER.info("[Professor G] Pickup item not yet implemented");
    }
    
    /**
     * Handle idle action
     */
    private void handleIdleAction() {
        clearDynamicGoals();
        this.currentTask = "idle";
        AiNpcMod.LOGGER.info("[Professor G] Now idling");
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
                case "note" -> net.minecraft.core.particles.ParticleTypes.NOTE;
                case "flame" -> net.minecraft.core.particles.ParticleTypes.FLAME;
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
                    sayInChat("*stops* You're too far away, I'll wait here.");
                    clearDynamicGoals();
                    currentTask = "idle";
                }
            }
            
            // Check if target is dead during attack
            if ("attacking".equals(currentTask)) {
                LivingEntity target = this.getTarget();
                if (target == null || !target.isAlive()) {
                    sayInChat("*stops* The threat is neutralized!");
                    clearDynamicGoals();
                    currentTask = "idle";
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