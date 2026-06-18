package com.mydata.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class Chunker {
    private static final int CHUNK_SIZE = 120;
    private static final int OVERLAP = 20;

    public List<Chunk> chunk(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        String[] words = normalized.split("\\s+");
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + CHUNK_SIZE, words.length);
            chunks.add(new Chunk(String.join(" ", Arrays.asList(words).subList(start, end)), end - start));
            if (end == words.length) {
                break;
            }
            start = end - OVERLAP;
        }
        return chunks;
    }

    public record Chunk(String content, int tokenCount) {
    }
}
