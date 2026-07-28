package com.mydata.connectors.googledrive;

import java.time.Instant;
import java.util.List;

public interface GoogleDriveClient {
    FileList listChildren(String folderId, String pageToken);

    String exportFile(String fileId, String exportMimeType);

    byte[] downloadFile(String fileId);

    record DriveFile(
        String id,
        String name,
        String mimeType,
        Long size,
        Instant createdTime,
        Instant modifiedTime,
        String webViewLink,
        String md5Checksum
    ) {
    }

    record FileList(List<DriveFile> files, String nextPageToken) {
    }
}
