package com.mainuddin.codereview.design;

import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Structural fingerprint, not a runtime-risk score. Ordinary literals/arithmetic/comments are excluded. */
final class JavaDesignChangeImpact {
    private JavaDesignChangeImpact() {}

    static Set<String> signals(JavaTypeIndex.Type type) {
        Set<String> signals = new TreeSet<>();
        signals.add("kind:" + type.ast().getClass().getSimpleName());
        type.extendsTypes().forEach(v -> signals.add("extends:" + v));
        type.implementsTypes().forEach(v -> signals.add("implements:" + v));
        type.annotations().forEach(v -> signals.add("annotation:" + v));
        type.fieldTypes().forEach(v -> signals.add("field:" + v));
        type.constructorParameterTypes().forEach(v -> signals.add("constructor:" + v));
        type.methods().forEach(m -> signals.add("method:" + declaration(m)));
        type.calls().forEach(c -> signals.add("call:" + c.getScope().map(Object::toString).orElse("")
                + "." + c.getNameAsString() + "/" + c.getArguments().size()));
        type.creations().forEach(c -> signals.add("new:" + c.getType().asString()));
        return signals;
    }

    private static String declaration(MethodDeclaration m) {
        return m.getModifiers() + ":" + m.getType().asString() + ":" + m.getNameAsString()
                + m.getParameters().stream().map(p -> p.getType().asString()).toList();
    }

    static boolean sameRelevantStructure(PatternCandidate pattern, JavaTypeIndex head, JavaTypeIndex base, String path) {
        List<JavaTypeIndex.Type> before = base.types().stream().filter(t -> t.path().equals(path)).toList();
        List<JavaTypeIndex.Type> after = head.types().stream().filter(t -> t.path().equals(path)).toList();
        if (before.isEmpty() || before.size() != after.size()) return false;
        for (var old : before) {
            var now = after.stream().filter(t -> t.qualifiedName().equals(old.qualifiedName())).findFirst();
            if (now.isEmpty() || !signals(old).equals(signals(now.get()))) return false;
            // Pattern-specific conditional selections need separate comparison even when calls are unchanged.
            if ("STRATEGY".equals(pattern.patternType()) && !branches(old).equals(branches(now.get()))) return false;
        }
        return true;
    }

    static Set<String> branches(JavaTypeIndex.Type type) {
        Set<String> result = new TreeSet<>();
        type.ast().findAll(com.github.javaparser.ast.stmt.IfStmt.class).stream()
                .filter(b -> !b.getCondition().findAll(com.github.javaparser.ast.expr.StringLiteralExpr.class).isEmpty())
                .forEach(b -> result.add(b.getCondition().toString().replaceAll("\\s+", "")));
        type.ast().findAll(com.github.javaparser.ast.stmt.SwitchStmt.class).stream()
                .filter(b -> b.getEntries().stream().anyMatch(e -> e.getLabels().stream().anyMatch(l -> l.isStringLiteralExpr())))
                .forEach(b -> result.add(b.getSelector() + ":" + b.getEntries().stream().flatMap(e -> e.getLabels().stream()).map(Object::toString).sorted().toList()));
        return result;
    }
}
