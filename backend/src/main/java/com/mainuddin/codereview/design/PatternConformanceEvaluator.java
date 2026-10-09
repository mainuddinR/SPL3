package com.mainuddin.codereview.design;

import com.mainuddin.codereview.entity.PullRequestFile;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Reports potential deviations only for changed code relevant to a saved pattern occurrence. */
@Component
public class PatternConformanceEvaluator {
    public List<PatternConformanceDTO.Finding> evaluate(List<PatternCandidate> baseline,
            JavaTypeIndex changed, List<PullRequestFile> files, boolean baselineMatchesBase) {
        return evaluateCandidates(baseline, changed, null, files, baselineMatchesBase);
    }
    private List<PatternConformanceDTO.Finding> evaluateCandidates(List<PatternCandidate> baseline,
            JavaTypeIndex changed, JavaTypeIndex base, List<PullRequestFile> files, boolean baselineMatchesBase) {
        Set<String> changedPaths = files.stream().map(PullRequestFile::getFilename)
                .filter(path -> path != null && path.endsWith(".java")).collect(Collectors.toSet());
        List<PatternConformanceDTO.Finding> results = new ArrayList<>();
        if (!baselineMatchesBase) return List.of(new PatternConformanceDTO.Finding(
                "REPOSITORY_BASELINE", "ARCHITECTURAL", "INSUFFICIENT_EVIDENCE", "MEDIUM", null, null, null,
                "The saved repository profile does not match the PR base revision.", "Analyze the matching baseline before interpreting conformance.", List.of()));
        for (PatternCandidate occurrence : baseline) {
            results.add(switch (occurrence.patternType()) {
                case "STRATEGY" -> strategy(occurrence, changed, base, files, changedPaths);
                case "CONTROLLER_SERVICE_REPOSITORY" -> layering(occurrence, changed, changedPaths);
                case "FACTORY_METHOD" -> factory(occurrence, changed, changedPaths);
                default -> general(occurrence, changedPaths);
            });
        }
        // One neutral card per irrelevant pattern type; do not drown useful checks in occurrence-level no-ops.
        Set<String> relevantTypes = results.stream().filter(f -> !"NOT_APPLICABLE".equals(f.status()))
                .map(PatternConformanceDTO.Finding::patternType).collect(Collectors.toSet());
        Set<String> neutralTypes = new java.util.HashSet<>();
        return results.stream().filter(f -> !"NOT_APPLICABLE".equals(f.status())
                || !relevantTypes.contains(f.patternType()) && neutralTypes.add(f.patternType())).toList();
    }

