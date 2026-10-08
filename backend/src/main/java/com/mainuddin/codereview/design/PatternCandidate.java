package com.mainuddin.codereview.design;

import java.util.List;

public record PatternCandidate(String patternType, String patternFamily, String confidence,
                               String rationale, String confidenceReason, List<Evidence> evidence) {
    public record Evidence(String role, String className, String sourcePath, String methodName, Integer line) {
        public static Evidence of(String role, JavaTypeIndex.Type type) {
            return new Evidence(role, type.qualifiedName(), type.path(), null, type.line());
        }
        public static Evidence method(String role, JavaTypeIndex.Type type, String method, Integer line) {
            return new Evidence(role, type.qualifiedName(), type.path(), method, line);
        }
    }
}
