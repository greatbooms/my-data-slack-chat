package com.mydata.chat;

import com.mydata.retrieval.RetrievedChunk;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class LlmPromptBuilder {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> METADATA_MAP_TYPE = new TypeReference<>() {
    };
    private static final String INSTRUCTIONS = """
        당신은 개인 데이터 검색 결과만 근거로 답변하는 RAG 챗봇입니다.
        이전 대화가 있으면 사용자의 후속 질문이 무엇을 가리키는지 해석하는 데만 사용하세요.
        검색 근거에 없는 내용은 추측하지 말고, 근거가 부족하면 답변하기 어렵다고 말하세요.
        사용자가 다른 언어를 명시하지 않으면 한국어로 간결하게 답변하세요.
        답변 본문에는 근거 번호를 표시하지 마세요. 출처 목록은 시스템이 별도로 붙입니다.
        """;

    private final LlmProperties properties;

    public LlmPromptBuilder(LlmProperties properties) {
        this.properties = properties;
    }

    public LlmPrompt build(String question, List<RetrievedChunk> chunks) {
        return build(question, chunks, List.of());
    }

    public LlmPrompt build(String question, List<RetrievedChunk> chunks, List<ChatContextMessage> contextMessages) {
        StringBuilder input = new StringBuilder();
        appendContext(input, contextMessages);
        input.append("현재 질문:\n")
            .append(blankToDefault(question, "(빈 질문)"))
            .append("\n\n검색 근거:\n");

        List<RetrievedChunk> safeChunks = chunks == null ? List.of() : chunks;
        int limit = Math.min(properties.maxInputChunks(), safeChunks.size());
        if (limit == 0) {
            input.append("검색 근거 없음\n");
            return new LlmPrompt(INSTRUCTIONS, input.toString());
        }

        for (int index = 0; index < limit; index++) {
            RetrievedChunk chunk = safeChunks.get(index);
            appendChunk(input, index + 1, chunk);
        }

        return new LlmPrompt(INSTRUCTIONS, input.toString());
    }

    private void appendContext(StringBuilder input, List<ChatContextMessage> contextMessages) {
        List<ChatContextMessage> safeMessages = contextMessages == null ? List.of() : contextMessages;
        if (safeMessages.isEmpty()) {
            return;
        }

        input.append("이전 대화:\n");
        for (ChatContextMessage message : safeMessages) {
            String role = blankToDefault(message.role(), "UNKNOWN");
            String content = blankToDefault(message.content(), "");
            if (!content.isBlank()) {
                input.append(role)
                    .append(": ")
                    .append(content)
                    .append("\n");
            }
        }
        input.append("\n");
    }

    private void appendChunk(StringBuilder input, int number, RetrievedChunk chunk) {
        input.append("근거 ").append(number).append(": ")
            .append(blankToDefault(chunk.title(), "제목 없음"))
            .append("\n");

        String uri = blankToNull(chunk.uri());
        if (uri != null) {
            input.append("uri: ").append(uri).append("\n");
        }

        String sourceType = blankToNull(chunk.sourceType());
        if (sourceType != null) {
            input.append("source_type: ").append(sourceType).append("\n");
        }

        appendNotionHierarchy(input, chunk);

        input.append("content:\n")
            .append(trimToLimit(chunk.content()))
            .append("\n\n");
    }

    private void appendNotionHierarchy(StringBuilder input, RetrievedChunk chunk) {
        Map<String, Object> metadata = readMetadata(chunk.documentMetadataJson());
        List<String> notionPath = stringList(metadata.get("notionPath"));
        if (!notionPath.isEmpty()) {
            input.append("notion_path: ")
                .append(String.join(" > ", notionPath))
                .append("\n");
        }

        String notionDepth = metadataValue(metadata.get("notionDepth"));
        if (notionDepth != null) {
            input.append("notion_depth: ")
                .append(notionDepth)
                .append("\n");
        }

        String parentTitle = metadataValue(metadata.get("notionParentTitle"));
        if (parentTitle != null) {
            input.append("notion_parent: ")
                .append(parentTitle)
                .append("\n");
        }
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(metadataJson, METADATA_MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }

        return values.stream()
            .map(item -> item == null ? "" : item.toString().trim())
            .filter(item -> !item.isBlank())
            .toList();
    }

    private String metadataValue(Object value) {
        if (value == null) {
            return null;
        }

        String stringValue = value.toString().trim();
        return stringValue.isBlank() ? null : stringValue;
    }

    private String trimToLimit(String content) {
        String safeContent = blankToDefault(content, "");
        if (safeContent.length() <= properties.maxCharsPerChunk()) {
            return safeContent;
        }
        return safeContent.substring(0, properties.maxCharsPerChunk());
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
