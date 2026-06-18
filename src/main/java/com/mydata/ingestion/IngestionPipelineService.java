package com.mydata.ingestion;

import com.mydata.auth.Permission;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.documents.DocumentAclEntryEntity;
import com.mydata.documents.DocumentAclEntryRepository;
import com.mydata.documents.DocumentChunkEntity;
import com.mydata.documents.DocumentChunkRepository;
import com.mydata.documents.ExternalDocumentEntity;
import com.mydata.documents.ExternalDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionPipelineService {
    private final ExternalDocumentRepository documents;
    private final DocumentAclEntryRepository aclEntries;
    private final DocumentChunkRepository chunks;
    private final Chunker chunker;

    public IngestionPipelineService(
        ExternalDocumentRepository documents,
        DocumentAclEntryRepository aclEntries,
        DocumentChunkRepository chunks,
        Chunker chunker
    ) {
        this.documents = documents;
        this.aclEntries = aclEntries;
        this.chunks = chunks;
        this.chunker = chunker;
    }

    public void ingest(DataSourceEntity dataSource, RawExternalDocument rawDocument) {
        var existingDocument = documents.findByDataSourceIdAndExternalId(dataSource.getId(), rawDocument.externalId());
        if (existingDocument.isPresent() && rawDocument.contentHash().equals(existingDocument.get().getContentHash())) {
            return;
        }

        ExternalDocumentEntity document = existingDocument
            .orElseGet(() -> ExternalDocumentEntity.create(
                dataSource.getWorkspaceId(),
                dataSource.getId(),
                rawDocument.externalId(),
                rawDocument.sourceType().name(),
                rawDocument.title(),
                rawDocument.contentHash()
            ));

        document.updateFromIngestion(
            rawDocument.sourceType().name(),
            rawDocument.title(),
            rawDocument.contentHash()
        );
        document = documents.saveAndFlush(document);

        replaceAclEntries(document, rawDocument.aclEntries());
        replaceChunks(document, rawDocument.content().text());
    }

    private void replaceAclEntries(ExternalDocumentEntity document, List<RawAclEntry> rawAclEntries) {
        aclEntries.deleteAll(aclEntries.findByDocumentId(document.getId()));
        aclEntries.flush();

        List<DocumentAclEntryEntity> replacements = new ArrayList<>();
        for (RawAclEntry rawAclEntry : rawAclEntries) {
            if (!Permission.READ.name().equals(rawAclEntry.permission())) {
                throw new IllegalArgumentException("Unsupported ACL permission: " + rawAclEntry.permission());
            }
            replacements.add(DocumentAclEntryEntity.read(
                document,
                rawAclEntry.principalKey(),
                "MANUAL",
                rawAclEntry.inherited()
            ));
        }
        aclEntries.saveAll(replacements);
        aclEntries.flush();
    }

    private void replaceChunks(ExternalDocumentEntity document, String text) {
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
        chunks.saveAll(replacements);
        chunks.flush();
    }
}
