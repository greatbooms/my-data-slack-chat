package com.mydata.chat;

public record LlmPrompt(String instructions, String input) {
    public LlmPrompt {
        instructions = instructions == null ? "" : instructions;
        input = input == null ? "" : input;
    }
}
