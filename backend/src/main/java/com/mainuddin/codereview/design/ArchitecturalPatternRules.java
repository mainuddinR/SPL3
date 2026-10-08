package com.mainuddin.codereview.design;

import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ArchitecturalPatternRules {
    private ArchitecturalPatternRules() {}
    public static List<DesignPatternRule> all() {
        return List.of(new ControllerServiceRepository(), new RepositoryAbstraction(),
                new DependencyInjection(), new DtoMapperSeparation());
    }

    static boolean repository(JavaTypeIndex.Type type) {
        return type.isInterface() && (type.hasAnnotation("Repository")
                || type.extendsTypes().stream().anyMatch(name -> Set.of("Repository", "JpaRepository",
                "CrudRepository", "PagingAndSortingRepository").contains(JavaTypeIndexBuilder.simple(name))));
    }

    static final class ControllerServiceRepository implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var controller : index.types()) {
                if (!controller.hasAnnotation("RestController") && !controller.hasAnnotation("Controller")) continue;
                for (var service : index.types()) {
                    if (!service.hasAnnotation("Service") || !index.dependsOn(controller, service)) continue;
                    for (var repository : index.types()) {
                        if (!repository(repository) || !index.dependsOn(service, repository)) continue;
                        results.add(PatternRuleSupport.candidate("CONTROLLER_SERVICE_REPOSITORY", "ARCHITECTURAL", "HIGH",
                                controller.name() + " depends on " + service.name() + ", which depends on " + repository.name() + ".",
                                "Spring controller/service annotations and dependency path to repository abstraction.",
                                PatternCandidate.Evidence.of("Controller", controller), PatternCandidate.Evidence.of("Service", service),
                                PatternCandidate.Evidence.of("Repository", repository)));
                    }
                }
            }
            return results;
        }
    }

    static final class RepositoryAbstraction implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var repository : index.types()) {
                if (!repository(repository)) continue;
                var consumers = index.types().stream().filter(type -> type != repository && index.dependsOn(type, repository)).toList();
                if (consumers.isEmpty()) continue;
                List<PatternCandidate.Evidence> evidence = new ArrayList<>();
                evidence.add(PatternCandidate.Evidence.of("Repository", repository));
                consumers.forEach(type -> evidence.add(PatternCandidate.Evidence.of("Consumer", type)));
                results.add(new PatternCandidate("REPOSITORY_ABSTRACTION", "ARCHITECTURAL", "HIGH",
                        repository.name() + " is a Spring Data repository abstraction used by application types.",
                        "Repository interface extends a recognized Spring Data base or has @Repository; a consumer depends on it.",
                        List.copyOf(evidence)));
            }
            return results;
        }
    }

    static final class DependencyInjection implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var consumer : index.types()) {
                if (consumer.annotations().stream().noneMatch(Set.of("Controller", "RestController", "Service", "Component")::contains)) continue;
                for (String dependencyName : consumer.constructorParameterTypes()) {
                    var dependency = index.resolve(consumer, dependencyName);
                    if (dependency == null || dependency == consumer || !consumer.fieldTypes().contains(dependencyName)) continue;
                    if (!dependency.isInterface() && dependency.annotations().stream()
                            .noneMatch(Set.of("Repository", "Service", "Component")::contains)) continue;
                    results.add(PatternRuleSupport.candidate("DEPENDENCY_INJECTION", "ARCHITECTURAL", "HIGH",
                            consumer.name() + " receives " + dependency.name() + " through a constructor.",
                            "Spring component with constructor parameter and stored field for an application abstraction/component.",
                            PatternCandidate.Evidence.of("Consumer", consumer), PatternCandidate.Evidence.of("Dependency", dependency)));
                }
            }
            return results;
        }
    }

    static final class DtoMapperSeparation implements DesignPatternRule {
        public List<PatternCandidate> detect(JavaTypeIndex index) {
            List<PatternCandidate> results = new ArrayList<>();
            for (var mapper : index.types()) for (var method : mapper.methods()) {
                if (method.getParameters().size() != 1 || method.getBody().isEmpty()) continue;
                var input = index.resolve(mapper, method.getParameter(0).getType().asString());
                var output = index.resolve(mapper, method.getType().asString());
                if (input == null || output == null || input == output) continue;
                boolean createsOutput = method.findAll(ObjectCreationExpr.class).stream()
                        .anyMatch(expr -> JavaTypeIndexBuilder.simple(expr.getType().asString()).equals(output.name()));
                boolean boundary = index.types().stream().filter(type -> type.hasAnnotation("RestController") || type.hasAnnotation("Service"))
                        .anyMatch(type -> index.dependsOn(type, mapper) || type.methods().stream()
                                .anyMatch(boundaryMethod -> JavaTypeIndexBuilder.simple(boundaryMethod.getType().asString()).equals(output.name())));
                boolean distinctDataTypes = input.hasAnnotation("Entity") || output.hasAnnotation("Entity")
                        || input.ast().isRecordDeclaration() || output.ast().isRecordDeclaration();
                if (!createsOutput || !boundary || !distinctDataTypes) continue;
                results.add(PatternRuleSupport.candidate("DTO_MAPPER_SEPARATION", "ARCHITECTURAL", "MEDIUM",
                        mapper.name() + "." + method.getNameAsString() + " maps between " + input.name() + " and " + output.name() + " at a service/controller boundary.",
                        "Method accepts one application type, constructs a distinct output type, and the types cross a boundary.",
                        PatternCandidate.Evidence.method("Mapper", mapper, method.getNameAsString(), PatternRuleSupport.line(method)),
                        PatternCandidate.Evidence.of("Input", input), PatternCandidate.Evidence.of("Output", output)));
            }
            return results;
        }
    }
}
