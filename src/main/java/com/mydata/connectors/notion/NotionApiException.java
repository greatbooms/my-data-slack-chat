package com.mydata.connectors.notion;

public class NotionApiException extends RuntimeException {
    public NotionApiException(String message) {
        super(message);
    }

    public NotionApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
