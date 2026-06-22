package com.mydata.admin;

import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.DataSourceVisibility;
import com.mydata.datasources.SyncMode;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.users.UserStatus;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDomainModelTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;

    @Test
    void restoresSoftDeletedUserAndHidesDeletedUsersFromActiveLookup() {
        UserEntity user = users.save(UserEntity.create("deleted-user@example.com", "Deleted User"));
        user.markDeleted();
        users.saveAndFlush(user);

        assertThat(users.findByIdAndDeletedAtIsNull(user.getId())).isEmpty();

        user.restore();
        users.saveAndFlush(user);

        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(users.findByIdAndDeletedAtIsNull(user.getId()).map(UserEntity::getId)).contains(user.getId());
    }

    @Test
    void restoresSoftDeletedDataSourceAndHidesDeletedDataSourcesFromActiveLookup() {
        UserEntity owner = users.save(UserEntity.create("owner-for-data-source@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        DataSourceEntity dataSource = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        dataSource.assignOwner(owner.getId());
        dataSource.changeVisibility(DataSourceVisibility.WORKSPACE);
        dataSource.markDeleted();
        dataSources.saveAndFlush(dataSource);

        assertThat(dataSources.findByIdAndDeletedAtIsNull(dataSource.getId())).isEmpty();

        dataSource.restore();
        dataSources.saveAndFlush(dataSource);

        assertThat(dataSource.getDeletedAt()).isNull();
        assertThat(dataSource.getUpdatedAt()).isNotNull();
        assertThat(dataSources.findByIdAndDeletedAtIsNull(dataSource.getId()).map(DataSourceEntity::getId))
            .contains(dataSource.getId());
    }
}
