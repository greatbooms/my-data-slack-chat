package com.mydata.retrieval;

import com.mydata.embeddings.EmbeddingClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RetrievalService {
    private final EmbeddingClient embeddings;
    private final PgVectorSearchRepository searchRepository;

    public RetrievalService(EmbeddingClient embeddings, PgVectorSearchRepository searchRepository) {
        this.embeddings = embeddings;
        this.searchRepository = searchRepository;
    }

    public List<RetrievedChunk> retrieve(UUID workspaceId, List<String> principalKeys, String query, int limit) {
        if (principalKeys == null || principalKeys.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> effectivePrincipalKeys = principalKeys.stream()
            .filter(principalKey -> principalKey != null && !principalKey.isBlank())
            .toList();
        if (effectivePrincipalKeys.isEmpty()) {
            return List.of();
        }
        return searchRepository.search(
            workspaceId,
            effectivePrincipalKeys,
            embeddings.model(),
            embeddings.embed(query),
            limit
        );
    }
}
