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
            "\"file\": \"db/changelog/changes/005-cascade-chat-citations-on-chunk-delete.sql\""
        );
        assertThat(masterContent)
            .contains(
                """
                "file": "db/changelog/changes/001-initial-schema.sql",
                        "logicalFilePath": "db/changelog/db.changelog-master.sql"
                """.stripIndent().trim()
            )
            .contains(
                """
                "file": "db/changelog/changes/004-workspace-admin-schema.sql",
                        "logicalFilePath": "db/changelog/db.changelog-master.sql"
                """.stripIndent().trim()
            );

        Matcher includedFiles = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+)\"").matcher(masterContent);
        int includeCount = 0;
        while (includedFiles.find()) {
            includeCount++;
            assertThat(Files.exists(Path.of("src/main/resources").resolve(includedFiles.group(1)))).isTrue();
        }

        assertThat(includeCount).isGreaterThan(0);
    }
}
