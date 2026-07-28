# Google Drive 커넥터 구현 계획

> **For agentic workers:** 이 계획은 task 단위로 구현한다. 이 저장소 규칙(CLAUDE.md)에 따라 실제 구현은 Codex에 위임하고, push 전 적대적 리뷰를 통과시킨다. 각 task는 독립적으로 검증 가능한 단위다.

**Goal:** `GOOGLE_DRIVE` 데이터소스가 지정 Drive 폴더를 재귀 수집해 Docs/Sheets/텍스트/PDF 문서를 기존 파이프라인으로 흘려보내고, 어드민 UI에서 생성할 수 있게 한다.

**Architecture:** Notion 커넥터 패턴 그대로 — env 전역 자격증명(`GoogleOAuthTokenProvider`가 refresh token→access token 캐시), `GoogleDriveApiClient`(`java.net.http.HttpClient`+Jackson)가 files.list/export/download를 담당, `GoogleDriveConnector`가 `FULL_SNAPSHOT` 모드로 폴더를 재귀 순회하며 문서 이벤트를 방출한다. 삭제 정합·미변경 SKIPPED는 기존 reconciliation이 처리한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Apache PDFBox 3.x(유일한 신규 의존성), React(admin)+graphql-codegen, JUnit(`com.sun.net.httpserver` fake 서버), Playwright.

**설계 스펙:** `docs/superpowers/specs/2026-07-28-google-drive-connector-design.md`

## Global Constraints

- 사용자-facing 문자열/로그/주석/예외 메시지는 한국어로 작성한다.
- Liquibase 스키마 변경 없음. 신규 의존성은 `org.apache.pdfbox:pdfbox:3.0.5` 하나만.
- 커넥터는 문서/실패 이벤트만 방출하고 저장하지 않는다(기존 경계 유지).
- 지원 외 mimeType·20MB 초과 파일·추출 텍스트가 빈 파일은 **실패가 아니라 무시**한다(`onFailure` 금지, 로그만). 이유: `failed > 0`이면 FULL_SNAPSHOT 삭제 정합이 보류되어 영구 실패가 된다.
- API 오류(목록/export/다운로드 실패)는 Notion처럼 `ConnectorFailureEvent`로 방출하고 순회는 계속한다.
- `contentHash`는 추출 텍스트의 SHA-256(Notion과 동일).
- ACL은 `dataSource.visibilityPrincipalKey()`에 READ 1건, `source`는 `"GOOGLE_DRIVE"`.
- 테스트는 정상 케이스와 실패 케이스를 각각 1개 이상 포함한다.

---

### Task 1: Google Drive 설정 + OAuth 토큰 공급자

**Files:**
- Create: `src/main/java/com/mydata/connectors/googledrive/GoogleDriveProperties.java`
- Create: `src/main/java/com/mydata/connectors/googledrive/GoogleDriveApiException.java`
- Create: `src/main/java/com/mydata/connectors/googledrive/GoogleOAuthTokenProvider.java`
- Modify: `src/main/resources/application.yml` (my-data.notion 블록 아래)
- Modify: `.env.example` (Notion 블록 아래)
- Test: `src/test/java/com/mydata/connectors/googledrive/GoogleOAuthTokenProviderTest.java`

**Interfaces:**
- Produces:
  - `record GoogleDriveProperties(String clientId, String clientSecret, String refreshToken, URI tokenUrl, URI apiBaseUrl, Duration connectTimeout, Duration requestTimeout)` — prefix `my-data.google-drive`
  - `class GoogleOAuthTokenProvider { String accessToken() }` — 만료 전 캐시, 실패 시 `GoogleDriveApiException`
  - `class GoogleDriveApiException extends RuntimeException { Integer statusCode() }`

- [ ] **Step 1: 실패 테스트 작성** — `GoogleOAuthTokenProviderTest`. `NotionApiClientTest` 스타일(`com.sun.net.httpserver.HttpServer`)로 3케이스:

