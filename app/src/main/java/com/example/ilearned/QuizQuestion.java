package com.example.ilearned;

import java.io.Serializable;
import java.util.List;


public class QuizQuestion implements Serializable {

    public enum QuizType {
        MULTIPLE_CHOICE,
        APPLICATION,
        ESSAY
    }

    // ── Fields ────────────────────────────────────────────────
    private String         questionText;
    private QuizType       quizType;

    // MCQ only
    private List<String>   options;        // ["A. ...", "B. ...", "C. ...", "D. ..."]
    private int            correctIndex;   // 0-based index of correct option
    private List<String>   explanations;   // explanation for each option

    // Application / Essay only
    private String         modelAnswer;    // the ideal answer shown after submission

    // Runtime state (not saved to Firebase)
    private int            selectedIndex  = -1;   // MCQ: which option student picked
    private boolean        answered       = false;
    private String         studentAnswer  = "";   // Application/Essay typed text
    private String         aiFeedback     = "";   // AI feedback after submission

    // ── Constructors ──────────────────────────────────────────

    /** MCQ constructor */
    public QuizQuestion(String questionText,
                        List<String> options,
                        int correctIndex,
                        List<String> explanations) {
        this.questionText  = questionText;
        this.quizType      = QuizType.MULTIPLE_CHOICE;
        this.options       = options;
        this.correctIndex  = correctIndex;
        this.explanations  = explanations;
    }

    /** Application / Essay constructor */
    public QuizQuestion(String questionText, QuizType type, String modelAnswer) {
        this.questionText = questionText;
        this.quizType     = type;
        this.modelAnswer  = modelAnswer;
    }

    /** Empty constructor for Firebase deserialization */
    public QuizQuestion() {}

    // ── Getters / Setters ─────────────────────────────────────

    public String         getQuestionText()  { return questionText; }
    public QuizType       getQuizType()      { return quizType; }
    public List<String>   getOptions()       { return options; }
    public int            getCorrectIndex()  { return correctIndex; }
    public List<String>   getExplanations()  { return explanations; }
    public String         getModelAnswer()   { return modelAnswer; }
    public int            getSelectedIndex() { return selectedIndex; }
    public boolean        isAnswered()       { return answered; }
    public String         getStudentAnswer() { return studentAnswer; }
    public String         getAiFeedback()    { return aiFeedback; }

    public void setQuestionText(String q)   { this.questionText  = q; }
    public void setQuizType(QuizType t)     { this.quizType      = t; }
    public void setOptions(List<String> o)  { this.options       = o; }
    public void setCorrectIndex(int i)      { this.correctIndex  = i; }
    public void setExplanations(List<String> e) { this.explanations = e; }
    public void setModelAnswer(String a)    { this.modelAnswer   = a; }
    public void setSelectedIndex(int i)     { this.selectedIndex = i; }
    public void setAnswered(boolean b)      { this.answered      = b; }
    public void setStudentAnswer(String s)  { this.studentAnswer = s; }
    public void setAiFeedback(String f)     { this.aiFeedback    = f; }

    /** Returns true if the student selected the correct MCQ option */
    public boolean isCorrect() {
        return quizType == QuizType.MULTIPLE_CHOICE && selectedIndex == correctIndex;
    }
}