package com.hackathon.ainpc.networking;

import com.google.gson.annotations.SerializedName;

/**
 * Response from state polling endpoint
 * Phase 5: New class for /api/npc_state
 */
public class NPCStateResponse {
    @SerializedName("npc_id")
    public String npcId;

    @SerializedName("emotion")
    public String emotion;

    @SerializedName("current_objective")
    public String currentObjective;

    @SerializedName("recent_memory_summary")
    public String recentMemorySummary;

    @SerializedName("x")
    public Integer x;

    @SerializedName("z")
    public Integer z;

    @SerializedName("last_updated")
    public String lastUpdated;

    @SerializedName("memory_count")
    public Integer memoryCount;

    @Override
    public String toString() {
        return String.format("NPCState{npc='%s', emotion='%s', objective='%s', memories=%d}",
                npcId, emotion, currentObjective, memoryCount);
    }
}