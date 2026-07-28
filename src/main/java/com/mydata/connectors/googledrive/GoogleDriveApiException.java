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