```java
package com.mydata.connectors.googledrive;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleOAuthTokenProviderTest {
    private HttpServer server;
    private final List<String> requestBodies = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 토큰을_발급받고_만료_전에는_캐시를_재사용한다() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/token", exchange -> {
            calls.incrementAndGet();
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {"access_token":"token-1","expires_in":3600,"token_type":"Bearer"}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        GoogleOAuthTokenProvider provider = provider(java.time.Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        assertThat(provider.accessToken()).isEqualTo("token-1");
        assertThat(provider.accessToken()).isEqualTo("token-1");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(requestBodies.getFirst())
            .contains("grant_type=refresh_token")
            .contains("client_id=client-1")
            .contains("client_secret=secret-1")
            .contains("refresh_token=refresh-1");
    }

    @Test
    void 만료가_지나면_토큰을_갱신한다() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/token", exchange -> {
            int call = calls.incrementAndGet();
            byte[] body = ("{\"access_token\":\"token-" + call + "\",\"expires_in\":1,\"token_type\":\"Bearer\"}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        MutableClock clock = new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
        GoogleOAuthTokenProvider provider = provider(clock);

        assertThat(provider.accessToken()).isEqualTo("token-1");
        clock.advanceSeconds(120);
        assertThat(provider.accessToken()).isEqualTo("token-2");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void 갱신_실패는_상태코드와_함께_예외를_던진다() {
        server.createContext("/token", exchange -> {
            byte[] body = "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        GoogleOAuthTokenProvider provider = provider(java.time.Clock.systemUTC());

        assertThatThrownBy(provider::accessToken)
            .isInstanceOf(GoogleDriveApiException.class)
            .satisfies(error -> assertThat(((GoogleDriveApiException) error).statusCode()).isEqualTo(400));
    }

    private GoogleOAuthTokenProvider provider(java.time.Clock clock) {
        return new GoogleOAuthTokenProvider(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            URI.create("http://localhost:" + server.getAddress().getPort() + "/token"),
            "client-1",
            "secret-1",
            "refresh-1",
            Duration.ofSeconds(5),
            clock
        );
    }

    private static final class MutableClock extends java.time.Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인** — `./gradlew test --tests 'com.mydata.connectors.googledrive.*'` → 컴파일 에러(클래스 없음)로 실패.

- [ ] **Step 3: 구현**

`GoogleDriveApiException.java`:
```java
package com.mydata.connectors.googledrive;

public class GoogleDriveApiException extends RuntimeException {
    private final Integer statusCode;

