package com.example.ilearned;
public class ChatMessage {

    public static final int TYPE_USER   = 0;
    public static final int TYPE_BOT    = 1;
    public static final int TYPE_TYPING = 2;   // Typing indicator bubble

    private final String text;
    private final int    type;

    public ChatMessage(String text, int type) {
        this.text = text;
        this.type = type;
    }

    public String getText() { return text; }
    public int    getType() { return type; }
}
