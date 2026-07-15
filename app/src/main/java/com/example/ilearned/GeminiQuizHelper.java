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

public class GeminiQuizHelper {

    private static final String TAG      = "GeminiQuizHelper";
    private static final String API_KEY  = "HIDDEN API KEY";
    private static final String MODEL    = "gemini-flash-latest";
    private static final String API_URL  =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent?key=" + API_KEY;

    private static final MediaType JSON_MEDIA =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient   httpClient;
    private final ExecutorService executor;

    public GeminiQuizHelper() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)  // quiz gen can take up to 2 min
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        executor = Executors.newSingleThreadExecutor();
    }

    // ── Callbacks ─────────────────────────────────────────────

    public interface QuizCallback {
        void onSuccess(List<QuizQuestion> questions, List<String> topics);
        void onError(String error);
    }

    public interface FeedbackCallback {
        void onSuccess(String feedback);
        void onError(String error);
    }

    // ── Quiz generation ───────────────────────────────────────

    /**
     * Generate 20 Multiple Choice questions from the document text.
     * Each question has 4 options, a correct index, and an explanation per option.
     */
    public void generateMCQ(String documentText, QuizCallback callback) {
        executor.execute(() -> {
            String prompt = "You are a quiz generator. Based on the following study material, "
                    + "generate exactly 20 multiple choice questions.\n\n"
                    + "IMPORTANT: Respond ONLY with a valid JSON object. No markdown, no explanation, "
                    + "no backticks. Just raw JSON.\n\n"
                    + "JSON format:\n"
                    + "{\n"
                    + "  \"topics\": [\"topic1\", \"topic2\"],\n"
                    + "  \"questions\": [\n"
                    + "    {\n"
                    + "      \"question\": \"Question text here?\",\n"
                    + "      \"options\": [\"A. option\", \"B. option\", \"C. option\", \"D. option\"],\n"
                    + "      \"correctIndex\": 2,\n"
                    + "      \"explanations\": [\n"
                    + "        \"Why A is wrong or right\",\n"
                    + "        \"Why B is wrong or right\",\n"
                    + "        \"Why C is wrong or right\",\n"
                    + "        \"Why D is wrong or right\"\n"
                    + "      ]\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}\n\n"
                    + "Study material:\n" + truncate(documentText, 12000);

            callGeminiAndParseMCQ(prompt, callback);
        });
    }

    /**
     * Generate 20 Application questions from the document text.
     * Each has a question and a model answer.
     */
    public void generateApplication(String documentText, QuizCallback callback) {
        executor.execute(() -> {
            String prompt = "You are a quiz generator. Based on the following study material, "
                    + "generate exactly 20 application questions that require students to apply "
                    + "concepts to real scenarios.\n\n"
                    + "IMPORTANT: Respond ONLY with a valid JSON object. No markdown, no explanation, "
                    + "no backticks. Just raw JSON.\n\n"
                    + "JSON format:\n"
                    + "{\n"
                    + "  \"topics\": [\"topic1\", \"topic2\"],\n"
                    + "  \"questions\": [\n"
                    + "    {\n"
                    + "      \"question\": \"Application question here?\",\n"
                    + "      \"modelAnswer\": \"Detailed model answer here\"\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}\n\n"
                    + "Study material:\n" + truncate(documentText, 12000);

            callGeminiAndParseOpenEnded(prompt, QuizQuestion.QuizType.APPLICATION, callback);
        });
    }

    /**
     * Generate 20 Essay questions from the document text.
     */
    public void generateEssay(String documentText, QuizCallback callback) {
        executor.execute(() -> {
            String prompt = "You are a quiz generator. Based on the following study material, "
                    + "generate exactly 20 essay questions that require in-depth analytical responses.\n\n"
                    + "IMPORTANT: Respond ONLY with a valid JSON object. No markdown, no explanation, "
                    + "no backticks. Just raw JSON.\n\n"
                    + "JSON format:\n"
                    + "{\n"
                    + "  \"topics\": [\"topic1\", \"topic2\"],\n"
                    + "  \"questions\": [\n"
                    + "    {\n"
                    + "      \"question\": \"Essay question here?\",\n"
                    + "      \"modelAnswer\": \"Key points and model answer here\"\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}\n\n"
                    + "Study material:\n" + truncate(documentText, 12000);

            callGeminiAndParseOpenEnded(prompt, QuizQuestion.QuizType.ESSAY, callback);
        });
    }

    /**
     * Mark a student's application or essay answer.
     * Returns detailed feedback: what was right, what was wrong, model answer.
     */
    public void markAnswer(String question,
                           String studentAnswer,
                           String modelAnswer,
                           FeedbackCallback callback) {
        executor.execute(() -> {
            String prompt = "You are a fair and helpful examiner marking a student's answer.\n\n"
                    + "Question: " + question + "\n\n"
                    + "Student's answer: " + studentAnswer + "\n\n"
                    + "Model answer: " + modelAnswer + "\n\n"
                    + "Give brief, constructive feedback. Structure your response like this:\n"
                    + " What was correct: [list what the student got right]\n"
                    + " What was missing or incorrect: [list gaps or errors]\n"
                    + " Model answer: [show the ideal answer]\n\n"
                    + "Keep feedback concise and encouraging. Maximum 150 words.";

            try {
                String response = callGemini(prompt);
                callback.onSuccess(response);
            } catch (Exception e) {
                Log.e(TAG, "Marking failed", e);
                callback.onError(e.getMessage());
            }
        });
    }

    // ── Private: API calls ────────────────────────────────────

    private void callGeminiAndParseMCQ(String prompt, QuizCallback callback) {
        try {
            String raw = callGemini(prompt);
            String json = extractJson(raw);

            JSONObject root      = new JSONObject(json);
            JSONArray  qArray    = root.getJSONArray("questions");
            JSONArray  topicArr  = root.optJSONArray("topics");

            List<String> topics = new ArrayList<>();
            if (topicArr != null) {
                for (int i = 0; i < topicArr.length(); i++)
                    topics.add(topicArr.getString(i));
            }

            List<QuizQuestion> questions = new ArrayList<>();
            for (int i = 0; i < qArray.length(); i++) {
                JSONObject q = qArray.getJSONObject(i);

                String questionText  = q.getString("question");
                int    correctIndex  = q.getInt("correctIndex");
                JSONArray optArr     = q.getJSONArray("options");
                JSONArray expArr     = q.getJSONArray("explanations");

                List<String> options = new ArrayList<>();
                for (int j = 0; j < optArr.length(); j++)
                    options.add(optArr.getString(j));

                List<String> explanations = new ArrayList<>();
                for (int j = 0; j < expArr.length(); j++)
                    explanations.add(expArr.getString(j));

                questions.add(new QuizQuestion(questionText, options, correctIndex, explanations));
            }
            callback.onSuccess(questions, topics);

        } catch (Exception e) {
            Log.e(TAG, "MCQ parse failed", e);
            callback.onError("Failed to generate quiz: " + e.getMessage());
        }
    }

    private void callGeminiAndParseOpenEnded(String prompt,
                                             QuizQuestion.QuizType type,
                                             QuizCallback callback) {
        try {
            String raw  = callGemini(prompt);
            String json = extractJson(raw);

            JSONObject root     = new JSONObject(json);
            JSONArray  qArray   = root.getJSONArray("questions");
            JSONArray  topicArr = root.optJSONArray("topics");

            List<String> topics = new ArrayList<>();
            if (topicArr != null) {
                for (int i = 0; i < topicArr.length(); i++)
                    topics.add(topicArr.getString(i));
            }

            List<QuizQuestion> questions = new ArrayList<>();
            for (int i = 0; i < qArray.length(); i++) {
                JSONObject q    = qArray.getJSONObject(i);
                String question = q.getString("question");
                String model    = q.optString("modelAnswer", "");
                questions.add(new QuizQuestion(question, type, model));
            }
            callback.onSuccess(questions, topics);

        } catch (Exception e) {
            Log.e(TAG, "Open-ended parse failed", e);
            callback.onError("Failed to generate quiz: " + e.getMessage());
        }
    }

    private String callGemini(String promptText) throws IOException, JSONException {
        JSONObject part = new JSONObject();
        part.put("text", promptText);

        JSONArray parts = new JSONArray();
        parts.put(part);

        JSONObject content = new JSONObject();
        content.put("role", "user");
        content.put("parts", parts);

        JSONArray contents = new JSONArray();
        contents.put(content);

        JSONObject body = new JSONObject();
        body.put("contents", contents);

        JSONObject genConfig = new JSONObject();
        genConfig.put("maxOutputTokens", 8192);
        genConfig.put("temperature", 0.3); // low = consistent structured output
        body.put("generationConfig", genConfig);

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseStr = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                Log.e(TAG, "API error " + response.code() + ": " + responseStr);
                throw new IOException("API error " + response.code());
            }
            JSONObject json      = new JSONObject(responseStr);
            JSONArray  candidates = json.getJSONArray("candidates");
            JSONObject first     = candidates.getJSONObject(0);
            JSONArray  resParts  = first.getJSONObject("content").getJSONArray("parts");
            return resParts.getJSONObject(0).getString("text");
        }
    }

    /**
     * Strip markdown code fences if Gemini wraps JSON in ```json ... ```
     */
    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end   = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    /** Truncate document to avoid exceeding Gemini's token limit */
    private String truncate(String text, int maxChars) {
        return text.length() > maxChars ? text.substring(0, maxChars) + "..." : text;
    }
}
