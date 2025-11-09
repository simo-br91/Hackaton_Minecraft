package com.hackathon.ainpc.networking;

import com.google.gson.annotations.SerializedName;

/**
 * Request payload sent to Python AI server
 */
public class NpcInteractionRequest {
    @SerializedName("player")
    public String player;

    @SerializedName("npc_id")
    public String npcId;

    @SerializedName("message")
    public String message;

    public NpcInteractionRequest(String player, String npcId, String message) {
        this.player = player;
        this.npcId = npcId;
        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("Request{player='%s', npc='%s', message='%s'}",
                player, npcId, message);
    }
}