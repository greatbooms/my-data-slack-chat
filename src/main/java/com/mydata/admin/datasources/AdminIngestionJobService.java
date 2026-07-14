package com.mydata.admin.datasources;

import com.mydata.datasources.DataSourceRepository;
import com.mydata.ingestion.IngestionJobEntity;
import com.mydata.ingestion.IngestionJobItemCountProjection;
import com.mydata.ingestion.IngestionJobItemEntity;
import com.mydata.ingestion.IngestionJobItemRepository;
import com.mydata.ingestion.IngestionJobItemStatus;
import com.mydata.ingestion.IngestionJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminIngestionJobService {
    private static final int DEFAULT_JOB_LIMIT = 20;
    private static final int MAX_JOB_LIMIT = 100;
    private static final int DEFAULT_ITEM_LIMIT = 50;
    private static final int MAX_ITEM_LIMIT = 100;

    private final DataSourceRepository dataSources;
    private final IngestionJobRepository ingestionJobs;
    private final IngestionJobItemRepository jobItems;

    public AdminIngestionJobService(
        DataSourceRepository dataSources,
        IngestionJobRepository ingestionJobs,
        IngestionJobItemRepository jobItems
    ) {
        this.dataSources = dataSources;
        this.ingestionJobs = ingestionJobs;
        this.jobItems = jobItems;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminIngestionJobPayload> listJobs(String dataSourceId, Integer first) {
        UUID sourceId = parseId(dataSourceId, "dataSourceId");
        dataSources.findActiveById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("데이터소스를 찾을 수 없습니다"));
        int limit = first == null || first < 1
            ? DEFAULT_JOB_LIMIT
            : Math.min(first, MAX_JOB_LIMIT);
        List<IngestionJobEntity> jobs = ingestionJobs
            .findByDataSourceIdOrderByCreatedAtDesc(sourceId)
            .stream()
            .limit(limit)
            .toList();
        if (jobs.isEmpty()) {
            return List.of();
        }

        Map<UUID, IngestionJobItemCountProjection> counts = jobItems
            .summarizeByJobIdIn(jobs.stream().map(IngestionJobEntity::getId).toList())
            .stream()
            .collect(Collectors.toMap(
                IngestionJobItemCountProjection::getJobId,
                Function.identity()
            ));
        return jobs.stream()
            .map(job -> {
                IngestionJobItemCountProjection count = counts.get(job.getId());
                return AdminIngestionJobPayload.from(
                    job,
                    count == null ? 0 : count.getSucceededItemCount(),
                    count == null ? 0 : count.getFailedItemCount()
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminIngestionJobItemPagePayload listItems(
        String jobId,
        IngestionJobItemStatus status,
        Integer first,
        String after
    ) {
        IngestionJobEntity job = ingestionJobs.findById(parseId(jobId, "jobId"))
            .orElseThrow(() -> new IllegalArgumentException("수집 job을 찾을 수 없습니다"));
        dataSources.findActiveById(job.getDataSourceId())
            .filter(dataSource -> dataSource.getWorkspaceId().equals(job.getWorkspaceId()))
            .orElseThrow(() -> new IllegalArgumentException("수집 job을 찾을 수 없습니다"));
        int limit = normalizeItemLimit(first);
        ItemCursor cursor = decodeCursor(after);
        List<IngestionJobItemEntity> fetched = jobItems.findAdminPage(
            job.getId(),
            status,
            cursor == null ? null : cursor.processedAt(),
            cursor == null ? null : cursor.id(),
            PageRequest.of(0, limit + 1)
        );
        boolean hasNextPage = fetched.size() > limit;
        List<IngestionJobItemEntity> page = fetched.stream().limit(limit).toList();
        String endCursor = hasNextPage && !page.isEmpty()
            ? encodeCursor(page.get(page.size() - 1))
            : null;
        return new AdminIngestionJobItemPagePayload(
            page.stream().map(AdminIngestionJobItemPayload::from).toList(),
            hasNextPage,
            endCursor
        );
    }

    private int normalizeItemLimit(Integer first) {
        int limit = first == null ? DEFAULT_ITEM_LIMIT : first;
        if (limit < 1 || limit > MAX_ITEM_LIMIT) {
            throw new IllegalArgumentException("first는 1 이상 100 이하여야 합니다");
        }
        return limit;
    }

    private ItemCursor decodeCursor(String after) {
        if (after == null || after.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                Base64.getUrlDecoder().decode(after),
                StandardCharsets.UTF_8
            );
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new ItemCursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException invalidCursor) {
            throw new IllegalArgumentException("after cursor 형식이 올바르지 않습니다");
        }
    }

    private String encodeCursor(IngestionJobItemEntity item) {
        String value = item.getProcessedAt() + "|" + item.getId();
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private UUID parseId(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException invalidId) {
            throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다");
        }
    }

    private record ItemCursor(OffsetDateTime processedAt, UUID id) {
    }
}
