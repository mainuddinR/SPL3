package com.mainuddin.codereview.design;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PatternDiagnosticsTest {
    @Test void importedTypesResolveDespiteDuplicateNamesAndAmbiguityIsRejected() {
        var index = new JavaTypeIndexBuilder().build(Map.of(
                "a/Repo.java", "package a; public interface Repo extends JpaRepository<Object, Long> {}",
                "b/Repo.java", "package b; public interface Repo {}",
                "c/Service.java", "package c; import a.Repo; @Service class Service { Repo repo; Service(Repo repo) { this.repo=repo; } }",
                "d/Unknown.java", "package d; class Unknown { Repo repo; }"));
        var service = index.byQualifiedName().get("c.Service");
        assertSame(index.byQualifiedName().get("a.Repo"), index.resolve(service, "Repo"));
        assertNull(index.resolve(index.byQualifiedName().get("d.Unknown"), "Repo"));
        assertTrue(new DesignPatternDetectionService().detect(index).stream().anyMatch(p -> p.patternType().equals("DEPENDENCY_INJECTION")));
        var diagnostic = PatternDiagnostics.from(index, 2);
        assertEquals(diagnostic, PatternDiagnostics.read(diagnostic.savedProgress()));
        assertEquals(2, diagnostic.interfaceCount());
        assertEquals(1, diagnostic.repositoryCount());
        assertNull(PatternDiagnostics.read("Completed"));
    }

    @Test void countsAllSixKindsOfResolvedLocalRelationshipIncludingGenericMethodTypes() {
        var index = new JavaTypeIndexBuilder().build(Map.of("p/Types.java", """
                package p;
                interface Contract {} class Base {} class Target {}
                class Owner extends Base implements Contract {
                    Target target; String external; int primitive;
                    Owner(Target target) {this.target=target;}
                    java.util.List<Target> map(Target first, Target duplicate) {return null;}
                }
                """));
        var edges = index.declaredRelationships();
        assertEquals(6, edges.size());
        assertEquals(java.util.Set.of("EXTENDS", "IMPLEMENTS", "FIELD", "CONSTRUCTOR", "METHOD_PARAMETER", "METHOD_RETURN"),
                edges.stream().map(JavaTypeIndex.Relationship::kind).collect(java.util.stream.Collectors.toSet()));
        assertEquals(6, PatternDiagnostics.from(index, 0).relationshipCount());
    }

    @Test void preservesHistoricalCountsAndDistinguishesTheNewDefinition() {
        var old = PatternDiagnostics.read("Completed|16,16,0,0,1,13,0,1,0");
        assertEquals(1, old.relationshipCount()); assertEquals("LEGACY_DECLARATIONS", old.relationshipDefinition());
        var current = PatternDiagnostics.from(new JavaTypeIndexBuilder().build(Map.of()), 0);
        assertEquals("REPOSITORY_LOCAL_BY_KIND_V1", current.relationshipDefinition());
        assertEquals(current, PatternDiagnostics.read(current.savedProgress()));
    }

    @Test void zeroSourceProducesHonestZeroCounts() {
        var d = PatternDiagnostics.from(new JavaTypeIndexBuilder().build(Map.of()), 0);
        assertEquals(0, d.javaFileCount()); assertEquals(0, d.typeCount());
    }
}
