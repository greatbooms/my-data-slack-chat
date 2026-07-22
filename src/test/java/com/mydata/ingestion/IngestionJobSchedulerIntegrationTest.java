package com.mydata.ingestion;

import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class IngestionJobSchedulerIntegrationTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;
    @Autowired IngestionJobScheduler scheduler;

    @Test
    void recoveryFailsRunningJobAndUnblocksPendingJob() {
        // 공유 컨테이너의 다른 테스트가 남긴 전역 job에 영향받지 않도록
        // 검증은 이 fixture의 고유 데이터소스로 한정한다.
        Fixture fixture = fixture("recovery");
        IngestionJobEntity runningJob = pendingJob(fixture);
        runningJob.markRunning();
        ingestionJobs.saveAndFlush(runningJob);
        IngestionJobEntity pendingJob = pendingJob(fixture);

        // RUNNING job이 남아 있는 동안에는 같은 데이터소스의 PENDING이 차단된다.
        assertThat(ingestionJobs.markPendingJobRunningIfDataSourceIdle(pendingJob.getId())).isZero();

        scheduler.recoverStuckRunningJobs();

        assertThat(ingestionJobs.findById(runningJob.getId()).orElseThrow())
            .satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
                assertThat(job.getErrorMessage())
                    .isEqualTo("서버 재시작으로 실행이 중단된 job입니다");
                assertThat(job.getFinishedAt()).isNotNull();
            });
        // 복구 후에는 데이터소스가 idle이 되어 PENDING을 claim할 수 있다.
        assertThat(ingestionJobs.markPendingJobRunningIfDataSourceIdle(pendingJob.getId())).isEqualTo(1);
    }

    @Test
    void recoveryLeavesNonRunningJobsUntouched() {
        Fixture fixture = fixture("non-running");
        IngestionJobEntity pendingJob = pendingJob(fixture);
        IngestionJobEntity succeededJob = pendingJob(fixture);
        succeededJob.markRunning();
        succeededJob.markSucceeded();
        ingestionJobs.saveAndFlush(succeededJob);
        IngestionJobEntity failedJob = pendingJob(fixture);
        failedJob.markRunning();
        failedJob.markFailed("기존 실패");
        ingestionJobs.saveAndFlush(failedJob);

        scheduler.recoverStuckRunningJobs();

        assertThat(ingestionJobs.findById(pendingJob.getId()).orElseThrow())
            .satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.PENDING);
                assertThat(job.getErrorMessage()).isNull();
            });
        assertThat(ingestionJobs.findById(succeededJob.getId()).orElseThrow())
            .satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.SUCCEEDED);
                assertThat(job.getErrorMessage()).isNull();
            });
        assertThat(ingestionJobs.findById(failedJob.getId()).orElseThrow())
            .satisfies(job -> {
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.FAILED);
                assertThat(job.getErrorMessage()).isEqualTo("기존 실패");
            });
    }

    private Fixture fixture(String name) {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create(
            "scheduler-" + suffix + "@example.com", "Scheduler Owner"
        ));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), name));
        DataSourceEntity dataSource = dataSources.saveAndFlush(DataSourceEntity.create(
            workspace.getId(), DataSourceType.LOCAL_TEXT, name, DataSourceStatus.ACTIVE, SyncMode.MANUAL
        ));
        return new Fixture(owner, workspace, dataSource);
    }

    private IngestionJobEntity pendingJob(Fixture fixture) {
        return ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            fixture.workspace().getId(),
            fixture.dataSource().getId(),
            IngestionTriggerType.MANUAL,
            fixture.owner().getId()
        ));
    }

    private record Fixture(
        UserEntity owner,
        WorkspaceEntity workspace,
        DataSourceEntity dataSource
    ) {
    }
}
