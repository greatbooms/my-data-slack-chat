package com.mydata.chat;

import com.mydata.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class StubLlmClient implements LlmClient {
    @Override
    public String generate(String question, List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "답변할 수 있는 검색 근거를 찾지 못했습니다.";
        }

        String titles = chunks.stream()
            .map(RetrievedChunk::title)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining(", "));
        return "검색된 근거를 찾았습니다. 출처: " + titles;
    }
}
