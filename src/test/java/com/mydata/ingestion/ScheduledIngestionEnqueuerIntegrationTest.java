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
class ScheduledIngestionEnqueuerIntegrationTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository ingestionJobs;
    @Autowired ScheduledIngestionEnqueuer enqueuer;

    @Test
    void createsScheduledPendingJobWhenCronIsDue() throws InterruptedException {
        DataSourceEntity dataSource = fixture(SyncMode.SCHEDULED, "* * * * * *");
        Thread.sleep(1100);

        assertThat(enqueuer.enqueueDueScheduledJobs()).isEqualTo(1);
        assertThat(ingestionJobs.findByDataSourceIdOrderByCreatedAtDesc(dataSource.getId()))
            .singleElement()
            .satisfies(job -> {
                assertThat(job.getTriggerType()).isEqualTo(IngestionTriggerType.SCHEDULED);
                assertThat(job.getStatus()).isEqualTo(IngestionJobStatus.PENDING);
                assertThat(job.getRequestedByUserId()).isNull();
            });
    }

    @Test
    void doesNotCreateJobWhenCronIsNotDue() {
        DataSourceEntity dataSource = fixture(SyncMode.SCHEDULED, "0 0 0 1 1 *");

        assertThat(enqueuer.enqueueDueScheduledJobs()).isZero();
        assertThat(ingestionJobs.findByDataSourceIdOrderByCreatedAtDesc(dataSource.getId())).isEmpty();
    }

    @Test
    void doesNotCreateJobWhenActiveJobExists() {
        DataSourceEntity dataSource = fixture(SyncMode.SCHEDULED, "* * * * * *");
        ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
            dataSource.getWorkspaceId(),
            dataSource.getId(),
            IngestionTriggerType.MANUAL,
            null
        ));
        assertThat(enqueuer.enqueueDueScheduledJobs()).isZero();
        assertThat(ingestionJobs.findByDataSourceIdOrderByCreatedAtDesc(dataSource.getId())).hasSize(1);
    }

    @Test
    void doesNotCreateJobForManualDataSource() {
        DataSourceEntity dataSource = fixture(SyncMode.MANUAL, "* * * * * *");

        assertThat(enqueuer.enqueueDueScheduledJobs()).isZero();
        assertThat(ingestionJobs.findByDataSourceIdOrderByCreatedAtDesc(dataSource.getId())).isEmpty();
    }

    private DataSourceEntity fixture(SyncMode syncMode, String syncCron) {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = users.save(UserEntity.create("schedule-" + suffix + "@example.com", "Schedule Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Scheduled workspace"));
        DataSourceEntity dataSource = DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Scheduled source",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(owner.getId());
        dataSource.changeSyncMode(syncMode);
        dataSource.changeSyncCron(syncCron);
        return dataSources.saveAndFlush(dataSource);
    }
}
