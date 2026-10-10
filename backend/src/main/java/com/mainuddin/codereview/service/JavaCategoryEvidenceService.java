package com.mainuddin.codereview.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Service;

import java.util.Comparator;

/**
 * Collects additional category evidence from the already-verified GitHub source.
 * A missing JavaDoc is only evidence for an explicitly documentation-oriented
 * comment; ordinary undocumented methods are not classified as debt.
 */
@Service
public class JavaCategoryEvidenceService {

    public JavaCategoryEvidence inspect(VerifiedSourceResult verifiedSource,
                                        int candidateLine,
                                        MethodMetricsResult metrics) {
        if (verifiedSource == null || verifiedSource.status() != VerifiedSourceResult.Status.VERIFIED
                || verifiedSource.source() == null || candidateLine < 1) {
            return JavaCategoryEvidence.unavailable();
        }

        Integer length = metrics != null && metrics.status() == MethodMetricsResult.Status.AVAILABLE
                ? metrics.methodLength() : null;
        Integer complexity = metrics != null && metrics.status() == MethodMetricsResult.Status.AVAILABLE
                ? metrics.complexity() : null;
        String methodName = metrics != null && metrics.status() == MethodMetricsResult.Status.AVAILABLE
                ? metrics.name() : null;

        boolean undocumentedPublicMethod = false;
        try {
            var parser = new JavaParser(
                    new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
            var parsed = parser.parse(verifiedSource.source());
            if (parsed.isSuccessful() && parsed.getResult().isPresent()) {
                var unit = parsed.getResult().orElseThrow();
                var nearest = unit.findAll(MethodDeclaration.class).stream()
                        .filter(MethodDeclaration::isPublic)
                        .filter(method -> method.getRange().isPresent())
                        .filter(method -> {
                            var range = method.getRange().orElseThrow();
                            return candidateLine >= Math.max(1, range.begin.line - 2)
                                    && candidateLine <= range.end.line;
                        })
                        .min(Comparator.comparingInt(method ->
                                method.getRange().orElseThrow().getLineCount()));
                if (nearest.isPresent()) {
                    var method = nearest.orElseThrow();
                    undocumentedPublicMethod = method.getJavadocComment().isEmpty();
                    if (methodName == null) methodName = method.getNameAsString();
                }
            }
        } catch (RuntimeException ignored) {
            // Validated metrics are still useful if the second AST parse fails.
        }
        return new JavaCategoryEvidence(true, length, complexity,
                undocumentedPublicMethod, methodName);
    }
}
