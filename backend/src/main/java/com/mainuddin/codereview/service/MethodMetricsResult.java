package com.mainuddin.codereview.service;

public record MethodMetricsResult(
        Status status,
        String name,
        Kind kind,
        Integer startLine,
        Integer endLine,
        Integer methodLength,
        Integer complexity,
        String metricsRuleVersion
) {
    public enum Status { AVAILABLE, METHOD_NOT_FOUND, PARSE_FAILED, SOURCE_POSITION_UNAVAILABLE, UNSUPPORTED_SOURCE }
    public enum Kind { METHOD, CONSTRUCTOR }

    public static MethodMetricsResult unavailable(Status status) {
        return new MethodMetricsResult(status, null, null, null, null, null, null, null);
    }
}
