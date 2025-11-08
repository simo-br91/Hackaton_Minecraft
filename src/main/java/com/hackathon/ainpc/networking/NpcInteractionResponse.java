package com.hackathon.ainpc.networking;

import com.google.gson.annotations.SerializedName;

/**
 * Complete response from Python AI backend
 */
public class NpcInteractionResponse {
    @SerializedName("action")
    public ActionPayload action;

    @SerializedName("new_state")
    public StatePayload newState;
    
    @Override
    public String toString() {
        return "NPCResponse{" + 
               "action=" + (action != null ? action.toString() : "null") + 
               ", state=" + (newState != null ? newState.toString() : "null") + 
               "}";
    }
}