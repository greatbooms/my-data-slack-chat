package com.mydata.admin;

import com.mydata.ingestion.IngestionCommandService;
import com.mydata.ingestion.IngestionJobEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/admin/data-sources")
public class AdminDataSourceController {
    private final IngestionCommandService ingestionCommands;
    private final String adminToken;

    public AdminDataSourceController(
        IngestionCommandService ingestionCommands,
        @Value("${my-data.admin-token}") String adminToken
    ) {
        this.ingestionCommands = ingestionCommands;
        this.adminToken = adminToken;
    }

    @PostMapping("/{id}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncResponse requestManualSync(
        @PathVariable UUID id,
        @RequestHeader(name = "X-Admin-Token", required = false) String token,
        @RequestBody SyncRequest request
    ) {
        if (!adminToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin token");
        }

        IngestionJobEntity job = ingestionCommands.requestManualSync(id, request.requestedByUserId());
        return new SyncResponse(
            job.getId(),
            job.getDataSourceId(),
            job.getStatus().name(),
            job.getTriggerType().name()
        );
    }

    public record SyncRequest(UUID requestedByUserId) {
    }

    public record SyncResponse(UUID jobId, UUID dataSourceId, String status, String triggerType) {
    }
}
