package com.hackathon.ainpc.entity;

import com.hackathon.ainpc.networking.StatePayload;
import com.hackathon.ainpc.networking.AiBridgeService;
import com.hackathon.ainpc.networking.NpcInteractionResponse;
import net.minecraft.nbt.CompoundTag;
import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ai.FollowPlayerGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import com.hackathon.ainpc.handler.ChatHandler;

import java.util.UUID;
import java.util.Random;
import java.util.List;

public class ProfessorGEntity extends PathfinderMob {
    // List of possible NPC names
    private static final String[] NPC_NAMES = {
        "Professor Redstone",
        "Professor Diamond",
        "Professor Emerald",
        "Professor Obsidian",
        "Professor Quartz",
        "Professor Prismarine",
        "Professor Netherite",
        "Professor Amethyst"
    };
    
    private static final Random RANDOM = new Random();
    
    // Unique NPC identifier (name + UUID)
    private String npcId;
    private String npcName;
    
    // Inventory system - 27 slots like a chest
    private final SimpleContainer inventory = new SimpleContainer(27);
    
    // Track current AI task
    private String currentTask = "idle";
    private LivingEntity followTarget = null;
    private FollowPlayerGoal followGoal = null;
    private MeleeAttackGoal attackGoal = null;
    private BlockPos targetPosition = null;
    private ItemEntity targetPickupItem = null;

    private String currentEmotion = "neutral";
    private String currentObjective = "Exploring the world";
    private long lastStateSyncTime = 0;
    private static final long STATE_SYNC_INTERVAL = 60000; // 60 seconds

    // Autonomous behavior
    private long lastAutonomousAction = 0;
    private static final long AUTONOMOUS_ACTION_INTERVAL = 15000; // 15 seconds
    private Player lastDetectedPlayer = null;
    private long lastPlayerDetection = 0;
    private static final long PLAYER_DETECTION_COOLDOWN = 30000; // 30 seconds between approaches
    private boolean hasInitiatedConversation = false;

    // Potion system
    private long lastPotionCheck = 0;
    private static final long POTION_CHECK_INTERVAL = 10000; // Check every 10 seconds
    private static final float HEALTH_THRESHOLD_LOW = 0.5f; // 50% health
    private static final float HEALTH_THRESHOLD_CRITICAL = 0.3f; // 30% health

    public ProfessorGEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        
        // Generate unique name
        this.npcName = NPC_NAMES[RANDOM.nextInt(NPC_NAMES.length)];
        this.npcId = this.npcName + "_" + UUID.randomUUID().toString().substring(0, 8);
        
        this.setCustomName(Component.literal(this.npcName));
        this.setCustomNameVisible(true);
        
        AiNpcMod.LOGGER.info("[NPC] Spawned new NPC: {} (ID: {})", this.npcName, this.npcId);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
    
    /**
     * Get the unique NPC ID (used for memory storage)
     */
    public String getNpcId() {
        return this.npcId;
    }
    
    /**
     * Get the NPC's display name
     */
    public String getNpcName() {
        return this.npcName;
    }
    
    // ==================== POTION SYSTEM ====================
    
    /**
     * Check if NPC needs to drink a potion and do so autonomously
     */
    private void checkAndDrinkPotions() {
        long currentTime = System.currentTimeMillis();
        
        // Only check every 10 seconds
        if (currentTime - lastPotionCheck < POTION_CHECK_INTERVAL) {
            return;
        }
        
        lastPotionCheck = currentTime;
        
        // Check health percentage
        float healthPercent = this.getHealth() / this.getMaxHealth();
        
        // Critical health - prioritize healing
        if (healthPercent < HEALTH_THRESHOLD_CRITICAL) {
            if (drinkBestHealingPotion()) {
                sayInChat("*urgently drinks healing potion* I needed that!");
                return;
            }
        }
        
        // Low health - drink healing if available
        if (healthPercent < HEALTH_THRESHOLD_LOW) {
            if (drinkBestHealingPotion()) {
                sayInChat("*drinks healing potion* That's better.");
                return;
            }
        }
        
        // If in combat, consider drinking buff potions
        if (this.getTarget() != null && this.getTarget().isAlive()) {
            // Try strength potion first
            if (drinkPotionByEffect("strength")) {
                sayInChat("*drinks strength potion* Time to fight!");
                return;
            }
            // Try speed potion
            if (drinkPotionByEffect("speed")) {
                sayInChat("*drinks swiftness potion* Let's go!");
                return;
            }
        }
        
        // Random chance to drink buff potions when idle (5%)
        if (RANDOM.nextInt(100) < 5 && "idle".equals(currentTask)) {
            if (drinkRandomBuffPotion()) {
                return;
            }
        }
    }
    
