package com.hackathon.ainpc.networking;

/**
 * Response from Python AI server
 * Updated to match the actual Python response structure
 */
public class NpcInteractionResponse {
    // Nested action object
    public ActionData action;
    
    // Nested state object
    public StateData new_state;
    
    /**
     * Inner class for action data
     */
    public static class ActionData {
        public String action_type;
        public String chat_response;
        public String target_name;
        public Integer x;
        public Integer z;
    }
    
    /**
     * Inner class for state data
     */
    public static class StateData {
        public String emotion;
        public String current_objective;
        public String recent_memory_summary;
        public Integer x;
        public Integer z;
    }
    
    // Helper methods to maintain compatibility with existing code
    public String getReply() {
        return action != null ? action.chat_response : null;
    }
    
    public String getAction() {
        return action != null ? action.action_type : null;
    }
    
    public String getActionParams() {
        if (action == null) return null;
        
        // Build params string based on action type
        if (action.target_name != null) {
            return action.target_name;
        } else if (action.x != null && action.z != null) {
            return action.x + "," + action.z;
        }
        return null;
    }

    @Override
    public String toString() {
        String reply = action != null ? action.chat_response : "null";
        String actionType = action != null ? action.action_type : "null";
        String params = getActionParams();
        return String.format("Response{reply='%s', action='%s', params='%s'}", 
                             reply, actionType, params);
    }
}