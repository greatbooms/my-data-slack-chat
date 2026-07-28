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
