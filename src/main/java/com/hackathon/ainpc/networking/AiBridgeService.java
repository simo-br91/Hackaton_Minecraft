package com.hackathon.ainpc.networking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hackathon.ainpc.AiNpcMod;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced AI Bridge Service for Phase 5+ (Multiple NPCs)
 * Adds event recording, relationship querying, and NPC deletion
 */
public class AiBridgeService {
    private static final String AI_API_URL = "http://127.0.0.1:5000/api/npc_interact_enhanced";
    private static final String EVENT_API_URL = "http://127.0.0.1:5000/api/npc_event";
    private static final String RELATIONSHIP_API_URL = "http://127.0.0.1:5000/api/npc_relationship";
    private static final String STATE_API_URL = "http://127.0.0.1:5000/api/npc_state";
    private static final String DELETE_API_URL = "http://127.0.0.1:5000/api/npc_delete";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Send interaction to AI backend (ENHANCED version with NPC ID)
     */
    public static void sendToAI(String player, String npcId, String message, Callback callback) {
        NpcInteractionRequest requestData = new NpcInteractionRequest(player, npcId, message);
        String jsonPayload = GSON.toJson(requestData);

        AiNpcMod.LOGGER.info("[AI Bridge Enhanced] Sending: npc={}, player={}, message={}",
                npcId, player, message);

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
                AiNpcMod.LOGGER.error("[AI Bridge Enhanced] Connection failed: {}", error.getMessage());
                callback.onFailure("AI system offline");
                return;
            }

            try {
                String responseBody = response.body();

                if (response.statusCode() == 200) {
                    NpcInteractionResponse npcResponse = GSON.fromJson(
                            responseBody, 
                            NpcInteractionResponse.class
                    );
                    
                    if (npcResponse == null) {
                        callback.onFailure("Empty AI response");
                        return;
                    }

                    AiNpcMod.LOGGER.info("[AI Bridge Enhanced] Success for {}: action={}, emotion={}",
                            npcId,
                            npcResponse.action.actionType,
                            npcResponse.newState.emotion);
                    
                    callback.onSuccess(npcResponse);
                } else {
                    callback.onFailure("Server error: " + response.statusCode());
                }
            } catch (Exception e) {
                AiNpcMod.LOGGER.error("[AI Bridge Enhanced] Parse failed: {}", e.getMessage());
                callback.onFailure("Could not parse response");
            }
        });
    }

    /**
     * Send event to AI backend (combat, social, environmental)
     */
    public static void sendEvent(JsonObject eventPayload, EventCallback callback) {
        String jsonPayload = GSON.toJson(eventPayload);

        AiNpcMod.LOGGER.debug("[AI Bridge] Sending event for NPC: {}", 
                eventPayload.get("npc_id").getAsString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EVENT_API_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        CompletableFuture<HttpResponse<String>> futureResponse =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        futureResponse.whenComplete((response, error) -> {
            if (error != null) {
                AiNpcMod.LOGGER.error("[AI Bridge] Event send failed: {}", error.getMessage());
                callback.onFailure("Could not send event");
                return;
            }

            try {
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    JsonObject jsonResponse = GSON.fromJson(responseBody, JsonObject.class);
                    callback.onSuccess(jsonResponse);
                } else {
                    callback.onFailure("Event error: " + response.statusCode());
                }
            } catch (Exception e) {
                AiNpcMod.LOGGER.error("[AI Bridge] Event parse failed: {}", e.getMessage());
                callback.onFailure("Could not parse event response");
            }
        });
    }

    /**
     * Get relationship status with specific entity
     */
    public static void getRelationship(String npcId, String entityName, RelationshipCallback callback) {
        String url = RELATIONSHIP_API_URL + 
                "?npc_id=" + npcId.replace(" ", "%20") +
                "&entity=" + entityName.replace(" ", "%20");

        AiNpcMod.LOGGER.debug("[AI Bridge] Querying relationship: {} with {}", npcId, entityName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        CompletableFuture<HttpResponse<String>> futureResponse =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        futureResponse.whenComplete((response, error) -> {
            if (error != null) {
                AiNpcMod.LOGGER.error("[AI Bridge] Relationship query failed: {}", error.getMessage());
                callback.onFailure("Could not query relationship");
                return;
            }

            try {
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    JsonObject relationship = GSON.fromJson(responseBody, JsonObject.class);
                    
                    AiNpcMod.LOGGER.debug("[AI Bridge] Relationship: status={}", 
                            relationship.get("status").getAsString());
                    
                    callback.onSuccess(relationship);
                } else {
                    callback.onFailure("Relationship error: " + response.statusCode());
                }
            } catch (Exception e) {
                AiNpcMod.LOGGER.error("[AI Bridge] Relationship parse failed: {}", e.getMessage());
                callback.onFailure("Could not parse relationship");
            }
        });
    }

    /**
     * Delete NPC memory (called when NPC dies)
     */
    public static void deleteNPCMemory(String npcId, DeleteCallback callback) {
        JsonObject payload = new JsonObject();
        payload.addProperty("npc_id", npcId);
        String jsonPayload = GSON.toJson(payload);

        AiNpcMod.LOGGER.info("[AI Bridge] Deleting memory for NPC: {}", npcId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DELETE_API_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        CompletableFuture<HttpResponse<String>> futureResponse =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        futureResponse.whenComplete((response, error) -> {
            if (error != null) {
                AiNpcMod.LOGGER.error("[AI Bridge] Delete failed: {}", error.getMessage());
                callback.onFailure("Could not delete NPC memory");
                return;
            }

            try {
                if (response.statusCode() == 200) {
                    AiNpcMod.LOGGER.info("[AI Bridge] Successfully deleted memory for {}", npcId);
                    callback.onSuccess();
                } else if (response.statusCode() == 404) {
                    AiNpcMod.LOGGER.warn("[AI Bridge] Memory not found for {}", npcId);
                    callback.onSuccess(); // Not an error - memory didn't exist
                } else {
                    callback.onFailure("Delete error: " + response.statusCode());
                }
            } catch (Exception e) {
                AiNpcMod.LOGGER.error("[AI Bridge] Delete parse failed: {}", e.getMessage());
                callback.onFailure("Could not parse delete response");
            }
        });
    }

    /**
     * Poll NPC state
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
                callback.onFailure("State poll failed");
                return;
            }

            try {
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    NPCStateResponse stateResponse = GSON.fromJson(
                            responseBody, 
                            NPCStateResponse.class
                    );
                    callback.onSuccess(stateResponse);
                } else {
                    callback.onFailure("State error: " + response.statusCode());
                }
            } catch (Exception e) {
                callback.onFailure("Could not parse state");
            }
        });
    }

    // Callback interfaces
    public interface Callback {
        void onSuccess(NpcInteractionResponse response);
        void onFailure(String error);
    }

    public interface EventCallback {
        void onSuccess(JsonObject response);
        void onFailure(String error);
    }

    public interface RelationshipCallback {
        void onSuccess(JsonObject relationship);
        void onFailure(String error);
    }

    public interface StateCallback {
        void onSuccess(NPCStateResponse state);
        void onFailure(String error);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onFailure(String error);
    }
}