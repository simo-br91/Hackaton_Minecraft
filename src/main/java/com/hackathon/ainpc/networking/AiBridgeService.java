package com.hackathon.ainpc.networking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hackathon.ainpc.AiNpcMod;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Service for communicating with Python AI backend
 * Uses Java 11+ built-in HttpClient (NO external dependencies!)
 */
public class AiBridgeService {
    // Python Flask server endpoint
    private static final String AI_API_URL = "http://localhost:5000/api/npc_interact";

    // HTTP client with timeouts
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // JSON serializer (Gson is provided by Minecraft, so this is safe)
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AI_API_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        // Execute async (this runs in a separate thread)
        CompletableFuture<HttpResponse<String>> futureResponse =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        // Handle response when it arrives
        futureResponse.whenComplete((response, error) -> {
            if (error != null) {
                // Connection failed
                AiNpcMod.LOGGER.error("[AI Bridge] Failed to reach Python server: {}", error.getMessage());
                callback.onFailure("AI system is offline. Is Python server running?");
                return;
            }

            try {
                String responseBody = response.body();
                AiNpcMod.LOGGER.debug("[AI Bridge] Received JSON: {}", responseBody);

                if (response.statusCode() == 200) {
                    // Parse JSON response
                    NpcInteractionResponse npcResponse = GSON.fromJson(responseBody, NpcInteractionResponse.class);
                    if (npcResponse == null) {
                        AiNpcMod.LOGGER.error("[AI Bridge] Response was null");
                        callback.onFailure("AI returned empty response.");
                        return;
                    }

                    AiNpcMod.LOGGER.info("[AI Bridge] Success: {}", npcResponse);
                    callback.onSuccess(npcResponse);
                } else {
                    // Server returned error
                    AiNpcMod.LOGGER.error("[AI Bridge] Server error code: {}", response.statusCode());
                    callback.onFailure("Python server error: " + response.statusCode());
                }
            } catch (Exception e) {
                AiNpcMod.LOGGER.error("[AI Bridge] JSON parsing failed: {}", e.getMessage());
                callback.onFailure("Could not parse AI response.");
            }
        });
    }

    /**
     * Callback interface for handling AI responses
     */
    public interface Callback {
        void onSuccess(NpcInteractionResponse response);
        void onFailure(String error);
    } // ✅ FIXED: Added closing brace for interface
} // ✅ FIXED: Added closing brace for class
