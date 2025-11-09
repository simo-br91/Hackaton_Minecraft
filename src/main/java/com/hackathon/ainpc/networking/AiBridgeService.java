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
 * Phase 5: Enhanced AI Bridge with memory and state support
 * Uses Java 11+ built-in HttpClient (no external dependencies)
 */
public class AiBridgeService {
    private static final String AI_API_URL = "http://127.0.0.1:5000/api/npc_interact";
    private static final String STATE_API_URL = "http://127.0.0.1:5000/api/npc_state";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Send interaction to AI backend
     */
    public static void sendToAI(String player, String npcId, String message, Callback callback) {
        NpcInteractionRequest requestData = new NpcInteractionRequest(player, npcId, message);
        String jsonPayload = GSON.toJson(requestData);

        AiNpcMod.LOGGER.info("[AI Bridge] Sending to Python: player={}, npc={}, message={}",
                player, npcId, message);
        AiNpcMod.LOGGER.debug("[AI Bridge] JSON payload: {}", jsonPayload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AI_API_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        CompletableFuture<HttpResponse<String>> futureResponse =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        futureResponse.whenComplete((response, error) -> {
            if (error != null) {
                AiNpcMod.LOGGER.error("[AI Bridge] Connection failed: {}", error.getMessage());
                callback.onFailure("AI system offline. Is Python server running?");
                return;
            }

            try {
                String responseBody = response.body();
                AiNpcMod.LOGGER.debug("[AI Bridge] Received JSON: {}", responseBody);

                if (response.statusCode() == 200) {
                    NpcInteractionResponse npcResponse = GSON.fromJson(responseBody, NpcInteractionResponse.class);
                    
                    if (npcResponse == null) {
                        AiNpcMod.LOGGER.error("[AI Bridge] Response was null");
                        callback.onFailure("AI returned empty response.");
                        return;
                    }

                    AiNpcMod.LOGGER.info("[AI Bridge] Success: action={}, emotion={}",
                            npcResponse.action.actionType,
                            npcResponse.newState.emotion);
                    
                    callback.onSuccess(npcResponse);
                } else {
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
     * Poll NPC state from backend
     */
    public static void pollState(String npcId, StateCallback callback) {
        String url = STATE_API_URL + "?npc_id=" + npcId.replace(" ", "%20");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        CompletableFuture<HttpResponse<String>> futureResponse =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        futureResponse.whenComplete((response, error) -> {
            if (error != null) {
                AiNpcMod.LOGGER.error("[AI Bridge] State poll failed: {}", error.getMessage());
                callback.onFailure("Cannot reach AI server");
                return;
            }

            try {
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    NPCStateResponse stateResponse = GSON.fromJson(responseBody, NPCStateResponse.class);
                    
                    AiNpcMod.LOGGER.debug("[AI Bridge] State poll: emotion={}, objective={}",
                            stateResponse.emotion,
                            stateResponse.currentObjective);
                    
                    callback.onSuccess(stateResponse);
                } else {
                    callback.onFailure("State poll error: " + response.statusCode());
                }
            } catch (Exception e) {
                AiNpcMod.LOGGER.error("[AI Bridge] State parse failed: {}", e.getMessage());
                callback.onFailure("Could not parse state response.");
            }
        });
    }

    public interface Callback {
        void onSuccess(NpcInteractionResponse response);
        void onFailure(String error);
    }

    public interface StateCallback {
        void onSuccess(NPCStateResponse state);
        void onFailure(String error);
    }
}