package com.mydata.chat;

import com.mydata.retrieval.RetrievedChunk;

import java.util.List;

public interface LlmClient {
    String generate(String question, List<RetrievedChunk> chunks);
}
