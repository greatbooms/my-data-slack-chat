package com.mydata.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LiquibaseChangelogStructureTest {
    @Test
    void masterChangelogOnlyIncludesVersionedChangeFiles() throws Exception {
        Path changelogDirectory = Path.of("src/main/resources/db/changelog");
        Path master = changelogDirectory.resolve("db.changelog-master.json");

        String masterContent = Files.readString(master);

        assertThat(masterContent)
            .startsWith("{")
            .contains("\"databaseChangeLog\"")
            .doesNotContain("\"changeSet\"");
        assertThat(masterContent).contains(
            "\"file\": \"db/changelog/changes/001-initial-schema.sql\"",
            "\"file\": \"db/changelog/changes/002-unique-chat-session-external-thread.sql\"",
            "\"file\": \"db/changelog/changes/003-admin-console-schema.sql\"",
            "\"file\": \"db/changelog/changes/004-workspace-admin-schema.sql\"",
            "\"file\": \"db/changelog/changes/005-cascade-chat-citations-on-chunk-delete.sql\"",
            "\"file\": \"db/changelog/changes/006-ingestion-job-item-indexes.sql\"",
            "\"file\": \"db/changelog/changes/007-full-snapshot-reconciliation-indexes.sql\""
        );

        Matcher includedFiles = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+)\"").matcher(masterContent);
        int includeCount = 0;
        while (includedFiles.find()) {
            includeCount++;
            assertThat(Files.exists(Path.of("src/main/resources").resolve(includedFiles.group(1)))).isTrue();
        }

        assertThat(includeCount).isEqualTo(7);

        String reconciliationMigration = Files.readString(
            changelogDirectory.resolve("changes/007-full-snapshot-reconciliation-indexes.sql")
        );
        assertThat(reconciliationMigration)
            .contains("--preconditions onFail:HALT onError:HALT")
            .contains("HAVING count(*) > 1")
            .contains("idx_ingestion_job_items_job_succeeded_document")
            .contains("uq_ingestion_jobs_running_data_source")
            .doesNotContain("CREATE UNIQUE INDEX IF NOT EXISTS");
    }
}
