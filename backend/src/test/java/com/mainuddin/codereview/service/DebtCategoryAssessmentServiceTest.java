package com.mainuddin.codereview.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DebtCategoryAssessmentServiceTest {
    private final DebtCategoryAssessmentService assessor = new DebtCategoryAssessmentService();

    private CategoryAssessment assess(String comment) {
        return assessor.assess(comment, "src/Main.java", "class Main {", "int value;");
    }

    @Test void recognizesExplicitProposalCategories() {
        assertEquals("DESIGN", assess("// TODO: refactor this tightly coupled design").category());
        assertEquals("DEFECT", assess("// FIXME: incorrect result for empty input").category());
        assertEquals("TEST", assess("// TODO: missing regression tests for empty input").category());
        assertEquals("REQUIREMENT", assess("// TODO: missing required feature for export").category());
        assertEquals("DOCUMENTATION", assess("// TODO: outdated API documentation").category());
    }

    @Test void genericMarkersDoNotBecomeCategories() {
        assertEquals("UNCLASSIFIED", assess("// TODO fix this").category());
        assertEquals("UNCLASSIFIED", assess("// FIXME later").category());
        assertEquals("UNCLASSIFIED", assess("// HACK temporary").category());
    }

    @Test void pathAndJavadocAloneDoNotDecide() {
        assertEquals("UNCLASSIFIED", assessor.assess("// TODO fix this", "src/test/java/ThingTest.java", "@Test", "").category());
        assertEquals("UNCLASSIFIED", assessor.assess("/** Calculates total. */", "src/Main.java", "", "").category());
        assertEquals("DOCUMENTATION", assessor.assess("/** Missing JavaDoc for public API. */", "src/Main.java", "", "").category());
    }

    @Test void contextCorroboratesButDoesNotCreateCategory() {
        var withContext = assessor.assess("// missing unit tests", "src/test/java/ThingTest.java", "@Test", "");
        var withoutContext = assess("// missing unit tests");
        assertEquals("TEST", withContext.category());
        assertEquals("TEST", withoutContext.category());
        assertTrue(withContext.reason().contains("corroborates"));
        assertFalse(withoutContext.reason().contains("corroborates"));
    }

    @Test void workaroundAndConflictingEvidenceAbstain() {
        assertEquals("UNCLASSIFIED", assess("// workaround for now").category());
        assertEquals("UNCLASSIFIED", assess("// structural workaround for broken behavior").category());
        assertEquals("UNCLASSIFIED", assess("// refactor this; missing unit tests").category());
    }

    @Test void repeatedAssessmentIsDeterministicAndVersioned() {
        var first = assess("// TODO: outdated API documentation");
        assertEquals(first, assess("// TODO: outdated API documentation"));
        assertEquals(CategoryAssessment.RULE_VERSION, first.ruleVersion());
        var unknown = assess("// TODO fix this");
        assertNotNull(unknown.reason());
        assertEquals(CategoryAssessment.RULE_VERSION, unknown.ruleVersion());
    }
}
