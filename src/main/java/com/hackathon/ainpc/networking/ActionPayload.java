package com.hackathon.ainpc.networking;

import com.google.gson.annotations.SerializedName;

/**
 * Action part of AI response
 */
public class ActionPayload {
    @SerializedName("action_type")
    public String actionType;

    @SerializedName("target_name")
    public String targetName;

    @SerializedName("x")
    public Integer x;

    @SerializedName("z")
    public Integer z;

    @SerializedName("chat_response")
    public String chatResponse;

    @Override
    public String toString() {
        return "Action{type=" + actionType +
                ", target=" + targetName +
                ", pos=(" + x + "," + z + ")" +
                ", chat=" + chatResponse + "}";
    } // ✅ FIXED: Added closing brace for method
} // ✅ FIXED: Added closing brace for class
