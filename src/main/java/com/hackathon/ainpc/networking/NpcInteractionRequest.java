package com.hackathon.ainpc.networking;

import com.google.gson.annotations.SerializedName;

/**
 * Request payload sent to Python AI backend
 */
public class NpcInteractionRequest {
    @SerializedName("player")
    public String player;
    
    @SerializedName("npc_id")
    public String npcId;
    
    @SerializedName("message")
    public String message;
    
    @SerializedName("context")
    public String context;

    public NpcInteractionRequest(String player, String npcId, String message) {
        this.player = player;
        this.npcId = npcId;
        this.message = message;
        this.context = null;
    }
    
    public NpcInteractionRequest(String player, String npcId, String message, String context) {
        this.player = player;
        this.npcId = npcId;
        this.message = message;
        this.context = context;
    }
}