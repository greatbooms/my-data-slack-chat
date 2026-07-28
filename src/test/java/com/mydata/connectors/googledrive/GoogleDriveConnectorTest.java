package com.mydata.connectors.googledrive;

import com.mydata.auth.PrincipalKeys;
import com.mydata.connectors.core.ConnectorDocumentEvent;
import com.mydata.connectors.core.ConnectorEventSink;
import com.mydata.connectors.core.ConnectorFailureEvent;
import com.mydata.connectors.core.ConnectorFailureStage;
import com.mydata.connectors.core.ConnectorItemReference;
import com.mydata.connectors.core.ConnectorItemType;
import com.mydata.connectors.core.ConnectorReconciliationMode;
import com.mydata.connectors.core.DataSourceSnapshot;
import com.mydata.connectors.core.RawExternalDocument;
import com.mydata.connectors.core.SyncCursor;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.DataSourceVisibility;
import com.mydata.datasources.SyncMode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleDriveConnectorTest {
    private static final String GOOGLE_DOC_MIME = "application/vnd.google-apps.document";
    private static final String GOOGLE_FOLDER_MIME = "application/vnd.google-apps.folder";

    @Test
    void 하위_폴더를_재귀_순회해_Docs_텍스트_PDF_문서를_방출한다() throws IOException {
        UUID ownerId = UUID.randomUUID();
        DataSourceEntity dataSource = dataSource(ownerId);
        dataSource.putConfig(GoogleDriveConnector.FOLDER_ID_CONFIG_KEY, "root-folder");
        FakeGoogleDriveClient drive = new FakeGoogleDriveClient();
        drive.page("root-folder", null, null,
            file("doc-1", "기획서", GOOGLE_DOC_MIME, null),
            file("folder-1", "하위폴더", GOOGLE_FOLDER_MIME, null)
        );
        drive.page("folder-1", null, null,
            file("text-1", "메모.txt", "text/plain", 14L),
            file("pdf-1", "문서.pdf", "application/pdf", 100L)
        );
        drive.export("doc-1", "text/plain", "Docs body text");
        drive.download("text-1", "Text body text".getBytes(StandardCharsets.UTF_8));
        drive.download("pdf-1", pdfBytes("PDF body text"));
        RecordingSink sink = new RecordingSink();
        GoogleDriveConnector connector = new GoogleDriveConnector(drive);

        SyncCursor returnedCursor = connector.fetchChanges(snapshot(dataSource), sink);
        List<RawExternalDocument> documents = sink.documents.stream()
            .map(ConnectorDocumentEvent::document)
            .toList();

        assertThat(returnedCursor.value()).isEmpty();
        assertThat(connector.supports()).isEqualTo(DataSourceType.GOOGLE_DRIVE);
        assertThat(connector.reconciliationMode()).isEqualTo(ConnectorReconciliationMode.FULL_SNAPSHOT);
        assertThat(documents)
            .extracting(RawExternalDocument::externalId)
            .containsExactly("doc-1", "text-1", "pdf-1");
        assertThat(sink.documents)
            .extracting(ConnectorDocumentEvent::reference)
            .extracting(ConnectorItemReference::type)
            .containsOnly(ConnectorItemType.FILE);
        assertThat(sink.documents)
            .extracting(event -> event.reference().qualifiedExternalId())
            .containsExactly("file:doc-1", "file:text-1", "file:pdf-1");
        assertThat(sink.documents)
            .extracting(event -> event.reference().path())
            .containsExactly(
                List.of("기획서"),
                List.of("하위폴더", "메모.txt"),
                List.of("하위폴더", "문서.pdf")
            );

        assertDocument(documents.get(0), "기획서", GOOGLE_DOC_MIME, List.of("기획서"));
        assertDocument(documents.get(1), "메모.txt", "text/plain", List.of("하위폴더", "메모.txt"));
        assertDocument(documents.get(2), "문서.pdf", "application/pdf", List.of("하위폴더", "문서.pdf"));
        assertThat(documents.get(0).content().text()).isEqualTo("Docs body text");
        assertThat(documents.get(1).content().text()).isEqualTo("Text body text");
        assertThat(documents.get(2).content().text()).contains("PDF body text");
        assertThat(documents).allSatisfy(document ->
            assertThat(document.aclEntries()).singleElement().satisfies(acl -> {
                assertThat(acl.principalKey()).isEqualTo(PrincipalKeys.user(ownerId));
                assertThat(acl.permission()).isEqualTo("READ");
                assertThat(acl.inherited()).isFalse();
                assertThat(acl.source()).isEqualTo("GOOGLE_DRIVE");
            })
        );
        assertThat(drive.exportRequests)
            .containsExactly(new ExportRequest("doc-1", "text/plain"));
        assertThat(sink.failures).isEmpty();
    }

    @Test
    void 다음_페이지_토큰이_있으면_모든_페이지를_수집한다() {
        DataSourceEntity dataSource = dataSource(UUID.randomUUID());
        dataSource.putConfig(GoogleDriveConnector.FOLDER_ID_CONFIG_KEY, "root-folder");
        FakeGoogleDriveClient drive = new FakeGoogleDriveClient();
        drive.page("root-folder", null, "page-2",
            file("text-1", "첫째.txt", "text/plain", 5L)
        );
        drive.page("root-folder", "page-2", null,
            file("text-2", "둘째.txt", "text/plain", 5L)
        );
        drive.download("text-1", "first".getBytes(StandardCharsets.UTF_8));
        drive.download("text-2", "second".getBytes(StandardCharsets.UTF_8));
        RecordingSink sink = new RecordingSink();

        new GoogleDriveConnector(drive).fetchChanges(snapshot(dataSource), sink);

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("text-1", "text-2");
        assertThat(drive.listRequests).containsExactly(
            new ListRequest("root-folder", null),
            new ListRequest("root-folder", "page-2")
        );
        assertThat(sink.failures).isEmpty();
    }

    @Test
    void Docs_내보내기_실패는_FILE_RETRIEVE_실패로_방출하고_나머지를_수집한다() {
        DataSourceEntity dataSource = dataSource(UUID.randomUUID());
        dataSource.putConfig(GoogleDriveConnector.FOLDER_ID_CONFIG_KEY, "root-folder");
        FakeGoogleDriveClient drive = new FakeGoogleDriveClient();
        drive.page("root-folder", null, null,
            file("doc-bad", "실패문서", GOOGLE_DOC_MIME, null),
            file("text-good", "정상.txt", "text/plain", 6L)
        );
        drive.failExport(
            "doc-bad",
            "text/plain",
            new GoogleDriveApiException("Google Docs 내보내기에 실패했습니다", 500)
        );
        drive.download("text-good", "normal".getBytes(StandardCharsets.UTF_8));
        RecordingSink sink = new RecordingSink();

        new GoogleDriveConnector(drive).fetchChanges(snapshot(dataSource), sink);

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("text-good");
        assertThat(sink.failures).singleElement().satisfies(failure -> {
            assertThat(failure.reference().type()).isEqualTo(ConnectorItemType.FILE);
            assertThat(failure.reference().qualifiedExternalId()).isEqualTo("file:doc-bad");
            assertThat(failure.reference().path()).containsExactly("실패문서");
            assertThat(failure.stage()).isEqualTo(ConnectorFailureStage.RETRIEVE);
            assertThat(failure.userSafeReason()).contains("내보내기");
        });
    }

    @Test
    void 폴더_목록_실패는_FOLDER_QUERY_실패로_방출하고_다른_폴더를_계속한다() {
        DataSourceEntity dataSource = dataSource(UUID.randomUUID());
        dataSource.putConfig(GoogleDriveConnector.FOLDER_ID_CONFIG_KEY, "root-folder");
        FakeGoogleDriveClient drive = new FakeGoogleDriveClient();
        drive.page("root-folder", null, null,
            file("folder-bad", "오류폴더", GOOGLE_FOLDER_MIME, null),
            file("folder-good", "정상폴더", GOOGLE_FOLDER_MIME, null)
        );
        drive.failList(
            "folder-bad",
            null,
            new GoogleDriveApiException("Google Drive 폴더 목록 조회에 실패했습니다", 503)
        );
        drive.page("folder-good", null, null,
            file("text-good", "정상.txt", "text/plain", 6L)
        );
        drive.download("text-good", "normal".getBytes(StandardCharsets.UTF_8));
        RecordingSink sink = new RecordingSink();

        new GoogleDriveConnector(drive).fetchChanges(snapshot(dataSource), sink);

        assertThat(sink.documents)
            .extracting(event -> event.document().externalId())
            .containsExactly("text-good");
        assertThat(sink.failures).singleElement().satisfies(failure -> {
            assertThat(failure.reference().type()).isEqualTo(ConnectorItemType.FOLDER);
            assertThat(failure.reference().qualifiedExternalId()).isEqualTo("folder:folder-bad");
            assertThat(failure.reference().path()).containsExactly("오류폴더");
            assertThat(failure.stage()).isEqualTo(ConnectorFailureStage.QUERY);
            assertThat(failure.userSafeReason()).contains("목록 조회");
        });
    }

    @Test
    void 지원하지_않거나_너무_크거나_본문이_빈_파일은_이벤트를_방출하지_않는다() {
        DataSourceEntity dataSource = dataSource(UUID.randomUUID());
        dataSource.putConfig(GoogleDriveConnector.FOLDER_ID_CONFIG_KEY, "root-folder");
        FakeGoogleDriveClient drive = new FakeGoogleDriveClient();
        drive.page("root-folder", null, null,
            file("image-1", "그림.png", "image/png", 100L),
            file("large-1", "대용량.txt", "text/plain", 21L * 1024 * 1024),
            file("blank-1", "빈파일.txt", "text/plain", 3L)
        );
        drive.download("blank-1", "   ".getBytes(StandardCharsets.UTF_8));
        RecordingSink sink = new RecordingSink();

        new GoogleDriveConnector(drive).fetchChanges(snapshot(dataSource), sink);

        assertThat(sink.documents).isEmpty();
        assertThat(sink.failures).isEmpty();
        assertThat(drive.downloadRequests).containsExactly("blank-1");
    }

    @Test
    void driveFolderId가_없으면_한국어_예외를_던진다() {
        DataSourceEntity dataSource = dataSource(UUID.randomUUID());

        assertThatThrownBy(() -> new GoogleDriveConnector(new FakeGoogleDriveClient()).fetchChanges(
            snapshot(dataSource),
            new RecordingSink()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("GOOGLE_DRIVE 설정값이 없습니다: driveFolderId");
    }

    private static void assertDocument(
        RawExternalDocument document,
        String title,
        String mimeType,
        List<String> path
    ) {
        assertThat(document.title()).isEqualTo(title);
        assertThat(document.sourceType()).isEqualTo(DataSourceType.GOOGLE_DRIVE);
        assertThat(document.mimeType()).isEqualTo(mimeType);
        assertThat(document.metadata()).containsEntry("drivePath", path);
    }

    private static DataSourceEntity dataSource(UUID ownerId) {
        DataSourceEntity dataSource = DataSourceEntity.create(
            UUID.randomUUID(),
            DataSourceType.GOOGLE_DRIVE,
            "Google Drive",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        );
        dataSource.assignOwner(ownerId);
        dataSource.changeVisibility(DataSourceVisibility.PRIVATE);
        return dataSource;
    }

    private static DataSourceSnapshot snapshot(DataSourceEntity dataSource) {
        return new DataSourceSnapshot(
            dataSource.getId(),
            dataSource.getWorkspaceId(),
            dataSource.getOwnerUserId(),
            dataSource.getType(),
            dataSource.getVisibility(),
            dataSource.configValues(),
            new SyncCursor(Map.of())
        );
    }

    private static GoogleDriveClient.DriveFile file(
        String id,
        String name,
        String mimeType,
        Long size
    ) {
        return new GoogleDriveClient.DriveFile(
            id,
            name,
            mimeType,
            size,
            Instant.parse("2026-07-01T00:00:00Z"),
            Instant.parse("2026-07-02T00:00:00Z"),
            "https://drive.google.com/file/d/" + id + "/view",
            "checksum-" + id
        );
    }

    private static byte[] pdfBytes(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static final class RecordingSink implements ConnectorEventSink {
        private final List<ConnectorDocumentEvent> documents = new ArrayList<>();
        private final List<ConnectorFailureEvent> failures = new ArrayList<>();

        @Override
        public void onDocument(ConnectorDocumentEvent event) {
            documents.add(event);
        }

        @Override
        public void onFailure(ConnectorFailureEvent event) {
            failures.add(event);
        }
    }

    private static final class FakeGoogleDriveClient implements GoogleDriveClient {
        private final Map<ListRequest, FileList> pages = new LinkedHashMap<>();
        private final Map<ExportRequest, String> exports = new LinkedHashMap<>();
        private final Map<String, byte[]> downloads = new LinkedHashMap<>();
        private final Map<ListRequest, GoogleDriveApiException> listFailures = new LinkedHashMap<>();
        private final Map<ExportRequest, GoogleDriveApiException> exportFailures = new LinkedHashMap<>();
        private final List<ListRequest> listRequests = new ArrayList<>();
        private final List<ExportRequest> exportRequests = new ArrayList<>();
        private final List<String> downloadRequests = new ArrayList<>();

        void page(String folderId, String pageToken, String nextPageToken, DriveFile... files) {
            pages.put(new ListRequest(folderId, pageToken), new FileList(List.of(files), nextPageToken));
        }

        void export(String fileId, String mimeType, String text) {
            exports.put(new ExportRequest(fileId, mimeType), text);
        }

        void download(String fileId, byte[] bytes) {
            downloads.put(fileId, bytes);
        }

        void failList(String folderId, String pageToken, GoogleDriveApiException failure) {
            listFailures.put(new ListRequest(folderId, pageToken), failure);
        }

        void failExport(String fileId, String mimeType, GoogleDriveApiException failure) {
            exportFailures.put(new ExportRequest(fileId, mimeType), failure);
        }

        @Override
        public FileList listChildren(String folderId, String pageToken) {
            ListRequest request = new ListRequest(folderId, pageToken);
            listRequests.add(request);
            GoogleDriveApiException failure = listFailures.get(request);
            if (failure != null) {
                throw failure;
            }
            FileList page = pages.get(request);
            if (page == null) {
                throw new AssertionError("등록되지 않은 폴더 목록 요청입니다: " + request);
            }
            return page;
        }

        @Override
        public String exportFile(String fileId, String exportMimeType) {
            ExportRequest request = new ExportRequest(fileId, exportMimeType);
            exportRequests.add(request);
            GoogleDriveApiException failure = exportFailures.get(request);
            if (failure != null) {
                throw failure;
            }
            String text = exports.get(request);
            if (text == null) {
                throw new AssertionError("등록되지 않은 파일 내보내기 요청입니다: " + request);
            }
            return text;
        }

        @Override
        public byte[] downloadFile(String fileId) {
            downloadRequests.add(fileId);
            byte[] bytes = downloads.get(fileId);
            if (bytes == null) {
                throw new AssertionError("등록되지 않은 파일 다운로드 요청입니다: " + fileId);
            }
            return bytes;
        }
    }

    private record ListRequest(String folderId, String pageToken) {
    }

    private record ExportRequest(String fileId, String mimeType) {
    }
}
