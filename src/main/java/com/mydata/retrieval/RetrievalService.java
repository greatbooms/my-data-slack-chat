package com.mydata.retrieval;

import com.mydata.embeddings.EmbeddingClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RetrievalService {
    private static final int MAX_QUERY_TERMS = 8;
    private static final Pattern TERM_PATTERN = Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Set<String> STOP_TERMS = Set.of(
        "요약",
        "요약해줘",
        "정리",
        "정리해줘",
        "알려줘",
        "보여줘",
        "마지막",
        "최신",
        "최근",
        "내용",
        "상세"
    );

    private final EmbeddingClient embeddings;
    private final PgVectorSearchRepository searchRepository;

    public RetrievalService(EmbeddingClient embeddings, PgVectorSearchRepository searchRepository) {
        this.embeddings = embeddings;
        this.searchRepository = searchRepository;
    }

    public List<RetrievedChunk> retrieve(UUID workspaceId, List<String> principalKeys, String query, int limit) {
        return retrieve(workspaceId, principalKeys, query, query, limit);
    }

    public List<RetrievedChunk> retrieve(
        UUID workspaceId,
        List<String> principalKeys,
        String semanticQuery,
        String lexicalQuery,
        int limit
    ) {
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
            embeddings.embed(semanticQuery),
            queryTerms(lexicalQuery),
            limit
        );
    }

    private List<String> queryTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<String> terms = new ArrayList<>();
        Matcher matcher = TERM_PATTERN.matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find() && terms.size() < MAX_QUERY_TERMS) {
            String term = matcher.group().trim();
            if (term.length() < 2 || STOP_TERMS.contains(term) || terms.contains(term)) {
                continue;
            }
            terms.add(term);
        }
        if (query.contains("기업분석")) {
            addTerm(terms, "분석");
            addTerm(terms, "리포트");
        }
        return terms;
    }

    private void addTerm(List<String> terms, String term) {
        if (terms.size() < MAX_QUERY_TERMS && !terms.contains(term)) {
            terms.add(term);
        }
    }
}
