package com.example.ilearned;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * QuizActivity — updated with session-aware button labels.
 *
 * When the student returns from a quiz (back button), the Access button
 * updates to show the session status:
 *   "In Progress →"  — started but not finished
 *   "Completed ✓"    — reached the result screen
 *   "Access Quiz →"  — not yet started
 *
 * onResume() refreshes the button labels every time the student comes
 * back to this screen from QuizScreenActivity or QuizResultActivity.
 */
public class QuizActivity extends AppCompatActivity {

    private Button       buttonChooseFile;
    private TextView     textFileName;
    private LinearLayout layoutFileName;
    private Button       buttonMCQ, buttonApplication, buttonEssay;

    private String extractedText = null, documentFileName = null;
    private String mcqQuizId = null, applicationQuizId = null, essayQuizId = null;
    private List<QuizQuestion> mcqQuestions = null, appQuestions = null, essayQuestions = null;
    private List<String> mcqTopics = new ArrayList<>(), appTopics = new ArrayList<>(), essayTopics = new ArrayList<>();

    private GeminiQuizHelper  geminiQuizHelper;
    private QuizStorageHelper storageHelper;

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) processFile(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        geminiQuizHelper = new GeminiQuizHelper();
        storageHelper    = new QuizStorageHelper();
        setupToolbar();
        initViews();
        setupClickListeners();
        setAllButtonsState(ButtonState.DISABLED);
    }

    /**
     * Called every time the student returns to this screen.
     * Refreshes Access button labels from the session manager
     * so "In Progress" and "Completed" badges appear correctly.
     */
    @Override
    protected void onResume() {
        super.onResume();
        refreshSessionBadges();
    }

    private void refreshSessionBadges() {
        refreshBadge(buttonMCQ,         mcqQuizId,         mcqQuestions,  "Multiple Choice Quiz");
        refreshBadge(buttonApplication, applicationQuizId, appQuestions,  "Application Quiz");
        refreshBadge(buttonEssay,       essayQuizId,       essayQuestions,"Essay Quiz");
    }

    private void refreshBadge(Button button, String quizId,
                              List<QuizQuestion> questions, String label) {
        if (quizId == null || questions == null) return; // not yet generated

        QuizSessionManager.SessionStatus status =
                QuizSessionManager.get().getStatus(quizId);

        switch (status) {
            case COMPLETED:
                button.setText("Completed ✓  " + label);
                button.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.quizDoneGreen));
                button.setEnabled(true);
                button.setAlpha(1.0f);
                break;
            case IN_PROGRESS:
                button.setText("In Progress  " + label + "  →");
                button.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.accent));
                button.setEnabled(true);
                button.setAlpha(1.0f);
                break;
            case NONE:
                // Already generated but not yet started — show normal Access button
                button.setText("Access " + label + "  →");
                button.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.quizDoneGreen));
                button.setEnabled(true);
                button.setAlpha(1.0f);
                break;
        }
    }

    private String getFriendlyErrorMessage(String technicalError) {
        if (technicalError.contains("503")) {
            return "Our servers are currently busy. Please try again in a moment.";
        } else if (technicalError.contains("400") || technicalError.contains("403")) {
            return "We couldn't process this document. Please try a different file.";
        } else {
            return "Something went wrong. Please check your connection and try again.";
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Generate Quiz");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Quiz History").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, QuizHistoryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo net = cm.getActiveNetworkInfo();
        return net != null && net.isConnectedOrConnecting();
    }

    private void showNoInternetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Internet Connection")
                .setMessage("Generating a quiz requires an internet connection.\n\nPlease turn on Wi-Fi or Mobile Data and try again.")
                .setPositiveButton("Open Settings", (d, w) -> startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)))
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private enum ButtonState { DISABLED, READY, GENERATING, DONE }

    private void setAllButtonsState(ButtonState s) {
        setButtonState(buttonMCQ, s, "Multiple Choice Quiz");
        setButtonState(buttonApplication, s, "Application Quiz");
        setButtonState(buttonEssay, s, "Essay Quiz");
    }

    private void setButtonState(Button b, ButtonState s, String label) {
        switch (s) {
            case DISABLED:
                b.setEnabled(false); b.setText(label);
                b.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.buttonDisabled));
                b.setAlpha(0.55f); break;
            case READY:
                b.setEnabled(true); b.setText("Generate " + label);
                b.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.accent));
                b.setAlpha(1.0f); break;
            case GENERATING:
                b.setEnabled(false); b.setText("Generating " + label + "...");
                b.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.buttonDisabled));
                b.setAlpha(0.75f); break;
            case DONE:
                b.setEnabled(true); b.setText("Access " + label + "  \u2192");
                b.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.quizDoneGreen));
                b.setAlpha(1.0f); break;
        }
    }

    private void initViews() {
        buttonChooseFile  = findViewById(R.id.buttonChooseFile);
        textFileName      = findViewById(R.id.textFileName);
        layoutFileName    = findViewById(R.id.layoutFileName);
        buttonMCQ         = findViewById(R.id.buttonMCQ);
        buttonApplication = findViewById(R.id.buttonApplication);
        buttonEssay       = findViewById(R.id.buttonEssay);
        layoutFileName.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        buttonChooseFile.setOnClickListener(v -> filePicker.launch(new String[]{
                "application/pdf","text/plain","application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"}));

        buttonMCQ.setOnClickListener(v -> {
            if (mcqQuizId != null) openQuiz(QuizQuestion.QuizType.MULTIPLE_CHOICE, mcqQuestions, mcqTopics, mcqQuizId);
            else { if (!isConnected()) { showNoInternetDialog(); return; } generateQuiz(QuizQuestion.QuizType.MULTIPLE_CHOICE); }
        });
        buttonApplication.setOnClickListener(v -> {
            if (applicationQuizId != null) openQuiz(QuizQuestion.QuizType.APPLICATION, appQuestions, appTopics, applicationQuizId);
            else { if (!isConnected()) { showNoInternetDialog(); return; } generateQuiz(QuizQuestion.QuizType.APPLICATION); }
        });
        buttonEssay.setOnClickListener(v -> {
            if (essayQuizId != null) openQuiz(QuizQuestion.QuizType.ESSAY, essayQuestions, essayTopics, essayQuizId);
            else { if (!isConnected()) { showNoInternetDialog(); return; } generateQuiz(QuizQuestion.QuizType.ESSAY); }
        });
    }

    private void processFile(Uri uri) {
        String text = DocumentTextExtractor.extractText(this, uri);
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "Could not read text. Try a .txt or .pdf file.", Toast.LENGTH_LONG).show();
            return;
        }
        extractedText = text; documentFileName = getFileName(uri);
        textFileName.setText(documentFileName);
        layoutFileName.setVisibility(View.VISIBLE);
        resetGeneratedQuizzes();
        setButtonState(buttonMCQ, ButtonState.READY, "Multiple Choice Quiz");
        setButtonState(buttonApplication, ButtonState.READY, "Application Quiz");
        setButtonState(buttonEssay, ButtonState.READY, "Essay Quiz");
        Toast.makeText(this, "File loaded! Tap a quiz type to generate.", Toast.LENGTH_SHORT).show();
    }

    private void resetGeneratedQuizzes() {
        mcqQuizId = null; applicationQuizId = null; essayQuizId = null;
        mcqQuestions = null; appQuestions = null; essayQuestions = null;
        mcqTopics.clear(); appTopics.clear(); essayTopics.clear();
    }

    private String getFileName(Uri uri) {
        String p = uri.getLastPathSegment(); return p != null ? p : "document";
    }

    private void generateQuiz(QuizQuestion.QuizType type) {
        if (extractedText == null) return;
        switch (type) {
            case MULTIPLE_CHOICE: setButtonState(buttonMCQ, ButtonState.GENERATING, "Multiple Choice Quiz"); break;
            case APPLICATION: setButtonState(buttonApplication, ButtonState.GENERATING, "Application Quiz"); break;
            case ESSAY: setButtonState(buttonEssay, ButtonState.GENERATING, "Essay Quiz"); break;
        }
        GeminiQuizHelper.QuizCallback cb = new GeminiQuizHelper.QuizCallback() {
            @Override public void onSuccess(List<QuizQuestion> q, List<String> t) {
                storageHelper.saveQuiz(documentFileName, type, q, t, new QuizStorageHelper.SaveCallback() {
                    @Override public void onSuccess(String id) { runOnUiThread(() -> onQuizReady(type, id, q, t)); }
                    @Override public void onError(String e)    { runOnUiThread(() -> onQuizReady(type, "local", q, t)); }
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(QuizActivity.this, getFriendlyErrorMessage(error), Toast.LENGTH_LONG).show();                    switch (type) {
                        case MULTIPLE_CHOICE: setButtonState(buttonMCQ, ButtonState.READY, "Multiple Choice Quiz"); break;
                        case APPLICATION: setButtonState(buttonApplication, ButtonState.READY, "Application Quiz"); break;
                        case ESSAY: setButtonState(buttonEssay, ButtonState.READY, "Essay Quiz"); break;
                    }
                });
            }
        };
        switch (type) {
            case MULTIPLE_CHOICE: geminiQuizHelper.generateMCQ(extractedText, cb); break;
            case APPLICATION: geminiQuizHelper.generateApplication(extractedText, cb); break;
            case ESSAY: geminiQuizHelper.generateEssay(extractedText, cb); break;
        }
    }

    private void onQuizReady(QuizQuestion.QuizType type, String id,
                             List<QuizQuestion> q, List<String> t) {
        switch (type) {
            case MULTIPLE_CHOICE: mcqQuizId=id; mcqQuestions=q; mcqTopics=t;
                setButtonState(buttonMCQ, ButtonState.DONE, "Multiple Choice Quiz"); break;
            case APPLICATION: applicationQuizId=id; appQuestions=q; appTopics=t;
                setButtonState(buttonApplication, ButtonState.DONE, "Application Quiz"); break;
            case ESSAY: essayQuizId=id; essayQuestions=q; essayTopics=t;
                setButtonState(buttonEssay, ButtonState.DONE, "Essay Quiz"); break;
        }
        Toast.makeText(this, "Quiz ready! Tap to start.", Toast.LENGTH_SHORT).show();
    }

    private void openQuiz(QuizQuestion.QuizType type, List<QuizQuestion> q,
                          List<String> t, String quizId) {
        if (q == null || q.isEmpty()) return;

        // Check session — if completed open in review mode automatically
        QuizSessionManager.SessionStatus status = QuizSessionManager.get().getStatus(quizId);
        boolean reviewMode = (status == QuizSessionManager.SessionStatus.COMPLETED);

        Intent i = new Intent(this, QuizScreenActivity.class);
        i.putExtra(QuizScreenActivity.EXTRA_QUIZ_TYPE,   type.name());
        i.putExtra(QuizScreenActivity.EXTRA_FILE_NAME,   documentFileName);
        i.putExtra(QuizScreenActivity.EXTRA_QUIZ_ID,     quizId);
        i.putExtra(QuizScreenActivity.EXTRA_REVIEW_MODE, reviewMode);
        i.putExtra(QuizScreenActivity.EXTRA_QUESTIONS,   new ArrayList<>(q));
        i.putStringArrayListExtra(QuizScreenActivity.EXTRA_TOPICS, new ArrayList<>(t));
        startActivity(i);
    }
}