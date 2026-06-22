package com.mydata.admin.graphql;

import com.mydata.admin.auth.AdminUserDetailsService;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.ingestion.IngestionJobRepository;
import com.mydata.ingestion.IngestionJobStatus;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class AdminGraphQlController {
    private final UserRepository users;
    private final DataSourceRepository dataSources;
    private final IngestionJobRepository ingestionJobs;

    public AdminGraphQlController(
        UserRepository users,
        DataSourceRepository dataSources,
        IngestionJobRepository ingestionJobs
    ) {
        this.users = users;
        this.dataSources = dataSources;
        this.ingestionJobs = ingestionJobs;
    }

    @QueryMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminViewerPayload viewer() {
        UserEntity currentAdmin = currentAdmin();
        return new AdminViewerPayload(
            currentAdmin.getId(),
            currentAdmin.getEmail(),
            currentAdmin.getDisplayName(),
            currentAdmin.getRole()
        );
    }

    @QueryMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardSummaryPayload dashboardSummary() {
        int userCount = users.findByDeletedAtIsNullOrderByCreatedAtDesc().size();
        int dataSourceCount = dataSources.findByDeletedAtIsNullOrderByCreatedAtDesc().size();
        int runningJobCount = (int) ingestionJobs.findAll().stream()
            .filter(job -> job.getStatus() == IngestionJobStatus.RUNNING)
            .count();

        return new AdminDashboardSummaryPayload(userCount, dataSourceCount, runningJobCount);
    }

    private UserEntity currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("관리자 인증 정보가 없습니다");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AdminUserDetailsService.AdminUserDetails adminUserDetails) {
            return users.findByIdAndDeletedAtIsNull(adminUserDetails.id())
                .orElseThrow(() -> new IllegalStateException("관리자 계정을 찾을 수 없습니다"));
        }

        return users.findByEmailAndDeletedAtIsNull(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("관리자 계정을 찾을 수 없습니다"));
    }
}
