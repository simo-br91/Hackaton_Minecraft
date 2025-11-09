package com.hackathon.ainpc.command;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ProfessorGEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Debug command for testing NPC actions
 * Usage: /testnpc <action> [params]
 */
public class TestNPCCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("testnpc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("action", StringArgumentType.word())
                    .executes(context -> executeAction(context, ""))
                    .then(Commands.argument("params", StringArgumentType.greedyString())
                        .executes(context -> executeAction(
                            context, 
                            StringArgumentType.getString(context, "params")
                        ))
                    )
                )
                .executes(context -> showHelp(context))
        );
    }
    
    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> 
            Component.literal("§e=== Professor G Test Commands ===\n" +
                "§6Basic Actions:§r\n" +
                "§f/testnpc say <message> §7- Make NPC say something\n" +
                "§f/testnpc move_to <x,y,z> §7- Move to coordinates\n" +
                "§f/testnpc move_to player:<n> §7- Move to player\n" +
                "§f/testnpc follow [player] §7- Follow player\n" +
                "§f/testnpc attack_target <type> §7- Attack entity (pig, nearest)\n" +
                "§f/testnpc emote <emotion> §7- Show emotion\n" +
                "§f/testnpc mine_block <block> §7- Mine nearby block\n" +
                "§f/testnpc pickup_item §7- Pick up nearby items\n" +
                "§f/testnpc give_item <item> §7- Give item to nearest player\n\n" +
                "§6Inventory Commands:§r\n" +
                "§f/testnpc inventory §7- Show inventory contents\n" +
                "§f/testnpc give <item> [amount] §7- Give item to NPC (e.g., diamond 5)\n" +
                "§f/testnpc drop <item> §7- Make NPC drop an item\n" +
                "§f/testnpc clear_inventory §7- Clear NPC's inventory\n\n" +
                "§6Status:§r\n" +
                "§f/testnpc status §7- Show NPC status"
            ), false);
        return 1;
    }
    
    private static int executeAction(CommandContext<CommandSourceStack> context, String params) {
        String action = StringArgumentType.getString(context, "action");
        CommandSourceStack source = context.getSource();
        
        // Handle special commands
        if ("status".equals(action)) {
            return showStatus(context);
        } else if ("inventory".equals(action)) {
            return showInventory(context);
        } else if ("give".equals(action)) {
            return giveItemToNPC(context, params);
        } else if ("drop".equals(action)) {
            return makeNPCDropItem(context, params);
        } else if ("clear_inventory".equals(action)) {
            return clearNPCInventory(context);
        }
        
        try {
            ServerLevel level = source.getLevel();
            ProfessorGEntity npc = findNearestProfessorG(level, source.getPosition());
            
            if (npc == null) {
                source.sendFailure(Component.literal("§cNo Professor G found nearby!"));
                return 0;
            }
            
            source.sendSuccess(() -> 
                Component.literal("§aExecuting action: §f" + action + 
                    (params.isEmpty() ? "" : " §7(" + params + ")")), 
                false);
            
            // Execute the action
            npc.executeAIAction(action, params);
            
            AiNpcMod.LOGGER.info("[TestCommand] Executed {} with params: {}", action, params);
            return 1;
            
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError: " + e.getMessage()));
            AiNpcMod.LOGGER.error("[TestCommand] Error executing action", e);
            return 0;
        }
    }
    
    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            ServerLevel level = source.getLevel();
            ProfessorGEntity npc = findNearestProfessorG(level, source.getPosition());
            
            if (npc == null) {
                source.sendFailure(Component.literal("§cNo Professor G found nearby!"));
                return 0;
            }
            
            final String inventoryContents = npc.getInventoryContents();
            final String targetName = npc.getTarget() != null ? npc.getTarget().getName().getString() : "None";
            final String pathStatus = npc.getNavigation().isInProgress() ? "Active" : "Idle";
            final double x = npc.getX();
            final double y = npc.getY();
            final double z = npc.getZ();
            final float health = npc.getHealth();
            final float maxHealth = npc.getMaxHealth();
            final String task = npc.getCurrentTask();
            
            String status = String.format(
                "§e=== Professor G Status ===\n" +
                "§fPosition: §7%.1f, %.1f, %.1f\n" +
                "§fHealth: §7%.1f/%.1f\n" +
                "§fCurrent Task: §7%s\n" +
                "§fTarget: §7%s\n" +
                "§fPath Finding: §7%s\n" +
                "§fInventory: §7%s",
                x, y, z,
                health, maxHealth,
                task,
                targetName,
                pathStatus,
                inventoryContents
            );
            
            source.sendSuccess(() -> Component.literal(status), false);
            return 1;
            
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int showInventory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            ServerLevel level = source.getLevel();
            ProfessorGEntity npc = findNearestProfessorG(level, source.getPosition());
            
            if (npc == null) {
                source.sendFailure(Component.literal("§cNo Professor G found nearby!"));
                return 0;
            }
            
            final String contents = npc.getInventoryContents();
            source.sendSuccess(() -> 
                Component.literal("§e[Professor G's Inventory]§r\n" + 
                    (contents.equals("Empty") ? "§7Empty" : "§f" + contents)), 
                false);
            return 1;
            
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int giveItemToNPC(CommandContext<CommandSourceStack> context, String params) {
        CommandSourceStack source = context.getSource();
        
        if (params == null || params.isEmpty()) {
            source.sendFailure(Component.literal("§cUsage: /testnpc give <item> [amount]"));
            return 0;
        }
        
        try {
            ServerLevel level = source.getLevel();
            ProfessorGEntity npc = findNearestProfessorG(level, source.getPosition());
            
            if (npc == null) {
                source.sendFailure(Component.literal("§cNo Professor G found nearby!"));
                return 0;
            }
            
            // Parse item and amount
            String[] parts = params.split(" ");
            String itemName = parts[0];
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            
            // Try to find the item
            ItemStack foundItemStack = null;
            
            // First try as a direct registry name (e.g., "minecraft:diamond")
            if (itemName.contains(":")) {
                ResourceLocation itemId = new ResourceLocation(itemName);
                if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                    foundItemStack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), amount);
                }
            } else {
                // Try with minecraft namespace
                ResourceLocation itemId = new ResourceLocation("minecraft", itemName.toLowerCase());
                if (BuiltInRegistries.ITEM.containsKey(itemId)) {
                    foundItemStack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), amount);
                } else {
                    // Try to find by partial match
                    for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                        if (entry.getKey().location().getPath().contains(itemName.toLowerCase())) {
                            foundItemStack = new ItemStack(entry.getValue(), amount);
                            break;
                        }
                    }
                }
            }
            
            final ItemStack itemStack = foundItemStack;
            
            if (itemStack != null && !itemStack.isEmpty()) {
                boolean success = npc.addItemToInventory(itemStack);
                
                if (success) {
                    final int finalAmount = amount;
                    final String itemDisplayName = itemStack.getHoverName().getString();
                    
                    source.sendSuccess(() -> 
                        Component.literal(String.format("§aGave %dx %s to Professor G!", 
                            finalAmount, itemDisplayName)), 
                        false);
                    npc.sayInChat(String.format("*receives item* Thank you for the %s!", 
                        itemDisplayName));
                    return 1;
                } else {
                    source.sendFailure(Component.literal("§cProfessor G's inventory is full!"));
                    npc.sayInChat("My pockets are full!");
                    return 0;
                }
            } else {
                final String finalItemName = itemName;
                source.sendFailure(Component.literal("§cItem not found: " + finalItemName));
                source.sendSuccess(() -> 
                    Component.literal("§7Try: diamond, iron_sword, bread, etc."), 
                    false);
                return 0;
            }
            
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("§cInvalid amount. Use: /testnpc give <item> [amount]"));
            return 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError: " + e.getMessage()));
            AiNpcMod.LOGGER.error("[TestCommand] Error giving item", e);
            return 0;
        }
    }
    
    private static int makeNPCDropItem(CommandContext<CommandSourceStack> context, String params) {
        CommandSourceStack source = context.getSource();
        
        if (params == null || params.isEmpty()) {
            source.sendFailure(Component.literal("§cUsage: /testnpc drop <item>"));
            return 0;
        }
        
        try {
            ServerLevel level = source.getLevel();
            ProfessorGEntity npc = findNearestProfessorG(level, source.getPosition());
            
            if (npc == null) {
                source.sendFailure(Component.literal("§cNo Professor G found nearby!"));
                return 0;
            }
            
            final ItemStack itemStack = npc.removeItemFromInventory(params);
            
            if (!itemStack.isEmpty()) {
                npc.dropItem(itemStack);
                final String itemDisplayName = itemStack.getHoverName().getString();
                
                source.sendSuccess(() -> 
                    Component.literal(String.format("§aProfessor G dropped %s!", 
                        itemDisplayName)), 
                    false);
                npc.sayInChat("*drops item* Here you go!");
                return 1;
            } else {
                final String finalParams = params;
                source.sendFailure(Component.literal("§cProfessor G doesn't have: " + finalParams));
                npc.sayInChat("I don't have any " + params + "...");
                return 0;
            }
            
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError: " + e.getMessage()));
            AiNpcMod.LOGGER.error("[TestCommand] Error dropping item", e);
            return 0;
        }
    }
    
    private static int clearNPCInventory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            ServerLevel level = source.getLevel();
            ProfessorGEntity npc = findNearestProfessorG(level, source.getPosition());
            
            if (npc == null) {
                source.sendFailure(Component.literal("§cNo Professor G found nearby!"));
                return 0;
            }
            
            // Clear all slots
            for (int i = 0; i < npc.getInventory().getContainerSize(); i++) {
                npc.getInventory().setItem(i, ItemStack.EMPTY);
            }
            
            source.sendSuccess(() -> 
                Component.literal("§aCleared Professor G's inventory!"), 
                false);
            npc.sayInChat("*empties pockets* All clear!");
            return 1;
            
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }
    
    private static ProfessorGEntity findNearestProfessorG(ServerLevel level, net.minecraft.world.phys.Vec3 pos) {
        double nearestDistance = Double.MAX_VALUE;
        ProfessorGEntity nearest = null;
        
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ProfessorGEntity npc) {
                double distance = entity.position().distanceToSqr(pos);
                if (distance < nearestDistance) {
                    nearest = npc;
                    nearestDistance = distance;
                }
            }
        }
        
        return nearest;
    }
}