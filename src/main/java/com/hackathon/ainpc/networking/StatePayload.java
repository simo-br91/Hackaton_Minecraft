package com.hackathon.ainpc.networking;

import com.google.gson.annotations.SerializedName;

/**
 * State data from AI response (emotion, objectives, memory)
 */
public class StatePayload {
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

    @Override
    public String toString() {
        return String.format("State{emotion='%s', objective='%s', memory='%s'}",
                emotion, currentObjective, recentMemorySummary);
    }
}