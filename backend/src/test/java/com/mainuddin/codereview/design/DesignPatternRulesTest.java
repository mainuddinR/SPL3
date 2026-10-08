package com.mainuddin.codereview.design;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DesignPatternRulesTest {
    private final JavaTypeIndexBuilder builder = new JavaTypeIndexBuilder();
    private final DesignPatternDetectionService detector = new DesignPatternDetectionService();

    private boolean detects(String pattern, Map<String, String> sources) {
        return detector.detect(builder.build(sources)).stream().anyMatch(item -> item.patternType().equals(pattern));
    }

    @Test void strategyHasCrossFileRolesAndHighConfidence() {
        var index = builder.build(Map.of(
                "NotificationStrategy.java", "interface NotificationStrategy { void send(); }",
                "EmailNotificationStrategy.java", "class EmailNotificationStrategy implements NotificationStrategy { public void send() {} }",
                "SmsNotificationStrategy.java", "class SmsNotificationStrategy implements NotificationStrategy { public void send() {} }",
                "NotificationService.java", "class NotificationService { private final NotificationStrategy strategy; NotificationService(NotificationStrategy strategy) { this.strategy = strategy; } void send() { strategy.send(); } }"));
        var result = detector.detect(index).stream().filter(item -> item.patternType().equals("STRATEGY")).findFirst().orElseThrow();
        assertEquals("HIGH", result.confidence());
        assertEquals(4, result.evidence().size());
        assertEquals("EmailNotificationStrategy.java", index.unique("EmailNotificationStrategy").path());
        assertTrue(index.unique("EmailNotificationStrategy").implementsTypes().contains("NotificationStrategy"));
    }

    @Test void oneImplementationOrNamingOnlyDoesNotCreateHighStrategy() {
        assertFalse(detects("STRATEGY", Map.of(
                "S.java", "interface S { void run(); }",
                "Only.java", "class Only implements S { public void run() {} }",
                "C.java", "class C { S strategy; void run() { strategy.run(); } }")));
        assertFalse(detects("STRATEGY", Map.of("PaymentStrategy.java", "class PaymentStrategy {}")));
    }

    @Test void factoryNeedsAbstractionCreationAndCaller() {
        assertTrue(detects("FACTORY_METHOD", Map.of(
                "Product.java", "interface Product {}",
                "One.java", "class One implements Product {}",
                "Two.java", "class Two implements Product {}",
                "Creator.java", "class Creator { Product create(boolean flag) { return flag ? new One() : new Two(); } }",
                "Consumer.java", "class Consumer { Creator creator; Product use() { return creator.create(true); } }")));
        assertFalse(detects("FACTORY_METHOD", Map.of("FakeFactory.java", "class FakeFactory { Object make() { return new Object(); } }")));
    }

    @Test void builderNeedsFluentConfigurationAndConstruction() {
        assertTrue(detects("BUILDER", Map.of(
                "Product.java", "class Product { Product(int a, int b) {} }",
                "Maker.java", "class Maker { int a,b; Maker a(int value) { a=value; return this; } Maker b(int value) { b=value; return this; } Product build() { return new Product(a,b); } }")));
    }

    @Test void classicSingletonButNotSpringBean() {
        assertTrue(detects("SINGLETON", Map.of("Only.java",
                "class Only { private static final Only INSTANCE = new Only(); private Only() {} public static Only get() { return INSTANCE; } }")));
        assertFalse(detects("SINGLETON", Map.of("Service.java", "@Service class Service { public Service() {} }")));
    }

    @Test void observerNeedsRegistrationRemovalAndCallbackLoop() {
        assertTrue(detects("OBSERVER", Map.of(
                "Listener.java", "interface Listener { void changed(); }",
                "Subject.java", "import java.util.*; class Subject { List<Listener> listeners = new ArrayList<>(); void add(Listener l) { listeners.add(l); } void remove(Listener l) { listeners.remove(l); } void fire() { for (Listener l : listeners) l.changed(); } }")));
    }

    @Test void adapterNeedsDistinctAdapteeAndDelegatedTargetOperation() {
        assertTrue(detects("ADAPTER", Map.of(
                "Target.java", "interface Target { String request(); }",
                "Legacy.java", "class Legacy { String old() { return \"x\"; } }",
                "Bridge.java", "class Bridge implements Target { private final Legacy legacy; Bridge(Legacy legacy) { this.legacy=legacy; } public String request() { return legacy.old(); } }")));
    }

    @Test void templateMethodNeedsAbstractHooksAndSubclass() {
        assertTrue(detects("TEMPLATE_METHOD", Map.of(
                "Base.java", "abstract class Base { final void execute() { before(); work(); } protected abstract void before(); protected abstract void work(); }",
                "Concrete.java", "class Concrete extends Base { protected void before() {} protected void work() {} }")));
    }

    @Test void decoratorNeedsSameContractAndAddedBehavior() {
        assertTrue(detects("DECORATOR", Map.of(
                "Action.java", "interface Action { void run(); }",
                "LoggingAction.java", "class LoggingAction implements Action { private final Action wrapped; LoggingAction(Action wrapped) { this.wrapped=wrapped; } public void run() { System.out.println(\"before\"); wrapped.run(); } }")));
        assertFalse(detects("DECORATOR", Map.of(
                "Action.java", "interface Action { void run(); }",
                "Simple.java", "class Simple implements Action { private final Action wrapped; Simple(Action wrapped) { this.wrapped=wrapped; } public void run() { wrapped.run(); } }")));
    }

    @Test void springLayeringAndRepositoryAbstractionRequireDependencyPath() {
        var sources = Map.of(
                "UserRepository.java", "interface UserRepository extends JpaRepository<User, Long> {}",
                "UserService.java", "@Service class UserService { private final UserRepository repository; UserService(UserRepository repository) { this.repository=repository; } }",
                "UserController.java", "@RestController class UserController { private final UserService service; UserController(UserService service) { this.service=service; } }",
                "User.java", "class User {}" );
        assertTrue(detects("CONTROLLER_SERVICE_REPOSITORY", sources));
        assertTrue(detects("REPOSITORY_ABSTRACTION", sources));
        assertTrue(detects("DEPENDENCY_INJECTION", sources));
    }

    @Test void dtoMapperNeedsActualConversionAndBoundaryUse() {
        assertTrue(detects("DTO_MAPPER_SEPARATION", Map.of(
                "User.java", "@Entity class User {}",
                "UserDto.java", "class UserDto {}",
                "UserMapper.java", "class UserMapper { UserDto map(User user) { return new UserDto(); } }",
                "UserService.java", "@Service class UserService { UserMapper mapper; UserDto view(User user) { return mapper.map(user); } }")));
        assertFalse(detects("DTO_MAPPER_SEPARATION", Map.of("UserDto.java", "class UserDto {}")));
    }

    @Test void invalidJavaCannotSilentlyBecomeACompleteProfile() {
        assertThrows(DesignSourceException.class,
                () -> builder.build(Map.of("Broken.java", "class Broken { void run( }")));
    }
}
