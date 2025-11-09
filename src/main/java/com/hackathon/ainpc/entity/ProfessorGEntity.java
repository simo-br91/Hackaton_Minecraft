package com.hackathon.ainpc.entity;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ai.FollowPlayerGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class ProfessorGEntity extends PathfinderMob {
    public static final String NPC_NAME = "Professor G";
    
    // Inventory system - 27 slots like a chest
    private final SimpleContainer inventory = new SimpleContainer(27);
    
    // Track current AI task
    private String currentTask = "idle";
    private LivingEntity followTarget = null;
    private FollowPlayerGoal followGoal = null;
    private MeleeAttackGoal attackGoal = null;
    private BlockPos targetPosition = null;
    private ItemEntity targetPickupItem = null;

    public ProfessorGEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal(NPC_NAME));
        this.setCustomNameVisible(true);
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
    
    // ==================== INVENTORY METHODS ====================
    
    /**
     * Get the NPC's inventory
     */
    public SimpleContainer getInventory() {
        return inventory;
    }
    
    /**
     * Add an item to inventory
     */
    public boolean addItemToInventory(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        // Try to add to existing stacks first
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (slotStack.isEmpty()) {
                inventory.setItem(i, stack.copy());
                AiNpcMod.LOGGER.info("[Professor G] Added {} to inventory slot {}", 
                    stack.getHoverName().getString(), i);
                return true;
            } else if (ItemStack.isSameItemSameTags(slotStack, stack) && 
                       slotStack.getCount() < slotStack.getMaxStackSize()) {
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                int toAdd = Math.min(space, stack.getCount());
                slotStack.grow(toAdd);
                stack.shrink(toAdd);
                
                if (stack.isEmpty()) {
                    AiNpcMod.LOGGER.info("[Professor G] Stacked {} in slot {}", 
                        slotStack.getHoverName().getString(), i);
                    return true;
                }
            }
        }
        
        // Inventory full
        AiNpcMod.LOGGER.warn("[Professor G] Inventory is full!");
        return false;
    }
    
    /**
     * Remove an item from inventory
     */
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
                    AiNpcMod.LOGGER.info("[Professor G] Removed {} from inventory slot {}", 
                        result.getHoverName().getString(), i);
                    return result;
                }
            }
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * Check if inventory has a specific item
     */
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
    
    /**
     * Get inventory contents as string
     */
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
    
    /**
     * Drop an item at NPC's position
     */
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
            AiNpcMod.LOGGER.info("[Professor G] Dropped {} at position", 
                stack.getHoverName().getString());
        }
    }
    
    /**
     * Save inventory to NBT
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        
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
    
    /**
     * Load inventory from NBT
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        
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
    
    /**
     * Drop all inventory items on death
     */
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
    }
    
    // ==================== AI ACTION METHODS ====================
    
    public void executeAIAction(String action, String actionParams) {
        if (this.level().isClientSide) {
            return;
        }
        
        AiNpcMod.LOGGER.info("[Professor G] Executing action: {} with params: {}", action, actionParams);
        
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
                
            case "idle":
                handleIdleAction();
                break;
                
            default:
                AiNpcMod.LOGGER.warn("[Professor G] Unknown action: {}", action);
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
                Component.literal("§e[Professor G]§r " + message), 
                false
            );
            AiNpcMod.LOGGER.info("[Professor G] Said: {}", message);
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
                        AiNpcMod.LOGGER.info("[Professor G] Moving to player {} at {}", 
                            playerName, targetPlayer.blockPosition());
                    } else {
                        sayInChat("I can't find a path to " + playerName + "!");
                        AiNpcMod.LOGGER.warn("[Professor G] Pathfinding failed - no valid path");
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
                        
                        AiNpcMod.LOGGER.info("[Professor G] Pathfinding to {} - Current pos: {}", 
                            targetPos, this.blockPosition());
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
            AiNpcMod.LOGGER.info("[Professor G] Following player: {}", 
                targetPlayer.getName().getString());
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
        
        AiNpcMod.LOGGER.info("[Professor G] Searching for attack target: {}", targetType);
        
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
            AiNpcMod.LOGGER.info("[Professor G] Attacking {} at distance {} - Target set: {}", 
                target.getType().getDescription().getString(), 
                this.distanceTo(target),
                this.getTarget() != null);
        } else {
            sayInChat("*looks around* I don't see any " + targetType + " nearby...");
            AiNpcMod.LOGGER.warn("[Professor G] No target found for: {}", targetType);
        }
    }
    
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean success = super.doHurtTarget(target);
        
        if (success) {
            this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            AiNpcMod.LOGGER.info("[Professor G] Successfully attacked target!");
        }
        
        return success;
    }
    
    private void handleMineBlockAction(String params) {
        if (params == null || params.isEmpty()) {
            sayInChat("What should I mine?");
            return;
        }
        
        String blockType = params.toLowerCase().trim();
        AiNpcMod.LOGGER.info("[Professor G] Looking for block to mine: {}", blockType);
        
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
            AiNpcMod.LOGGER.warn("[Professor G] No {} found nearby", blockType);
        }
    }
    
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
    
    private void scheduleBlockBreak(BlockPos pos, int delayTicks) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> {
                try {
                    Thread.sleep(delayTicks * 50);
                    
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
        
        AiNpcMod.LOGGER.info("[Professor G] Emoted: {}", emote);
    }
    
    private void handleGiveItemAction(String params) {
        if (params == null || params.isEmpty()) {
            sayInChat("What should I give?");
            return;
        }
        
        // Find nearest player
        Player nearestPlayer = this.level().getNearestPlayer(this, 10.0D);
        if (nearestPlayer == null) {
            sayInChat("*looks around* I don't see anyone nearby to give this to...");
            return;
        }
        
        // Try to find item in inventory
        ItemStack itemToGive = removeItemFromInventory(params);
        
        if (!itemToGive.isEmpty()) {
            // Drop the item near the player
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
            
            AiNpcMod.LOGGER.info("[Professor G] Gave {} to {}", 
                itemToGive.getHoverName().getString(),
                nearestPlayer.getName().getString());
        } else {
            sayInChat(String.format("*checks pockets* I don't have any %s right now...", params));
            AiNpcMod.LOGGER.info("[Professor G] Tried to give {} but don't have it", params);
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
            AiNpcMod.LOGGER.info("[Professor G] No items found nearby");
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
                // Pick up the item into inventory
                ItemStack pickupStack = closestItem.getItem().copy();
                if (addItemToInventory(pickupStack)) {
                    closestItem.discard();
                    sayInChat(String.format("*picks up* Got %d %s!", itemCount, itemName));
                    spawnParticles("happy_villager");
                    AiNpcMod.LOGGER.info("[Professor G] Picked up {} x{}", itemName, itemCount);
                } else {
                    sayInChat("*tries to pick up* My pockets are full!");
                }
            } else {
                boolean success = this.getNavigation().moveTo(closestItem, 1.2D);
                
                if (success) {
                    sayInChat(String.format("I see %d %s! Going to get it...", itemCount, itemName));
                    AiNpcMod.LOGGER.info("[Professor G] Moving to pick up {} at distance {}", 
                        itemName, distance);
                    
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
        AiNpcMod.LOGGER.info("[Professor G] Now idling");
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
            AiNpcMod.LOGGER.error("[Professor G] Failed to spawn particles", e);
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (!this.level().isClientSide) {
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
                        
                        // Pick up into inventory
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
    
    public String getCurrentTask() {
        return currentTask;
    }
}