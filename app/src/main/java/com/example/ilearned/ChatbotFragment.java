package com.example.ilearned;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

 public class ChatbotFragment extends Fragment {

    private static final int REQUEST_CODE_SUMMARIZE = 1001;

    // Main chat views
    private DrawerLayout      drawerLayout;
    private RecyclerView      recyclerViewChat;
    private EditText          editTextMessage, editTextMessageNormal;
    private ImageButton       buttonMenu, buttonSend, buttonUpload, buttonSendNormal;
    private TextView          buttonSummarizeNotes, buttonGenerateQuiz, buttonReport;
    private ProgressBar       progressBar;

    //  Drawer views
    private EditText          editTextSearch;
    private RecyclerView      recyclerViewHistory;
    private TextView          buttonNewChat;
    private ChatHistoryAdapter historyAdapter;

    // Data & helpers
    private ChatAdapter         chatAdapter;
    private List<ChatMessage>   chatMessages  = new ArrayList<>();
    private GeminiApiHelper     geminiHelper;
    private FirestoreHelper     firestoreHelper;

    //views for chat preview
     private View filePreviewBar;
     private View normalInputBar;
     private TextView textPreviewFileName;

     private ImageButton buttonClearFile;

     private String pendingFileText = null;
     private String pendingFileName = null;



     private String              pendingDocumentText = null;

    // Session persistence across fragment navigation
    private static List<ChatMessage>  savedMessages       = null;
    private static String             savedSessionId      = null;
    private static GeminiApiHelper    savedGeminiHelper   = null;

    private final ActivityResultLauncher<String[]> documentPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) handleDocumentUri(uri);
            });

    //  Lifecycle

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chatbot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
            return;
        }

        firestoreHelper = new FirestoreHelper();

        initViews(view);
        setupRecyclerView();
        setupDrawer();
        setupClickListeners();

        // ── Restore previous session if user navigated away and came back ──
        if (savedSessionId != null && savedMessages != null && !savedMessages.isEmpty()) {
            restoreSession();
        } else {
            geminiHelper = new GeminiApiHelper();
            startNewSession();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Save current session state so it survives navigation
        if (firestoreHelper.getCurrentSessionId() != null) {
            savedSessionId    = firestoreHelper.getCurrentSessionId();
            savedMessages     = new ArrayList<>(chatMessages);
            savedGeminiHelper = geminiHelper; // keeps conversation history
        }
        // Null out views to prevent memory leaks
        drawerLayout         = null;
        recyclerViewChat     = null;
        editTextMessage      = null;
        buttonMenu           = null;
        buttonSend           = null;
        buttonUpload         = null;
        buttonSummarizeNotes = null;
        buttonGenerateQuiz = null;
        buttonReport = null;
        progressBar          = null;
        editTextSearch       = null;
        recyclerViewHistory  = null;
        buttonNewChat        = null;
        filePreviewBar = null;
        normalInputBar = null;
        textPreviewFileName = null;
        buttonClearFile = null;
        pendingFileText = null;
        pendingFileName = null;
        buttonSendNormal = null;
        editTextMessageNormal = null;
    }

    //  Session persistence helpers

    private void restoreSession() {
        geminiHelper = savedGeminiHelper; // restore conversation context
        firestoreHelper.setCurrentSession(savedSessionId);
        chatMessages.clear();
        chatMessages.addAll(savedMessages);
        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
        if (recyclerViewChat != null && !chatMessages.isEmpty())
            recyclerViewChat.scrollToPosition(chatMessages.size() - 1);
    }

    //Call this to fully clear the saved state (e.g. on sign out)
    public static void clearSavedSession() {
        savedMessages     = null;
        savedSessionId    = null;
        savedGeminiHelper = null;
    }

    //  UI setup
    private void initViews(View view) {
        drawerLayout         = view.findViewById(R.id.drawerLayout);
        recyclerViewChat     = view.findViewById(R.id.recyclerViewChat);
        editTextMessage      = view.findViewById(R.id.editTextMessage);
        buttonMenu           = view.findViewById(R.id.buttonMenu);
        buttonSend           = view.findViewById(R.id.buttonSend);
        buttonUpload         = view.findViewById(R.id.buttonUpload);
        buttonSummarizeNotes = view.findViewById(R.id.buttonSummarizeNotes);
        buttonGenerateQuiz = view.findViewById(R.id.buttonGenerateQuiz);
        buttonReport = view.findViewById(R.id.report);
        progressBar          = view.findViewById(R.id.progressBar);
        buttonSendNormal           = view.findViewById(R.id.buttonSendNormal);
        editTextMessageNormal = view.findViewById(R.id.editTextMessageNormal);

        // Drawer views
        View drawerContent   = view.findViewById(R.id.drawerContent);
        editTextSearch       = drawerContent.findViewById(R.id.editTextSearch);
        recyclerViewHistory  = drawerContent.findViewById(R.id.recyclerViewHistory);
        buttonNewChat        = drawerContent.findViewById(R.id.buttonNewChat);
        filePreviewBar = view.findViewById(R.id.filePreviewBar);
        normalInputBar = view.findViewById(R.id.inputBar);
        textPreviewFileName = view.findViewById(R.id.textPreviewF1leName);
        buttonClearFile = view.findViewById(R.id.buttonClearFile);

        filePreviewBar.setVisibility(View.GONE);

    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(chatMessages);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        recyclerViewChat.setLayoutManager(lm);
        recyclerViewChat.setAdapter(chatAdapter);
    }

    private void setupDrawer() {
        // History RecyclerView inside drawer
        historyAdapter = new ChatHistoryAdapter(session -> {
            closeDrawer();
            loadSession(session);
        });
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewHistory.setAdapter(historyAdapter);

        // Search bar — searches BOTH session titles AND message content via Firestore
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                firestoreHelper.searchSessions(query, new FirestoreHelper.SessionsCallback() {
                    @Override public void onSuccess(List<FirestoreHelper.ChatSession> sessions) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            historyAdapter.setSessions(sessions);
                            historyAdapter.setActiveSessionId(firestoreHelper.getCurrentSessionId());
                        });
                    }
                    @Override public void onError(String err) {
                        // Silently ignore search errors — just show empty list
                    }
                });
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // New Chat button inside drawer
        buttonNewChat.setOnClickListener(v -> {
            closeDrawer();
            confirmNewChat();
        });

        // Hamburger button opens the drawer
        buttonMenu.setOnClickListener(v -> {
            loadHistoryIntoDrawer(); // refresh list every time drawer opens
            openDrawer();
        });
    }

    private void setupClickListeners() {
        buttonSend.setOnClickListener(v -> {
            String text = editTextMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                if(pendingFileText != null){
                    //file loaded with instruction
                    sendMessageWithFile(text,pendingFileText, pendingFileName);
                    clearFilePreview();

                }else{
                    sendUserMessage(text);

                }
                editTextMessage.setText("");
            } else if(pendingFileText != null){
                Toast.makeText(requireContext(), "Please type what you want to do with this file", Toast.LENGTH_SHORT).show();
            }
        });

        buttonSendNormal.setOnClickListener(v->{
            String userText = editTextMessageNormal.getText().toString().trim();
            if (!TextUtils.isEmpty(userText)) {
                sendUserMessage(userText);
                editTextMessageNormal.setText("");
            }
        });

        if(buttonClearFile != null){
            buttonClearFile.setOnClickListener(v->
                clearFilePreview());
        }

        buttonUpload.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ — use the new media permissions
                if (ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.READ_MEDIA_IMAGES)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                            101);
                    return;
                }
            } else {
                // Android 12 and below
                if (ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            101);
                    return;
                }
            }
            // Permission already granted — launch picker
            documentPickerLauncher.launch(new String[]{
                    "application/pdf",
                    "text/plain",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            });
        });

        buttonSummarizeNotes.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SummarizeNotesActivity.class);
            startActivityForResult(intent, REQUEST_CODE_SUMMARIZE);
        });

        buttonGenerateQuiz.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), QuizActivity.class));
        });

        buttonReport.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), StudyReportActivity.class));
        });
    }

    //  Drawer helpers
    private void openDrawer() {
        if (drawerLayout != null)
            drawerLayout.openDrawer(GravityCompat.START);
    }

    private void closeDrawer() {
        if (drawerLayout != null)
            drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void loadHistoryIntoDrawer() {
        firestoreHelper.loadSessions(new FirestoreHelper.SessionsCallback() {
            @Override public void onSuccess(List<FirestoreHelper.ChatSession> sessions) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    historyAdapter.setSessions(sessions);
                    historyAdapter.setActiveSessionId(firestoreHelper.getCurrentSessionId());
                });
            }
            @Override public void onError(String err) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "Could not load chat history.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    //  Session management
    private void startNewSession() {
        chatMessages.clear();
        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
        geminiHelper.clearHistory();
        clearSavedSession();


        String sessionTitle = "Chat " + android.text.format.DateFormat
                .format("dd MMM HH:mm", new java.util.Date());

        firestoreHelper.createNewSession(sessionTitle, new FirestoreHelper.SimpleCallback() {
            @Override public void onSuccess() {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        addBotMessage("Hello! I'm your AI study assistant. "
                                + "You can chat with me, summarize your notes, or generate study material!"));
            }
            @Override public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        addBotMessage("Hello! I'm your AI study assistant."));
            }
        });
    }

    private void confirmNewChat() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("New Chat")
                .setMessage("Start a new conversation? Your current chat is already saved.")
                .setPositiveButton("New Chat", (d, w) -> {
                    geminiHelper = new GeminiApiHelper();
                    startNewSession();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadSession(FirestoreHelper.ChatSession session) {
        chatMessages.clear();
        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
        geminiHelper = new GeminiApiHelper(); // fresh context
        geminiHelper.clearHistory();
        firestoreHelper.setCurrentSession(session.id);
        clearSavedSession();

        showProgressBar(true);
        firestoreHelper.loadMessages(session.id, new FirestoreHelper.MessagesCallback() {
            @Override public void onSuccess(List<ChatMessage> messages) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    showProgressBar(false);
                    for (ChatMessage msg : messages) {
                        chatMessages.add(msg);
                        // Rebuild Gemini conversation context
                        if (msg.getType() == ChatMessage.TYPE_USER)
                            geminiHelper.addToHistoryUser(msg.getText());
                        else if (msg.getType() == ChatMessage.TYPE_BOT)
                            geminiHelper.addToHistoryAssistant(msg.getText());
                    }
                    if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
                    if (!chatMessages.isEmpty() && recyclerViewChat != null)
                        recyclerViewChat.scrollToPosition(chatMessages.size() - 1);
                    // Update active highlight in drawer
                    historyAdapter.setActiveSessionId(session.id);
                });
            }
            @Override public void onError(String err) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    showProgressBar(false);
                    Toast.makeText(requireContext(),
                            "Could not load messages.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    //  Activity result — SummarizeNotesActivity
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SUMMARIZE
                && resultCode == android.app.Activity.RESULT_OK && data != null) {
            String text  = data.getStringExtra(SummarizeNotesActivity.EXTRA_TEXT_TO_SUMMARIZE);
            String label = data.getStringExtra(SummarizeNotesActivity.EXTRA_SOURCE_LABEL);
            if (!TextUtils.isEmpty(text)) {
                String userLabel = TextUtils.isEmpty(label)
                        ? "Summarize my notes." : "Summarize: " + label;
                ChatMessage userMsg = new ChatMessage(userLabel, ChatMessage.TYPE_USER);
                addMessage(userMsg);
                firestoreHelper.saveMessage(userMsg);
                summarizeText(text);
            }
        }
    }

    //  Core messaging
    private void sendUserMessage(String text) {
        ChatMessage userMsg = new ChatMessage(text, ChatMessage.TYPE_USER);
        addMessage(userMsg);
        firestoreHelper.saveMessage(userMsg);
        showTypingIndicator();

        geminiHelper.sendMessage(text, new GeminiApiHelper.ResponseCallback() {
            @Override public void onSuccess(String reply) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTypingIndicator();
                    ChatMessage botMsg = new ChatMessage(reply, ChatMessage.TYPE_BOT);
                    addMessage(botMsg);
                    firestoreHelper.saveMessage(botMsg);
                });
            }
            @Override public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTypingIndicator();
                    addBotMessage("Sorry, I had trouble responding: " + error);
                });
            }
        });
    }

    private void summarizeText(String text) {
        showTypingIndicator();
        String prompt = "Please provide a clear, structured summary of the following study material. "
                + "Highlight key concepts, important terms, and main takeaways. "
                + "Use bullet points and headings:\n\n" + text;

        geminiHelper.sendMessage(prompt, new GeminiApiHelper.ResponseCallback() {
            @Override public void onSuccess(String reply) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTypingIndicator();
                    ChatMessage botMsg = new ChatMessage(reply, ChatMessage.TYPE_BOT);
                    addMessage(botMsg);
                    firestoreHelper.saveMessage(botMsg);
                });
            }
            @Override public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTypingIndicator();
                    addBotMessage("Sorry, I could not summarize the document: " + error);
                });
            }
        });
    }

    private void generateStudyMaterial(String topic) {
        ChatMessage userMsg = new ChatMessage("Generate study material for: " + topic,
                ChatMessage.TYPE_USER);
        addMessage(userMsg);
        firestoreHelper.saveMessage(userMsg);
        showTypingIndicator();

        String prompt = "Generate comprehensive study material for: " + topic + "\n\n"
                + "Include:\n"
                + "- Key concepts and definitions\n"
                + "- Main ideas explained simply\n"
                + "- Real-world examples\n"
                + "- 5 practice questions with answers";

        geminiHelper.sendMessage(prompt, new GeminiApiHelper.ResponseCallback()     {
            @Override public void onSuccess(String reply) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTypingIndicator();
                    ChatMessage botMsg = new ChatMessage(reply, ChatMessage.TYPE_BOT);
                    addMessage(botMsg);
                    firestoreHelper.saveMessage(botMsg);
                });
            }
            @Override public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTypingIndicator();
                    addBotMessage("Sorry, I encountered an error: " + error);
                });
            }
        });
    }

    private void handleDocumentUri(Uri uri) {
        String text = DocumentTextExtractor.extractText(requireContext(), uri);
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(requireContext(),
                    "Could not read the document. Try a .txt or .pdf file.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        //store the extracted content
        pendingFileText = text;
        pendingFileName = uri.getLastPathSegment() != null
                ? uri.getLastPathSegment() : "document";

        showFilePreview(pendingFileName);
    }

    private void showFilePreview(String fileName){
        if(filePreviewBar == null || normalInputBar == null) return;

        if(textPreviewFileName != null){
            String display = fileName.length() > 30
                    ? fileName.substring(0, 28) + "..." : fileName;
            textPreviewFileName.setText(display);
        }
        normalInputBar.setVisibility(View.GONE);
        filePreviewBar.setVisibility(View.VISIBLE);

        if(editTextMessage != null){
            editTextMessage.setHint("What do you want to do with this file");
            editTextMessage.requestFocus();
        }
    }

    private void clearFilePreview(){
        pendingFileText = null;
        pendingFileName = null;

        if(filePreviewBar != null)
            filePreviewBar.setVisibility(View.GONE);
        if(normalInputBar != null)
            normalInputBar.setVisibility(View.VISIBLE);
        if(editTextMessage != null){
            editTextMessage.setHint("Ask Your Study Assistant..");
            editTextMessage.setText("");
        }
    }

    private void sendMessageWithFile(String instruction, String fileText, String fileName){
        String displayMsg = "[file]" + instruction;
        ChatMessage userMsg = new ChatMessage(displayMsg, ChatMessage.TYPE_USER);
        addMessage(userMsg);
        firestoreHelper.saveMessage(userMsg);

        showTypingIndicator();

        //full prompt with file content and instruction
        String prompt = instruction + "\\n\\nDocumentcontent below:\\n\\n"
                +fileText;

        geminiHelper.sendMessage(prompt, new GeminiApiHelper.ResponseCallback(){
            @Override public void onSuccess(String reply){
                if(!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTypingIndicator();
                    ChatMessage botMsg = new ChatMessage(reply, ChatMessage.TYPE_BOT);
                    addMessage(botMsg);
                    firestoreHelper.saveMessage(botMsg);
                });
            }
            @Override public void onError(String error){
                if(!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    removeTypingIndicator();
                    addBotMessage("Sorry, I had trouble processing that file: " + error);
                });
            }
        });
    }


    //  Sign out
    private void signOut() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure?")
                .setPositiveButton("Sign Out", (d, w) -> {
                    clearSavedSession();
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(requireContext(), LoginActivity.class));
                    requireActivity().finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    //  Chat list helpers
    private void addMessage(ChatMessage message) {
        chatMessages.add(message);
        if (chatAdapter != null)
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        if (recyclerViewChat != null)
            recyclerViewChat.scrollToPosition(chatMessages.size() - 1);
    }

    private void addBotMessage(String text) {
        addMessage(new ChatMessage(text, ChatMessage.TYPE_BOT));
    }

    private void showTypingIndicator() {
        chatMessages.add(new ChatMessage("...", ChatMessage.TYPE_TYPING));
        if (chatAdapter != null)
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        if (recyclerViewChat != null)
            recyclerViewChat.scrollToPosition(chatMessages.size() - 1);
    }

    private void removeTypingIndicator() {
        for (int i = chatMessages.size() - 1; i >= 0; i--) {
            if (chatMessages.get(i).getType() == ChatMessage.TYPE_TYPING) {
                chatMessages.remove(i);
                if (chatAdapter != null) chatAdapter.notifyItemRemoved(i);
                return;
            }
        }
    }

    private void showProgressBar(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}