package com.example.ilearned;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

//Communicates with the Google Gemini API

public class GeminiApiHelper {

    private static final String TAG = "GeminiApiHelper";
    private static final String API_KEY = "AIzaSyD3aNhQ6Z6frVKEpsJlI1rrZPUGQjtWE0A";
    private static final String MODEL = "gemini-flash-latest";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent?key=" + API_KEY;

    private static final MediaType JSON_MEDIA =
            MediaType.get("application/json; charset=utf-8");

    // System prompt — tells Gemini how to behave as a study assistant
    private static final String SYSTEM_PROMPT =
            "You are a helpful AI study assistant for students. "
                    + "You help summarize notes, explain complex academic concepts simply, "
                    + "generate study material, create practice questions, and answer questions. "
                    + "Be encouraging, clear, and concise. "
                    + "When summarizing, use bullet points and clear headings. "
                    + "When explaining, use simple language and relatable examples.";

    // Conversation history
    private final List<JSONObject> conversationHistory = new ArrayList<>();

    private final OkHttpClient    httpClient;
    private final ExecutorService executor;

    public GeminiApiHelper() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60,    TimeUnit.SECONDS)
                .writeTimeout(30,   TimeUnit.SECONDS)
                .build();
        executor = Executors.newSingleThreadExecutor();
    }

    //  Public interface

    public interface ResponseCallback {
        void onSuccess(String reply);
        void onError(String error);
    }

    /**
     * Send a user message and receive an AI reply.
     * Maintains full conversation context within the session.
     */
    public void sendMessage(String userMessage, ResponseCallback callback) {
        executor.execute(() -> {
            try {
                // Add user message to history
                conversationHistory.add(buildTurn("user", userMessage));

                // Call Gemini API
                String reply = callApi();

                // Add assistant reply to history
                conversationHistory.add(buildTurn("model", reply));

                callback.onSuccess(reply);

            } catch (Exception e) {
                Log.e(TAG, "Gemini API call failed", e);
                callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
            }
        });
    }

    //Call when starting a new chat session.
    public void clearHistory() {
        conversationHistory.clear();
    }

    /**
     * Rebuild conversation context when loading an old session from Firestore.
     * Call in order for each message.
     */
    public void addToHistoryUser(String text) {
        try { conversationHistory.add(buildTurn("user", text)); }
        catch (JSONException e) { Log.e(TAG, "History error", e); }
    }

    public void addToHistoryAssistant(String text) {
        try { conversationHistory.add(buildTurn("model", text)); }
        catch (JSONException e) { Log.e(TAG, "History error", e); }
    }

    //  Private — API call logic
    private String callApi() throws IOException, JSONException {

        // Build request body in Gemini's format
        JSONObject requestBody = new JSONObject();

        // System instruction
        JSONObject systemInstruction = new JSONObject();
        JSONObject systemPart = new JSONObject();
        systemPart.put("text", SYSTEM_PROMPT);
        JSONArray systemParts = new JSONArray();
        systemParts.put(systemPart);
        systemInstruction.put("parts", systemParts);
        requestBody.put("systemInstruction", systemInstruction);

        // Conversation contents
        JSONArray contents = new JSONArray();
        for (JSONObject turn : conversationHistory) {
            contents.put(turn);
        }
        requestBody.put("contents", contents);

        // Generation config — tweak these as needed
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("maxOutputTokens", 2048);
        generationConfig.put("temperature",     0.7);  // 0 = focused, 1 = creative
        requestBody.put("generationConfig", generationConfig);

        // Safety settings
        JSONArray safetySettings = new JSONArray();
        String[] categories = {
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT"
        };
        for (String category : categories) {
            JSONObject setting = new JSONObject();
            setting.put("category",  category);
            setting.put("threshold", "BLOCK_ONLY_HIGH");
            safetySettings.put(setting);
        }
        requestBody.put("safetySettings", safetySettings);

        // Make the HTTP request
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseStr = response.body() != null
                    ? response.body().string() : "";

            if (!response.isSuccessful()) {

                /*if (response.code() == 400) {
                    throw new IOException("Bad request — check your API key is correct.");
                } else if (response.code() == 429) {
                    throw new IOException("Rate limit reached. Please wait a moment and try again.");
                } else {
                    throw new IOException("API error " + response.code() + ": " + responseStr);
                } */
                //CHANGED TO SHOW USER SPECIFIC ERROR MESSAGE
                int code = response.code();
                String friendlyMessage;
                switch (code) {
                    case 400:
                        friendlyMessage = "Bad request — please check your connection or query.";
                        break;
                    case 429:
                        friendlyMessage = "Rate limit reached. Please wait a moment and try again";
                        break;
                    case 503:
                        friendlyMessage = "The model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.";
                        break;
                    case 500:
                        friendlyMessage = "Oops! My brain took a quick nap. Could you please rephrase that or try again in a moment?";
                        break;
                    case 502:
                        friendlyMessage = "I tried to get that information, but the connection was broken. Can you try again, please?";
                        break;
                    case 504:
                        friendlyMessage = "The server is having trouble. Please try again in a few minutes.";
                        break;
                    default:
                        friendlyMessage = "An unexpected error occurred (Code: " + code + "). Please try again later.";
                }
                throw new IOException(friendlyMessage);
            }


            // Parse Gemini's response format
            JSONObject json       = new JSONObject(responseStr);
            JSONArray  candidates = json.getJSONArray("candidates");

            if (candidates.length() == 0) {
                throw new IOException("No response generated. Try rephrasing your message.");
            }

            JSONObject firstCandidate = candidates.getJSONObject(0);

            // Check if the response was blocked by safety filters
            if (firstCandidate.has("finishReason")) {
                String finishReason = firstCandidate.getString("finishReason");
                if ("SAFETY".equals(finishReason)) {
                    return "I'm sorry, I couldn't respond to that. Please try rephrasing your question.";
                }
            }

            JSONObject content = firstCandidate.getJSONObject("content");
            JSONArray  parts   = content.getJSONArray("parts");

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (part.has("text")) {
                    result.append(part.getString("text"));
                }
            }

            String finalText = result.toString().trim();
            if (finalText.isEmpty()) {
                throw new IOException("Empty response from AI.");
            }
            return finalText;
        }
    }
    // conversation turn in Gemini's format.
    // role must be "user" or "model"

    private JSONObject buildTurn(String role, String text) throws JSONException {
        JSONObject part = new JSONObject();
        part.put("text", text);

        JSONArray parts = new JSONArray();
        parts.put(part);

        JSONObject turn = new JSONObject();
        turn.put("role",  role);
        turn.put("parts", parts);

        return turn;
    }
}
