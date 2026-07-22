package com.mydata.embeddings;

public class OpenAiEmbeddingException extends RuntimeException {
    public OpenAiEmbeddingException(String message) {
        super(message);
    }

    public OpenAiEmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
