package com.mydata.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngestionJobScheduler {
    private static final Logger log = LoggerFactory.getLogger(IngestionJobScheduler.class);

    private final IngestionJobRepository ingestionJobs;
    private final IngestionWorker worker;
    private final boolean schedulerEnabled;

    public IngestionJobScheduler(
        IngestionJobRepository ingestionJobs,
        IngestionWorker worker,
        @Value("${my-data.ingestion.scheduler-enabled:true}") boolean schedulerEnabled
    ) {
        this.ingestionJobs = ingestionJobs;
        this.worker = worker;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(
        initialDelayString = "${my-data.ingestion.scheduler-initial-delay-ms:1000}",
        fixedDelayString = "${my-data.ingestion.scheduler-fixed-delay-ms:2000}"
    )
    public void runPendingJobs() {
        if (!schedulerEnabled) {
            return;
        }
        runPendingJobsNow();
    }

    public int runPendingJobsNow() {
        List<IngestionJobEntity> pendingJobs = ingestionJobs
            .findTop10ByStatusOrderByCreatedAtAsc(IngestionJobStatus.PENDING);
        if (pendingJobs.isEmpty()) {
            return 0;
        }

        log.info("PENDING 수집 job {}개 처리 시작", pendingJobs.size());
        pendingJobs.forEach(job -> worker.run(job.getId()));
        return pendingJobs.size();
    }
}
