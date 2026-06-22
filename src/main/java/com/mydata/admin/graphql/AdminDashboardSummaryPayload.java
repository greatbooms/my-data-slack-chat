package com.mydata.admin.graphql;

public record AdminDashboardSummaryPayload(
    int userCount,
    int dataSourceCount,
    int runningJobCount
) {
}
