package com.mydata.connectors.googledrive;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleDriveApiClientTest {
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/token", exchange -> respond(exchange, 200, """
            {"access_token":"token-1","expires_in":3600,"token_type":"Bearer"}
            """));
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 자식_목록_요청을_보내고_파일_메타데이터를_파싱한다() {
        List<String> queries = new ArrayList<>();
        List<String> authorizations = new ArrayList<>();
        server.createContext("/drive/v3/files", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                {
                  "nextPageToken": "cursor-2",
                  "files": [{
                    "id": "file-1",
                    "name": "메모.txt",
                    "mimeType": "text/plain",
                    "size": "12",
                    "createdTime": "2026-07-01T00:00:00.000Z",
                    "modifiedTime": "2026-07-02T00:00:00.000Z",
                    "webViewLink": "https://drive.google.com/file/d/file-1/view",
                    "md5Checksum": "abc"
                  }]
                }
                """);
        });
        GoogleDriveApiClient client = client();

        GoogleDriveClient.FileList first = client.listChildren("folder-1", null);
        client.listChildren("folder-1", "cursor 2");

        assertThat(first.nextPageToken()).isEqualTo("cursor-2");
        assertThat(first.files()).singleElement().satisfies(file -> {
            assertThat(file.id()).isEqualTo("file-1");
            assertThat(file.name()).isEqualTo("메모.txt");
            assertThat(file.mimeType()).isEqualTo("text/plain");
            assertThat(file.size()).isEqualTo(12L);
            assertThat(file.createdTime()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
            assertThat(file.modifiedTime()).isEqualTo(Instant.parse("2026-07-02T00:00:00Z"));
            assertThat(file.webViewLink()).isEqualTo("https://drive.google.com/file/d/file-1/view");
            assertThat(file.md5Checksum()).isEqualTo("abc");
        });
        assertThat(queries).hasSize(2);
        assertThat(queries.getFirst())
            .contains("q=%27folder-1%27+in+parents+and+trashed+%3D+false")
            .contains("fields=nextPageToken%2Cfiles%28id%2Cname%2CmimeType%2Csize%2CcreatedTime%2CmodifiedTime%2CwebViewLink%2Cmd5Checksum%29")
            .contains("pageSize=100")
            .contains("supportsAllDrives=true")
            .contains("includeItemsFromAllDrives=true")
            .doesNotContain("pageToken=");
        assertThat(queries.get(1)).contains("pageToken=cursor+2");
        assertThat(authorizations).containsExactly("Bearer token-1", "Bearer token-1");
    }

    @Test
    void 구글_문서를_일반_텍스트로_export한다() {
        List<String> requests = new ArrayList<>();
        server.createContext("/drive/v3/files/doc-1/export", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                + " auth=" + exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "exported text");
        });

        String result = client().exportFile("doc-1", "text/plain");

        assertThat(result).isEqualTo("exported text");
        assertThat(requests)
            .containsExactly("GET /drive/v3/files/doc-1/export?mimeType=text%2Fplain auth=Bearer token-1");
    }

    @Test
    void 파일_본문을_바이트로_다운로드한다() {
        byte[] body = {0, 1, 2, -1};
        List<String> requests = new ArrayList<>();
        server.createContext("/drive/v3/files/file-1", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                + " auth=" + exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, body);
        });

        byte[] result = client().downloadFile("file-1");

        assertThat(result).containsExactly(body);
        assertThat(requests)
            .containsExactly("GET /drive/v3/files/file-1?alt=media&supportsAllDrives=true auth=Bearer token-1");
    }

    @Test
    void 실패_응답은_상태코드와_본문을_포함한_예외를_던진다() {
        server.createContext("/drive/v3/files/forbidden/export", exchange ->
            respond(exchange, 403, """
                {"error":{"message":"forbidden"}}
                """));

        assertThatThrownBy(() -> client().exportFile("forbidden", "text/plain"))
            .isInstanceOf(GoogleDriveApiException.class)
            .hasMessageContaining("forbidden")
            .satisfies(error ->
                assertThat(((GoogleDriveApiException) error).statusCode()).isEqualTo(403)
            );
    }

    private GoogleDriveApiClient client() {
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        GoogleOAuthTokenProvider tokenProvider = new GoogleOAuthTokenProvider(
            httpClient,
            objectMapper,
            URI.create("http://localhost:" + server.getAddress().getPort() + "/token"),
            "client-1",
            "secret-1",
            "refresh-1",
            Duration.ofSeconds(5),
            Clock.systemUTC()
        );
        return new GoogleDriveApiClient(
            httpClient,
            objectMapper,
            URI.create("http://localhost:" + server.getAddress().getPort()),
            tokenProvider,
            Duration.ofSeconds(5)
        );
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
