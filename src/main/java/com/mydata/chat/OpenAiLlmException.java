package com.mydata.chat;

public class OpenAiLlmException extends RuntimeException {
    public OpenAiLlmException(String message) {
        super(message);
    }

    public OpenAiLlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