    public List<PatternConformanceDTO.Finding> evaluate(List<PatternCandidate> baseline, JavaTypeIndex head,
            JavaTypeIndex base, List<PullRequestFile> files, boolean baselineMatchesBase) {
        var results = evaluateCandidates(baseline, head, base, files, baselineMatchesBase);
        if (!baselineMatchesBase) return results;
        return results.stream().map(result -> {
            if ("INSUFFICIENT_EVIDENCE".equals(result.status()) && result.file() != null) {
                var pattern = baseline.stream().filter(p -> p.patternType().equals(result.patternType())
                        && p.evidence().equals(result.evidence())).findFirst();
                if (pattern.isPresent() && JavaDesignChangeImpact.sameRelevantStructure(pattern.get(), head, base, result.file()))
                    return new PatternConformanceDTO.Finding(result.patternType(), result.patternFamily(), "NOT_APPLICABLE",
                            result.confidence(), result.file(), result.line(), result.className(),
                            "The changed file preserves its declarations, dependencies and design-related calls. No applicable structural design change was detected.",
                            null, result.evidence());
            }
            if (!"POTENTIAL_DEVIATION".equals(result.status())) return result;
            PullRequestFile file = files.stream().filter(f -> java.util.Objects.equals(f.getFilename(), result.file())).findFirst().orElse(null);
            if (file != null && "added".equalsIgnoreCase(file.getStatus())) return result;
            var oldType = base.types().stream().filter(t -> t.qualifiedName().equals(result.className()) && t.path().equals(result.file())).findFirst();
            if (oldType.isEmpty()) return uncertain(result, "The changed type could not be compared reliably with its base-revision structure.");
            boolean existing = switch (result.patternType()) {
                case "CONTROLLER_SERVICE_REPOSITORY" -> depends(oldType.get(), result.evidence().stream()
                        .filter(e -> e.role().equals("Repository")).map(PatternCandidate.Evidence::className).toList());
                case "FACTORY_METHOD" -> oldType.get().creations().stream().anyMatch(expr -> result.evidence().stream()
                        .filter(e -> e.role().equals("ConcreteProduct")).anyMatch(e -> matches(oldType.get(), expr.getType().asString(), e.className())));
                case "STRATEGY" -> {
                    var newType = head.types().stream().filter(t -> t.qualifiedName().equals(result.className()) && t.path().equals(result.file())).findFirst();
                    var pattern = baseline.stream().filter(p -> p.patternType().equals(result.patternType()) && p.evidence().equals(result.evidence())).findFirst().orElseThrow();
                    Set<String> oldBranches = providerBranches(oldType.get(), pattern, base);
                    yield newType.isPresent() && oldBranches.containsAll(providerBranches(newType.get(), pattern, head));
                }
                default -> false;
            };
            return existing ? new PatternConformanceDTO.Finding(result.patternType(), result.patternFamily(), "NOT_APPLICABLE",
                    result.confidence(), result.file(), result.line(), result.className(),
                    "This structure already existed at the PR base; this PR does not introduce the reported design concern.", null, result.evidence()) : result;
        }).toList();
    }
    private Set<String> providerBranches(JavaTypeIndex.Type type, PatternCandidate pattern, JavaTypeIndex index) {
        Set<String> branches = new java.util.HashSet<>();
        type.ast().findAll(com.github.javaparser.ast.stmt.IfStmt.class).stream()
                .filter(b -> !b.getCondition().findAll(com.github.javaparser.ast.expr.StringLiteralExpr.class).isEmpty())
                .filter(b -> concreteStrategyAction(b.getThenStmt(), type, pattern, index))
                .forEach(b -> branches.add(b.getCondition().toString().replaceAll("\\s+", "") + ":" + actionSignature(b.getThenStmt(), type, pattern, index)));
        type.ast().findAll(com.github.javaparser.ast.stmt.SwitchStmt.class).stream()
                .filter(b -> b.getEntries().stream().anyMatch(e -> e.getLabels().stream().anyMatch(l -> l.isStringLiteralExpr())))
                .filter(b -> concreteStrategyAction(b, type, pattern, index))
                .forEach(b -> branches.add(b.getSelector().toString() + b.getEntries().stream().flatMap(e -> e.getLabels().stream()).map(Object::toString).sorted().toList() + ":" + actionSignature(b, type, pattern, index)));
        return branches;
    }
    private String actionSignature(com.github.javaparser.ast.Node body, JavaTypeIndex.Type owner, PatternCandidate pattern, JavaTypeIndex index) {
        return body.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class).stream().filter(c -> concreteStrategyAction(c, owner, pattern, index)).map(c -> c.getType().asString()).sorted().toList()
                + ":" + body.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).stream()
                .filter(c -> concreteStrategyAction(c, owner, pattern, index)).map(c -> c.getScope().map(Object::toString).orElse("") + "." + c.getNameAsString()).sorted().toList();
    }
    private PatternConformanceDTO.Finding uncertain(PatternConformanceDTO.Finding f, String rationale) {
        return new PatternConformanceDTO.Finding(f.patternType(), f.patternFamily(), "INSUFFICIENT_EVIDENCE", f.confidence(),
                f.file(), f.line(), f.className(), rationale, null, f.evidence());
    }

    private PatternConformanceDTO.Finding strategy(PatternCandidate occurrence, JavaTypeIndex changed, JavaTypeIndex base,
            List<PullRequestFile> files, Set<String> paths) {
        var abstractions = occurrence.evidence().stream().filter(item -> item.role().equals("Strategy")).toList();
        for (var context : occurrence.evidence().stream().filter(item -> item.role().equals("Context")).toList()) {
            if (!paths.contains(context.sourcePath())) continue;
            PullRequestFile file = files.stream().filter(item -> context.sourcePath().equals(item.getFilename())).findFirst().orElse(null);
            String patch = file == null ? "" : file.getPatch();
            var changedContext = changed.types().stream().filter(t -> t.qualifiedName().equals(context.className()) && t.path().equals(context.sourcePath())).findFirst();
            boolean newProviderBranch = changedContext.isPresent() && patch != null && changedContext.get().ast()
                    .findAll(com.github.javaparser.ast.stmt.IfStmt.class).stream().anyMatch(branch ->
                        !branch.getCondition().findAll(com.github.javaparser.ast.expr.StringLiteralExpr.class).isEmpty()
                        && concreteStrategyAction(branch.getThenStmt(), changedContext.get(), occurrence, changed)
                        && patch.lines().anyMatch(line -> line.startsWith("+") && !line.startsWith("+++")
                            && line.replaceAll("\\s+", "").contains(branch.getCondition().toString().replaceAll("\\s+", ""))))
                    || changedContext.isPresent() && patch != null && changedContext.get().ast()
                        .findAll(com.github.javaparser.ast.stmt.SwitchStmt.class).stream().anyMatch(branch ->
                            branch.getEntries().stream().anyMatch(entry -> entry.getLabels().stream().anyMatch(label -> label.isStringLiteralExpr()))
                            && concreteStrategyAction(branch, changedContext.get(), occurrence, changed)
                            && patch.lines().anyMatch(line -> line.startsWith("+") && line.contains("switch")
                                && line.contains(branch.getSelector().toString())));
            if (base != null && changedContext.isPresent()) {
                var prior = base.types().stream().filter(t -> t.qualifiedName().equals(context.className()) && t.path().equals(context.sourcePath())).findFirst();
                var headBranches = providerBranches(changedContext.get(), occurrence, changed);
                newProviderBranch = !headBranches.isEmpty() && (prior.isEmpty()
                        || !providerBranches(prior.get(), occurrence, base).containsAll(headBranches));
            }
            if (newProviderBranch)
                return finding(occurrence, "POTENTIAL_DEVIATION", context.sourcePath(), changedContext.get().line(), context.className(),
                        "The Strategy context gained a literal-based branch that directly selects a concrete strategy instead of using the established abstraction.",
                        abstractions.isEmpty() ? "Review whether this branch bypasses an established abstraction." : "Consider a new implementation of " + simple(abstractions.getFirst().className()) + " if this branch selects behavior.");

        }
        for (var abstraction : abstractions) for (var type : changed.types()) {
            if (java.util.stream.Stream.concat(type.implementsTypes().stream(), type.extendsTypes().stream()).anyMatch(ref -> matches(type, ref, abstraction.className())))
                return finding(occurrence, "CONFORMS", type.path(), type.line(), type.qualifiedName(),
                        type.name() + " extends the repository's established " + simple(abstraction.className()) + " strategy abstraction.", null);
        }
        if (occurrence.evidence().stream().anyMatch(e -> paths.contains(e.sourcePath()))) return general(occurrence, paths);
        return irrelevant(occurrence);
    }

    private boolean concreteStrategyAction(com.github.javaparser.ast.Node branch, JavaTypeIndex.Type owner,
            PatternCandidate pattern, JavaTypeIndex changed) {
        List<String> concrete = new ArrayList<>(pattern.evidence().stream().filter(e -> e.role().equals("ConcreteStrategy"))
                .map(PatternCandidate.Evidence::className).toList());
        changed.types().stream().filter(t -> pattern.evidence().stream().filter(e -> e.role().equals("Strategy"))
                .anyMatch(e -> java.util.stream.Stream.concat(t.extendsTypes().stream(), t.implementsTypes().stream())
                        .anyMatch(ref -> matches(t, ref, e.className())))).map(JavaTypeIndex.Type::qualifiedName).forEach(concrete::add);
        if (branch.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class).stream()
                .anyMatch(c -> concrete.stream().anyMatch(target -> matches(owner, c.getType().asString(), target)))) return true;
        Set<String> concreteFields = owner.ast().getFields().stream().flatMap(f -> f.getVariables().stream())
                .filter(v -> concrete.stream().anyMatch(target -> matches(owner, v.getType().asString(), target)))
                .map(v -> v.getNameAsString()).collect(Collectors.toSet());
        return branch.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).stream()
                .anyMatch(c -> c.getScope().map(Object::toString).map(scope -> concreteFields.contains(scope.replaceFirst("^this\\.", ""))).orElse(false));
    }

    private PatternConformanceDTO.Finding layering(PatternCandidate occurrence, JavaTypeIndex changed, Set<String> paths) {
        var repositories = occurrence.evidence().stream().filter(e -> e.role().equals("Repository")).map(PatternCandidate.Evidence::className).toList();
        var services = occurrence.evidence().stream().filter(e -> e.role().equals("Service")).map(PatternCandidate.Evidence::className).toList();
        List<JavaTypeIndex.Type> controllers = changed.types().stream().filter(t -> t.hasAnnotation("RestController") || t.hasAnnotation("Controller")).toList();
        for (var controller : controllers) {
            if (depends(controller, repositories)) return finding(occurrence, "POTENTIAL_DEVIATION", controller.path(), controller.line(), controller.qualifiedName(),
                    controller.name() + " directly depends on a repository used behind a service in the saved baseline.",
                    "Route persistence coordination through the relevant service layer.");
        }
        for (var controller : controllers) {
            boolean newChain = changed.types().stream().filter(t -> t.hasAnnotation("Service"))
                    .anyMatch(service -> changed.dependsOn(controller, service) && depends(service, repositories));
            if (depends(controller, services) || newChain) return finding(occurrence, "CONFORMS", controller.path(), controller.line(), controller.qualifiedName(),
                    controller.name() + " uses a service boundary leading to the established repository layer.", null);
        }
        return general(occurrence, paths);
    }

    private boolean depends(JavaTypeIndex.Type owner, List<String> targets) {
        return java.util.stream.Stream.concat(owner.fieldTypes().stream(), owner.constructorParameterTypes().stream())
                .anyMatch(ref -> targets.stream().anyMatch(target -> matches(owner, ref, target)));
    }
    private boolean matches(JavaTypeIndex.Type owner, String reference, String target) {
        String ref = reference.replaceAll("<.*", "").replace("[]", "");
        if (ref.contains(".")) return ref.equals(target);
        if (!simple(target).equals(ref)) return false;
        if (!target.contains(".")) return true; // Historical default-package evidence.
        var unit = owner.ast().findCompilationUnit().orElseThrow();
        var explicit = unit.getImports().stream().filter(i -> !i.isStatic() && !i.isAsterisk() && i.getNameAsString().endsWith("." + ref)).toList();
        if (!explicit.isEmpty()) return explicit.size() == 1 && explicit.getFirst().getNameAsString().equals(target);
        if ((owner.packageName() + "." + ref).equals(target)) return true;
        var wildcard = unit.getImports().stream().filter(i -> !i.isStatic() && i.isAsterisk()).toList();
        return wildcard.size() == 1 && (wildcard.getFirst().getNameAsString() + "." + ref).equals(target);
    }

    private PatternConformanceDTO.Finding factory(PatternCandidate occurrence, JavaTypeIndex changed, Set<String> paths) {
        Set<String> products = occurrence.evidence().stream().filter(item -> item.role().equals("ConcreteProduct"))
                .map(PatternCandidate.Evidence::className).collect(Collectors.toSet());
        Set<String> creators = occurrence.evidence().stream().filter(item -> item.role().equals("Creator"))
                .map(PatternCandidate.Evidence::sourcePath).collect(Collectors.toSet());
        for (var type : changed.types()) {
            if (creators.contains(type.path())) continue;
            var direct = type.creations().stream().filter(expr -> products.stream().anyMatch(product -> matches(type, expr.getType().asString(), product)))
                    .findFirst();
            if (direct.isPresent())
                return finding(occurrence, "POTENTIAL_DEVIATION", type.path(),
                        direct.get().getBegin().map(position -> position.line).orElse(null), type.qualifiedName(),
                        type.name() + " directly constructs a product created through the saved factory baseline.",
                        "Consider obtaining the product through the established factory method.");
        }
        if (creators.stream().anyMatch(paths::contains))
            return finding(occurrence, "INSUFFICIENT_EVIDENCE", creators.iterator().next(), null, null,
                    "The factory changed; a whole-repository comparison is needed to establish its behavior.", null);
        return irrelevant(occurrence);
    }

    private PatternConformanceDTO.Finding general(PatternCandidate occurrence, Set<String> paths) {
        return occurrence.evidence().stream().filter(item -> paths.contains(item.sourcePath())).findFirst()
                .map(item -> finding(occurrence, "INSUFFICIENT_EVIDENCE", item.sourcePath(), item.line(), item.className(),
                        "A file participating in this pattern changed, but the available diff does not prove conformance or deviation.", null))
                .orElseGet(() -> irrelevant(occurrence));
    }

    private PatternConformanceDTO.Finding irrelevant(PatternCandidate occurrence) {
        return finding(occurrence, "NOT_APPLICABLE", null, null, null,
                "This PR does not change code relevant to this saved pattern occurrence.", null);
    }

    private PatternConformanceDTO.Finding finding(PatternCandidate occurrence, String status, String file,
            Integer line, String className, String rationale, String recommendation) {
        String impact = !"POTENTIAL_DEVIATION".equals(status) ? null : switch (occurrence.patternType()) {
            case "STRATEGY" -> "Provider selection inside a context can couple behavior extensions to context edits and duplicate existing strategy responsibilities.";
            case "CONTROLLER_SERVICE_REPOSITORY" -> "A controller-to-repository dependency can bypass service coordination and couple the HTTP boundary to persistence details.";
            case "FACTORY_METHOD" -> "Direct concrete-product construction can couple the caller to implementation choices handled by the established factory.";
            default -> null;
        };
        String priority = impact == null ? null : "CONTROLLER_SERVICE_REPOSITORY".equals(occurrence.patternType())
                && "HIGH".equals(occurrence.confidence()) ? "HIGH" : "MEDIUM";
        return new PatternConformanceDTO.Finding(occurrence.patternType(), occurrence.patternFamily(), status,
                occurrence.confidence(), file, line, className, rationale, recommendation, occurrence.evidence(),
                "POTENTIAL_DEVIATION".equals(status) ? rationale : null, impact, priority, null);
    }

    private String simple(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }
}
