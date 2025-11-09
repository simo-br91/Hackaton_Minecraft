package com.hackathon.ainpc.networking;

/**
 * Request payload sent to Python AI server
 */
public class NpcInteractionRequest {
    public String player;
    public String npc_id;
    public String message;
    public String context; // Optional: game state context

    public NpcInteractionRequest(String player, String npcId, String message) {
        this.player = player;
        this.npc_id = npcId;
        this.message = message;
        this.context = ""; // Can add game state info later
    }

    @Override
    public String toString() {
        return String.format("Request{player='%s', npc='%s', message='%s'}", 
            player, npc_id, message);
    }
}