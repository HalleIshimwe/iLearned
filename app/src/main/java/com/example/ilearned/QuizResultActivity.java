package com.example.ilearned;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ilearned.views.CircularProgressView;

import java.util.ArrayList;
import java.util.List;

public class QuizResultActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ_TYPE  = "extra_result_type";
    public static final String EXTRA_FILE_NAME  = "extra_result_file";
    public static final String EXTRA_TOTAL      = "extra_result_total";
    public static final String EXTRA_CORRECT    = "extra_result_correct";
    public static final String EXTRA_QUESTIONS  = "extra_result_questions";
    public static final String EXTRA_TOPICS     = "extra_result_topics";
    public static final String EXTRA_QUIZ_ID    = "extra_result_quiz_id";

    private CircularProgressView circularProgress;
    private TextView     textResultTitle, textScore, textPercent, textRight, textWrong;
    private LinearLayout layoutTopics;
    private Button       buttonReview, buttonRetake;

    private List<QuizQuestion> questions;
    private List<String>       topics;
    private String             quizType, fileName, quizId;
    private int                total, correct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_result);

        quizType  = getIntent().getStringExtra(EXTRA_QUIZ_TYPE);
        fileName  = getIntent().getStringExtra(EXTRA_FILE_NAME);
        quizId    = getIntent().getStringExtra(EXTRA_QUIZ_ID);
        total     = getIntent().getIntExtra(EXTRA_TOTAL, 20);
        correct   = getIntent().getIntExtra(EXTRA_CORRECT, 0);
        questions = (ArrayList<QuizQuestion>) getIntent().getSerializableExtra(EXTRA_QUESTIONS);
        topics    = getIntent().getStringArrayListExtra(EXTRA_TOPICS);

        // Mark session as completed so Access button shows "Completed"
        // and pressing Access opens review mode
        QuizSessionManager.get().markCompleted(quizId, questions, correct);

        // Save result to Firebase
        new QuizStorageHelper().saveResult(quizId, correct, total, null);

        initViews();
        populateResults();
        setupClickListeners();
    }

    private void initViews() {
        textResultTitle  = findViewById(R.id.textResultTitle);
        circularProgress = findViewById(R.id.circularProgress);
        textScore        = findViewById(R.id.textScore);
        textPercent      = findViewById(R.id.textPercent);
        textRight        = findViewById(R.id.textRight);
        textWrong        = findViewById(R.id.textWrong);
        layoutTopics     = findViewById(R.id.layoutTopics);
        buttonReview     = findViewById(R.id.buttonReview);
        buttonRetake     = findViewById(R.id.buttonRetake);

        ImageButton btnBack = findViewById(R.id.buttonBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void populateResults() {
        textResultTitle.setText("You did it! Quiz Complete.");

        int wrong   = total - correct;
        int percent = total > 0 ? (correct * 100) / total : 0;

        textScore.setText(correct + "/" + total);
        textPercent.setText(percent + "%");
        textRight.setText(String.valueOf(correct));
        textWrong.setText(String.valueOf(wrong));
        if (circularProgress != null) circularProgress.setProgress(percent);

        // Application/Essay — hide right/wrong counts
        QuizQuestion.QuizType type = QuizQuestion.QuizType.valueOf(
                quizType != null ? quizType : "MULTIPLE_CHOICE");
        if (type != QuizQuestion.QuizType.MULTIPLE_CHOICE) {
            textRight.setVisibility(View.GONE);
            textWrong.setVisibility(View.GONE);
            TextView lr = findViewById(R.id.labelRight);
            TextView lw = findViewById(R.id.labelWrong);
            if (lr != null) lr.setVisibility(View.GONE);
            if (lw != null) lw.setVisibility(View.GONE);
            textScore.setText("Completed");
            textPercent.setVisibility(View.GONE);
            if (circularProgress != null) circularProgress.setProgress(100);
        }

        // Topics
        layoutTopics.removeAllViews();
        if (topics != null) {
            for (String topic : topics) {
                TextView tv = new TextView(this);
                tv.setText("• " + topic);
                tv.setTextSize(14f);
                tv.setTextColor(getResources().getColor(R.color.textPrimary, null));
                tv.setPadding(0, 6, 0, 6);
                layoutTopics.addView(tv);
            }
        }
    }

    private void setupClickListeners() {
        // Review — open quiz in read-only mode from Q1
        buttonReview.setOnClickListener(v -> openQuiz(true));

        // Retake — clear session so all answers reset, open fresh
        buttonRetake.setOnClickListener(v -> {
            QuizSessionManager.get().clearSession(quizId);
            // Reset all question state
            if (questions != null) {
                for (QuizQuestion q : questions) {
                    q.setAnswered(false);
                    q.setSelectedIndex(-1);
                    q.setStudentAnswer("");
                    q.setAiFeedback("");
                }
            }
            openQuiz(false);
            finish(); // remove result screen from back stack
        });
    }

    private void openQuiz(boolean review) {
        if (questions == null) return;
        Intent intent = new Intent(this, QuizScreenActivity.class);
        intent.putExtra(QuizScreenActivity.EXTRA_QUIZ_TYPE,   quizType);
        intent.putExtra(QuizScreenActivity.EXTRA_FILE_NAME,   fileName);
        intent.putExtra(QuizScreenActivity.EXTRA_QUIZ_ID,     quizId);
        intent.putExtra(QuizScreenActivity.EXTRA_REVIEW_MODE, review);
        intent.putExtra(QuizScreenActivity.EXTRA_QUESTIONS,   new ArrayList<>(questions));
        intent.putStringArrayListExtra(QuizScreenActivity.EXTRA_TOPICS,
                topics != null ? new ArrayList<>(topics) : new ArrayList<>());
        startActivity(intent);
    }
}