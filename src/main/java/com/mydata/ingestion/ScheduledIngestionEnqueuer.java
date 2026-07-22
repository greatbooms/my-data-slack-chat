package com.mydata.ingestion;

import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class ScheduledIngestionEnqueuer {
    private static final Logger log = LoggerFactory.getLogger(ScheduledIngestionEnqueuer.class);

    private final DataSourceRepository dataSources;
    private final IngestionJobRepository ingestionJobs;
    private final IngestionCommandService ingestionCommands;
    private final boolean scheduleEnqueueEnabled;

    public ScheduledIngestionEnqueuer(
        DataSourceRepository dataSources,
        IngestionJobRepository ingestionJobs,
        IngestionCommandService ingestionCommands,
        @Value("${my-data.ingestion.schedule-enqueue-enabled:true}") boolean scheduleEnqueueEnabled
    ) {
        this.dataSources = dataSources;
        this.ingestionJobs = ingestionJobs;
        this.ingestionCommands = ingestionCommands;
        this.scheduleEnqueueEnabled = scheduleEnqueueEnabled;
    }

    @Scheduled(
        initialDelayString = "${my-data.ingestion.schedule-scan-initial-delay-ms:5000}",
        fixedDelayString = "${my-data.ingestion.schedule-scan-fixed-delay-ms:60000}"
    )
    public void scanAndEnqueue() {
        if (!scheduleEnqueueEnabled) {
            return;
        }
        enqueueDueScheduledJobs();
    }

    public int enqueueDueScheduledJobs() {
        int enqueuedJobCount = 0;
        // ponytail: 분 단위 스캔 + baseline(최근 job 생성 시각)으로 due 판정 — 초 단위 정밀 스케줄이 필요하면 Quartz로 교체
        for (DataSourceEntity dataSource : dataSources.findSchedulableActive()) {
            try {
                if (ingestionJobs.existsByDataSourceIdAndStatusIn(
                    dataSource.getId(),
                    List.of(IngestionJobStatus.PENDING, IngestionJobStatus.RUNNING)
                )) {
                    continue;
                }

                OffsetDateTime baseline = ingestionJobs
                    .findFirstByDataSourceIdOrderByCreatedAtDesc(dataSource.getId())
                    .map(IngestionJobEntity::getCreatedAt)
                    .orElse(dataSource.getCreatedAt());
                CronExpression cron = CronExpression.parse(dataSource.getSyncCron());
                OffsetDateTime next = cron.next(baseline);
                if (next == null || next.isAfter(OffsetDateTime.now())) {
                    continue;
                }

                ingestionCommands.requestScheduledSync(dataSource.getId());
                enqueuedJobCount++;
                log.info(
                    "스케줄 수집 job 생성: dataSource={}, cron={}",
                    dataSource.getId(),
                    dataSource.getSyncCron()
                );
            } catch (Exception exception) {
                log.warn("스케줄 수집 대상 평가 실패: dataSource={}", dataSource.getId(), exception);
            }
        }
        return enqueuedJobCount;
    }
}
