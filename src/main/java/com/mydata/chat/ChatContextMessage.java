package com.mydata.chat;

public record ChatContextMessage(String role, String content) {
    public ChatContextMessage {
        role = role == null ? "" : role;
        content = content == null ? "" : content;
    }
}
