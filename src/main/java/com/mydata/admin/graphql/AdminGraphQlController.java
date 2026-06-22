package com.mydata.admin.graphql;

import com.mydata.admin.auth.AdminUserDetailsService;
import com.mydata.admin.datasources.AdminDataSourceInputs.CreateDataSourceInput;
import com.mydata.admin.datasources.AdminDataSourceInputs.UpdateDataSourceInput;
import com.mydata.admin.datasources.AdminDataSourcePagePayload;
import com.mydata.admin.datasources.AdminDataSourcePayload;
import com.mydata.admin.datasources.AdminDataSourceService;
import com.mydata.admin.datasources.AdminIngestionJobPayload;
import com.mydata.admin.users.AdminUserInputs.CreateUserInput;
import com.mydata.admin.users.AdminUserInputs.ResetUserPasswordInput;
import com.mydata.admin.users.AdminUserInputs.UpdateUserInput;
import com.mydata.admin.users.AdminUserPagePayload;
import com.mydata.admin.users.AdminUserPayload;
import com.mydata.admin.users.AdminUserService;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.ingestion.IngestionJobRepository;
import com.mydata.ingestion.IngestionJobStatus;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Controller
public class AdminGraphQlController {
    private final UserRepository users;
    private final DataSourceRepository dataSources;
    private final IngestionJobRepository ingestionJobs;
    private final AdminUserService adminUsers;
    private final AdminDataSourceService adminDataSources;

    public AdminGraphQlController(
        UserRepository users,
        DataSourceRepository dataSources,
        IngestionJobRepository ingestionJobs,
        AdminUserService adminUsers,
        AdminDataSourceService adminDataSources
    ) {
        this.users = users;
        this.dataSources = dataSources;
        this.ingestionJobs = ingestionJobs;
        this.adminUsers = adminUsers;
        this.adminDataSources = adminDataSources;
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

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPagePayload users() {
        return adminUsers.listUsers();
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload user(@Argument String id) {
        return adminUsers.findUser(id);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload createUser(@Argument CreateUserInput input) {
        return adminUsers.createUser(input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload updateUser(@Argument String id, @Argument UpdateUserInput input) {
        return adminUsers.updateUser(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload disableUser(@Argument String id) {
        return adminUsers.disableUser(id);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload softDeleteUser(@Argument String id) {
        return adminUsers.softDeleteUser(id);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload restoreUser(@Argument String id) {
        return adminUsers.restoreUser(id);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserPayload resetUserPassword(
        @Argument String id,
        @Argument ResetUserPasswordInput input
    ) {
        return adminUsers.resetUserPassword(id, input);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePagePayload dataSources() {
        return adminDataSources.listDataSources();
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePayload dataSource(@Argument String id) {
        return adminDataSources.findDataSource(id);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminIngestionJobPayload> ingestionJobs(
        @Argument String dataSourceId,
        @Argument Integer first
    ) {
        return adminDataSources.ingestionJobs(dataSourceId, first);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePayload createDataSource(@Argument CreateDataSourceInput input) {
        return adminDataSources.createDataSource(input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePayload updateDataSource(
        @Argument String id,
        @Argument UpdateDataSourceInput input
    ) {
        return adminDataSources.updateDataSource(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDataSourcePayload softDeleteDataSource(@Argument String id) {
        return adminDataSources.softDeleteDataSource(id);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminIngestionJobPayload requestDataSourceSync(@Argument String id) {
        return adminDataSources.requestDataSourceSync(id, currentAdmin().getId());
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
