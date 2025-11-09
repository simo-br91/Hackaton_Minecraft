package com.hackathon.ainpc.networking;

import com.google.gson.annotations.SerializedName;

/**
 * Action data from AI response
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
        return String.format("Action{type='%s', target='%s', coords=(%s, %s), chat='%s'}",
                actionType, targetName, x, z, chatResponse);
    }
}