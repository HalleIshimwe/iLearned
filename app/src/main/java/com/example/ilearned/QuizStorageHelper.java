package com.example.ilearned;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuizStorageHelper {

    private static final String TAG              = "QuizStorageHelper";
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_QUIZ  = "quizzes";

    private final FirebaseFirestore db;
    private final String            userId;

    public interface SaveCallback           { void onSuccess(String quizId); void onError(String e); }
    public interface LoadCallback           { void onSuccess(List<QuizQuestion> q, List<String> t); void onError(String e); }
    public interface QuizListCallback       { void onSuccess(List<SavedQuizMeta> q); void onError(String e); }
    public interface SimpleCallback         { void onSuccess(); void onError(String e); }
    public interface GroupedHistoryCallback { void onSuccess(List<DocumentHistory> docs); void onError(String e); }

    public QuizStorageHelper() {
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        userId = (user != null) ? user.getUid() : null;
    }

    // ── Save quiz ─────────────────────────────────────────────

    public void saveQuiz(String fileName, QuizQuestion.QuizType type,
                         List<QuizQuestion> questions, List<String> topics,
                         SaveCallback callback) {
        if (userId == null) { callback.onError("Not logged in"); return; }

        List<Map<String, Object>> questionMaps = new ArrayList<>();
        for (QuizQuestion q : questions) {
            Map<String, Object> map = new HashMap<>();
            map.put("questionText", q.getQuestionText());
            map.put("quizType",     q.getQuizType().name());
            map.put("modelAnswer",  q.getModelAnswer() != null ? q.getModelAnswer() : "");
            if (q.getOptions()      != null) map.put("options",      q.getOptions());
            if (q.getExplanations() != null) map.put("explanations", q.getExplanations());
            map.put("correctIndex", q.getCorrectIndex());
            questionMaps.add(map);
        }

        Map<String, Object> doc = new HashMap<>();
        doc.put("type",        type.name());
        doc.put("fileName",    fileName != null ? fileName : "document");
        doc.put("topics",      topics   != null ? topics   : new ArrayList<>());
        doc.put("createdAt",   new Date());
        doc.put("questions",   questionMaps);
        doc.put("lastScore",   -1);
        doc.put("lastTotal",   questions.size());
        doc.put("lastPercent", -1);

        db.collection(COLLECTION_USERS).document(userId)
                .collection(COLLECTION_QUIZ).add(doc)
                .addOnSuccessListener(ref -> callback.onSuccess(ref.getId()))
                .addOnFailureListener(e  -> callback.onError(e.getMessage()));
    }

    // ── Save result after quiz is completed ───────────────────

    public void saveResult(String quizId, int score, int total, SimpleCallback callback) {
        if (userId == null || quizId == null || quizId.equals("local")) {
            if (callback != null) callback.onSuccess();
            return;
        }
        int percent = total > 0 ? (score * 100) / total : 0;
        Map<String, Object> update = new HashMap<>();
        update.put("lastScore",   score);
        update.put("lastTotal",   total);
        update.put("lastPercent", percent);
        update.put("attemptedAt", new Date());

        db.collection(COLLECTION_USERS).document(userId)
                .collection(COLLECTION_QUIZ).document(quizId)
                .update(update)
                .addOnSuccessListener(v -> { if (callback != null) callback.onSuccess(); })
                .addOnFailureListener(e -> { if (callback != null) callback.onError(e.getMessage()); });
    }

    // ── Load quiz questions ───────────────────────────────────

    public void loadQuiz(String quizId, LoadCallback callback) {
        if (userId == null) { callback.onError("Not logged in"); return; }

        db.collection(COLLECTION_USERS).document(userId)
                .collection(COLLECTION_QUIZ).document(quizId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onError("Quiz not found"); return; }
                    List<Map<String, Object>> qMaps = (List<Map<String, Object>>) doc.get("questions");
                    List<String> topics = (List<String>) doc.get("topics");
                    List<QuizQuestion> questions = new ArrayList<>();
                    if (qMaps != null) for (Map<String, Object> m : qMaps) questions.add(mapToQuestion(m));
                    callback.onSuccess(questions, topics != null ? topics : new ArrayList<>());
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ── List all quizzes flat ─────────────────────────────────

    public void listQuizzes(QuizListCallback callback) {
        if (userId == null) { callback.onError("Not logged in"); return; }

        db.collection(COLLECTION_USERS).document(userId)
                .collection(COLLECTION_QUIZ)
                .orderBy("createdAt", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(snapshots -> {
                    List<SavedQuizMeta> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) list.add(docToMeta(doc));
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ── Load history grouped by fileName (deduplicated by type) ──

    /**
     * Groups quizzes by fileName, keeping only the MOST RECENT quiz
     * per type per file. Since Firestore returns results ordered by
     * createdAt descending, the first quiz of each type we see is
     * always the newest. Any subsequent duplicate is deleted from
     * Firebase immediately to clean up the database.
     *
     * Result: each DocumentHistory contains at most 3 quizzes —
     * one MULTIPLE_CHOICE, one APPLICATION, one ESSAY.
     */
    public void loadGroupedHistory(GroupedHistoryCallback callback) {
        if (userId == null) { callback.onError("Not logged in"); return; }

        db.collection(COLLECTION_USERS).document(userId)
                .collection(COLLECTION_QUIZ)
                .orderBy("createdAt", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(snapshots -> {

                    // One DocumentHistory per fileName (preserves insertion order)
                    Map<String, DocumentHistory> grouped = new LinkedHashMap<>();

                    // Tracks which quiz types have already been kept per fileName
                    // Key: fileName  →  Value: set of type strings already added
                    Map<String, Set<String>> seenTypes = new HashMap<>();

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String fileName = doc.getString("fileName");
                        if (fileName == null) fileName = "Unknown document";

                        String type = doc.getString("type");
                        if (type == null) type = "UNKNOWN";

                        // Initialise structures for this file on first encounter
                        if (!grouped.containsKey(fileName)) {
                            grouped.put(fileName, new DocumentHistory(fileName));
                            seenTypes.put(fileName, new HashSet<>());
                        }

                        Set<String> typesForFile = seenTypes.get(fileName);

                        if (!typesForFile.contains(type)) {
                            // First (newest) quiz of this type for this file — keep it
                            typesForFile.add(type);
                            grouped.get(fileName).quizzes.add(docToMeta(doc));
                        } else {
                            // Duplicate — delete from Firebase silently
                            final String dupId = doc.getId();
                            db.collection(COLLECTION_USERS)
                                    .document(userId)
                                    .collection(COLLECTION_QUIZ)
                                    .document(dupId)
                                    .delete()
                                    .addOnSuccessListener(v ->
                                            Log.d(TAG, "Deleted duplicate quiz: " + dupId))
                                    .addOnFailureListener(e ->
                                            Log.w(TAG, "Failed to delete duplicate: " + dupId, e));
                        }
                    }

                    callback.onSuccess(new ArrayList<>(grouped.values()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ── Private helpers ───────────────────────────────────────

    private SavedQuizMeta docToMeta(QueryDocumentSnapshot doc) {
        Long s = doc.getLong("lastScore");
        Long t = doc.getLong("lastTotal");
        Long p = doc.getLong("lastPercent");
        return new SavedQuizMeta(doc.getId(), doc.getString("type"),
                doc.getString("fileName"), doc.getDate("createdAt"),
                s != null ? s.intValue() : -1,
                t != null ? t.intValue() : 20,
                p != null ? p.intValue() : -1,
                doc.getDate("attemptedAt"));
    }

    private QuizQuestion mapToQuestion(Map<String, Object> map) {
        String typeStr = (String) map.get("quizType");
        QuizQuestion.QuizType type = QuizQuestion.QuizType.valueOf(
                typeStr != null ? typeStr : "MULTIPLE_CHOICE");
        String questionText = (String) map.get("questionText");
        String modelAnswer  = (String) map.get("modelAnswer");

        if (type == QuizQuestion.QuizType.MULTIPLE_CHOICE) {
            List<String> options      = (List<String>) map.get("options");
            List<String> explanations = (List<String>) map.get("explanations");
            Long cl = (Long) map.get("correctIndex");
            return new QuizQuestion(questionText, options, cl != null ? cl.intValue() : 0, explanations);
        }
        return new QuizQuestion(questionText, type, modelAnswer);
    }

    // ── Data models ───────────────────────────────────────────

    public static class SavedQuizMeta {
        public final String id;
        public final String type;
        public final String fileName;
        public final Date   createdAt;
        public final int    lastScore;
        public final int    lastTotal;
        public final int    lastPercent;
        public final Date   attemptedAt;

        public SavedQuizMeta(String id, String type, String fileName, Date createdAt,
                             int lastScore, int lastTotal, int lastPercent, Date attemptedAt) {
            this.id = id; this.type = type; this.fileName = fileName;
            this.createdAt = createdAt; this.lastScore = lastScore;
            this.lastTotal = lastTotal; this.lastPercent = lastPercent;
            this.attemptedAt = attemptedAt;
        }

        public String getDisplayType() {
            if (type == null) return "Quiz";
            switch (type) {
                case "MULTIPLE_CHOICE": return "Multiple Choice Quiz";
                case "APPLICATION":     return "Application Quiz";
                case "ESSAY":           return "Essay Quiz";
                default:                return type;
            }
        }

        public boolean hasBeenAttempted() { return lastScore >= 0; }
    }

    public static class DocumentHistory {
        public final String              fileName;
        public final List<SavedQuizMeta> quizzes  = new ArrayList<>();
        public boolean                   expanded = false;

        public DocumentHistory(String fileName) { this.fileName = fileName; }
    }
}