package com.musicai.agent.api;

public class InvalidAccessKeyException extends RuntimeException {

    public InvalidAccessKeyException() {
        super("Invalid project access key");
    }
}
