package com.mainuddin.codereview.design;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.List;

final class PatternRuleSupport {
    private PatternRuleSupport() {}
    static PatternCandidate candidate(String type, String family, String confidence, String rationale,
            String reason, PatternCandidate.Evidence... evidence) {
        return new PatternCandidate(type, family, confidence, rationale, reason, List.of(evidence));
    }
    static boolean callsOn(JavaTypeIndex.Type type, String fieldOrParameter, String method) {
        return type.calls().stream().anyMatch(call -> call.getNameAsString().equals(method)
                && call.getScope().map(scope -> scope.toString().equals(fieldOrParameter)).orElse(false));
    }
    static boolean calls(JavaTypeIndex.Type type, String method) {
        return type.calls().stream().anyMatch(call -> call.getNameAsString().equals(method));
    }
    static int line(MethodDeclaration method) { return method.getBegin().map(value -> value.line).orElse(1); }
    static int line(MethodCallExpr call) { return call.getBegin().map(value -> value.line).orElse(1); }
}
