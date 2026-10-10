package com.mainuddin.codereview.service;

/**
 * Structural evidence from a verified Java source snapshot.
 * Unavailable evidence must never produce a new SATD debt category.
 */
public record JavaCategoryEvidence(
        boolean verified,
        Integer methodLength,
        Integer complexity,
        boolean publicMethodWithoutJavadoc,
        String methodName
) {
    public static JavaCategoryEvidence unavailable() {
        return new JavaCategoryEvidence(false, null, null, false, null);
    }
}