    /**
     * Drink the best available healing potion
     */
    private boolean drinkBestHealingPotion() {
        // Priority: Strong Healing > Healing > Regeneration
        if (drinkPotionByEffect("strong_healing")) {
            return true;
        }
        if (drinkPotionByEffect("healing")) {
            return true;
        }
        if (drinkPotionByEffect("regeneration")) {
            return true;
        }
        return false;
    }
    
    /**
     * Drink a random buff potion (for fun/roleplay)
     */
    private boolean drinkRandomBuffPotion() {
        String[] buffEffects = {"strength", "speed", "fire_resistance", "water_breathing", "night_vision", "invisibility", "jump_boost"};
        String randomEffect = buffEffects[RANDOM.nextInt(buffEffects.length)];
        
        if (drinkPotionByEffect(randomEffect)) {
            sayInChat("*drinks " + randomEffect.replace("_", " ") + " potion* Interesting effects!");
            return true;
        }
        return false;
    }
    
    /**
     * Find and drink a potion with specific effect
     */
    private boolean drinkPotionByEffect(String effectName) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof PotionItem) {
                String potionName = PotionUtils.getPotion(stack).getName("").toLowerCase();
                
                if (potionName.contains(effectName.toLowerCase())) {
                    return drinkPotionFromSlot(i);
                }
            }
        }
        return false;
    }
    
    /**
     * Drink potion from specific inventory slot
     */
    private boolean drinkPotionFromSlot(int slot) {
        ItemStack potionStack = inventory.getItem(slot);
        
        if (potionStack.isEmpty() || !(potionStack.getItem() instanceof PotionItem)) {
            return false;
        }
        
        // Get potion effects
        List<MobEffectInstance> effects = PotionUtils.getMobEffects(potionStack);
        String potionName = PotionUtils.getPotion(potionStack).getName("");
        
        AiNpcMod.LOGGER.info("[{}] Drinking potion: {}", this.npcName, potionName);
        
        // Apply effects
        for (MobEffectInstance effect : effects) {
            this.addEffect(new MobEffectInstance(effect));
        }
        
        // Instant health/damage effects
        if (PotionUtils.getPotion(potionStack) == Potions.HEALING) {
            this.heal(4.0F);
        } else if (PotionUtils.getPotion(potionStack) == Potions.STRONG_HEALING) {
            this.heal(8.0F);
        }
        
        // Spawn particles
        spawnPotionParticles();
        
        // Play drinking sound
        this.playSound(net.minecraft.sounds.SoundEvents.GENERIC_DRINK, 1.0F, 1.0F);
        
        // Remove potion from inventory (consume it)
        potionStack.shrink(1);
        if (potionStack.isEmpty()) {
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        
        // Add empty bottle
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (!addItemToInventory(bottle)) {
            dropItem(bottle); // Drop if inventory full
        }
        
        return true;
    }
    
    /**
     * Drink a specific potion by name (called by AI or command)
     */
    public boolean drinkPotionByName(String potionName) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof PotionItem) {
                String stackName = stack.getHoverName().getString().toLowerCase();
                String potionType = PotionUtils.getPotion(stack).getName("").toLowerCase();
                
                if (stackName.contains(potionName.toLowerCase()) || 
                    potionType.contains(potionName.toLowerCase())) {
                    
                    String displayName = stack.getHoverName().getString();
                    sayInChat("*drinks " + displayName + "*");
                    return drinkPotionFromSlot(i);
                }
            }
        }
        
        sayInChat("I don't have any " + potionName + " potion...");
        return false;
    }
    
    /**
     * Check if NPC has any potion
     */
    public boolean hasPotion(String potionName) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof PotionItem) {
                if (potionName == null || potionName.isEmpty()) {
                    return true; // Any potion
                }
                
                String stackName = stack.getHoverName().getString().toLowerCase();
                String potionType = PotionUtils.getPotion(stack).getName("").toLowerCase();
                
                if (stackName.contains(potionName.toLowerCase()) || 
                    potionType.contains(potionName.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Get health status as a string
     */
    public String getHealthStatus() {
        float percent = (this.getHealth() / this.getMaxHealth()) * 100;
        
        if (percent >= 90) {
            return "excellent";
        } else if (percent >= 70) {
            return "good";
        } else if (percent >= 50) {
            return "okay";
        } else if (percent >= 30) {
            return "hurt";
        } else {
            return "badly hurt";
        }
    }
    
    /**
     * Spawn particle effects for drinking potion
     */
    private void spawnPotionParticles() {
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        
        try {
            for (int i = 0; i < 8; i++) {
                double offsetX = (this.random.nextDouble() - 0.5) * 0.3;
                double offsetY = this.random.nextDouble() * 0.5 + 1.5;
                double offsetZ = (this.random.nextDouble() - 0.5) * 0.3;
                
                serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.EFFECT,
                    this.getX() + offsetX,
                    this.getY() + offsetY,
                    this.getZ() + offsetZ,
                    1, 0, 0, 0, 0
                );
            }
        } catch (Exception e) {
            AiNpcMod.LOGGER.error("[{}] Failed to spawn potion particles", this.npcName, e);
        }
    }
    
    // ==================== AUTONOMOUS BEHAVIOR ====================
    
    /**
     * Detect nearby players and potentially initiate conversation
     */
    private void performAutonomousBehavior() {
        long currentTime = System.currentTimeMillis();
        
        // Only act every 15 seconds
        if (currentTime - lastAutonomousAction < AUTONOMOUS_ACTION_INTERVAL) {
            return;
        }
        
        lastAutonomousAction = currentTime;
        
        // Find nearest player within 10 blocks
        Player nearestPlayer = this.level().getNearestPlayer(this, 10.0D);
        
        if (nearestPlayer != null && !nearestPlayer.isSpectator()) {
            double distance = this.distanceTo(nearestPlayer);
            
            // If player is close (3-8 blocks) and we haven't talked recently
            if (distance > 3.0D && distance < 8.0D) {
                if (lastDetectedPlayer != nearestPlayer || 
                    currentTime - lastPlayerDetection > PLAYER_DETECTION_COOLDOWN) {
                    
                    lastDetectedPlayer = nearestPlayer;
                    lastPlayerDetection = currentTime;
                    hasInitiatedConversation = false;
                    
                    // Approach and greet the player
                    initiateConversationWith(nearestPlayer);
                }
            }
            
            // Random autonomous comments (20% chance)
            if (RANDOM.nextInt(100) < 20) {
                makeAutonomousComment(nearestPlayer);
            }
        }
    }
    
    /**
     * NPC initiates conversation with a player
     */
    private void initiateConversationWith(Player player) {
        if (hasInitiatedConversation) {
            return;
        }
        
        hasInitiatedConversation = true;
        
        String playerName = player.getName().getString();
        
        // Walk towards player
        boolean success = this.getNavigation().moveTo(player, 1.0D);
        
        if (success) {
            AiNpcMod.LOGGER.info("[{}] Approaching player: {}", this.npcName, playerName);
            
            // Send greeting to AI
            String greeting = "I notice " + playerName + " nearby. Should I greet them?";
            
            AiBridgeService.sendToAI(
                "SYSTEM", 
                this.npcId, 
                greeting, 
                new AiBridgeService.Callback() {
                    @Override
                    public void onSuccess(NpcInteractionResponse response) {
                        if (level() instanceof ServerLevel serverLevel) {
                            serverLevel.getServer().execute(() -> {
                                if (response != null && response.action != null) {
                                    String chatResponse = response.action.chatResponse;
                                    if (chatResponse != null && !chatResponse.isEmpty()) {
                                        sayInChat(chatResponse);
                                        
                                        // Update emotion
                                        if (response.newState != null && response.newState.emotion != null) {
                                            setEmotion(response.newState.emotion);
                                        }
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        // Fallback to generic greeting
                        sayInChat("*waves* Hello, " + playerName + "!");
                    }
                }
            );
        }
    }
    
    /**
     * Make random autonomous comments
     */
    private void makeAutonomousComment(Player nearbyPlayer) {
        String[] comments = {
            "*looks around curiously*",
            "*hums thoughtfully*",
            "Interesting day for research...",
            "*adjusts spectacles*",
            "The weather is quite nice today.",
            "*mutters about experiments*"
        };
        
        String comment = comments[RANDOM.nextInt(comments.length)];
        sayInChat(comment);
    }
    
    // ==================== INVENTORY METHODS ====================
    
    public SimpleContainer getInventory() {
        return inventory;
    }
    
    public boolean addItemToInventory(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (slotStack.isEmpty()) {
                inventory.setItem(i, stack.copy());
                AiNpcMod.LOGGER.info("[{}] Added {} to inventory slot {}", 
                    this.npcName, stack.getHoverName().getString(), i);
                return true;
            } else if (ItemStack.isSameItemSameTags(slotStack, stack) && 
                       slotStack.getCount() < slotStack.getMaxStackSize()) {
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                int toAdd = Math.min(space, stack.getCount());
                slotStack.grow(toAdd);
                stack.shrink(toAdd);
                
                if (stack.isEmpty()) {
                    AiNpcMod.LOGGER.info("[{}] Stacked {} in slot {}", 
                        this.npcName, slotStack.getHoverName().getString(), i);
                    return true;
                }
            }
        }
        
        AiNpcMod.LOGGER.warn("[{}] Inventory is full!", this.npcName);
        return false;
    }
    
    public ItemStack removeItemFromInventory(String itemName) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                String stackName = stack.getHoverName().getString().toLowerCase();
                String registryName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                
                if (stackName.contains(itemName.toLowerCase()) || 
                    registryName.contains(itemName.toLowerCase())) {
                    ItemStack result = stack.split(1);
                    if (stack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                    AiNpcMod.LOGGER.info("[{}] Removed {} from inventory slot {}", 
                        this.npcName, result.getHoverName().getString(), i);
                    return result;
                }
            }
        }
        return ItemStack.EMPTY;
    }
    
    public boolean hasItem(String itemName) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                String stackName = stack.getHoverName().getString().toLowerCase();
                String registryName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                
                if (stackName.contains(itemName.toLowerCase()) || 
                    registryName.contains(itemName.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public String getInventoryContents() {
        StringBuilder sb = new StringBuilder();
        int itemCount = 0;
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                if (itemCount > 0) sb.append(", ");
                sb.append(stack.getCount()).append("x ").append(stack.getHoverName().getString());
                itemCount++;
            }
        }
        
        return itemCount > 0 ? sb.toString() : "Empty";
    }
    
    public void dropItem(ItemStack stack) {
        if (!stack.isEmpty() && !this.level().isClientSide) {
            ItemEntity itemEntity = new ItemEntity(
                this.level(),
                this.getX(),
                this.getY() + 1.0,
                this.getZ(),
                stack.copy()
            );
            itemEntity.setDefaultPickUpDelay();
            this.level().addFreshEntity(itemEntity);
            AiNpcMod.LOGGER.info("[{}] Dropped {} at position", 
                this.npcName, stack.getHoverName().getString());
        }
    }
    
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        // Save unique identity
        tag.putString("NpcId", this.npcId);
        tag.putString("NpcName", this.npcName);
        
        tag.putString("CurrentEmotion", this.currentEmotion);
        tag.putString("CurrentObjective", this.currentObjective);
        tag.putLong("LastStateSyncTime", this.lastStateSyncTime);
        tag.putLong("LastAutonomousAction", this.lastAutonomousAction);
        tag.putLong("LastPlayerDetection", this.lastPlayerDetection);
        tag.putLong("LastPotionCheck", this.lastPotionCheck);

        CompoundTag inventoryTag = new CompoundTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                stack.save(slotTag);
                inventoryTag.put("Slot" + i, slotTag);
            }
        }
        tag.put("Inventory", inventoryTag);
        tag.putString("CurrentTask", currentTask);
    }
    
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        
        // Load unique identity
        if (tag.contains("NpcId")) {
            this.npcId = tag.getString("NpcId");
        }
        if (tag.contains("NpcName")) {
            this.npcName = tag.getString("NpcName");
            this.setCustomName(Component.literal(this.npcName));
        }
        
        if (tag.contains("CurrentEmotion")) {
            this.currentEmotion = tag.getString("CurrentEmotion");
        }
        if (tag.contains("CurrentObjective")) {
            this.currentObjective = tag.getString("CurrentObjective");
        }
        if (tag.contains("LastStateSyncTime")) {
            this.lastStateSyncTime = tag.getLong("LastStateSyncTime");
        }
        if (tag.contains("LastAutonomousAction")) {
            this.lastAutonomousAction = tag.getLong("LastAutonomousAction");
        }
        if (tag.contains("LastPlayerDetection")) {
            this.lastPlayerDetection = tag.getLong("LastPlayerDetection");
        }
        if (tag.contains("LastPotionCheck")) {
            this.lastPotionCheck = tag.getLong("LastPotionCheck");
        }

        if (tag.contains("Inventory")) {
            CompoundTag inventoryTag = tag.getCompound("Inventory");
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (inventoryTag.contains("Slot" + i)) {
                    ItemStack stack = ItemStack.of(inventoryTag.getCompound("Slot" + i));
                    inventory.setItem(i, stack);
                }
            }
        }
        
        if (tag.contains("CurrentTask")) {
            currentTask = tag.getString("CurrentTask");
        }
    }
    
    public String getCurrentTask() {
        return String.format("%s (feeling: %s)", 
                this.currentTask, 
                this.currentEmotion);
    }
    
    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int lootingLevel, boolean allowDrops) {
        super.dropCustomDeathLoot(source, lootingLevel, allowDrops);
        
        // Drop all inventory items
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                dropItem(stack);
            }
        }
        
        // Clear memory on death (will be handled by Python backend)
        AiNpcMod.LOGGER.info("[{}] Died - memory will be cleared", this.npcName);
    }
    
    // ==================== AI ACTION METHODS ====================
    
    public void executeAIAction(String action, String actionParams) {
        if (this.level().isClientSide) {
            return;
        }
        
        AiNpcMod.LOGGER.info("[{}] Executing action: {} with params: {}", 
            this.npcName, action, actionParams);
        
        if (action == null) {
            return;
        }
        
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
                
            case "drink_potion":
                handleDrinkPotionAction(actionParams);
                break;
                
            case "idle":
                handleIdleAction();
                break;
                
            default:
                AiNpcMod.LOGGER.warn("[{}] Unknown action: {}", this.npcName, action);
                sayInChat("*looks confused* I'm not sure how to do that...");
        }
    }
    
    private void clearDynamicGoals() {
        if (followGoal != null) {
            this.goalSelector.removeGoal(followGoal);
            followGoal = null;
        }
        if (attackGoal != null) {
            this.goalSelector.removeGoal(attackGoal);
            this.targetSelector.getAvailableGoals().clear();
            attackGoal = null;
        }
        this.setTarget(null);
        this.getNavigation().stop();
    }
    
    public void sayInChat(String message) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§e[" + this.npcName + "]§r " + message), 
                false
            );
            AiNpcMod.LOGGER.info("[{}] Said: {}", this.npcName, message);
        }
    }
    
    private void handleDrinkPotionAction(String params) {
        if (params == null || params.isEmpty()) {
            // Drink any healing potion
            if (drinkBestHealingPotion()) {
                sayInChat("*drinks healing potion* Refreshing!");
            } else if (hasPotion(null)) {
                // Drink any potion
                drinkRandomBuffPotion();
            } else {
                sayInChat("I don't have any potions to drink...");
            }
        } else {
            // Drink specific potion
            drinkPotionByName(params);
        }
    }
    
    private void handleMoveToAction(String params) {
        if (params == null || params.isEmpty()) {
            sayInChat("*scratches head* Where should I go?");
            return;
        }
        
        try {
            if (params.startsWith("player:")) {
                String playerName = params.substring(7).trim();
                Player targetPlayer = this.level().getServer()
                    .getPlayerList()
                    .getPlayerByName(playerName);
                
                if (targetPlayer != null) {
                    double distance = this.distanceTo(targetPlayer);
                    boolean success = this.getNavigation().moveTo(targetPlayer, 1.2D);
                    this.currentTask = "moving_to_player";
                    
                    if (success) {
                        sayInChat(String.format("On my way to %s! (%.1f blocks away)", 
                            playerName, distance));
                    } else {
                        sayInChat("I can't find a path to " + playerName + "!");
                    }
                } else {
                    sayInChat("I can't find " + playerName + "!");
                }
            } else if (params.contains(",")) {
                String[] coords = params.split(",");
                
                if (coords.length >= 2) {
                    int x = Integer.parseInt(coords[0].trim());
                    int z = coords.length == 3 ? 
                        Integer.parseInt(coords[2].trim()) : 
                        Integer.parseInt(coords[1].trim());
                    int y = coords.length == 3 ? 
                        Integer.parseInt(coords[1].trim()) : 
                        (int)findGroundLevel(x, z);
                    
                    BlockPos targetPos = new BlockPos(x, y, z);
                    double distance = this.position().distanceTo(Vec3.atCenterOf(targetPos));
                    
                    boolean success = this.getNavigation().moveTo(
                        targetPos.getX() + 0.5, 
                        targetPos.getY(), 
                        targetPos.getZ() + 0.5, 
                        1.2D
                    );
                    
                    if (success) {
                        this.currentTask = "moving_to_coords";
                        this.targetPosition = targetPos;
                        
                        sayInChat(String.format("Heading to (%d, %d, %d)! That's %.1f blocks away.", 
                            x, y, z, distance));
                    } else {
                        sayInChat("*stumbles* I can't find a path there...");
                    }
                }
            }
        } catch (Exception e) {
            AiNpcMod.LOGGER.error("[{}] Error in move_to action", this.npcName, e);
            sayInChat("*stumbles* I can't quite navigate there...");
        }
    }
    
    private double findGroundLevel(double x, double z) {
        BlockPos checkPos = new BlockPos((int)x, (int)this.getY(), (int)z);
        
        for (int dy = 0; dy >= -10; dy--) {
            BlockPos testPos = checkPos.offset(0, dy, 0);
            if (!this.level().getBlockState(testPos).isAir() && 
                this.level().getBlockState(testPos.above()).isAir()) {
                return testPos.getY() + 1;
            }
        }
        
        for (int dy = 1; dy <= 10; dy++) {
            BlockPos testPos = checkPos.offset(0, dy, 0);
            if (!this.level().getBlockState(testPos).isAir() && 
                this.level().getBlockState(testPos.above()).isAir()) {
                return testPos.getY() + 1;
            }
        }
        
        return this.getY();
    }
    
    private void handleFollowAction(String params) {
        Player targetPlayer = null;
        
        if (params != null && !params.isEmpty() && !params.equals("nearest")) {
            targetPlayer = this.level().getServer()
                .getPlayerList()
                .getPlayerByName(params.trim());
        }
        
        if (targetPlayer == null) {
            targetPlayer = this.level().getNearestPlayer(this, 50.0D);
        }
        
        if (targetPlayer != null) {
            this.followTarget = targetPlayer;
            this.currentTask = "following";
            
            followGoal = new FollowPlayerGoal(this, 1.0D, 3.0F, 10.0F);
            followGoal.setTargetPlayer(targetPlayer);
            this.goalSelector.addGoal(1, followGoal);
            
            sayInChat("I'll follow " + targetPlayer.getName().getString() + "!");
        } else {
            sayInChat("*looks around* Who should I follow?");
        }
    }
    
    private void handleAttackAction(String params) {
        if (params == null || params.isEmpty()) {
            params = "nearest";
        }
        
        String targetType = params.toLowerCase().trim();
        final LivingEntity target;
        
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
            final String finalTargetType = targetType;
            target = this.level().getNearestEntity(
                Monster.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT
                    .selector(e -> e.getType().toString().toLowerCase().contains(finalTargetType)),
                this,
                this.getX(), this.getY(), this.getZ(),
                this.getBoundingBox().inflate(20.0D)
            );
        } else {
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
            this.setTarget(target);
            this.currentTask = "attacking";
            
            attackGoal = new MeleeAttackGoal(this, 1.2D, true);
            this.goalSelector.addGoal(2, attackGoal);
            
            NearestAttackableTargetGoal<LivingEntity> targetGoal = 
                new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, 
                    e -> e == target);
            this.targetSelector.addGoal(1, targetGoal);
            
            sayInChat("*charges* Engaging " + target.getType().getDescription().getString() + "!");
        } else {
            sayInChat("*looks around* I don't see any " + targetType + " nearby...");
        }
    }
    
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean success = super.doHurtTarget(target);
        
        if (success) {
            this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        
        return success;
    }
    
    private void handleMineBlockAction(String params) {
        if (params == null || params.isEmpty()) {
            sayInChat("What should I mine?");
            return;
        }
        
        String blockType = params.toLowerCase().trim();
        BlockPos nearestBlock = findNearbyBlock(blockType, 16);
        
        if (nearestBlock != null) {
            double distance = this.position().distanceTo(Vec3.atCenterOf(nearestBlock));
            this.currentTask = "mining";
            
            boolean success = this.getNavigation().moveTo(
                nearestBlock.getX() + 0.5, 
                nearestBlock.getY(), 
                nearestBlock.getZ() + 0.5, 
                1.0D
            );
            
            if (success) {
                sayInChat(String.format("I'll mine that %s! It's %.1f blocks away.", 
                    blockType, distance));
                
                scheduleBlockBreak(nearestBlock, 100);
            } else {
                sayInChat("I can't reach that " + blockType + "!");
            }
        } else {
            sayInChat("I don't see any " + blockType + " nearby...");
        }
    }
    
    private BlockPos findNearbyBlock(String blockType, int radius) {
        BlockPos npcPos = this.blockPosition();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = npcPos.offset(x, y, z);
                    var state = this.level().getBlockState(checkPos);
                    var block = state.getBlock();
                    
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
    
    private void scheduleBlockBreak(BlockPos pos, int delayTicks) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> {
                try {
                    Thread.sleep(delayTicks * 50L);
                    
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
    }
    
    private void handleGiveItemAction(String params) {
        if (params == null || params.isEmpty()) {
            sayInChat("What should I give?");
            return;
        }
        
        Player nearestPlayer = this.level().getNearestPlayer(this, 10.0D);
        if (nearestPlayer == null) {
            sayInChat("*looks around* I don't see anyone nearby to give this to...");
            return;
        }
        
        ItemStack itemToGive = removeItemFromInventory(params);
        
        if (!itemToGive.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(
                this.level(),
                nearestPlayer.getX(),
                nearestPlayer.getY() + 0.5,
                nearestPlayer.getZ(),
                itemToGive
            );
            itemEntity.setDefaultPickUpDelay();
            itemEntity.setThrower(this.getUUID());
            this.level().addFreshEntity(itemEntity);
            
            sayInChat(String.format("*hands over* Here's %s for you, %s!", 
                itemToGive.getHoverName().getString(),
                nearestPlayer.getName().getString()));
        } else {
            sayInChat(String.format("*checks pockets* I don't have any %s right now...", params));
        }
    }
    
    private void handlePickupItemAction(String params) {
        java.util.List<ItemEntity> nearbyItems = 
            this.level().getEntitiesOfClass(
                ItemEntity.class,
                this.getBoundingBox().inflate(10.0D)
            );
        
        if (nearbyItems.isEmpty()) {
            sayInChat("*looks around* I don't see any items on the ground...");
            return;
        }
        
        ItemEntity closestItem = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (ItemEntity item : nearbyItems) {
            double distance = this.distanceToSqr(item);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestItem = item;
            }
        }
        
        if (closestItem != null) {
            String itemName = closestItem.getItem().getHoverName().getString();
            int itemCount = closestItem.getItem().getCount();
            double distance = Math.sqrt(closestDistance);
            
            this.currentTask = "picking_up_item";
            
            if (distance < 2.0D) {
                ItemStack pickupStack = closestItem.getItem().copy();
                if (addItemToInventory(pickupStack)) {
                    closestItem.discard();
                    sayInChat(String.format("*picks up* Got %d %s!", itemCount, itemName));
                    spawnParticles("happy_villager");
                } else {
                    sayInChat("*tries to pick up* My pockets are full!");
                }
            } else {
                boolean success = this.getNavigation().moveTo(closestItem, 1.2D);
                
                if (success) {
                    sayInChat(String.format("I see %d %s! Going to get it...", itemCount, itemName));
                    this.targetPickupItem = closestItem;
                } else {
                    sayInChat("I can't reach that " + itemName + "...");
                }
            }
        }
    }
    
    private void handleIdleAction() {
        clearDynamicGoals();
        this.currentTask = "idle";
    }
    
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
            AiNpcMod.LOGGER.error("[{}] Failed to spawn particles", this.npcName, e);
        }
    }
    
    // ==================== STATE MANAGEMENT ====================
    
    public void setEmotion(String emotion) {
        if (emotion != null && !emotion.equals(this.currentEmotion)) {
            this.currentEmotion = emotion;
            spawnEmotionParticles(emotion);
        }
    }

    public String getEmotion() {
        return this.currentEmotion;
    }

    public void setCurrentObjective(String objective) {
        if (objective != null) {
            this.currentObjective = objective;
        }
    }

    public String getCurrentObjective() {
        return this.currentObjective;
    }

    public void updateFromState(StatePayload state) {
        if (state == null) return;

        if (state.emotion != null) {
            setEmotion(state.emotion);
        }

        if (state.currentObjective != null) {
            setCurrentObjective(state.currentObjective);
        }

        if (state.x != null && state.z != null) {
            AiNpcMod.LOGGER.debug("[{}] Backend position: ({}, {})",
                    this.npcName, state.x, state.z);
        }
    }

    private void spawnEmotionParticles(String emotion) {
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel)) {
            return;
        }

        String particleType = switch (emotion.toLowerCase()) {
            case "happy" -> "heart";
            case "angry" -> "angry_villager";
            case "excited" -> "happy_villager";
            case "sad" -> "rain";
            case "confused" -> "smoke";
            case "thinking" -> "enchant";
            case "helpful", "generous" -> "happy_villager";
            default -> null;
        };

        if (particleType != null) {
            spawnParticles(particleType);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            // Periodic state sync with Python backend
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastStateSyncTime > STATE_SYNC_INTERVAL) {
                lastStateSyncTime = currentTime;
                ChatHandler.syncNPCState(this);
            }
            
            // AUTONOMOUS BEHAVIOR - check for nearby players
            performAutonomousBehavior();
            
            // POTION SYSTEM - check if NPC needs to drink potions
            checkAndDrinkPotions();

            // Task completion checks
            if ("moving_to_coords".equals(currentTask) && targetPosition != null) {
                if (this.getNavigation().isDone()) {
                    double distanceToTarget = this.position().distanceTo(Vec3.atCenterOf(targetPosition));
                    
                    if (distanceToTarget < 3.0D) {
                        sayInChat(String.format("*arrives* I'm here! (%.1f blocks from target)", distanceToTarget));
                    } else {
                        sayInChat(String.format("*stops* I got as close as I could! (%.1f blocks away)", distanceToTarget));
                    }
                    
                    currentTask = "idle";
                    targetPosition = null;
                }
            }
            
            if ("moving_to_player".equals(currentTask)) {
                if (this.getNavigation().isDone()) {
                    sayInChat("*arrives* I've reached you!");
                    currentTask = "idle";
                }
            }
            
            if ("following".equals(currentTask) && followTarget != null) {
                double distance = this.distanceTo(followTarget);
                if (distance > 50.0D) {
                    sayInChat("*stops* You're too far away, I'll wait here.");
                    clearDynamicGoals();
                    currentTask = "idle";
                }
            }
            
            if ("attacking".equals(currentTask)) {
                LivingEntity target = this.getTarget();
                if (target == null || !target.isAlive()) {
                    sayInChat("*stops* The threat is neutralized!");
                    clearDynamicGoals();
                    currentTask = "idle";
                }
            }
            
            if ("picking_up_item".equals(currentTask) && targetPickupItem != null) {
                if (!targetPickupItem.isAlive()) {
                    currentTask = "idle";
                    targetPickupItem = null;
                } else {
                    double distance = this.distanceTo(targetPickupItem);
                    
                    if (distance < 2.0D) {
                        String itemName = targetPickupItem.getItem().getHoverName().getString();
                        int itemCount = targetPickupItem.getItem().getCount();
                        
                        ItemStack pickupStack = targetPickupItem.getItem().copy();
                        if (addItemToInventory(pickupStack)) {
                            targetPickupItem.discard();
                            sayInChat(String.format("*picks up* Got %d %s!", itemCount, itemName));
                            spawnParticles("happy_villager");
                        } else {
                            sayInChat("*tries to pick up* My pockets are full!");
                        }
                        
                        currentTask = "idle";
                        targetPickupItem = null;
                        this.getNavigation().stop();
                    } else if (this.getNavigation().isDone()) {
                        sayInChat("*reaches* Almost got it...");
                        this.getNavigation().moveTo(targetPickupItem, 1.2D);
                    }
                }
            }
        }
    }
}