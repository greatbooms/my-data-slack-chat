package com.mydata.embeddings;

import com.mydata.documents.DocumentChunkEntity;
import com.mydata.documents.DocumentChunkRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class EmbeddingMigrationService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingMigrationService.class);

    private final DocumentChunkRepository chunks;
    private final DocumentEmbeddingRepository embeddings;
    private final EmbeddingClient embeddingClient;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().daemon().name("embedding-reembed").factory()
    );

    public EmbeddingMigrationService(
        DocumentChunkRepository chunks,
        DocumentEmbeddingRepository embeddings,
        EmbeddingClient embeddingClient
    ) {
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.embeddingClient = embeddingClient;
    }

    public EmbeddingCoverage coverage(UUID dataSourceId) {
        String model = embeddingClient.model();
        EmbeddingCoverageProjection projection = chunks.coverageByDataSource(dataSourceId, model);
        return new EmbeddingCoverage(
            model,
            projection == null ? 0 : projection.getTotalChunks(),
            projection == null ? 0 : projection.getCoveredChunks()
        );
    }

    public boolean isRunning(UUID dataSourceId) {
        return running.contains(dataSourceId);
    }

    public boolean startReembed(UUID dataSourceId) {
        if (!running.add(dataSourceId)) {
            return false;
        }
        executor.submit(() -> reembedLoop(dataSourceId));
        return true;
    }

    public void reembedLoop(UUID dataSourceId) {
        String model = embeddingClient.model();
        try {
            for (DocumentChunkEntity chunk : chunks.findByDataSource(dataSourceId)) {
                try {
                    if (!embeddings.existsByChunkIdAndModel(chunk.getId(), model)) {
                        embeddings.upsert(chunk.getId(), model, embeddingClient.embed(chunk.getContent()));
                    }
                } catch (RuntimeException exception) {
                    log.warn("청크 재임베딩에 실패해 건너뜁니다: 청크 ID={}", chunk.getId(), exception);
                }
            }
        } finally {
            running.remove(dataSourceId);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public record EmbeddingCoverage(String model, long totalChunks, long coveredChunks) {
    }
}
