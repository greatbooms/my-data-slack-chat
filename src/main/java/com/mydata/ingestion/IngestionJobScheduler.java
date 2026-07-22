package com.mydata.ingestion;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Component
public class IngestionJobScheduler {
    private static final Logger log = LoggerFactory.getLogger(IngestionJobScheduler.class);

    private final IngestionJobRepository ingestionJobs;
    private final IngestionWorker worker;
    private final TransactionTemplate transactions;
    private final boolean schedulerEnabled;

    public IngestionJobScheduler(
        IngestionJobRepository ingestionJobs,
        IngestionWorker worker,
        TransactionTemplate transactions,
        @Value("${my-data.ingestion.scheduler-enabled:true}") boolean schedulerEnabled
    ) {
        this.ingestionJobs = ingestionJobs;
        this.worker = worker;
        this.transactions = transactions;
        this.schedulerEnabled = schedulerEnabled;
    }

    @PostConstruct
    public void recoverStuckRunningJobs() {
        // ponytail: 단일 인스턴스 전제의 시작 시 일괄 정리 — 멀티 인스턴스로 가면 heartbeat/lease 방식으로 교체
        transactions.executeWithoutResult(status -> {
            int recoveredJobCount = ingestionJobs.failStuckRunningJobs(
                "서버 재시작으로 실행이 중단된 job입니다"
            );
            if (recoveredJobCount > 0) {
                log.warn("중단된 RUNNING 수집 job {}개를 FAILED로 정리했습니다", recoveredJobCount);
            }
        });
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
            .findTop10RunnablePendingJobs();
        if (pendingJobs.isEmpty()) {
            return 0;
        }

        log.info("PENDING 수집 job {}개 처리 시작", pendingJobs.size());
        pendingJobs.forEach(job -> worker.run(job.getId()));
        return pendingJobs.size();
    }
}
