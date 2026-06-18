package com.mydata.chat;

import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.ingestion.IngestionJobEntity;
import com.mydata.ingestion.IngestionJobRepository;
import com.mydata.ingestion.IngestionTriggerType;
import com.mydata.ingestion.IngestionWorker;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatApplicationServiceTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;
    @Autowired IngestionWorker worker;
    @Autowired ChatApplicationService chat;
    @Autowired ChatSessionRepository sessions;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatRetrievalCitationRepository citations;

    @Test
    void answersQuestionAndPersistsMessagesWithRetrievalCitations() {
        UserEntity owner = users.save(UserEntity.create("chat-owner@example.com", "Chat Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Chat workspace"));
        DataSourceEntity source = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        source.putConfig("externalId", "budget-note");
        source.putConfig("title", "Budget note");
        source.putConfig("content", "alpha project budget is 1000");
        source.putConfig("uri", "local://budget-note");
        source.putConfig("principalKey", PrincipalKeys.user(owner.getId()));
        source = dataSources.saveAndFlush(source);
        IngestionJobEntity job = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            workspace.getId(),
            source.getId(),
            IngestionTriggerType.MANUAL,
            owner.getId()
        ));
        worker.run(job.getId());

        ChatApplicationService.Answer answer = chat.answer(
            workspace.getId(),
            owner.getId(),
            "SLACK",
            "C123",
            "1710000000.000000",
            List.of(PrincipalKeys.user(owner.getId())),
            "alpha budget?"
        );

        assertThat(answer.content()).contains("Budget note");
        assertThat(answer.citations())
            .hasSize(1)
            .first()
            .satisfies(citation -> {
                assertThat(citation.title()).isEqualTo("Budget note");
                assertThat(citation.uri()).isEqualTo("local://budget-note");
                assertThat(citation.sourceType()).isEqualTo("LOCAL_TEXT");
                assertThat(citation.rank()).isEqualTo(1);
                assertThat(citation.score()).isNotNull();
            });
        List<ChatMessageEntity> persistedMessages = messages.findBySessionIdOrderByCreatedAt(answer.sessionId());
        assertThat(persistedMessages)
            .hasSize(2)
            .satisfiesExactly(
                userMessage -> {
                    assertThat(userMessage.getRole()).isEqualTo("USER");
                    assertThat(userMessage.getContent()).isEqualTo("alpha budget?");
                },
                assistantMessage -> {
                    assertThat(assistantMessage.getRole()).isEqualTo("ASSISTANT");
                    assertThat(assistantMessage.getContent()).isEqualTo(answer.content());
                }
            );
        assertThat(citations.findByChatMessageIdOrderByRank(persistedMessages.get(1).getId()))
            .hasSize(1)
            .first()
            .satisfies(citation -> {
                assertThat(citation.getRank()).isEqualTo(1);
                assertThat(citation.getScore()).isNotNull();
                assertThat(citation.getChunkId()).isEqualTo(answer.citations().getFirst().chunkId());
            });
    }

    @Test
    void rejectsMissingExternalThreadIdBeforePersistingMessages() {
        UserEntity owner = users.save(UserEntity.create("thread-required-owner@example.com", "Thread Required"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Thread required workspace"));
        long sessionCount = sessions.count();
        long messageCount = messages.count();

        assertThatThrownBy(() -> chat.answer(
            workspace.getId(),
            owner.getId(),
            "SLACK",
            "C123",
            null,
            List.of(PrincipalKeys.user(owner.getId())),
            "alpha budget?"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("externalThreadId");
        assertThatThrownBy(() -> chat.answer(
            workspace.getId(),
            owner.getId(),
            "SLACK",
            "C123",
            "   ",
            List.of(PrincipalKeys.user(owner.getId())),
            "alpha budget?"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("externalThreadId");

        assertThat(sessions.count()).isEqualTo(sessionCount);
        assertThat(messages.count()).isEqualTo(messageCount);
    }

    @Test
    void rejectsDuplicateChatSessionKeysAtDatabaseLevel() {
        UserEntity owner = users.save(UserEntity.create("duplicate-chat-owner@example.com", "Duplicate Chat Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Duplicate chat workspace"));
        sessions.saveAndFlush(ChatSessionEntity.create(
            workspace.getId(),
            "SLACK",
            "C123",
            "1710000000.000000",
            owner.getId()
        ));

        assertThatThrownBy(() -> sessions.saveAndFlush(ChatSessionEntity.create(
            workspace.getId(),
            "SLACK",
            "C123",
            "1710000000.000000",
            owner.getId()
        )))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
