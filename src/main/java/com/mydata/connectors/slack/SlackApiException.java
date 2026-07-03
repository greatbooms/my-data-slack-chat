package com.mydata.connectors.slack;

public class SlackApiException extends RuntimeException {
    public SlackApiException(String message) {
        super(message);
    }

    public SlackApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
