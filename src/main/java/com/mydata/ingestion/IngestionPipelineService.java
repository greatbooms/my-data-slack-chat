package com.mydata.ingestion;

import com.mydata.auth.Permission;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.embeddings.DocumentEmbeddingRepository;
import com.mydata.embeddings.EmbeddingClient;
import com.mydata.documents.DocumentAclEntryEntity;
import com.mydata.documents.DocumentAclEntryRepository;
import com.mydata.documents.DocumentChunkEntity;
import com.mydata.documents.DocumentChunkRepository;
import com.mydata.documents.ExternalDocumentEntity;
import com.mydata.documents.ExternalDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IngestionPipelineService {
    private final ExternalDocumentRepository documents;
    private final DocumentAclEntryRepository aclEntries;
    private final DocumentChunkRepository chunks;
    private final Chunker chunker;
    private final EmbeddingClient embeddingClient;
    private final DocumentEmbeddingRepository embeddings;

    public IngestionPipelineService(
        ExternalDocumentRepository documents,
        DocumentAclEntryRepository aclEntries,
        DocumentChunkRepository chunks,
        Chunker chunker,
        EmbeddingClient embeddingClient,
        DocumentEmbeddingRepository embeddings
    ) {
        this.documents = documents;
        this.aclEntries = aclEntries;
        this.chunks = chunks;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.embeddings = embeddings;
    }

    @Transactional
    public void ingest(DataSourceEntity dataSource, RawExternalDocument rawDocument) {
        validateAclEntries(rawDocument.aclEntries());
        var existingDocument = documents.findByDataSourceIdAndExternalId(dataSource.getId(), rawDocument.externalId());
        if (existingDocument.isPresent() && isUnchanged(existingDocument.get(), rawDocument)) {
            backfillMissingEmbeddings(existingDocument.get());
            return;
        }

        ExternalDocumentEntity document = existingDocument
            .orElseGet(() -> ExternalDocumentEntity.create(
                dataSource.getWorkspaceId(),
                dataSource.getId(),
                rawDocument.externalId(),
                rawDocument.sourceType().name(),
                rawDocument.title(),
                rawDocument.uri(),
                rawDocument.contentHash()
            ));

        document.updateFromIngestion(
            rawDocument.sourceType().name(),
            rawDocument.title(),
            rawDocument.uri(),
            rawDocument.contentHash()
        );
        document = documents.saveAndFlush(document);

        replaceAclEntries(document, rawDocument.aclEntries());
        List<DocumentChunkEntity> savedChunks = replaceChunks(document, rawDocument.content().text());
        writeEmbeddings(savedChunks);
    }

    private void replaceAclEntries(ExternalDocumentEntity document, List<RawAclEntry> rawAclEntries) {
        aclEntries.deleteAll(aclEntries.findByDocumentId(document.getId()));
        aclEntries.flush();

        List<DocumentAclEntryEntity> replacements = new ArrayList<>();
        for (RawAclEntry rawAclEntry : rawAclEntries) {
            replacements.add(DocumentAclEntryEntity.read(
                document,
                rawAclEntry.principalKey(),
                normalizedSource(rawAclEntry),
                rawAclEntry.inherited()
            ));
        }
        aclEntries.saveAll(replacements);
        aclEntries.flush();
    }

    private void validateAclEntries(List<RawAclEntry> rawAclEntries) {
        for (RawAclEntry rawAclEntry : rawAclEntries) {
            if (rawAclEntry.principalKey() == null || rawAclEntry.principalKey().isBlank()) {
                throw new IllegalArgumentException("ACL principal key must not be blank");
            }
            if (!Permission.READ.name().equals(rawAclEntry.permission())) {
                throw new IllegalArgumentException("Unsupported ACL permission: " + rawAclEntry.permission());
            }
        }
    }

    private List<DocumentChunkEntity> replaceChunks(ExternalDocumentEntity document, String text) {
        chunks.deleteAll(chunks.findByDocumentIdOrderByChunkIndex(document.getId()));
        chunks.flush();

        List<DocumentChunkEntity> replacements = new ArrayList<>();
        List<Chunker.Chunk> rawChunks = chunker.chunk(text);
        for (int index = 0; index < rawChunks.size(); index++) {
            Chunker.Chunk rawChunk = rawChunks.get(index);
            replacements.add(DocumentChunkEntity.create(
                document,
                index,
                rawChunk.content(),
                rawChunk.tokenCount()
            ));
        }
        List<DocumentChunkEntity> savedChunks = chunks.saveAll(replacements);
        chunks.flush();
        return savedChunks;
    }

    private void backfillMissingEmbeddings(ExternalDocumentEntity document) {
        List<DocumentChunkEntity> existingChunks = chunks.findByDocumentIdOrderByChunkIndex(document.getId());
        List<DocumentChunkEntity> missingChunks = existingChunks.stream()
            .filter(chunk -> !embeddings.existsByChunkIdAndModel(chunk.getId(), embeddingClient.model()))
            .toList();
        writeEmbeddings(missingChunks);
    }

    private void writeEmbeddings(List<DocumentChunkEntity> chunks) {
        for (DocumentChunkEntity chunk : chunks) {
            embeddings.upsert(
                chunk.getId(),
                embeddingClient.model(),
                embeddingClient.embed(chunk.getContent())
            );
        }
    }

    private boolean isUnchanged(ExternalDocumentEntity document, RawExternalDocument rawDocument) {
        return Objects.equals(document.getContentHash(), rawDocument.contentHash())
            && Objects.equals(document.getTitle(), rawDocument.title())
            && Objects.equals(document.getUri(), rawDocument.uri())
            && existingAclKeys(document).equals(rawAclKeys(rawDocument.aclEntries()));
    }

    private Set<AclKey> existingAclKeys(ExternalDocumentEntity document) {
        return aclEntries.findByDocumentId(document.getId()).stream()
            .map(acl -> new AclKey(
                acl.getPrincipalKey(),
                acl.getPermission().name(),
                acl.isInherited(),
                acl.getSource()
            ))
            .collect(Collectors.toSet());
    }

    private Set<AclKey> rawAclKeys(List<RawAclEntry> rawAclEntries) {
        return rawAclEntries.stream()
            .map(rawAclEntry -> new AclKey(
                rawAclEntry.principalKey(),
                rawAclEntry.permission(),
                rawAclEntry.inherited(),
                normalizedSource(rawAclEntry)
            ))
            .collect(Collectors.toSet());
    }

    private String normalizedSource(RawAclEntry rawAclEntry) {
        return rawAclEntry.source() == null || rawAclEntry.source().isBlank() ? "MANUAL" : rawAclEntry.source();
    }

    private record AclKey(String principalKey, String permission, boolean inherited, String source) {
    }
}
