package com.mydata.chat;

import com.mydata.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPromptBuilderTest {
    @Test
    void buildsGroundedPromptWithLimitedAndTrimmedChunks() {
        LlmProperties properties = new LlmProperties(
            "openai",
            "gpt-test",
            2,
            12,
            300,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30)
        );
        LlmPromptBuilder builder = new LlmPromptBuilder(properties);

        LlmPrompt prompt = builder.build(
            "alpha 예산은?",
            List.of(
                chunk("Budget note", "abcdefghijklmnop", "local://one"),
                chunk("Roadmap", "abcdefghiJKLMNO", "local://two"),
                chunk("Ignored", "this should not be included", "local://three")
            )
        );

        assertThat(prompt.instructions())
            .contains("검색 근거")
            .contains("한국어")
            .doesNotContain("[1]");
        assertThat(prompt.input())
            .contains("질문:\nalpha 예산은?")
            .contains("근거 1: Budget note")
            .contains("uri: local://one")
            .contains("content:\nabcdefghijkl")
            .contains("근거 2: Roadmap")
            .contains("content:\nabcdefghiJKL")
            .doesNotContain("[1]")
            .doesNotContain("[2]")
            .doesNotContain("Ignored")
            .doesNotContain("mnop")
            .doesNotContain("MNO");
    }

    @Test
    void omitsBlankMetadataWithoutBreakingPromptFormat() {
        LlmProperties properties = new LlmProperties(
            "openai",
            "gpt-test",
            3,
            1200,
            300,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30)
        );
        LlmPromptBuilder builder = new LlmPromptBuilder(properties);

        LlmPrompt prompt = builder.build(
            "내용 알려줘",
            List.of(new RetrievedChunk(UUID.randomUUID(), "본문", null, " ", null, 0.1))
        );

        assertThat(prompt.input())
            .contains("근거 1: 제목 없음")
            .contains("content:\n본문")
            .doesNotContain("uri:")
            .doesNotContain("source_type:");
    }

    @Test
    void includesConversationContextForThreadFollowUpQuestions() {
        LlmProperties properties = new LlmProperties(
            "openai",
            "gpt-test",
            3,
            1200,
            300,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30)
        );
        LlmPromptBuilder builder = new LlmPromptBuilder(properties);

        LlmPrompt prompt = builder.build(
            "이거 뎁스별로 이야기해줘",
            List.of(chunk("20260630", "1뎁스 2뎁스 내용", "notion://page")),
            List.of(
                new ChatContextMessage("USER", "지금 노션 페이지 목록에 어떤게 있어?"),
                new ChatContextMessage("ASSISTANT", "목록은 20260630, 메모, meeting, 할일입니다.")
            )
        );

        assertThat(prompt.instructions())
            .contains("이전 대화");
        assertThat(prompt.input())
            .contains("이전 대화:")
            .contains("USER: 지금 노션 페이지 목록에 어떤게 있어?")
            .contains("ASSISTANT: 목록은 20260630, 메모, meeting, 할일입니다.")
            .contains("현재 질문:\n이거 뎁스별로 이야기해줘")
            .contains("근거 1: 20260630");
    }

    @Test
    void includesNotionHierarchyMetadataWhenAvailable() {
        LlmProperties properties = new LlmProperties(
            "openai",
            "gpt-test",
            3,
            1200,
            300,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30)
        );
        LlmPromptBuilder builder = new LlmPromptBuilder(properties);

        LlmPrompt prompt = builder.build(
            "이거 뎁스별로 이야기해줘",
            List.of(new RetrievedChunk(
                UUID.randomUUID(),
                "20260630 자산에 l1, l2, l3레벨로 표기",
                "20260630",
                "https://notion.so/20260630",
                "NOTION",
                0.1,
                """
                {"notionPath":["CMT","meeting","20260630"],"notionDepth":2,"notionParentTitle":"meeting"}
                """
            ))
        );

        assertThat(prompt.input())
            .contains("notion_path: CMT > meeting > 20260630")
            .contains("notion_depth: 2")
            .contains("notion_parent: meeting");
    }

    private RetrievedChunk chunk(String title, String content, String uri) {
        return new RetrievedChunk(UUID.randomUUID(), content, title, uri, "LOCAL_TEXT", 0.1);
    }
}
