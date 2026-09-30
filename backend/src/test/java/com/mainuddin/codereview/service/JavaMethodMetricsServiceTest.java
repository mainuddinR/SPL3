package com.mainuddin.codereview.service;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaMethodMetricsServiceTest {
    private final JavaMethodMetricsService service = new JavaMethodMetricsService();

    private MethodMetricsResult at(String source, String marker) {
        String[] lines = source.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(marker)) {
                return service.analyze(new VerifiedSourceResult(VerifiedSourceResult.Status.VERIFIED, source), i + 1);
            }
        }
        throw new AssertionError("Marker missing");
    }

    private MethodMetricsResult metric(String statements) {
        return at("class X {\n void m(int n, boolean a, boolean b, boolean c) {\n  // SATD\n" + statements +
                "\n }\n}\n", "// SATD");
    }

    @Test void identifiesMethodAndConstructor() {
        var method = metric("  int x = 1;");
        assertEquals(MethodMetricsResult.Status.AVAILABLE, method.status());
        assertEquals(MethodMetricsResult.Kind.METHOD, method.kind());
        assertEquals("m", method.name());
        assertEquals(2, method.startLine());
        assertEquals(JavaMethodMetricsService.RULE_VERSION, method.metricsRuleVersion());
        var constructor = at("class X {\n X() {\n // SATD\n int x = 1;\n }\n}", "// SATD");
        assertEquals(MethodMetricsResult.Kind.CONSTRUCTOR, constructor.kind());
        assertEquals("X", constructor.name());
        assertEquals(1, constructor.methodLength());
    }

    @Test void doesNotClaimClassFieldOrDeclarationAdjacentComments() {
        String source = "class X {\n // CLASS SATD\n int field; // FIELD SATD\n // BEFORE SATD\n void m() {\n // INSIDE SATD\n int x = 1;\n }\n}";
        assertEquals(MethodMetricsResult.Status.METHOD_NOT_FOUND, at(source, "CLASS SATD").status());
        assertEquals(MethodMetricsResult.Status.METHOD_NOT_FOUND, at(source, "FIELD SATD").status());
        assertEquals(MethodMetricsResult.Status.METHOD_NOT_FOUND, at(source, "BEFORE SATD").status());
        assertEquals(MethodMetricsResult.Status.AVAILABLE, at(source, "INSIDE SATD").status());
    }

    @Test void selectsInnerMethodInNestedLocalAndAnonymousClasses() {
        String nested = "class X {\n void outer() {\n  class Local {\n   void inner() {\n    // INNER SATD\n    if (true) {}\n   }\n  }\n }\n}";
        assertEquals("inner", at(nested, "INNER SATD").name());
        String anonymous = "class X {\n void outer() {\n  Runnable r = new Runnable() {\n   public void run() {\n    // ANON SATD\n    if (true) {}\n   }\n  };\n }\n}";
        assertEquals("run", at(anonymous, "ANON SATD").name());
    }

    @Test void nestedScopeCommentsDoNotBelongToOuterMethod() {
        String lambda = "class X {\n void outer() {\n  Runnable r = () -> {\n   // LAMBDA SATD\n   if (true) {}\n  };\n }\n}";
        assertEquals(MethodMetricsResult.Status.METHOD_NOT_FOUND, at(lambda, "LAMBDA SATD").status());
        String localClass = "class X {\n void outer() {\n  class Local {\n   // LOCAL CLASS SATD\n   int value;\n  }\n }\n}";
        assertEquals(MethodMetricsResult.Status.METHOD_NOT_FOUND, at(localClass, "LOCAL CLASS SATD").status());
    }

    @Test void reportsParseAndPositionFailuresWithoutInventingMetrics() {
        assertEquals(MethodMetricsResult.Status.PARSE_FAILED, at("class X { void m( { // BAD SATD", "BAD SATD").status());
        String source = "class X {\n void m() {\n  // SATD\n }\n}";
        assertEquals(MethodMetricsResult.Status.METHOD_NOT_FOUND,
                service.analyze(new VerifiedSourceResult(VerifiedSourceResult.Status.VERIFIED, source), 99).status());
        assertEquals(MethodMetricsResult.Status.UNSUPPORTED_SOURCE,
                service.analyze(VerifiedSourceResult.of(VerifiedSourceResult.Status.SHA_MISMATCH), 3).status());
        assertEquals(MethodMetricsResult.Status.SOURCE_POSITION_UNAVAILABLE,
                service.measure(new MethodDeclaration(), new BlockStmt(), List.of()).status());
    }

    @Test void lengthExcludesBlankAndCommentOnlyButIncludesInlineCode() {
        String source = "class X {\n void m() {\n // SATD\n\n /* comment\n    only */\n int a = 1; // inline\n // another comment\n a++;\n }\n}";
        var result = at(source, "// SATD");
        assertEquals(MethodMetricsResult.Status.AVAILABLE, result.status());
        assertEquals(2, result.methodLength());
        assertEquals(result, at(source, "// SATD"));
    }

    @Test void nestedScopesDoNotInflateOuterLengthOrComplexity() {
        String source = "class X {\n void outer() {\n  // OUTER SATD\n  int x = 1;\n  Runnable r = () -> { if (true) {} };\n  class Local { void inner() { if (true) {} } }\n  Runnable a = new Runnable() { public void run() { if (true) {} } };\n }\n}";
        var outer = at(source, "OUTER SATD");
        assertEquals(1, outer.complexity());
        assertEquals(3, outer.methodLength());
    }

    @Test void eachDecisionConstructAddsExactlyOne() {
        assertEquals(1, metric("  int x = 1;").complexity());
        assertEquals(2, metric("  if (a) {} ").complexity());
        assertEquals(2, metric("  for (int i=0;i<n;i++) {} ").complexity());
        assertEquals(2, metric("  for (String s : new String[0]) {} ").complexity());
        assertEquals(2, metric("  while (a) {} ").complexity());
        assertEquals(2, metric("  do {} while (a); ").complexity());
        assertEquals(2, metric("  try {} catch (Exception e) {} ").complexity());
        assertEquals(2, metric("  int x = a ? 1 : 2; ").complexity());
        assertEquals(4, metric("  if (a && b && c) {} ").complexity());
        assertEquals(4, metric("  if (a || b || c) {} ").complexity());
    }

    @Test void switchLabelsAndDefaultHaveDocumentedCounts() {
        assertEquals(3, metric("  switch (n) { case 1: case 2: break; default: break; } ").complexity());
        assertEquals(3, metric("  switch (n) { case 1, 2 -> {} default -> {} } ").complexity());
        assertEquals(3, metric("  int x = switch (n) { case 1, 2 -> 1; default -> 0; }; ").complexity());
        assertEquals(1, metric("  switch (n) { default -> {} } ").complexity());
    }

    @Test void combinedConstructsAddDeterministically() {
        var result = metric("  if (a && b) { for (int i=0;i<n;i++) { while (c) {} } }\n" +
                "  int x = a ? 1 : 2;\n  switch (n) { case 1, 2 -> {} default -> {} }");
        assertEquals(8, result.complexity());
        assertEquals(result, metric("  if (a && b) { for (int i=0;i<n;i++) { while (c) {} } }\n" +
                "  int x = a ? 1 : 2;\n  switch (n) { case 1, 2 -> {} default -> {} }"));
    }
}
