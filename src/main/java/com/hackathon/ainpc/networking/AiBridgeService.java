package com.hackathon.ainpc.networking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hackathon.ainpc.AiNpcMod;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Service for communicating with Python AI backend
 * Handles HTTP requests asynchronously with proper error handling
 */
public class AiBridgeService {
    // Python Flask server endpoint
    private static final String AI_API_URL = "http://localhost:5000/api/npc_interact";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // HTTP client with timeouts
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    // JSON serializer
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Send a chat message to the AI brain and get a response
     * 
     * @param player Player name who sent the message
     * @param npcId NPC identifier (e.g., "Professor G")
     * @param message The chat message
     * @param callback Callback for handling response or errors
     */
    public static void sendToAI(String player, String npcId, String message, Callback callback) {
        // Create request payload
        NpcInteractionRequest requestData = new NpcInteractionRequest(player, npcId, message);
        String jsonPayload = GSON.toJson(requestData);

        AiNpcMod.LOGGER.info("[AI Bridge] Sending to Python: player={}, npc={}, message={}", 
            player, npcId, message);
        AiNpcMod.LOGGER.debug("[AI Bridge] JSON payload: {}", jsonPayload);

        // Build HTTP request
        Request request = new Request.Builder()
                .url(AI_API_URL)
                .post(RequestBody.create(jsonPayload, JSON))
                .build();

        // Execute async
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                AiNpcMod.LOGGER.error("[AI Bridge] Failed to reach Python server: {}", e.getMessage());
                callback.onFailure("AI system is offline or unreachable. Check if Python server is running.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        AiNpcMod.LOGGER.error("[AI Bridge] Received empty response from server");
                        callback.onFailure("AI returned empty response.");
                        return;
                    }

                    String responseJson = responseBody.string();
                    AiNpcMod.LOGGER.debug("[AI Bridge] Received JSON: {}", responseJson);

                    if (response.isSuccessful()) {
                        try {
                            NpcInteractionResponse npcResponse = GSON.fromJson(responseJson, NpcInteractionResponse.class);
                            
                            // Validate response
                            if (npcResponse == null || npcResponse.action == null) {
                                AiNpcMod.LOGGER.error("[AI Bridge] Invalid response structure");
                                callback.onFailure("AI response was malformed.");
                                return;
                            }
                            
                            AiNpcMod.LOGGER.info("[AI Bridge] Success: {}", npcResponse);
                            callback.onSuccess(npcResponse);
                            
                        } catch (Exception e) {
                            AiNpcMod.LOGGER.error("[AI Bridge] JSON parsing failed: {}", e.getMessage());
                            callback.onFailure("Could not parse AI response: " + e.getMessage());
                        }
                    } else {
                        AiNpcMod.LOGGER.error("[AI Bridge] Server returned error code: {}", response.code());
                        callback.onFailure("Python server error: " + response.code());
                    }
                }
            }
        });
    }

    /**
     * Callback interface for handling AI responses
     */
    public interface Callback {
        void onSuccess(NpcInteractionResponse response);
        void onFailure(String error);
    }
}