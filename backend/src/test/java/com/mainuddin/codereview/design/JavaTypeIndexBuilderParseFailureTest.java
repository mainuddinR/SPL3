package com.mainuddin.codereview.design;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JavaTypeIndexBuilderParseFailureTest {
    @Test
    void identifiesBadJavaSourceBySafeRepositoryRelativePathWithoutLeakingSource() {
        String invalid = "package sample; public class Broken { void f( { SECRET_SOURCE_MARKER }";
        var failure = assertThrows(DesignSourceException.class,
                () -> new JavaTypeIndexBuilder().build(
                        Map.of("src/main/java/sample/Broken.java", invalid)));
        assertEquals("SOURCE_PARSE_FAILED", failure.code());
        assertTrue(failure.getReason().contains("src/main/java/sample/Broken.java"));
        assertTrue(failure.getReason().contains("invalid or unsupported Java syntax"));
        assertFalse(failure.getReason().contains("SECRET_SOURCE_MARKER"));
    }

    @Test
    void validRepositoryJavaSourceStillBuildsItsAst() {
        var index = new JavaTypeIndexBuilder().build(Map.of(
                "src/main/java/sample/UserService.java",
                "package sample; public class UserService { public int count() { return 1; } }"));
        assertEquals(1, index.javaFileCount());
        assertEquals(1, index.types().size());
    }
}

