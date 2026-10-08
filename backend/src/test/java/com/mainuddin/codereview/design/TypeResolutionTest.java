package com.mainuddin.codereview.design;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TypeResolutionTest {
    private final JavaTypeIndexBuilder builder = new JavaTypeIndexBuilder();

    @Test
    void testFullyQualifiedFieldDependency() {
        var sources = Map.of(
            "src/a/PaymentStrategy.java", "package a; public interface PaymentStrategy {}",
            "src/b/PaymentService.java", "package b; class PaymentService { private final a.PaymentStrategy strategy = null; }"
        );
        var index = builder.build(sources);
        var strategy = index.byQualifiedName().get("a.PaymentStrategy");
        var service = index.byQualifiedName().get("b.PaymentService");
        
        assertNotNull(strategy, "Strategy should exist");
        assertNotNull(service, "Service should exist");
        assertTrue(index.dependsOn(service, strategy), "PaymentService should depend on PaymentStrategy");
    }

    @Test
    void testExplicitImportDependency() {
        var sources = Map.of(
            "src/a/PaymentStrategy.java", "package a; public interface PaymentStrategy {}",
            "src/b/PaymentService.java", "package b; import a.PaymentStrategy; class PaymentService { private final PaymentStrategy strategy = null; }"
        );
        var index = builder.build(sources);
        var strategy = index.byQualifiedName().get("a.PaymentStrategy");
        var service = index.byQualifiedName().get("b.PaymentService");
        
        assertNotNull(strategy, "Strategy should exist");
        assertNotNull(service, "Service should exist");
        assertTrue(index.dependsOn(service, strategy), "PaymentService should depend on PaymentStrategy via import");
    }

    @Test
    void testSamePackageDependency() {
        var sources = Map.of(
            "src/a/PaymentStrategy.java", "package a; public interface PaymentStrategy {}",
            "src/a/PaymentService.java", "package a; class PaymentService { private final PaymentStrategy strategy = null; }"
        );
        var index = builder.build(sources);
        var strategy = index.byQualifiedName().get("a.PaymentStrategy");
        var service = index.byQualifiedName().get("a.PaymentService");
        
        assertNotNull(strategy, "Strategy should exist");
        assertNotNull(service, "Service should exist");
        assertTrue(index.dependsOn(service, strategy), "PaymentService should depend on PaymentStrategy in same package");
    }

    @Test
    void testDuplicateSimpleNameNoAmbiguousResolution() {
        var sources = Map.of(
            "src/a/UserService.java", "package a; public class UserService {}",
            "src/b/UserService.java", "package b; public class UserService {}",
            "src/c/Client.java", "package c; class Client { private final UserService service = null; }"
        );
        var index = builder.build(sources);
        var client = index.byQualifiedName().get("c.Client");
        var aService = index.byQualifiedName().get("a.UserService");
        var bService = index.byQualifiedName().get("b.UserService");
        
        assertNotNull(client, "Client should exist");
        assertNotNull(aService, "aService should exist");
        assertNotNull(bService, "bService should exist");
        assertFalse(index.dependsOn(client, aService), "Ambiguous simple name should not resolve to a.UserService");
        assertFalse(index.dependsOn(client, bService), "Ambiguous simple name should not resolve to b.UserService");
    }

    @Test
    void testFullyQualifiedImplements() {
        var sources = Map.of(
            "src/a/BaseInterface.java", "package a; public interface BaseInterface {}",
            "src/b/Impl.java", "package b; class Impl implements a.BaseInterface {}"
        );
        var index = builder.build(sources);
        var base = index.byQualifiedName().get("a.BaseInterface");
        var impl = index.byQualifiedName().get("b.Impl");
        
        assertNotNull(base, "BaseInterface should exist");
        assertNotNull(impl, "Impl should exist");
        assertTrue(index.implementsOrExtends(impl, base), "Impl should implement BaseInterface via FQN");
    }

    @Test
    void testImportedImplements() {
        var sources = Map.of(
            "src/a/BaseInterface.java", "package a; public interface BaseInterface {}",
            "src/b/Impl.java", "package b; import a.BaseInterface; class Impl implements BaseInterface {}"
        );
        var index = builder.build(sources);
        var base = index.byQualifiedName().get("a.BaseInterface");
        var impl = index.byQualifiedName().get("b.Impl");
        
        assertNotNull(base, "BaseInterface should exist");
        assertNotNull(impl, "Impl should exist");
        assertTrue(index.implementsOrExtends(impl, base), "Impl should implement BaseInterface via import");
    }

    @Test
    void testConstructorDependency() {
        var sources = Map.of(
            "src/a/PaymentStrategy.java", "package a; public interface PaymentStrategy {}",
            "src/b/PaymentService.java", "package b; import a.PaymentStrategy; class PaymentService { PaymentService(PaymentStrategy strategy) {} }"
        );
        var index = builder.build(sources);
        var strategy = index.byQualifiedName().get("a.PaymentStrategy");
        var service = index.byQualifiedName().get("b.PaymentService");
        
        assertNotNull(strategy, "Strategy should exist");
        assertNotNull(service, "Service should exist");
        assertTrue(index.dependsOn(service, strategy), "PaymentService should depend on PaymentStrategy via constructor");
    }
}
