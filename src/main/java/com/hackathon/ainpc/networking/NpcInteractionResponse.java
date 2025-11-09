package com.hackathon.ainpc.networking;

import com.google.gson.annotations.SerializedName;

/**
 * Complete response from Python AI server
 * Phase 5: Includes action + new state
 */
public class NpcInteractionResponse {
    @SerializedName("action")
    public ActionPayload action;

    @SerializedName("new_state")
    public StatePayload newState;

    // Helper methods for backward compatibility
    public String getReply() {
        return action != null ? action.chatResponse : null;
    }

    public String getAction() {
        return action != null ? action.actionType : null;
    }

    public String getActionParams() {
        if (action == null) return null;

        if (action.targetName != null) {
            return action.targetName;
        } else if (action.x != null && action.z != null) {
            return action.x + "," + action.z;
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("Response{action=%s, state=%s}",
                action, newState);
    }
}