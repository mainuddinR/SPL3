package com.mainuddin.codereview.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaCategoryEvidenceServiceTest {
    private final JavaCategoryEvidenceService inspector = new JavaCategoryEvidenceService();
    private final DebtCategoryAssessmentService categorizer = new DebtCategoryAssessmentService();

    private JavaCategoryEvidence inspect(String source, int line, MethodMetricsResult metrics) {
        return inspector.inspect(new VerifiedSourceResult(VerifiedSourceResult.Status.VERIFIED, source),
                line, metrics);
    }

    @Test
    void recognizesUndocumentedPublicMethodNearVerifiedCandidate() {
        String java = """
                class Example {
                    // TODO document this public method
                    public void calculate() {}
                }
                """;
        JavaCategoryEvidence evidence = inspect(java, 2,
                MethodMetricsResult.unavailable(MethodMetricsResult.Status.METHOD_NOT_FOUND));
        assertTrue(evidence.verified());
        assertTrue(evidence.publicMethodWithoutJavadoc());
        assertEquals("calculate", evidence.methodName());

        CategoryAssessment category = categorizer.assess("// TODO document this public method",
                "src/Example.java", "", "", evidence);
        assertEquals("DOCUMENTATION", category.category());
        assertTrue(category.reason().contains("without JavaDoc"));
    }

    @Test
    void existingJavadocPreventsInferredDocumentationDebt() {
        String java = """
                class Example {
                    // TODO document this public method
                    /** Already documented. */
                    public void calculate() {}
                }
                """;
        JavaCategoryEvidence evidence = inspect(java, 2,
                MethodMetricsResult.unavailable(MethodMetricsResult.Status.METHOD_NOT_FOUND));
        assertTrue(evidence.verified());
        assertFalse(evidence.publicMethodWithoutJavadoc());
        assertEquals("UNCLASSIFIED", categorizer.assess(
                "// TODO document this public method", "src/Example.java", "", "", evidence).category());
    }

    @Test
    void ignoresUnverifiedSourceAndCommentsFarFromMethod() {
        String java = """
                class Example {
                    // TODO document this public method
                    int x;
                    int y;
                    int z;
                    public void calculate() {}
                }
                """;
        JavaCategoryEvidence distant = inspect(java, 2,
                MethodMetricsResult.unavailable(MethodMetricsResult.Status.METHOD_NOT_FOUND));
        assertFalse(distant.publicMethodWithoutJavadoc());
        var rejected = inspector.inspect(VerifiedSourceResult.of(
                VerifiedSourceResult.Status.SHA_MISMATCH), 2, null);
        assertFalse(rejected.verified());
        assertFalse(rejected.publicMethodWithoutJavadoc());
    }

    @Test
    void usesAvailableVerifiedMethodMetricsForHybridDesign() {
        var metrics = new MethodMetricsResult(MethodMetricsResult.Status.AVAILABLE,
                "calculate", MethodMetricsResult.Kind.METHOD, 3, 93, 78, 12,
                JavaMethodMetricsService.RULE_VERSION);
        String java = """
                class Example {
                    public void calculate() {
                        // TODO simplify this method
                    }
                }
                """;
        JavaCategoryEvidence evidence = inspect(java, 3, metrics);
        assertTrue(evidence.verified());
        assertEquals(78, evidence.methodLength());
        assertEquals(12, evidence.complexity());
        assertEquals("DESIGN", categorizer.assess("// TODO simplify this method",
                "src/Example.java", "", "", evidence).category());
    }
}
