--liquibase formatted sql

--changeset eric:005-cascade-chat-citations-on-chunk-delete
ALTER TABLE chat_retrieval_citations
    DROP CONSTRAINT IF EXISTS chat_retrieval_citations_chunk_id_fkey;

ALTER TABLE chat_retrieval_citations
    ADD CONSTRAINT chat_retrieval_citations_chunk_id_fkey
    FOREIGN KEY (chunk_id) REFERENCES document_chunks(id) ON DELETE CASCADE;
