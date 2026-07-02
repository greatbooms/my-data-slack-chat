package com.mydata.chat;

public class ClaudeLlmException extends RuntimeException {
    public ClaudeLlmException(String message) {
        super(message);
    }

    public ClaudeLlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
