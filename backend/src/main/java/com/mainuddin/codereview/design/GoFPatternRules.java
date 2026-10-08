package com.mainuddin.codereview.design;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Conservative structural rules. Names alone never create an occurrence. */
public final class GoFPatternRules {
    private GoFPatternRules() {}

    public static List<DesignPatternRule> all() {
        return List.of(new Strategy(), new Factory(), new Builder(), new Singleton(),
                new Observer(), new Adapter(), new TemplateMethod(), new Decorator());
    }

    static final class Strategy implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var abstraction : index.types()) {
                if (!abstraction.isInterface() && !abstraction.isAbstract()) continue;
                var implementations = index.types().stream().filter(type -> !type.isInterface() && !type.isAbstract()
                        && index.implementsOrExtends(type, abstraction)).toList();
                if (implementations.size() < 2 || abstraction.methods().isEmpty()) continue;
                for (var context : index.types()) {
                    if (context == abstraction || implementations.contains(context) || !index.dependsOn(context, abstraction)) continue;
                    boolean delegates = context.ast().getFields().stream()
                            .filter(field -> field.getVariables().stream().anyMatch(v ->
                                    JavaTypeIndexBuilder.simple(v.getType().asString()).equals(abstraction.name())))
                            .flatMap(field -> field.getVariables().stream())
                            .anyMatch(variable -> abstraction.methods().stream().anyMatch(method ->
                                    PatternRuleSupport.callsOn(context, variable.getNameAsString(), method.getNameAsString())));
                    if (!delegates) delegates = context.ast().getConstructors().stream()
                            .flatMap(ctor -> ctor.getParameters().stream())
                            .filter(param -> JavaTypeIndexBuilder.simple(param.getType().asString()).equals(abstraction.name()))
                            .anyMatch(param -> abstraction.methods().stream().anyMatch(method ->
                                    PatternRuleSupport.callsOn(context, param.getNameAsString(), method.getNameAsString())));
                    List<PatternCandidate.Evidence> evidence = new ArrayList<>();
                    evidence.add(PatternCandidate.Evidence.of("Strategy", abstraction));
                    implementations.forEach(type -> evidence.add(PatternCandidate.Evidence.of("ConcreteStrategy", type)));
                    evidence.add(PatternCandidate.Evidence.of("Context", context));
                    results.add(new PatternCandidate("STRATEGY", "GOF", delegates ? "HIGH" : "MEDIUM",
                            context.name() + " depends on " + abstraction.name() + " with " + implementations.size()
                                    + " interchangeable implementations.", delegates
                                    ? "Abstraction, multiple implementations, context dependency and delegated call."
                                    : "Abstraction, multiple implementations and context dependency; delegation is not established.",
                            List.copyOf(evidence)));
                }
            }
            return results;
        }
    }

    static final class Factory implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var creator : index.types()) for (var method : creator.methods()) {
                var product = index.resolve(creator, method.getType().asString());
                if (product == null || (!product.isInterface() && !product.isAbstract()) || method.getBody().isEmpty()) continue;
                var concrete = method.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class).stream()
                        .map(expr -> index.resolve(creator, expr.getType().asString()))
                        .filter(type -> type != null && index.implementsOrExtends(type, product))
                        .distinct().toList();
                if (concrete.isEmpty()) continue;
                boolean caller = index.types().stream().filter(type -> type != creator && index.dependsOn(type, creator))
                        .anyMatch(type -> type.calls().stream().anyMatch(call ->
                                call.getNameAsString().equals(method.getNameAsString()) && call.getScope().isPresent()
                                && (call.getScope().orElseThrow().toString().equals(creator.name())
                                || type.ast().getFields().stream().flatMap(field -> field.getVariables().stream())
                                .filter(variable -> JavaTypeIndexBuilder.simple(variable.getType().asString()).equals(creator.name()))
                                .anyMatch(variable -> call.getScope().orElseThrow().toString().equals(variable.getNameAsString())))));
                if (!caller) continue;
                List<PatternCandidate.Evidence> evidence = new ArrayList<>();
                evidence.add(PatternCandidate.Evidence.method("Creator", creator, method.getNameAsString(), PatternRuleSupport.line(method)));
                evidence.add(PatternCandidate.Evidence.of("Product", product));
                concrete.forEach(type -> evidence.add(PatternCandidate.Evidence.of("ConcreteProduct", type)));
                results.add(new PatternCandidate("FACTORY_METHOD", "GOF", concrete.size() > 1 ? "HIGH" : "MEDIUM",
                        creator.name() + "." + method.getNameAsString() + " creates concrete products behind " + product.name() + ".",
                        concrete.size() > 1 ? "Abstract return, multiple concrete creations and a caller."
                                : "Abstract return, concrete creation and a caller.", List.copyOf(evidence)));
            }
            return results;
        }
    }

    static final class Builder implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var builder : index.types()) {
                var buildMethods = builder.methods().stream().filter(method -> method.getParameters().isEmpty()
                        && method.getBody().isPresent())
                        .filter(method -> {
                            String target = JavaTypeIndexBuilder.simple(method.getType().asString());
                            return !target.equals(builder.name()) && index.resolve(builder, method.getType().asString()) != null
                                    && method.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class).stream()
                                    .anyMatch(expr -> JavaTypeIndexBuilder.simple(expr.getType().asString()).equals(target));
                        }).toList();
                long fluent = builder.methods().stream().filter(method ->
                        JavaTypeIndexBuilder.simple(method.getType().asString()).equals(builder.name())
                                && !method.getParameters().isEmpty()
                                && method.findAll(ReturnStmt.class).stream().anyMatch(ret ->
                                        ret.getExpression().map(expr -> expr.isThisExpr()).orElse(false))).count();
                if (buildMethods.isEmpty() || fluent < 2) continue;
                var method = buildMethods.getFirst();
                var target = index.resolve(builder, method.getType().asString());
                results.add(PatternRuleSupport.candidate("BUILDER", "GOF", "HIGH",
                        builder.name() + " configures and constructs " + target.name() + ".",
                        "At least two fluent configuration methods return this; a build method constructs the target.",
                        PatternCandidate.Evidence.of("Builder", builder), PatternCandidate.Evidence.of("Product", target),
                        PatternCandidate.Evidence.method("BuildMethod", builder, method.getNameAsString(), PatternRuleSupport.line(method))));
            }
            return results;
        }
    }

    static final class Singleton implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var type : index.types()) {
                if (type.isEnum()) {
                    var enumeration = (com.github.javaparser.ast.body.EnumDeclaration) type.ast();
                    if (enumeration.getEntries().size() == 1 && enumeration.getEntry(0).getNameAsString().equals("INSTANCE"))
                        results.add(PatternRuleSupport.candidate("SINGLETON", "GOF", "HIGH",
                                type.name() + " has one enum instance.", "A single enum INSTANCE is language-enforced.",
                                PatternCandidate.Evidence.of("Singleton", type)));
                    continue;
                }
                if (!(type.ast() instanceof ClassOrInterfaceDeclaration declaration) || declaration.isInterface()) continue;
                boolean privateCtor = declaration.getConstructors().stream().anyMatch(ctor -> ctor.isPrivate());
                var instances = declaration.getFields().stream().filter(field -> field.isStatic())
                        .flatMap(field -> field.getVariables().stream())
                        .filter(variable -> JavaTypeIndexBuilder.simple(variable.getType().asString()).equals(type.name()))
                        .map(variable -> variable.getNameAsString()).toList();
                boolean staticInstance = !instances.isEmpty();
                boolean accessor = type.methods().stream().anyMatch(method -> method.isPublic() && method.isStatic()
                        && JavaTypeIndexBuilder.simple(method.getType().asString()).equals(type.name())
                        && method.findAll(ReturnStmt.class).stream().anyMatch(ret ->
                        ret.getExpression().map(expr -> instances.contains(expr.toString())).orElse(false)));
                if (privateCtor && staticInstance && accessor)
                    results.add(PatternRuleSupport.candidate("SINGLETON", "GOF", "HIGH",
                            type.name() + " restricts construction and exposes a static instance.",
                            "Private constructor, static same-type field and public static same-type accessor.",
                            PatternCandidate.Evidence.of("Singleton", type)));
            }
            return results;
        }
    }

    static final class Observer implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var subject : index.types()) {
                for (var field : subject.ast().getFields()) for (var variable : field.getVariables()) {
                    String declared = variable.getType().asString();
                    if (!declared.matches(".*(?:List|Set|Collection)<[^>]+>.*")) continue;
                    String observerName = JavaTypeIndexBuilder.simple(declared.replaceAll(".*<([^>]+)>.*", "$1"));
                    var observer = index.resolve(subject, declared.replaceAll(".*<([^>]+)>.*", "$1"));
                    if (observer == null || (!observer.isInterface() && !observer.isAbstract()) || observer.methods().isEmpty()) continue;
                    String collection = variable.getNameAsString();
                    boolean register = subject.calls().stream().anyMatch(call -> call.getNameAsString().equals("add")
                            && call.getScope().map(scope -> scope.toString().equals(collection)).orElse(false));
                    boolean remove = subject.calls().stream().anyMatch(call -> call.getNameAsString().equals("remove")
                            && call.getScope().map(scope -> scope.toString().equals(collection)).orElse(false));
                    boolean notifyLoop = subject.ast().findAll(com.github.javaparser.ast.stmt.ForEachStmt.class).stream()
                            .anyMatch(loop -> loop.getIterable().toString().equals(collection)
                                    && loop.findAll(MethodCallExpr.class).stream().anyMatch(call -> observer.methods().stream()
                                    .anyMatch(method -> method.getNameAsString().equals(call.getNameAsString()))));
                    if (register && remove && notifyLoop)
                        results.add(PatternRuleSupport.candidate("OBSERVER", "GOF", "HIGH",
                                subject.name() + " manages and notifies " + observer.name() + " observers.",
                                "Observer abstraction, collection, add/remove and callback inside an iteration.",
                                PatternCandidate.Evidence.of("Subject", subject), PatternCandidate.Evidence.of("Observer", observer)));
                }
            }
            return results;
        }
    }

    static final class Adapter implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var adapter : index.types()) {
                for (String targetName : adapter.implementsTypes()) {
                    var target = index.resolve(adapter, targetName);
                    if (target == null || !target.isInterface()) continue;
                    for (String adapteeName : adapter.fieldTypes()) {
                        var adaptee = index.resolve(adapter, adapteeName);
                        if (adaptee == null || adaptee == adapter || adaptee == target || index.implementsOrExtends(adaptee, target)
                                || !adapter.constructorParameterTypes().contains(adapteeName)) continue;
                        boolean translation = adapter.methods().stream().anyMatch(method -> target.methods().stream()
                                .anyMatch(contract -> contract.getNameAsString().equals(method.getNameAsString()))
                                && method.findAll(MethodCallExpr.class).stream().anyMatch(call ->
                                adapter.ast().getFields().stream().flatMap(field -> field.getVariables().stream())
                                        .filter(variable -> JavaTypeIndexBuilder.simple(variable.getType().asString()).equals(adapteeName))
                                        .anyMatch(variable -> call.getScope().map(scope -> scope.toString().equals(variable.getNameAsString())).orElse(false))));
                        if (translation)
                            results.add(PatternRuleSupport.candidate("ADAPTER", "GOF", "HIGH",
                                    adapter.name() + " translates " + target.name() + " calls to " + adaptee.name() + ".",
                                    "Implements target, receives a distinct adaptee and delegates target method to it.",
                                    PatternCandidate.Evidence.of("Adapter", adapter), PatternCandidate.Evidence.of("Target", target),
                                    PatternCandidate.Evidence.of("Adaptee", adaptee)));
                    }
                }
            }
            return results;
        }
    }

    static final class TemplateMethod implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var base : index.types()) {
                if (!base.isAbstract()) continue;
                var hooks = base.methods().stream().filter(MethodDeclaration::isAbstract).toList();
                if (hooks.isEmpty()) continue;
                var subclasses = index.types().stream().filter(type -> !type.isAbstract() && index.implementsOrExtends(type, base)
                        && hooks.stream().allMatch(hook -> type.methods().stream().anyMatch(method ->
                        method.getNameAsString().equals(hook.getNameAsString())))).toList();
                if (subclasses.isEmpty()) continue;
                for (var template : base.methods()) {
                    if (template.isAbstract() || template.getBody().isEmpty() || template.isPrivate()) continue;
                    var usedHooks = hooks.stream().filter(hook -> template.findAll(MethodCallExpr.class).stream()
                            .anyMatch(call -> call.getNameAsString().equals(hook.getNameAsString()))).toList();
                    if (usedHooks.isEmpty()) continue;
                    List<PatternCandidate.Evidence> evidence = new ArrayList<>();
                    evidence.add(PatternCandidate.Evidence.method("TemplateMethod", base, template.getNameAsString(), PatternRuleSupport.line(template)));
                    usedHooks.forEach(hook -> evidence.add(PatternCandidate.Evidence.method("Hook", base, hook.getNameAsString(), PatternRuleSupport.line(hook))));
                    subclasses.forEach(type -> evidence.add(PatternCandidate.Evidence.of("ConcreteSubclass", type)));
                    results.add(new PatternCandidate("TEMPLATE_METHOD", "GOF", "HIGH",
                            base.name() + "." + template.getNameAsString() + " orchestrates subclass hooks.",
                            "Concrete base method calls abstract hooks implemented by subclass.", List.copyOf(evidence)));
                }
            }
            return results;
        }
    }

    static final class Decorator implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var decorator : index.types()) for (String contractName : decorator.implementsTypes()) {
                var contract = index.resolve(decorator, contractName);
                if (contract == null || !contract.isInterface() || !decorator.fieldTypes().contains(contractName)
                        || !decorator.constructorParameterTypes().contains(contractName)) continue;
                var wrappedFields = decorator.ast().getFields().stream().flatMap(field -> field.getVariables().stream())
                        .filter(variable -> JavaTypeIndexBuilder.simple(variable.getType().asString()).equals(contractName)).toList();
                boolean delegates = decorator.methods().stream().anyMatch(method -> contract.methods().stream()
                        .anyMatch(contractMethod -> contractMethod.getNameAsString().equals(method.getNameAsString()))
                        && method.findAll(MethodCallExpr.class).stream().anyMatch(call ->
                        call.getNameAsString().equals(method.getNameAsString()) && wrappedFields.stream().anyMatch(field ->
                                call.getScope().map(scope -> scope.toString().equals(field.getNameAsString())).orElse(false))));
                boolean addsBehavior = decorator.methods().stream().anyMatch(method -> method.getBody().isPresent()
                        && contract.methods().stream().anyMatch(contractMethod ->
                        contractMethod.getNameAsString().equals(method.getNameAsString()))
                        && method.getBody().orElseThrow().getStatements().size() > 1);
                if (delegates && addsBehavior)
                    results.add(PatternRuleSupport.candidate("DECORATOR", "GOF", "HIGH",
                            decorator.name() + " wraps " + contract.name() + " and adds behavior around delegation.",
                            "Same abstraction, injected wrapped component, delegated method and additional statements.",
                            PatternCandidate.Evidence.of("Decorator", decorator), PatternCandidate.Evidence.of("Component", contract)));
            }
            return results;
        }
    }
}
