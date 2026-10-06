package com.mainuddin.codereview.service;

import org.springframework.http.HttpStatus;

public class GithubDiscoveryException extends RuntimeException {
    private final HttpStatus status;
    public GithubDiscoveryException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
