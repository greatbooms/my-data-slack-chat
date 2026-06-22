package com.mydata.admin.datasources;

import java.util.List;

public record AdminDataSourcePagePayload(
    List<AdminDataSourcePayload> items,
    int totalCount
) {
}
