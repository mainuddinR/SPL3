package com.mainuddin.codereview.design;

import java.util.*;

/** Derived from saved occurrences only. Scores rank evidence, not design quality or probability. */
public record RepositoryDesignSummary(String architecturalStyle, String primaryGofPattern,
        String primaryPatternStatus, List<String> secondaryGofPatterns, boolean mixedPatternUsage,
        List<PatternSummary> patternSummaries, List<PatternSummary> architecturalConventionSummaries,
        String rankingExplanation) {
    public record PatternSummary(String patternType, int occurrenceCount, int highConfidenceCount,
            int mediumConfidenceCount, int participatingClassCount, int packageCount,
            Integer sourceRootCount, int importanceScore, String classification) {}

    public static RepositoryDesignSummary from(List<PatternProfileDTO.Occurrence> occurrences, Integer javaFiles) {
        List<PatternSummary> gof = aggregate(occurrences, "GOF");
        List<PatternSummary> architecture = aggregate(occurrences, "ARCHITECTURAL");
        PatternSummary first = gof.isEmpty() ? null : gof.getFirst();
        int runnerUp = gof.size() < 2 ? 0 : gof.get(1).importanceScore();
        boolean dominant = first != null && first.occurrenceCount() >= 2 && first.highConfidenceCount() >= 2
                && Math.max(first.packageCount(), Objects.requireNonNullElse(first.sourceRootCount(), 0)) >= 2
                && first.importanceScore() - runnerUp >= 2 && first.importanceScore() > runnerUp * 1.25;
        boolean mixed = !dominant && gof.size() > 1;
        String primary = dominant ? first.patternType() : null;
        boolean onlyPattern = gof.size() == 1;
        gof = gof.stream().map(p -> new PatternSummary(p.patternType(), p.occurrenceCount(), p.highConfidenceCount(),
                p.mediumConfidenceCount(), p.participatingClassCount(), p.packageCount(), p.sourceRootCount(),
                p.importanceScore(), p.patternType().equals(primary) ? "PRIMARY"
                    : onlyPattern || p.occurrenceCount() == 1 || Math.max(p.packageCount(), Objects.requireNonNullElse(p.sourceRootCount(), 0)) < 2
                    ? "LOCALIZED" : "SECONDARY")).toList();
        String style = "No supported architectural style identified";
        if (count(architecture, "CONTROLLER_SERVICE_REPOSITORY") >= 2
                && count(architecture, "DEPENDENCY_INJECTION") >= 2 && count(architecture, "REPOSITORY_ABSTRACTION") >= 1)
            style = "Layered Spring Architecture";
        else if (count(architecture, "CONTROLLER_SERVICE_REPOSITORY") > 0) style = "Controller-Service-Repository layering detected";
        else if (count(architecture, "DEPENDENCY_INJECTION") > 0) style = "Constructor-based dependency injection detected";
        else if (!architecture.isEmpty()) style = "Supporting architectural conventions detected";
        String status = Integer.valueOf(0).equals(javaFiles) ? "NO_JAVA" : gof.isEmpty() ? "NO_GOF"
                : dominant ? "DOMINANT" : mixed ? "MIXED" : "LOCALIZED";
        return new RepositoryDesignSummary(style, primary, status,
                gof.stream().filter(p -> !p.patternType().equals(primary)).map(PatternSummary::patternType).toList(),
                mixed, gof, architecture,
                "HIGH = 2 points; MEDIUM = 1. Spread bonus: 2 packages/source roots = +1; 3+ = +2 (not added twice). "
                + "Primary requires 2+ occurrences, 2+ HIGH, spread across 2+ packages/source roots, a lead of 2+ points and more than 25% over the runner-up. "
                + "Spread is inferred from saved class names and recognized src/main/java or src/test/java roots; unknown roots are omitted.");
    }

    private static List<PatternSummary> aggregate(List<PatternProfileDTO.Occurrence> occurrences, String family) {
        Map<String, List<PatternProfileDTO.Occurrence>> groups = new TreeMap<>();
        occurrences.stream().filter(o -> family.equals(o.patternFamily()) && Set.of("HIGH", "MEDIUM").contains(o.confidence()))
                .forEach(o -> groups.computeIfAbsent(o.patternType(), ignored -> new ArrayList<>()).add(o));
        List<PatternSummary> result = new ArrayList<>();
        groups.forEach((pattern, items) -> {
            Set<String> classes = new HashSet<>(), packages = new HashSet<>(), roots = new HashSet<>();
            for (var item : items) for (var e : item.evidence()) {
                if (e.className() != null && !e.className().isBlank()) {
                    classes.add(e.className());
                    String name = e.className();
                    String fileName = e.sourcePath() == null ? "" : e.sourcePath().substring(e.sourcePath().lastIndexOf('/') + 1).replace(".java", "");
                    int nested = fileName.isEmpty() ? -1 : name.indexOf("." + fileName + ".");
                    if (nested >= 0) name = name.substring(0, nested + fileName.length() + 1);
                    else if (!fileName.isEmpty() && name.startsWith(fileName + ".")) name = fileName;
                    packages.add(name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : "");
                }
                if (e.sourcePath() != null) for (String root : List.of("src/main/java/", "src/test/java/")) {
                    int at = e.sourcePath().indexOf(root);
                    if (at >= 0 && (at == 0 || e.sourcePath().charAt(at - 1) == '/')) roots.add(e.sourcePath().substring(0, at + root.length()));
                }
            }
            int high = (int) items.stream().filter(o -> "HIGH".equals(o.confidence())).count();
            int medium = items.size() - high, spread = Math.max(packages.size(), roots.size());
            result.add(new PatternSummary(pattern, items.size(), high, medium, classes.size(), packages.size(),
                    roots.isEmpty() ? null : roots.size(), family.equals("GOF") ? high * 2 + medium + Math.min(2, Math.max(0, spread - 1)) : 0,
                    family.equals("GOF") ? "LOCALIZED" : "SUPPORTING"));
        });
        result.sort(Comparator.comparingInt(PatternSummary::importanceScore).reversed().thenComparing(PatternSummary::patternType));
        return List.copyOf(result);
    }
    private static int count(List<PatternSummary> summaries, String type) {
        return summaries.stream().filter(p -> p.patternType().equals(type)).mapToInt(PatternSummary::occurrenceCount).sum();
    }
    public String cardLabel() {
        return switch (primaryPatternStatus) {
            case "NO_JAVA" -> "No Java source";
            case "DOMINANT" -> display(primaryGofPattern) + " · Primary";
            case "MIXED" -> "Mixed patterns";
            case "LOCALIZED" -> display(patternSummaries.getFirst().patternType()) + " · Localized";
            default -> architecturalStyle.equals("Layered Spring Architecture") ? "Layered architecture · No dominant GoF" : "No supported GoF patterns";
        };
    }
    private static String display(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("_")).map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)).collect(java.util.stream.Collectors.joining(" "));
    }
}
