package com.hackathon.ainpc.command;

import com.hackathon.ainpc.AiNpcMod;
import com.hackathon.ainpc.entity.ProfessorGEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

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
                "§f/testnpc say <message> §7- Make NPC say something\n" +
                "§f/testnpc move_to <x,y,z> §7- Move to coordinates\n" +
                "§f/testnpc move_to player:<name> §7- Move to player\n" +
                "§f/testnpc follow [player] §7- Follow player\n" +
                "§f/testnpc attack_target <type> §7- Attack entity (pig, nearest)\n" +
                "§f/testnpc emote <emotion> §7- Show emotion\n" +
                "§f/testnpc give_item <item> §7- Give item to nearest player\n" +
                "§f/testnpc pickup_item §7- Pick up nearby items\n" +
                "§f/testnpc mine_block <block> §7- Mine nearby block\n" +
                "§f/testnpc status §7- Show NPC status"
            ), false);
        return 1;
    }
    
    private static int executeAction(CommandContext<CommandSourceStack> context, String params) {
        String action = StringArgumentType.getString(context, "action");
        CommandSourceStack source = context.getSource();
        
        if ("status".equals(action)) {
            return showStatus(context);
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
            
            String status = String.format(
                "§e=== Professor G Status ===\n" +
                "§fPosition: §7%.1f, %.1f, %.1f\n" +
                "§fHealth: §7%.1f/%.1f\n" +
                "§fCurrent Task: §7%s\n" +
                "§fTarget: §7%s\n" +
                "§fPath Finding: §7%s",
                npc.getX(), npc.getY(), npc.getZ(),
                npc.getHealth(), npc.getMaxHealth(),
                npc.getCurrentTask(),
                npc.getTarget() != null ? npc.getTarget().getName().getString() : "None",
                npc.getNavigation().isInProgress() ? "Active" : "Idle"
            );
            
            source.sendSuccess(() -> Component.literal(status), false);
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