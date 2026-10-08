package com.mainuddin.codereview.design;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryDesignSummaryTest {
    private PatternProfileDTO.Occurrence occurrence(String type, String family, String confidence, String pkg, int id) {
        return new PatternProfileDTO.Occurrence((long) id, type, family, confidence, "saved evidence", "structural",
                List.of(new PatternCandidate.Evidence("Participant", pkg + ".Type" + id, "module/src/main/java/" + pkg + "/Type" + id + ".java", null, 1)));
    }
    @Test void ranksEvidenceAndKeepsArchitectureSeparate() {
        List<PatternProfileDTO.Occurrence> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) rows.add(occurrence("STRATEGY", "GOF", i == 3 ? "MEDIUM" : "HIGH", "p" + (i % 3), i));
        rows.add(occurrence("FACTORY_METHOD", "GOF", "HIGH", "a", 5)); rows.add(occurrence("FACTORY_METHOD", "GOF", "HIGH", "b", 6));
        rows.add(occurrence("BUILDER", "GOF", "HIGH", "a", 7));
        for (int i = 0; i < 20; i++) rows.add(occurrence("DEPENDENCY_INJECTION", "ARCHITECTURAL", "HIGH", "a", i + 10));
        var result = RepositoryDesignSummary.from(rows, 40);
        assertEquals("STRATEGY", result.primaryGofPattern()); assertEquals("DOMINANT", result.primaryPatternStatus());
        assertEquals("Strategy · Primary", result.cardLabel());
        assertEquals(9, result.patternSummaries().getFirst().importanceScore());
        assertEquals("SECONDARY", result.patternSummaries().get(1).classification());
        assertEquals("LOCALIZED", result.patternSummaries().get(2).classification());
        assertEquals("Constructor-based dependency injection detected", result.architecturalStyle());
        assertEquals(3, result.patternSummaries().size());
    }
    @Test void equalEvidenceIsMixedNotAlphabeticalAndSingleOccurrenceIsLocalized() {
        var rows = new ArrayList<PatternProfileDTO.Occurrence>();
        for (String type : List.of("STRATEGY", "FACTORY_METHOD", "BUILDER")) for (int i = 0; i < 2; i++) rows.add(occurrence(type, "GOF", "HIGH", "p" + i, i));
        var mixed = RepositoryDesignSummary.from(rows, 12);
        assertNull(mixed.primaryGofPattern()); assertTrue(mixed.mixedPatternUsage()); assertEquals("MIXED", mixed.primaryPatternStatus());
        var local = RepositoryDesignSummary.from(List.of(rows.getFirst()), 12);
        assertNull(local.primaryGofPattern()); assertEquals("LOCALIZED", local.primaryPatternStatus());
        assertEquals("LOCALIZED", local.patternSummaries().getFirst().classification());
    }
    @Test void weakEvidenceCannotDominateAndNoGofDoesNotEraseArchitecture() {
        var weak = List.of(occurrence("STRATEGY", "GOF", "MEDIUM", "a", 1), occurrence("STRATEGY", "GOF", "MEDIUM", "b", 2));
        assertNull(RepositoryDesignSummary.from(weak, 3).primaryGofPattern());
        var rows = new ArrayList<PatternProfileDTO.Occurrence>();
        for (int i = 0; i < 2; i++) { rows.add(occurrence("CONTROLLER_SERVICE_REPOSITORY", "ARCHITECTURAL", "HIGH", "a", i)); rows.add(occurrence("DEPENDENCY_INJECTION", "ARCHITECTURAL", "HIGH", "a", i)); }
        rows.add(occurrence("REPOSITORY_ABSTRACTION", "ARCHITECTURAL", "HIGH", "a", 8));
        var noGof = RepositoryDesignSummary.from(rows, 16);
        assertEquals("NO_GOF", noGof.primaryPatternStatus()); assertEquals("Layered Spring Architecture", noGof.architecturalStyle());
        assertEquals("NO_JAVA", RepositoryDesignSummary.from(List.of(), 0).primaryPatternStatus());
    }
    @Test void countsDistinctClassesAndRecognizedModuleRootsWithoutDuplicatingSpreadBonus() {
        var rows = List.of(occurrence("STRATEGY", "GOF", "HIGH", "a", 1), occurrence("STRATEGY", "GOF", "HIGH", "b", 2));
        var summary = RepositoryDesignSummary.from(rows, 2).patternSummaries().getFirst();
        assertEquals(2, summary.participatingClassCount()); assertEquals(2, summary.packageCount()); assertEquals(1, summary.sourceRootCount()); assertEquals(5, summary.importanceScore());
    }
    @Test void detectsIndependentStructuresBeforeAggregating() {
        Map<String,String> sources = new HashMap<>();
        for (String name : List.of("Payment", "Notification")) {
            String pkg = name.toLowerCase();
            sources.put(pkg + "/Strategy.java", "package " + pkg + "; interface Strategy { void run(); } class A implements Strategy { public void run() {} } class B implements Strategy { public void run() {} } class Context { Strategy strategy; Context(Strategy strategy) {this.strategy=strategy;} void run(){strategy.run();} }");
        }
        sources.put("build/Product.java", "package build; class Product {} class Builder { Builder a(int x){return this;} Builder b(int y){return this;} Product build(){return new Product();} }");
        sources.put("factory/Product.java", "package factory; interface Product {} class A implements Product {} class B implements Product {} class Factory { Product create(boolean a){if(a)return new A();return new B();} } class Consumer { Factory factory; Product call(){return factory.create(true);} }");
        var candidates = new DesignPatternDetectionService().detect(new JavaTypeIndexBuilder().build(sources));
        assertEquals(2, candidates.stream().filter(p -> p.patternType().equals("STRATEGY")).count());
        assertEquals(1, candidates.stream().filter(p -> p.patternType().equals("BUILDER")).count());
        assertEquals(1, candidates.stream().filter(p -> p.patternType().equals("FACTORY_METHOD")).count());
        var rows = candidates.stream().map(c -> new PatternProfileDTO.Occurrence(null, c.patternType(), c.patternFamily(), c.confidence(), c.rationale(), c.confidenceReason(), c.evidence())).toList();
        assertEquals("STRATEGY", RepositoryDesignSummary.from(rows, sources.size()).primaryGofPattern());
    }
    @Test void nestedClassesDoNotManufacturePackageSpread() {
        var occurrence = new PatternProfileDTO.Occurrence(1L, "BUILDER", "GOF", "HIGH", "nested", "structural",
                List.of(new PatternCandidate.Evidence("Builder", "Owner.Builder", "Owner.java", null, 1),
                        new PatternCandidate.Evidence("Product", "Owner.Product", "Owner.java", null, 1)));
        var summary = RepositoryDesignSummary.from(List.of(occurrence), 1).patternSummaries().getFirst();
        assertEquals(1, summary.packageCount()); assertNull(summary.sourceRootCount());
        assertEquals("LOCALIZED", summary.classification());
    }
}
