--liquibase formatted sql

--changeset eric:002-unique-chat-session-external-thread
CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_sessions_unique_external_thread
    ON chat_sessions (
        workspace_id,
        channel_type,
        COALESCE(external_channel_id, ''),
        COALESCE(external_thread_id, '')
    );
