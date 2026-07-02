package com.mydata.chat;

import com.mydata.retrieval.RetrievedChunk;

import java.util.List;

public interface LlmClient {
    default String generate(String question, List<RetrievedChunk> chunks) {
        return generate(question, chunks, List.of());
    }

    String generate(String question, List<RetrievedChunk> chunks, List<ChatContextMessage> contextMessages);
}
