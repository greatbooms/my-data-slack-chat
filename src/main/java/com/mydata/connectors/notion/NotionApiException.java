package com.mydata.connectors.notion;

public class NotionApiException extends RuntimeException {
    private final Integer statusCode;
    private final String code;

    public NotionApiException(String message) {
        this(message, null, null, null);
    }

    public NotionApiException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public NotionApiException(int statusCode, String code) {
        this(
            "Notion API 오류: status=" + statusCode + ", code=" + (code == null ? "unknown" : code),
            statusCode,
            code,
            null
        );
    }

    private NotionApiException(String message, Integer statusCode, String code, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.code = code;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public String code() {
        return code;
    }
}
