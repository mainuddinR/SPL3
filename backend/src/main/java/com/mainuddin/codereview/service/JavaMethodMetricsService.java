package com.mainuddin.codereview.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class JavaMethodMetricsService {
    public static final String RULE_VERSION = "method-metrics-v1";

    public MethodMetricsResult analyze(VerifiedSourceResult verifiedSource, int candidateLine) {
        if (verifiedSource == null || verifiedSource.status() != VerifiedSourceResult.Status.VERIFIED ||
                verifiedSource.source() == null || candidateLine < 1 ||
                verifiedSource.source().getBytes(StandardCharsets.UTF_8).length > GithubSourceRetrievalService.MAX_SOURCE_BYTES) {
            return MethodMetricsResult.unavailable(MethodMetricsResult.Status.UNSUPPORTED_SOURCE);
        }
        var parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        try {
            var parsed = parser.parse(verifiedSource.source());
            if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
                return MethodMetricsResult.unavailable(MethodMetricsResult.Status.PARSE_FAILED);
            }
            var unit = parsed.getResult().orElseThrow();
            List<CallableDeclaration<?>> owners = new ArrayList<>();
            for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
                method.getBody().ifPresent(body -> {
                    if (insideBody(body, candidateLine)) owners.add(method);
                });
            }
            for (ConstructorDeclaration constructor : unit.findAll(ConstructorDeclaration.class)) {
                if (insideBody(constructor.getBody(), candidateLine)) owners.add(constructor);
            }
            if (owners.isEmpty()) return MethodMetricsResult.unavailable(MethodMetricsResult.Status.METHOD_NOT_FOUND);
            owners.sort(Comparator.comparingInt(owner -> body(owner).getRange()
                    .map(range -> range.getLineCount()).orElse(Integer.MAX_VALUE)));
            CallableDeclaration<?> owner = owners.get(0);
            BlockStmt body = body(owner);
            if (body.getRange().isEmpty() || owner.getRange().isEmpty()) {
                return MethodMetricsResult.unavailable(MethodMetricsResult.Status.SOURCE_POSITION_UNAVAILABLE);
            }
            List<Range> independentScopes = independentScopes(body);
            if (independentScopes.stream().anyMatch(range -> range.begin.line <= candidateLine && candidateLine <= range.end.line)) {
                return MethodMetricsResult.unavailable(MethodMetricsResult.Status.METHOD_NOT_FOUND);
            }
            return measure(owner, body, independentScopes);
        } catch (RuntimeException failure) {
            return MethodMetricsResult.unavailable(MethodMetricsResult.Status.PARSE_FAILED);
        }
    }

    private static boolean insideBody(BlockStmt body, int line) {
        // Line-only input cannot locate a comment on a brace line. Declaration-adjacent comments stay unowned.
        return body.getRange().map(range -> line > range.begin.line && line < range.end.line).orElse(false);
    }

    private static BlockStmt body(CallableDeclaration<?> owner) {
        return owner instanceof MethodDeclaration method ? method.getBody().orElseThrow()
                : ((ConstructorDeclaration) owner).getBody();
    }

    private static List<Range> independentScopes(BlockStmt body) {
        List<Range> ranges = new ArrayList<>();
        body.findAll(LambdaExpr.class).forEach(node -> node.getRange().ifPresent(ranges::add));
        body.findAll(TypeDeclaration.class).forEach(node -> node.getRange().ifPresent(ranges::add));
        body.findAll(ObjectCreationExpr.class).stream().filter(node -> node.getAnonymousClassBody().isPresent())
                .forEach(node -> node.getRange().ifPresent(ranges::add));
        return ranges;
    }

    MethodMetricsResult measure(CallableDeclaration<?> owner, BlockStmt body, List<Range> independentScopes) {
        if (owner.getRange().isEmpty() || body.getRange().isEmpty() || body.getTokenRange().isEmpty()) {
            return MethodMetricsResult.unavailable(MethodMetricsResult.Status.SOURCE_POSITION_UNAVAILABLE);
        }
        Set<Integer> codeLines = new HashSet<>();
        for (JavaToken token : body.getTokenRange().orElseThrow()) {
            if (token.getCategory().isWhitespaceOrComment() || token.getText().equals("{") || token.getText().equals("}")) continue;
            if (token.getRange().isEmpty()) {
                return MethodMetricsResult.unavailable(MethodMetricsResult.Status.SOURCE_POSITION_UNAVAILABLE);
            }
            Range tokenRange = token.getRange().orElseThrow();
            if (independentScopes.stream().anyMatch(range -> range.contains(tokenRange))) continue;
            codeLines.add(tokenRange.begin.line);
            // A text block's interior is data, not Java code; only its delimiter lines count.
            codeLines.add(tokenRange.end.line);
        }
        Range declarationRange = owner.getRange().orElseThrow();
        return new MethodMetricsResult(MethodMetricsResult.Status.AVAILABLE, owner.getNameAsString(),
                owner instanceof ConstructorDeclaration ? MethodMetricsResult.Kind.CONSTRUCTOR : MethodMetricsResult.Kind.METHOD,
                declarationRange.begin.line, declarationRange.end.line, codeLines.size(),
                1 + decisions(body), RULE_VERSION);
    }

    private static int decisions(Node node) {
        if (node instanceof LambdaExpr || node instanceof TypeDeclaration<?> ||
                node instanceof ObjectCreationExpr creation && creation.getAnonymousClassBody().isPresent()) return 0;
        int count = 0;
        if (node instanceof IfStmt || node instanceof ForStmt || node instanceof ForEachStmt ||
                node instanceof WhileStmt || node instanceof DoStmt || node instanceof CatchClause ||
                node instanceof ConditionalExpr) count++;
        if (node instanceof SwitchEntry entry) count += entry.getLabels().size(); // Default has no label.
        if (node instanceof BinaryExpr binary && (binary.getOperator() == BinaryExpr.Operator.AND ||
                binary.getOperator() == BinaryExpr.Operator.OR)) count++;
        for (Node child : node.getChildNodes()) count += decisions(child);
        return count;
    }
}
