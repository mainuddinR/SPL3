package com.mainuddin.codereview.design;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A single-parse, repository-wide index. References are resolved only when unambiguous. */
public record JavaTypeIndex(List<Type> types, Map<String, Type> byQualifiedName, Map<String, List<Type>> bySimpleName,
                            int javaFileCount) {
    public record Type(String packageName, String path, String name, TypeDeclaration<?> ast,
                       Set<String> extendsTypes, Set<String> implementsTypes, Set<String> annotations,
                       Set<String> fieldTypes, Set<String> constructorParameterTypes,
                       List<MethodDeclaration> methods, List<MethodCallExpr> calls,
                       List<ObjectCreationExpr> creations) {
        public String qualifiedName() {
            String nested = name;
            var parent = ast.findAncestor(TypeDeclaration.class);
            while (parent.isPresent()) { nested = parent.get().getNameAsString() + "." + nested; parent = parent.get().findAncestor(TypeDeclaration.class); }
            return packageName.isBlank() ? nested : packageName + "." + nested;
        }
        public int line() { return ast.getBegin().map(position -> position.line).orElse(1); }
        public boolean isInterface() { return ast instanceof ClassOrInterfaceDeclaration value && value.isInterface(); }
        public boolean isAbstract() { return ast instanceof ClassOrInterfaceDeclaration value && value.isAbstract(); }
        public boolean isEnum() { return ast instanceof EnumDeclaration; }
        public boolean hasAnnotation(String simpleName) { return annotations.contains(simpleName); }
        public boolean dependsOn(String simpleName) {
            return fieldTypes.contains(simpleName) || constructorParameterTypes.contains(simpleName);
        }
        public boolean implementsOrExtends(String simpleName) {
            return implementsTypes.contains(simpleName) || extendsTypes.contains(simpleName);
        }
    }

    public record Relationship(String owner, String target, String kind) {}

    /** Distinct repository-local dependency edges by declaration kind; primitives/external types are excluded. */
    public Set<Relationship> declaredRelationships() {
        Set<Relationship> edges = new java.util.HashSet<>();
        for (Type owner : types) {
            if (owner.ast() instanceof ClassOrInterfaceDeclaration c) {
                c.getExtendedTypes().forEach(t -> addRelationship(edges, owner, t, "EXTENDS"));
                c.getImplementedTypes().forEach(t -> addRelationship(edges, owner, t, "IMPLEMENTS"));
            } else if (owner.ast() instanceof EnumDeclaration e) {
                e.getImplementedTypes().forEach(t -> addRelationship(edges, owner, t, "IMPLEMENTS"));
            }
            owner.ast().getFields().forEach(f -> f.getVariables().forEach(v -> addRelationship(edges, owner, v.getType(), "FIELD")));
            owner.ast().getConstructors().forEach(c -> c.getParameters().forEach(p -> addRelationship(edges, owner, p.getType(), "CONSTRUCTOR")));
            if (owner.ast() instanceof com.github.javaparser.ast.body.RecordDeclaration r)
                r.getParameters().forEach(p -> addRelationship(edges, owner, p.getType(), "CONSTRUCTOR"));
            owner.methods().forEach(m -> {
                addRelationship(edges, owner, m.getType(), "METHOD_RETURN");
                m.getParameters().forEach(p -> addRelationship(edges, owner, p.getType(), "METHOD_PARAMETER"));
            });
        }
        return Set.copyOf(edges);
    }
    private void addRelationship(Set<Relationship> edges, Type owner, com.github.javaparser.ast.type.Type declaration, String kind) {
        declaration.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class).forEach(reference -> {
            Type target = resolve(owner, reference.getNameWithScope());
            if (target != null && target != owner) edges.add(new Relationship(owner.qualifiedName(), target.qualifiedName(), kind));
        });
    }

    public Type unique(String simpleName) {
        List<Type> matches = bySimpleName.getOrDefault(simpleName, List.of());
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    public Type resolve(Type owner, String reference) {
        String name = reference.replaceAll("<.*", "").replace("[]", "").trim();
        if (name.contains(".")) return byQualifiedName.get(name);
        var unit = owner.ast().findCompilationUnit().orElseThrow();
        var explicit = unit.getImports().stream().filter(value -> !value.isStatic() && !value.isAsterisk()
                && value.getNameAsString().endsWith("." + name)).toList();
        if (!explicit.isEmpty()) return explicit.size() == 1 ? byQualifiedName.get(explicit.getFirst().getNameAsString()) : null;
        var local = types.stream().filter(value -> value.name().equals(name) && value.packageName().equals(owner.packageName())).toList();
        if (!local.isEmpty()) {
            var sameFile = local.stream().filter(value -> value.path().equals(owner.path())).toList();
            return sameFile.size() == 1 ? sameFile.getFirst() : local.size() == 1 ? local.getFirst() : null;
        }
        var wildcard = unit.getImports().stream().filter(value -> !value.isStatic() && value.isAsterisk())
                .map(value -> byQualifiedName.get(value.getNameAsString() + "." + name)).filter(java.util.Objects::nonNull).distinct().toList();
        return wildcard.size() == 1 ? wildcard.getFirst() : null;
    }

    public boolean dependsOn(Type owner, Type target) {
        return java.util.stream.Stream.concat(owner.fieldTypes().stream(), owner.constructorParameterTypes().stream())
                .anyMatch(name -> resolve(owner, name) == target);
    }

    public boolean implementsOrExtends(Type owner, Type target) {
        return java.util.stream.Stream.concat(owner.extendsTypes().stream(), owner.implementsTypes().stream())
                .anyMatch(name -> resolve(owner, name) == target);
    }
}
