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
        Set<String> changedPaths = files.stream().map(PullRequestFile::getFilename)
                .filter(path -> path != null && path.endsWith(".java")).collect(Collectors.toSet());
        List<PatternConformanceDTO.Finding> results = new ArrayList<>();
        if (!baselineMatchesBase) return List.of(new PatternConformanceDTO.Finding(
                "REPOSITORY_BASELINE", "ARCHITECTURAL", "INSUFFICIENT_EVIDENCE", "MEDIUM", null, null, null,
                "The saved repository profile does not match the PR base revision.", "Analyze the matching baseline before interpreting conformance.", List.of()));
        for (PatternCandidate occurrence : baseline) {
            results.add(switch (occurrence.patternType()) {
                case "STRATEGY" -> strategy(occurrence, changed, files, changedPaths);
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
        var results = evaluate(baseline, head, files, baselineMatchesBase);
        if (!baselineMatchesBase) return results;
        return results.stream().map(result -> {
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
                    Set<String> oldBranches = providerBranches(oldType.get());
                    yield newType.isPresent() && oldBranches.containsAll(providerBranches(newType.get()));
                }
                default -> false;
            };
            return existing ? uncertain(result, "This structure already existed at the PR base; the available change does not establish a new deviation.") : result;
        }).toList();
    }
    private Set<String> providerBranches(JavaTypeIndex.Type type) {
        Set<String> branches = new java.util.HashSet<>();
        type.ast().findAll(com.github.javaparser.ast.stmt.IfStmt.class).stream()
                .filter(b -> !b.getCondition().findAll(com.github.javaparser.ast.expr.StringLiteralExpr.class).isEmpty())
                .forEach(b -> branches.add(b.getCondition().toString().replaceAll("\\s+", "")));
        type.ast().findAll(com.github.javaparser.ast.stmt.SwitchStmt.class).stream()
                .filter(b -> b.getEntries().stream().anyMatch(e -> e.getLabels().stream().anyMatch(l -> l.isStringLiteralExpr())))
                .forEach(b -> branches.add(b.getSelector().toString() + b.getEntries().stream().flatMap(e -> e.getLabels().stream()).map(Object::toString).sorted().toList()));
        return branches;
    }
    private PatternConformanceDTO.Finding uncertain(PatternConformanceDTO.Finding f, String rationale) {
        return new PatternConformanceDTO.Finding(f.patternType(), f.patternFamily(), "INSUFFICIENT_EVIDENCE", f.confidence(),
                f.file(), f.line(), f.className(), rationale, null, f.evidence());
    }

    private PatternConformanceDTO.Finding strategy(PatternCandidate occurrence, JavaTypeIndex changed,
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
                        && patch.lines().anyMatch(line -> line.startsWith("+") && !line.startsWith("+++")
                            && line.replaceAll("\\s+", "").contains(branch.getCondition().toString().replaceAll("\\s+", ""))))
                    || changedContext.isPresent() && patch != null && changedContext.get().ast()
                        .findAll(com.github.javaparser.ast.stmt.SwitchStmt.class).stream().anyMatch(branch ->
                            branch.getEntries().stream().anyMatch(entry -> entry.getLabels().stream().anyMatch(label -> label.isStringLiteralExpr()))
                            && patch.lines().anyMatch(line -> line.startsWith("+") && line.contains("switch")
                                && line.contains(branch.getSelector().toString())));
            if (newProviderBranch)
                return finding(occurrence, "POTENTIAL_DEVIATION", context.sourcePath(), null, context.className(),
                        "The Strategy context gained a conditional branch with a literal; review whether it selects provider-specific behavior directly.",
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
        return new PatternConformanceDTO.Finding(occurrence.patternType(), occurrence.patternFamily(), status,
                occurrence.confidence(), file, line, className, rationale, recommendation, occurrence.evidence());
    }

    private String simple(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }
}
