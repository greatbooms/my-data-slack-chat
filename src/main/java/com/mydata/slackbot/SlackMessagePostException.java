package com.mydata.slackbot;

public class SlackMessagePostException extends RuntimeException {
    public SlackMessagePostException(String message) {
        super(message);
    }

    public SlackMessagePostException(String message, Throwable cause) {
        super(message, cause);
    }
}
