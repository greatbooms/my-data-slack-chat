package com.mydata.connectors.googledrive;

import com.mydata.connectors.core.ConnectorDocumentEvent;
import com.mydata.connectors.core.ConnectorEventSink;
import com.mydata.connectors.core.ConnectorFailureEvent;
import com.mydata.connectors.core.ConnectorFailureStage;
import com.mydata.connectors.core.ConnectorItemReference;
import com.mydata.connectors.core.ConnectorItemType;
import com.mydata.connectors.core.ConnectorReconciliationMode;
import com.mydata.connectors.core.DataSourceConnector;
import com.mydata.connectors.core.DataSourceSnapshot;
import com.mydata.connectors.core.RawAclEntry;
import com.mydata.connectors.core.RawContent;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GoogleDriveConnector implements DataSourceConnector {
    public static final String FOLDER_ID_CONFIG_KEY = "driveFolderId";

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveConnector.class);
    private static final long MAX_DOWNLOAD_SIZE_BYTES = 20L * 1024 * 1024;
    private static final String GOOGLE_DOC_MIME = "application/vnd.google-apps.document";
    private static final String GOOGLE_SHEET_MIME = "application/vnd.google-apps.spreadsheet";
    private static final String GOOGLE_FOLDER_MIME = "application/vnd.google-apps.folder";
    private static final String PDF_MIME = "application/pdf";

    private final GoogleDriveClient driveClient;

    public GoogleDriveConnector(GoogleDriveClient driveClient) {
        this.driveClient = driveClient;
    }

    @Override
    public DataSourceType supports() {
        return DataSourceType.GOOGLE_DRIVE;
    }

    @Override
    public ConnectorReconciliationMode reconciliationMode() {
        return ConnectorReconciliationMode.FULL_SNAPSHOT;
    }

    @Override
    public SyncCursor fetchChanges(DataSourceSnapshot dataSource, ConnectorEventSink sink) {
        String rootFolderId = dataSource.configValue(FOLDER_ID_CONFIG_KEY);
        if (rootFolderId == null || rootFolderId.isBlank()) {
            throw new IllegalArgumentException("GOOGLE_DRIVE 설정값이 없습니다: " + FOLDER_ID_CONFIG_KEY);
        }

        String principalKey = dataSource.visibilityPrincipalKey();
        Set<String> visitedFolderIds = new HashSet<>();
        Set<String> visitedFileIds = new HashSet<>();
        Deque<FolderWork> folders = new ArrayDeque<>();
        folders.addLast(new FolderWork(rootFolderId.trim(), List.of()));

        while (!folders.isEmpty()) {
            FolderWork folder = folders.removeFirst();
            if (!visitedFolderIds.add(folder.id())) {
                continue;
            }
            listFolder(folder, folders, visitedFileIds, principalKey, sink);
        }
        return dataSource.cursor();
    }

    private void listFolder(
        FolderWork folder,
        Deque<FolderWork> folders,
        Set<String> visitedFileIds,
        String principalKey,
        ConnectorEventSink sink
    ) {
        String pageToken = null;
        do {
            GoogleDriveClient.FileList batch;
            try {
                batch = driveClient.listChildren(folder.id(), pageToken);
            } catch (GoogleDriveApiException listFailure) {
                sink.onFailure(new ConnectorFailureEvent(
                    new ConnectorItemReference(
                        ConnectorItemType.FOLDER,
                        folder.id(),
                        folder.path().isEmpty() ? folder.id() : folder.path().getLast(),
                        folder.path()
                    ),
                    ConnectorFailureStage.QUERY,
                    listFailure.getMessage()
                ));
                return;
            }

            for (GoogleDriveClient.DriveFile file : batch.files()) {
                if (GOOGLE_FOLDER_MIME.equals(file.mimeType())) {
                    folders.addLast(new FolderWork(file.id(), append(folder.path(), file.name())));
                    continue;
                }
                if (!visitedFileIds.add(file.id())) {
                    continue;
                }
                processFile(file, append(folder.path(), file.name()), principalKey, sink);
            }
            pageToken = batch.nextPageToken();
        } while (pageToken != null);
    }

    private void processFile(
        GoogleDriveClient.DriveFile file,
        List<String> path,
        String principalKey,
        ConnectorEventSink sink
    ) {
        if (file.size() != null && file.size() > MAX_DOWNLOAD_SIZE_BYTES) {
            log.info("크기 제한을 초과한 Google Drive 파일을 건너뜁니다: {} ({} bytes)", file.id(), file.size());
            return;
        }

        String text;
        try {
            text = extractText(file);
        } catch (GoogleDriveApiException | PdfExtractionException extractFailure) {
            sink.onFailure(new ConnectorFailureEvent(
                new ConnectorItemReference(ConnectorItemType.FILE, file.id(), file.name(), path),
                ConnectorFailureStage.RETRIEVE,
                extractFailure.getMessage()
            ));
            return;
        }
        if (text == null) {
            return;
        }
        if (text.isBlank()) {
            log.info("추출된 텍스트가 없는 Google Drive 파일을 건너뜁니다: {}", file.id());
            return;
        }

        RawExternalDocument document = new RawExternalDocument(
            file.id(),
            DataSourceType.GOOGLE_DRIVE,
            file.name(),
            file.webViewLink(),
            file.mimeType(),
            file.createdTime(),
            file.modifiedTime(),
            sha256(text),
            metadata(file, path),
            new RawContent(text, "text/plain"),
            List.of(new RawAclEntry(principalKey, "READ", false, "GOOGLE_DRIVE"))
        );
        sink.onDocument(new ConnectorDocumentEvent(
            document,
            new ConnectorItemReference(ConnectorItemType.FILE, file.id(), file.name(), path)
        ));
    }

    /** 지원 타입이면 추출 텍스트, 지원 외 타입이면 null을 반환한다. */
    private String extractText(GoogleDriveClient.DriveFile file) {
        String mimeType = file.mimeType() == null ? "" : file.mimeType();
        if (GOOGLE_DOC_MIME.equals(mimeType)) {
            return driveClient.exportFile(file.id(), "text/plain");
        }
        if (GOOGLE_SHEET_MIME.equals(mimeType)) {
            return driveClient.exportFile(file.id(), "text/csv");
        }
        if (mimeType.startsWith("text/") || "application/json".equals(mimeType)) {
            return new String(driveClient.downloadFile(file.id()), StandardCharsets.UTF_8);
        }
        if (PDF_MIME.equals(mimeType)) {
            return extractPdfText(file, driveClient.downloadFile(file.id()));
        }
        log.debug("지원하지 않는 Google Drive mimeType을 건너뜁니다: {} ({})", file.id(), mimeType);
        return null;
    }

    private String extractPdfText(GoogleDriveClient.DriveFile file, byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new PdfExtractionException(
                "PDF 텍스트 추출에 실패했습니다: " + file.name() + " — " + exception.getMessage(), exception
            );
        }
    }

    private Map<String, Object> metadata(GoogleDriveClient.DriveFile file, List<String> path) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("driveFileId", file.id());
        metadata.put("driveMimeType", file.mimeType());
        metadata.put("drivePath", List.copyOf(path));
        if (file.webViewLink() != null) {
            metadata.put("driveWebViewLink", file.webViewLink());
        }
        if (file.md5Checksum() != null) {
            metadata.put("driveMd5Checksum", file.md5Checksum());
        }
        if (file.size() != null) {
            metadata.put("driveSize", file.size());
        }
        return metadata;
    }

    private List<String> append(List<String> values, String value) {
        List<String> appended = new ArrayList<>(values);
        appended.add(value);
        return List.copyOf(appended);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", exception);
        }
    }

    private record FolderWork(String id, List<String> path) {
    }

    private static final class PdfExtractionException extends RuntimeException {
        private PdfExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