    public GoogleDriveApiException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GoogleDriveApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
```

`GoogleDriveProperties.java` (`NotionProperties` 패턴):
```java
package com.mydata.connectors.googledrive;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "my-data.google-drive")
public record GoogleDriveProperties(
    String clientId,
    String clientSecret,
    String refreshToken,
    URI tokenUrl,
    URI apiBaseUrl,
    Duration connectTimeout,
    Duration requestTimeout
) {
    private static final URI DEFAULT_TOKEN_URL = URI.create("https://oauth2.googleapis.com/token");
    private static final URI DEFAULT_API_BASE_URL = URI.create("https://www.googleapis.com");
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public GoogleDriveProperties {
        clientId = clientId == null ? "" : clientId;
        clientSecret = clientSecret == null ? "" : clientSecret;
        refreshToken = refreshToken == null ? "" : refreshToken;
        tokenUrl = tokenUrl == null ? DEFAULT_TOKEN_URL : tokenUrl;
        apiBaseUrl = apiBaseUrl == null ? DEFAULT_API_BASE_URL : apiBaseUrl;
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
    }
}
```

`GoogleOAuthTokenProvider.java`:
```java
package com.mydata.connectors.googledrive;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class GoogleOAuthTokenProvider {
    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final Duration requestTimeout;
    private final Clock clock;

    private CachedToken cached;

    public GoogleOAuthTokenProvider(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        URI tokenUrl,
        String clientId,
        String clientSecret,
        String refreshToken,
        Duration requestTimeout,
        Clock clock
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.requestTimeout = requestTimeout;
        this.clock = clock;
    }

    public synchronized String accessToken() {
        if (cached != null && clock.instant().isBefore(cached.expiresAt())) {
            return cached.value();
        }

        String body = "grant_type=refresh_token"
            + "&client_id=" + encode(clientId)
            + "&client_secret=" + encode(clientSecret)
            + "&refresh_token=" + encode(refreshToken);
        HttpRequest request = HttpRequest.newBuilder(tokenUrl)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(requestTimeout)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new GoogleDriveApiException("Google OAuth 토큰 갱신 요청에 실패했습니다", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GoogleDriveApiException("Google OAuth 토큰 갱신이 중단되었습니다", exception);
        }
        if (response.statusCode() != 200) {
            throw new GoogleDriveApiException(
                "Google OAuth 토큰 갱신에 실패했습니다: " + response.body(), response.statusCode()
            );
        }

        JsonNode root = objectMapper.readTree(response.body());
        String accessToken = root.path("access_token").asString(null);
        long expiresInSeconds = root.path("expires_in").asLong(0);
        if (accessToken == null || accessToken.isBlank()) {
            throw new GoogleDriveApiException("Google OAuth 응답에 access_token이 없습니다", response.statusCode());
        }
        Instant expiresAt = clock.instant().plusSeconds(expiresInSeconds).minus(EXPIRY_SAFETY_MARGIN);
        cached = new CachedToken(accessToken, expiresAt);
        return accessToken;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
```

주의: `tools.jackson.databind` API에서 `readTree`가 checked 예외를 던지지 않으면 그대로 두고, 파싱 예외(`JacksonException`)는 `GoogleDriveApiException`으로 감싼다(`NotionApiClient.sendJson`의 처리 방식을 그대로 참고).

`application.yml`의 `my-data.notion` 블록 아래 추가:
```yaml
  google-drive:
    client-id: ${GOOGLE_DRIVE_CLIENT_ID:}
    client-secret: ${GOOGLE_DRIVE_CLIENT_SECRET:}
    refresh-token: ${GOOGLE_DRIVE_REFRESH_TOKEN:}
    token-url: ${GOOGLE_DRIVE_TOKEN_URL:https://oauth2.googleapis.com/token}
    api-base-url: ${GOOGLE_DRIVE_API_BASE_URL:https://www.googleapis.com}
    connect-timeout: ${GOOGLE_DRIVE_CONNECT_TIMEOUT:5s}
    request-timeout: ${GOOGLE_DRIVE_REQUEST_TIMEOUT:30s}
```

`.env.example`의 Notion 블록 아래 추가:
```bash
# [조건부 필수] Google Drive OAuth 자격증명입니다.
# GOOGLE_DRIVE 데이터소스를 수집할 때 필요합니다. 발급 절차는 README를 참고하세요.
GOOGLE_DRIVE_CLIENT_ID=
GOOGLE_DRIVE_CLIENT_SECRET=
GOOGLE_DRIVE_REFRESH_TOKEN=

# [선택] Google Drive API 설정입니다. 일반 개발에서는 기본값을 유지합니다.
GOOGLE_DRIVE_TOKEN_URL=https://oauth2.googleapis.com/token
GOOGLE_DRIVE_API_BASE_URL=https://www.googleapis.com
GOOGLE_DRIVE_CONNECT_TIMEOUT=5s
GOOGLE_DRIVE_REQUEST_TIMEOUT=30s
```

- [ ] **Step 4: 테스트 통과 확인** — `./gradlew test --tests 'com.mydata.connectors.googledrive.*'` → PASS.

- [ ] **Step 5: 커밋** — `feat(connectors): add google drive oauth token provider`

---

### Task 2: GoogleDriveClient 인터페이스 + API 클라이언트

**Files:**
- Create: `src/main/java/com/mydata/connectors/googledrive/GoogleDriveClient.java`
- Create: `src/main/java/com/mydata/connectors/googledrive/GoogleDriveApiClient.java`
- Create: `src/main/java/com/mydata/connectors/googledrive/GoogleDriveConfiguration.java`
- Test: `src/test/java/com/mydata/connectors/googledrive/GoogleDriveApiClientTest.java`

**Interfaces:**
- Consumes: Task 1의 `GoogleOAuthTokenProvider.accessToken()`, `GoogleDriveProperties`, `GoogleDriveApiException`
- Produces:
```java
public interface GoogleDriveClient {
    FileList listChildren(String folderId, String pageToken);
    String exportFile(String fileId, String exportMimeType);
    byte[] downloadFile(String fileId);

    record DriveFile(
        String id, String name, String mimeType, Long size,
        java.time.Instant createdTime, java.time.Instant modifiedTime,
        String webViewLink, String md5Checksum
    ) {}

    record FileList(java.util.List<DriveFile> files, String nextPageToken) {}
}
```

- [ ] **Step 1: 실패 테스트 작성** — `GoogleDriveApiClientTest` (`NotionApiClientTest` 스타일). 토큰 컨텍스트(`/token` → `token-1` 고정)와 Drive 컨텍스트를 같은 fake 서버에 등록. 케이스:
  1. `listChildren`: 요청 쿼리에 인코딩된 `q='folder-1' in parents and trashed = false`, `fields`, `pageSize=100`, `supportsAllDrives=true`, `includeItemsFromAllDrives=true`, `Authorization: Bearer token-1`이 포함되는지 검증. 응답 JSON `{"nextPageToken":"cursor-2","files":[{"id":"file-1","name":"메모.txt","mimeType":"text/plain","size":"12","createdTime":"2026-07-01T00:00:00.000Z","modifiedTime":"2026-07-02T00:00:00.000Z","webViewLink":"https://drive.google.com/file/d/file-1/view","md5Checksum":"abc"}]}` 파싱 검증(`size`는 문자열로 오므로 `asLong` 계열로 읽는다). `pageToken` 전달 시 쿼리 포함 검증.
  2. `exportFile`: `GET /drive/v3/files/doc-1/export?mimeType=text%2Fplain` 경로·헤더 검증, 본문 문자열 반환.
  3. `downloadFile`: `GET /drive/v3/files/file-1?alt=media&supportsAllDrives=true` 바이트 반환.
  4. 실패: 403 응답 → `GoogleDriveApiException` `statusCode()==403`, 메시지에 응답 본문 포함.

- [ ] **Step 2: 테스트 실패 확인** — 컴파일 에러로 실패.

- [ ] **Step 3: 구현** — `GoogleDriveApiClient`는 `NotionApiClient`의 요청/오류 처리 구조를 그대로 따른다:

```java
package com.mydata.connectors.googledrive;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GoogleDriveApiClient implements GoogleDriveClient {
    private static final int PAGE_SIZE = 100;
    private static final String LIST_FIELDS =
        "nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime,webViewLink,md5Checksum)";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final GoogleOAuthTokenProvider tokenProvider;
    private final Duration requestTimeout;

    public GoogleDriveApiClient(
        HttpClient httpClient,
        ObjectMapper objectMapper,
        URI baseUri,
        GoogleOAuthTokenProvider tokenProvider,
        Duration requestTimeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = baseUri;
        this.tokenProvider = tokenProvider;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public FileList listChildren(String folderId, String pageToken) {
        String path = "/drive/v3/files?q=" + encode("'" + folderId + "' in parents and trashed = false")
            + "&fields=" + encode(LIST_FIELDS)
            + "&pageSize=" + PAGE_SIZE
            + "&orderBy=" + encode("name")
            + "&supportsAllDrives=true&includeItemsFromAllDrives=true";
        if (pageToken != null) {
            path += "&pageToken=" + encode(pageToken);
        }

        JsonNode root = readJson(sendString(get(path)));
        List<DriveFile> files = new ArrayList<>();
        for (JsonNode file : root.path("files")) {
            files.add(new DriveFile(
                file.path("id").asString(),
                file.path("name").asString(),
                file.path("mimeType").asString(),
                file.path("size").isMissingNode() ? null : file.path("size").asLong(),
                parseInstant(file.path("createdTime").asString(null)),
                parseInstant(file.path("modifiedTime").asString(null)),
                blankToNull(file.path("webViewLink").asString(null)),
                blankToNull(file.path("md5Checksum").asString(null))
            ));
        }
        return new FileList(files, blankToNull(root.path("nextPageToken").asString(null)));
    }

    @Override
    public String exportFile(String fileId, String exportMimeType) {
        String path = "/drive/v3/files/" + encode(fileId) + "/export?mimeType=" + encode(exportMimeType);
        return sendString(get(path));
    }

    @Override
    public byte[] downloadFile(String fileId) {
        String path = "/drive/v3/files/" + encode(fileId) + "?alt=media&supportsAllDrives=true";
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(get(path), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            throw new GoogleDriveApiException("Google Drive 요청에 실패했습니다", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GoogleDriveApiException("Google Drive 요청이 중단되었습니다", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GoogleDriveApiException(
                "Google Drive 요청이 실패했습니다: " + new String(response.body(), StandardCharsets.UTF_8),
                response.statusCode()
            );
        }
        return response.body();
    }

    private HttpRequest get(String pathAndQuery) {
        return HttpRequest.newBuilder(baseUri.resolve(pathAndQuery))
            .GET()
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + tokenProvider.accessToken())
            .header("Accept", "application/json")
            .build();
    }

    private String sendString(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new GoogleDriveApiException("Google Drive 요청에 실패했습니다", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GoogleDriveApiException("Google Drive 요청이 중단되었습니다", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GoogleDriveApiException(
                "Google Drive 요청이 실패했습니다: " + response.body(), response.statusCode()
            );
        }
        return response.body();
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException exception) {
            throw new GoogleDriveApiException("Google Drive 응답을 해석하지 못했습니다", exception);
        }
    }

    private Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
```

주의: `tools.jackson`의 실제 예외 처리 시그니처는 `NotionApiClient` 소스를 열어 확인하고 동일하게 맞춘다(`asString`/`asLong` 사용법 포함).

`GoogleDriveConfiguration.java` (`NotionConfiguration` 패턴):
```java
package com.mydata.connectors.googledrive;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(GoogleDriveProperties.class)
class GoogleDriveConfiguration {
    @Bean
    GoogleDriveClient googleDriveClient(GoogleDriveProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .build();
        GoogleOAuthTokenProvider tokenProvider = new GoogleOAuthTokenProvider(
            httpClient,
            objectMapper,
            properties.tokenUrl(),
            properties.clientId(),
            properties.clientSecret(),
            properties.refreshToken(),
            properties.requestTimeout(),
            Clock.systemUTC()
        );
        return new GoogleDriveApiClient(
            httpClient,
            objectMapper,
            properties.apiBaseUrl(),
            tokenProvider,
            properties.requestTimeout()
        );
    }
}
```

- [ ] **Step 4: 테스트 통과 확인** — `./gradlew test --tests 'com.mydata.connectors.googledrive.*'` → PASS.

- [ ] **Step 5: 커밋** — `feat(connectors): add google drive api client`

---

### Task 3: GoogleDriveConnector + PDF 텍스트 추출

**Files:**
- Modify: `build.gradle` (dependencies에 `implementation 'org.apache.pdfbox:pdfbox:3.0.5'`)
- Modify: `src/main/java/com/mydata/connectors/core/ConnectorItemType.java` (`FILE`, `FOLDER` 값 추가 — DB 저장 안 되므로 안전)
- Create: `src/main/java/com/mydata/connectors/googledrive/GoogleDriveConnector.java`
- Test: `src/test/java/com/mydata/connectors/googledrive/GoogleDriveConnectorTest.java`

**Interfaces:**
- Consumes: Task 2의 `GoogleDriveClient`(fake로 대체 가능), `connectors.core`의 이벤트 레코드들
- Produces: `GoogleDriveConnector implements DataSourceConnector` — `supports()=GOOGLE_DRIVE`, `reconciliationMode()=FULL_SNAPSHOT`, config key 상수 `GoogleDriveConnector.FOLDER_ID_CONFIG_KEY = "driveFolderId"` (Task 4가 사용)

- [ ] **Step 1: 실패 테스트 작성** — fake `GoogleDriveClient`(폴더ID→자식 목록 맵, 파일ID→본문 맵)로 케이스:
  1. **정상 재귀**: 루트에 Google Docs 1개 + 하위 폴더 1개, 하위 폴더에 `text/plain` 1개와 PDF 1개 → 문서 3건 방출. 각 문서의 `externalId`(파일 ID), `title`(파일명), `sourceType=GOOGLE_DRIVE`, `mimeType`, `metadata.drivePath`(예: `["메모.txt"]`, `["하위폴더", "문서.pdf"]`), ACL 1건(`RawAclEntry(principalKey, "READ", false, "GOOGLE_DRIVE")`) 검증. PDF 본문은 테스트 안에서 PDFBox로 직접 생성(`PDPageContentStream`으로 "PDF 본문 텍스트" 기록)해 추출 결과에 해당 문자열이 포함되는지 확인.
  2. **페이지네이션**: `listChildren`이 `nextPageToken`을 반환하면 다음 페이지도 수집.
  3. **export 실패**: Docs export에서 `GoogleDriveApiException` → `ConnectorFailureEvent`(`ConnectorItemType.FILE`, stage `RETRIEVE`) 방출, 나머지 파일은 정상 수집.
  4. **폴더 목록 실패**: `listChildren` 예외 → `ConnectorFailureEvent`(`ConnectorItemType.FOLDER`, stage `QUERY`) 방출, 다른 폴더는 계속.
  5. **무시 케이스**: 이미지 mimeType, 21MB `size`의 텍스트 파일, 추출 텍스트가 공백뿐인 파일 → 문서/실패 어느 이벤트도 방출되지 않음.
  6. **설정 누락**: config에 `driveFolderId` 없으면 `IllegalArgumentException`(한국어 메시지).

  `DataSourceSnapshot`은 `NotionPageConnectorTest`가 만드는 방식을 참고해 직접 생성한다(`visibility=PRIVATE`, `ownerUserId` 지정 → `visibilityPrincipalKey()`가 `USER:{id}`).

- [ ] **Step 2: 테스트 실패 확인** — 컴파일 에러로 실패.

- [ ] **Step 3: 구현**

```java
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
```

`ConnectorItemType`에 값 추가:
```java
public enum ConnectorItemType {
    PAGE,
    DATABASE,
    DATA_SOURCE,
    BLOCK,
    FILE,
    FOLDER
}
```

- [ ] **Step 4: 기존 통합 테스트의 빈 충돌 해결** — `IngestionWorkerIntegrationTest`는 `@TestConfiguration`으로 `supports()=GOOGLE_DRIVE`인 `TestConnector` 빈을 등록한다. 실제 `GoogleDriveConnector`(@Component)가 생기면 `IngestionWorker`의 커넥터 맵에서 어느 쪽이 이길지 주입 순서에 의존하게 된다. 결정적으로 만들기 위해 테스트 빈이 실제 빈을 **이름으로 교체**하게 한다:
  - `IngestionWorkerIntegrationTest`의 `@SpringBootTest`에 `properties = "spring.main.allow-bean-definition-overriding=true"` 추가(기존 properties가 있으면 배열에 추가).
  - `TestConnectorConfiguration`의 팩토리를 `@Bean("googleDriveConnector") TestConnector testConnector()` 로 변경(컴포넌트 스캔 빈 이름과 동일하게) — 실제 커넥터가 컨텍스트에서 대체되어 커넥터가 1개만 남는다.
  - `@Autowired TestConnector connector` 주입은 타입 기준이므로 그대로 동작한다.

- [ ] **Step 5: 테스트 통과 확인** — `./gradlew test --tests 'com.mydata.connectors.googledrive.*'` → PASS. 전체 `./gradlew test`로 `IngestionWorkerIntegrationTest` 포함 회귀 없음 확인.

- [ ] **Step 6: 커밋** — `feat(connectors): add google drive connector with pdf extraction`

---

### Task 4: 어드민 백엔드 — GOOGLE_DRIVE 생성 허용 + driveFolderId 설정

**Files:**
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceService.java`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceInputs.java` (create/update 레코드에 `String driveFolderId` 필드 추가)
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourcePayload.java` (`driveFolderId` 필드 + `configValue("driveFolderId")` 매핑)
- Modify: `src/main/resources/graphql/admin.graphqls` (`DataSource` 타입, `CreateDataSourceInput`, `UpdateDataSourceInput`에 `driveFolderId: String` 추가)
- Test: `src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java` (기존 스타일에 케이스 추가)

**Interfaces:**
- Consumes: Task 3의 `GoogleDriveConnector.FOLDER_ID_CONFIG_KEY`
- Produces: GraphQL `driveFolderId` 필드 (Task 5 프론트가 사용)

- [ ] **Step 1: 실패 테스트 작성** — `AdminDataSourceGraphQlTest`의 기존 NOTION/SLACK 생성 테스트 패턴을 따라:
  1. 정상: `type: GOOGLE_DRIVE`, `driveFolderId: "https://drive.google.com/drive/folders/abc123XYZ_-45?usp=sharing"` 로 생성 → payload `driveFolderId == "abc123XYZ_-45"` (URL에서 ID 정규화).
  2. 정상: 원시 ID `"abc123XYZ_-45"` 그대로도 생성 성공.
  3. 실패: `type: GOOGLE_DRIVE`인데 `driveFolderId` 누락 → 에러 메시지 `"GOOGLE_DRIVE 데이터소스는 driveFolderId를 설정해야 합니다"`.
  4. 실패: NOTION 데이터소스 update에 `driveFolderId` 전달 → `"driveFolderId는 GOOGLE_DRIVE 데이터소스에서만 설정할 수 있습니다"`.

- [ ] **Step 2: 테스트 실패 확인** — GraphQL 스키마에 필드가 없어 실패.

- [ ] **Step 3: 구현**

`admin.graphqls` — `DataSource` 타입의 `slackWorkspaceUrl` 아래, 그리고 `CreateDataSourceInput`/`UpdateDataSourceInput`의 같은 위치에 각각 `driveFolderId: String` 추가.

`AdminDataSourceInputs.java` — 두 레코드 모두 `slackWorkspaceUrl` 다음에 `String driveFolderId` 추가(레코드 생성자를 쓰는 모든 테스트 코드도 인자 추가 필요 — 컴파일 에러가 알려준다).

`AdminDataSourcePayload.java` — `slackWorkspaceUrl` 매핑 아래 `blankToNull(dataSource.configValue("driveFolderId"))` 추가.

`AdminDataSourceService.java`:
```java
private static final String DRIVE_FOLDER_ID_CONFIG_KEY = "driveFolderId";
private static final Pattern DRIVE_FOLDER_URL_PATTERN = Pattern.compile("/folders/([A-Za-z0-9_-]+)");
private static final Pattern DRIVE_FOLDER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
```

`requireType` 허용 목록에 `GOOGLE_DRIVE` 추가, 메시지는 `"현재 관리자 화면에서는 LOCAL_TEXT, NOTION, SLACK 또는 GOOGLE_DRIVE 데이터소스만 만들 수 있습니다"`.

`applyCreateConfig`에 추가:
```java
if (dataSource.getType() == DataSourceType.GOOGLE_DRIVE) {
    if (!hasText(input.driveFolderId())) {
        throw new IllegalArgumentException("GOOGLE_DRIVE 데이터소스는 driveFolderId를 설정해야 합니다");
    }
    dataSource.putConfig(DRIVE_FOLDER_ID_CONFIG_KEY, normalizeDriveFolderId(input.driveFolderId()));
}
```

`applyUpdateConfig`에 추가 (Slack 블록 패턴):
```java
if (input.driveFolderId() != null) {
    if (dataSource.getType() != DataSourceType.GOOGLE_DRIVE) {
        throw new IllegalArgumentException("driveFolderId는 GOOGLE_DRIVE 데이터소스에서만 설정할 수 있습니다");
    }
    dataSource.putConfig(DRIVE_FOLDER_ID_CONFIG_KEY, normalizeDriveFolderId(input.driveFolderId()));
}
```

정규화 (Notion의 `normalizeNotionId` 위치 근처에 추가):
```java
private static String normalizeDriveFolderId(String value) {
    String trimmed = requireText(value, "driveFolderId");
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        Matcher matcher = DRIVE_FOLDER_URL_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("driveFolderId 형식이 올바르지 않습니다");
    }
    if (!DRIVE_FOLDER_ID_PATTERN.matcher(trimmed).matches()) {
        throw new IllegalArgumentException("driveFolderId 형식이 올바르지 않습니다");
    }
    return trimmed;
}
```

- [ ] **Step 4: 테스트 통과 확인** — `./gradlew test --tests 'com.mydata.admin.datasources.*'` → PASS.

- [ ] **Step 5: 커밋** — `feat(admin): allow creating google drive data sources`

---

### Task 5: 어드민 프론트 — GOOGLE_DRIVE 폼 + Playwright 확인

**Files:**
- Modify: `frontend/admin/src/graphql/admin.graphql` (DataSource fragment에 `driveFolderId`, create/update mutation 변수 전달부 확인)
- Modify: `frontend/admin/src/routes/DataSourceFormDialog.tsx`
- Modify: `frontend/admin/src/routes/DataSourcesPage.tsx`
- Modify: `frontend/admin/src/App.test.tsx`
- Regenerate: `frontend/admin/src/generated/graphql.ts` (`npm run codegen`)

**Interfaces:**
- Consumes: Task 4의 GraphQL `driveFolderId` 필드

- [ ] **Step 1: 실패 테스트 수정/작성** — `App.test.tsx`:
  - 기존 단언 `expect(screen.queryByRole('option', { name: 'GOOGLE_DRIVE' })).not.toBeInTheDocument()` 를 `expect(screen.getByRole('option', { name: 'GOOGLE_DRIVE' })).toBeVisible()` 로 교체.
  - 새 케이스: 종류를 `GOOGLE_DRIVE`로 바꾸면 "Google Drive 폴더 링크 또는 ID" 입력이 보이고, 값을 넣어 저장하면 mutation 변수에 `driveFolderId`가 포함된다(기존 NOTION 폼 제출 테스트 패턴).

- [ ] **Step 2: 테스트 실패 확인** — `cd frontend/admin && npm test` → FAIL.

- [ ] **Step 3: 구현**
  - `admin.graphql`의 DataSource fragment `slackWorkspaceUrl` 아래에 `driveFolderId` 추가. create/update mutation이 `$input`을 통째로 넘기는 구조면 수정 불필요 — 확인만.
  - `npm run codegen`으로 `generated/graphql.ts` 재생성.
  - `DataSourceFormDialog.tsx`:
    - `DataSourceFormValues`에 `driveFolderId: string` 추가, `createInitialValues`에 `driveFolderId: dataSource?.driveFolderId ?? ''`.
    - 종류 select에 `<option value="GOOGLE_DRIVE">GOOGLE_DRIVE</option>` 추가.
    - NOTION 블록 패턴으로:
    ```tsx
    {values.type === 'GOOGLE_DRIVE' ? (
      <label>
        Google Drive 폴더 링크 또는 ID
        <input
          value={values.driveFolderId}
          disabled={Boolean(dataSource && dataSource.type !== 'GOOGLE_DRIVE')}
          placeholder="https://drive.google.com/drive/folders/..."
          required
          onChange={(event) => setValues({ ...values, driveFolderId: event.target.value })}
        />
      </label>
    ) : null}
    ```
  - `DataSourcesPage.tsx`: create 변수에 `driveFolderId: values.type === 'GOOGLE_DRIVE' ? values.driveFolderId : undefined`, update 변수에 `driveFolderId: editingDataSource.type === 'GOOGLE_DRIVE' ? values.driveFolderId : undefined` (기존 notion/slack 매핑과 같은 위치).

- [ ] **Step 4: 테스트 통과 확인** — `cd frontend/admin && npm test` → PASS. `npm run build`도 성공 확인(tsc).

- [ ] **Step 5: Playwright 실제 화면 확인** — 백엔드 `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` + 어드민 로그인 후 데이터소스 추가 다이얼로그에서 GOOGLE_DRIVE 선택 → 폴더 입력 필드 렌더링 스크린샷. (생성까지는 자격증명 없이도 가능하므로 생성 후 목록 표시까지 확인.)

- [ ] **Step 6: 커밋** — `feat(admin-ui): add google drive data source form`

---

### Task 6: README/문서 + 최종 검증

**Files:**
- Modify: `README.md` (데이터소스 종류 문단의 "GOOGLE_DRIVE 후속 단계" 문구 교체 + 리프레시 토큰 발급 절차 섹션 추가)
- Modify: `CLAUDE.md` "현재 구현 범위"에 Google Drive 커넥터 한 줄 추가

**Steps:**

- [ ] **Step 1: README 갱신** — 데이터소스 문단을 "GOOGLE_DRIVE는 지정 폴더를 재귀 수집하며 Google Docs/Sheets, 텍스트 계열, PDF에서 텍스트를 추출합니다"로 교체. 리프레시 토큰 발급 절차 추가:
  1. Google Cloud Console에서 프로젝트 생성 → "Google Drive API" 사용 설정.
  2. OAuth 동의 화면 구성(테스트 사용자에 본인 계정 추가) → 데스크톱 앱 OAuth 클라이언트 ID 생성.
  3. 브라우저로 `https://accounts.google.com/o/oauth2/v2/auth?client_id=<CLIENT_ID>&redirect_uri=http://localhost:8089&response_type=code&scope=https://www.googleapis.com/auth/drive.readonly&access_type=offline&prompt=consent` 접속 → 동의 → redirect URL의 `code` 복사.
  4. `curl -d "code=<CODE>&client_id=<CLIENT_ID>&client_secret=<CLIENT_SECRET>&redirect_uri=http://localhost:8089&grant_type=authorization_code" https://oauth2.googleapis.com/token` → 응답의 `refresh_token`을 `.env`에 저장.
- [ ] **Step 2: 전체 검증** — `./gradlew test` 전체 PASS + `cd frontend/admin && npm test` PASS 확인.
- [ ] **Step 3: 커밋** — `docs(readme): document google drive data source setup`

---

## 계획 셀프 리뷰 결과

- 스펙 커버리지: 인증(T1), API 클라이언트(T2), 커넥터+파일 타입+무시 정책(T3), 어드민 백엔드(T4), 어드민 UI(T5), 문서/발급 가이드(T6) — 스펙 전 섹션 매핑 확인.
- `tools.jackson` 예외/`asString` 시그니처는 구현 시 `NotionApiClient` 원본을 열어 맞추도록 명시(추측 금지).
- 기존 `IngestionWorkerIntegrationTest`의 GOOGLE_DRIVE test connector와 실제 커넥터의 빈 충돌은 Task 3 Step 4에서 빈 이름 교체(`@Bean("googleDriveConnector")` + bean overriding 허용)로 결정적으로 해결.
