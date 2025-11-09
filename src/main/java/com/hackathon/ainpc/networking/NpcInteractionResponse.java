package com.hackathon.ainpc.networking;

/**
 * Response from Python AI server
 */
public class NpcInteractionResponse {
    public String reply; // What NPC says
    public String action; // Action to perform: "say", "move_to", "attack_target", "follow", "emote"
    public String action_params; // Parameters for the action (e.g., coordinates, target type)
    public String emotion_update; // Emotion state change
    public String memory_patch; // Memory to save

    @Override
    public String toString() {
        return String.format("Response{reply='%s', action='%s', params='%s'}",
                reply, action, action_params);
    } // ✅ FIXED: Added closing brace for method
} // ✅ FIXED: Added closing brace for class
