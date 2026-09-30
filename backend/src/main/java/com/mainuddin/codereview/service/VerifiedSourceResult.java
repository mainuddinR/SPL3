package com.mainuddin.codereview.service;

public record VerifiedSourceResult(Status status, String source) {
    public enum Status { VERIFIED, UNAVAILABLE, SHA_MISMATCH, UNSUPPORTED, API_FAILURE, LOCATION_MISMATCH }

    public static VerifiedSourceResult of(Status status) {
        return new VerifiedSourceResult(status, null);
    }
}
