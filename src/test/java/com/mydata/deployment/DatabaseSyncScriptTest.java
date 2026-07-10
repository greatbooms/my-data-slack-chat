package com.mydata.deployment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseSyncScriptTest {
    @TempDir
    Path tempDir;

    @Test
    void requiresExplicitYesBeforeOverwritingTargetDatabase() throws Exception {
        Path sourceEnv = writeEnv("source.env", """
            DATABASE_URL=jdbc:postgresql://prod.example:15432/my_data?currentSchema=public
            DATABASE_USERNAME=postgres
            DATABASE_PASSWORD=source-password
            """);
        Path targetEnv = writeEnv("target.env", """
            DATABASE_URL=jdbc:postgresql://localhost:5433/my_data?currentSchema=public
            DATABASE_USERNAME=my_data
            DATABASE_PASSWORD=target-password
            """);

        ProcessResult result = runScript(List.of(
            "--source-env", sourceEnv.toString(),
            "--target-env", targetEnv.toString()
        ), Map.of());

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output())
            .contains("Refusing to overwrite")
            .contains("--yes")
            .doesNotContain("source-password")
            .doesNotContain("target-password");
    }

    @Test
    void refusesNonLocalTargetDatabaseUnlessExplicitlyAllowed() throws Exception {
        Path sourceEnv = writeEnv("source.env", """
            DATABASE_URL=jdbc:postgresql://prod.example:15432/my_data?currentSchema=public
            DATABASE_USERNAME=postgres
            DATABASE_PASSWORD=source-password
            """);
        Path targetEnv = writeEnv("target.env", """
            DATABASE_URL=jdbc:postgresql://10.0.0.5:5432/my_data?currentSchema=public
            DATABASE_USERNAME=my_data
            DATABASE_PASSWORD=target-password
            """);

        ProcessResult result = runScript(List.of(
            "--yes",
            "--source-env", sourceEnv.toString(),
            "--target-env", targetEnv.toString()
        ), Map.of());

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output())
            .contains("Target DB host is not local")
            .contains("--allow-non-local-target")
            .doesNotContain("source-password")
            .doesNotContain("target-password");
    }

    @Test
    void dumpsProductionSchemaResetsLocalSchemaAndRestoresIntoTarget() throws Exception {
        Path sourceEnv = writeEnv("source.env", """
            DATABASE_URL=jdbc:postgresql://prod.example:15432/my_data?currentSchema=public
            DATABASE_USERNAME=postgres
            DATABASE_PASSWORD=source-password
            """);
        Path targetEnv = writeEnv("target.env", """
            DATABASE_URL=jdbc:postgresql://localhost:5433/my_data?currentSchema=public
            DATABASE_USERNAME=my_data
            DATABASE_PASSWORD=target-password
            """);
        Path logFile = tempDir.resolve("fake-pg.log");
        Path fakePgDump = writeFakePgBinary("pg_dump");
        Path fakePsql = writeFakePgBinary("psql");
        Path fakePgRestore = writeFakePgBinary("pg_restore");

        ProcessResult result = runScript(List.of(
            "--yes",
            "--source-env", sourceEnv.toString(),
            "--target-env", targetEnv.toString()
        ), Map.of(
            "PG_DUMP_BIN", fakePgDump.toString(),
            "PSQL_BIN", fakePsql.toString(),
            "PG_RESTORE_BIN", fakePgRestore.toString(),
            "SYNC_TEST_LOG", logFile.toString()
        ));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
            .contains("Source DB: prod.example:15432/my_data schema=public user=postgres")
            .contains("Target DB: localhost:5433/my_data schema=public user=my_data")
            .contains("Sync complete")
            .doesNotContain("source-password")
            .doesNotContain("target-password");

        String log = Files.readString(logFile);
        assertThat(log)
            .contains("pg_dump|PGHOST=prod.example|PGPORT=15432|PGDATABASE=my_data|PGUSER=postgres|PGPASSWORD=source-password")
            .contains("args=--format=custom --no-owner --no-acl --schema=public --file=")
            .contains("psql|PGHOST=localhost|PGPORT=5433|PGDATABASE=my_data|PGUSER=my_data|PGPASSWORD=target-password")
            .contains("args=--set=ON_ERROR_STOP=1 --dbname=my_data --command=DROP SCHEMA IF EXISTS \"public\" CASCADE; CREATE SCHEMA \"public\";")
            .contains("pg_restore|PGHOST=localhost|PGPORT=5433|PGDATABASE=my_data|PGUSER=my_data|PGPASSWORD=target-password")
            .contains("args=--clean --if-exists --single-transaction --no-owner --no-acl --dbname=my_data ");
    }

    private Path writeEnv(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    private Path writeFakePgBinary(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, """
            #!/usr/bin/env sh
            set -eu
            printf '%s|PGHOST=%s|PGPORT=%s|PGDATABASE=%s|PGUSER=%s|PGPASSWORD=%s|args=%s\\n' "$(basename "$0")" "${PGHOST:-}" "${PGPORT:-}" "${PGDATABASE:-}" "${PGUSER:-}" "${PGPASSWORD:-}" "$*" >> "$SYNC_TEST_LOG"
            for arg in "$@"; do
              case "$arg" in
                --file=*) : > "${arg#--file=}" ;;
              esac
            done
            """);
        file.toFile().setExecutable(true);
        return file;
    }

    private ProcessResult runScript(List<String> args, Map<String, String> environment) throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath();
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(buildCommand(projectRoot, args));
        builder.directory(projectRoot.toFile());
        builder.environment().putAll(environment);
        builder.redirectErrorStream(true);

        Process process = builder.start();
        boolean finished = process.waitFor(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        assertThat(finished).as("script process should finish").isTrue();
        return new ProcessResult(process.exitValue(), new String(process.getInputStream().readAllBytes()));
    }

    private static List<String> buildCommand(Path projectRoot, List<String> args) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("sh");
        command.add(projectRoot.resolve("scripts/dev/sync-db-from-prod-env.sh").toString());
        command.addAll(args);
        return command;
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
