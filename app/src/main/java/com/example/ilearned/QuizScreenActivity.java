package com.example.ilearned;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class QuizScreenActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ_TYPE   = "extra_quiz_type";
    public static final String EXTRA_FILE_NAME   = "extra_file_name";
    public static final String EXTRA_QUESTIONS   = "extra_questions";
    public static final String EXTRA_TOPICS      = "extra_topics";
    public static final String EXTRA_QUIZ_ID     = "extra_quiz_id";
    public static final String EXTRA_REVIEW_MODE = "extra_review_mode";

    private TextView     textQuizTitle, textProgress, textQuestion;
    private LinearLayout layoutMCQ;
    private LinearLayout layoutOptionA, layoutOptionB, layoutOptionC, layoutOptionD;
    private TextView     textOptionA, textOptionB, textOptionC, textOptionD;
    private TextView     textExpA, textExpB, textExpC, textExpD;
    private LinearLayout layoutOpenEnded;
    private EditText     editTextAnswer;
    private Button       buttonSubmitAnswer;
    private TextView     textFeedback;
    private ProgressBar  progressMarking;
    private Button       buttonPrevious, buttonNext;

    private List<QuizQuestion>    questions;
    private List<String>          topics;
    private QuizQuestion.QuizType quizType;
    private String                fileName;
    private String                quizId;
    private int                   currentIndex = 0;
    private int                   correctCount = 0;
    private boolean               reviewMode   = false;

    private GeminiQuizHelper geminiQuizHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_screen);

        geminiQuizHelper = new GeminiQuizHelper();

        String typeStr = getIntent().getStringExtra(EXTRA_QUIZ_TYPE);
        quizType   = QuizQuestion.QuizType.valueOf(typeStr != null ? typeStr : "MULTIPLE_CHOICE");
        fileName   = getIntent().getStringExtra(EXTRA_FILE_NAME);
        quizId     = getIntent().getStringExtra(EXTRA_QUIZ_ID);
        reviewMode = getIntent().getBooleanExtra(EXTRA_REVIEW_MODE, false);
        topics     = getIntent().getStringArrayListExtra(EXTRA_TOPICS);

        // Restore from session if available
        QuizSessionManager.QuizSession session = QuizSessionManager.get().getSession(quizId);
        if (session != null && session.questions != null && !session.questions.isEmpty()) {
            questions    = session.questions;
            currentIndex = 0;
            correctCount = recalculateCorrectCount();
            if (session.status == QuizSessionManager.SessionStatus.COMPLETED) reviewMode = true;
        } else {
            questions    = (ArrayList<QuizQuestion>) getIntent().getSerializableExtra(EXTRA_QUESTIONS);
            currentIndex = 0;
            correctCount = 0;
        }

        if (questions == null || questions.isEmpty()) {
            Toast.makeText(this, "No questions found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupClickListeners();
        showQuestion(currentIndex);

        // back press handler
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        saveProgressToSession();
                        finish();
                    }
                });
    }



    private void saveProgressToSession() {
        if (quizId != null && questions != null)
            QuizSessionManager.get().saveProgress(quizId, questions, currentIndex, correctCount);
    }

    private int recalculateCorrectCount() {
        int count = 0;
        if (questions == null) return 0;
        for (QuizQuestion q : questions)
            if (q.getQuizType() == QuizQuestion.QuizType.MULTIPLE_CHOICE && q.isAnswered() && q.isCorrect())
                count++;
        return count;
    }

    private void initViews() {
        textQuizTitle      = findViewById(R.id.textQuizTitle);
        textProgress       = findViewById(R.id.textProgress);
        textQuestion       = findViewById(R.id.textQuestion);
        layoutMCQ          = findViewById(R.id.layoutMCQ);
        layoutOptionA      = findViewById(R.id.layoutOptionA);
        layoutOptionB      = findViewById(R.id.layoutOptionB);
        layoutOptionC      = findViewById(R.id.layoutOptionC);
        layoutOptionD      = findViewById(R.id.layoutOptionD);
        textOptionA        = findViewById(R.id.textOptionA);
        textOptionB        = findViewById(R.id.textOptionB);
        textOptionC        = findViewById(R.id.textOptionC);
        textOptionD        = findViewById(R.id.textOptionD);
        textExpA           = findViewById(R.id.textExpA);
        textExpB           = findViewById(R.id.textExpB);
        textExpC           = findViewById(R.id.textExpC);
        textExpD           = findViewById(R.id.textExpD);
        layoutOpenEnded    = findViewById(R.id.layoutOpenEnded);
        editTextAnswer     = findViewById(R.id.editTextAnswer);
        buttonSubmitAnswer = findViewById(R.id.buttonSubmitAnswer);
        textFeedback       = findViewById(R.id.textFeedback);
        progressMarking    = findViewById(R.id.progressMarking);
        buttonPrevious     = findViewById(R.id.buttonPrevious);
        buttonNext         = findViewById(R.id.buttonNext);

        String title = quizType == QuizQuestion.QuizType.MULTIPLE_CHOICE ? "Multiple Choice Quiz"
                : quizType == QuizQuestion.QuizType.APPLICATION ? "Application Quiz" : "Essay Quiz";
        textQuizTitle.setText(reviewMode ? title + " (Review)" : title);

        if (quizType == QuizQuestion.QuizType.MULTIPLE_CHOICE) {
            layoutMCQ.setVisibility(View.VISIBLE);
            layoutOpenEnded.setVisibility(View.GONE);
        } else {
            layoutMCQ.setVisibility(View.GONE);
            layoutOpenEnded.setVisibility(View.VISIBLE);
        }
    }

    private void setupClickListeners() {
        ImageButton btnBack = findViewById(R.id.buttonBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> { saveProgressToSession(); finish(); });

        buttonPrevious.setOnClickListener(v -> {
            if (currentIndex > 0) { currentIndex--; showQuestion(currentIndex); }
        });

        buttonNext.setOnClickListener(v -> {
            if (currentIndex < questions.size() - 1) { currentIndex++; showQuestion(currentIndex); }
            else { if (reviewMode) finish(); else goToResults(); }
        });

        if (!reviewMode) {
            buttonSubmitAnswer.setOnClickListener(v -> submitOpenEndedAnswer());
            layoutOptionA.setOnClickListener(v -> selectMCQOption(0));
            layoutOptionB.setOnClickListener(v -> selectMCQOption(1));
            layoutOptionC.setOnClickListener(v -> selectMCQOption(2));
            layoutOptionD.setOnClickListener(v -> selectMCQOption(3));
        }
    }

    private void showQuestion(int index) {
        QuizQuestion q = questions.get(index);
        textProgress.setText("Question " + (index + 1) + " of " + questions.size());
        textQuestion.setText(q.getQuestionText());
        buttonNext.setText(index == questions.size() - 1 ? (reviewMode ? "Close" : "Finish") : "Next");
        buttonPrevious.setVisibility(index > 0 ? View.VISIBLE : View.INVISIBLE);
        if (quizType == QuizQuestion.QuizType.MULTIPLE_CHOICE) showMCQQuestion(q);
        else showOpenEndedQuestion(q);
    }

    private void showMCQQuestion(QuizQuestion q) {
        setText(textOptionA, q.getOptions(), 0);
        setText(textOptionB, q.getOptions(), 1);
        setText(textOptionC, q.getOptions(), 2);
        setText(textOptionD, q.getOptions(), 3);
        resetMCQOptions();
        if (q.isAnswered()) revealMCQFeedback(q, q.getExplanations());
    }

    private void selectMCQOption(int idx) {
        QuizQuestion q = questions.get(currentIndex);
        if (q.isAnswered()) return;
        q.setSelectedIndex(idx);
        q.setAnswered(true);
        if (q.isCorrect()) correctCount++;
        revealMCQFeedback(q, q.getExplanations());
        saveProgressToSession();
    }

    private void revealMCQFeedback(QuizQuestion q, List<String> exps) {
        int correct = q.getCorrectIndex(), selected = q.getSelectedIndex();
        LinearLayout[] lay = {layoutOptionA, layoutOptionB, layoutOptionC, layoutOptionD};
        TextView[]     exp = {textExpA, textExpB, textExpC, textExpD};
        for (int i = 0; i < 4; i++) {
            if (i == correct)       lay[i].setBackgroundResource(R.drawable.bg_correct_option);
            else if (i == selected) lay[i].setBackgroundResource(R.drawable.bg_wrong_option);
            else                    lay[i].setBackgroundResource(R.drawable.bg_default_option);
            if (exps != null && i < exps.size()) {
                exp[i].setText((i == correct ? "✓ Right answer\n" : "✗ Not quite\n") + exps.get(i));
                exp[i].setTextColor(ContextCompat.getColor(this,
                        i == correct ? R.color.quizCorrectGreen : R.color.quizWrongRed));
                exp[i].setVisibility(View.VISIBLE);
            }
            lay[i].setClickable(false);
        }
    }

    private void resetMCQOptions() {
        LinearLayout[] lay = {layoutOptionA, layoutOptionB, layoutOptionC, layoutOptionD};
        TextView[]     exp = {textExpA, textExpB, textExpC, textExpD};
        for (int i = 0; i < 4; i++) {
            lay[i].setBackgroundResource(R.drawable.bg_default_option);
            lay[i].setClickable(!reviewMode);
            exp[i].setVisibility(View.GONE);
        }
    }

    private void showOpenEndedQuestion(QuizQuestion q) {
        textFeedback.setVisibility(View.GONE);
        progressMarking.setVisibility(View.GONE);
        if (q.isAnswered()) {
            editTextAnswer.setText(q.getStudentAnswer());
            editTextAnswer.setEnabled(false);
            buttonSubmitAnswer.setEnabled(false);
            buttonSubmitAnswer.setText("Submitted");
            textFeedback.setText(q.getAiFeedback());
            textFeedback.setVisibility(View.VISIBLE);
        } else {
            if (reviewMode) {
                editTextAnswer.setText("");
                editTextAnswer.setEnabled(false);
                editTextAnswer.setHint("This question was not answered");
                buttonSubmitAnswer.setEnabled(false);
                buttonSubmitAnswer.setText("Not answered");
            } else {
                editTextAnswer.setText("");
                editTextAnswer.setEnabled(true);
                buttonSubmitAnswer.setEnabled(true);
                buttonSubmitAnswer.setText("Submit Answer");
            }
        }
    }

    private void submitOpenEndedAnswer() {
        QuizQuestion q = questions.get(currentIndex);
        String studentText = editTextAnswer.getText().toString().trim();
        if (TextUtils.isEmpty(studentText)) {
            Toast.makeText(this, "Please write your answer first.", Toast.LENGTH_SHORT).show();
            return;
        }
        editTextAnswer.setEnabled(false);
        buttonSubmitAnswer.setEnabled(false);
        buttonSubmitAnswer.setText("Marking...");
        progressMarking.setVisibility(View.VISIBLE);
        textFeedback.setVisibility(View.GONE);

        geminiQuizHelper.markAnswer(q.getQuestionText(), studentText, q.getModelAnswer(),
                new GeminiQuizHelper.FeedbackCallback() {
                    @Override public void onSuccess(String feedback) {
                        runOnUiThread(() -> {
                            progressMarking.setVisibility(View.GONE);
                            buttonSubmitAnswer.setText("Submitted");
                            q.setStudentAnswer(studentText);
                            q.setAiFeedback(feedback);
                            q.setAnswered(true);
                            textFeedback.setText(feedback);
                            textFeedback.setVisibility(View.VISIBLE);
                            saveProgressToSession();
                        });
                    }
                    @Override public void onError(String error) {
                        runOnUiThread(() -> {
                            progressMarking.setVisibility(View.GONE);
                            editTextAnswer.setEnabled(true);
                            buttonSubmitAnswer.setEnabled(true);
                            buttonSubmitAnswer.setText("Submit Answer");
                            Toast.makeText(QuizScreenActivity.this,
                                    "Marking failed: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void goToResults() {
        QuizSessionManager.get().markCompleted(quizId, questions, correctCount);
        Intent intent = new Intent(this, QuizResultActivity.class);
        intent.putExtra(QuizResultActivity.EXTRA_QUIZ_TYPE,  quizType.name());
        intent.putExtra(QuizResultActivity.EXTRA_FILE_NAME,  fileName);
        intent.putExtra(QuizResultActivity.EXTRA_TOTAL,      questions.size());
        intent.putExtra(QuizResultActivity.EXTRA_CORRECT,    correctCount);
        intent.putExtra(QuizResultActivity.EXTRA_QUIZ_ID,    quizId);
        intent.putExtra(QuizResultActivity.EXTRA_QUESTIONS,  new ArrayList<>(questions));
        intent.putStringArrayListExtra(QuizResultActivity.EXTRA_TOPICS,
                topics != null ? new ArrayList<>(topics) : new ArrayList<>());
        startActivity(intent);
    }

    private void setText(TextView tv, List<String> list, int index) {
        tv.setText(list != null && index < list.size() ? list.get(index) : "");
    }
}