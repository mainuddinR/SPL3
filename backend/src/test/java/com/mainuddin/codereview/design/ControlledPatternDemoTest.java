package com.mainuddin.codereview.design;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ControlledPatternDemoTest {
    @Test void separateBaselineHasAllSixPatternsWithRealEvidence() throws Exception {
        Map<String, String> sources = Map.of(
                "NotificationStrategy.java", "package demo; interface NotificationStrategy { String send(String message); }",
                "Email.java", "package demo; class Email implements NotificationStrategy { public String send(String message) { return message; } }",
                "Sms.java", "package demo; class Sms implements NotificationStrategy { public String send(String message) { return message; } }",
                "Notification.java", "package demo; @Entity class Notification { Notification(String message) {} }",
                "NotificationRepository.java", "package demo; interface NotificationRepository extends JpaRepository<Notification, Long> {}",
                "NotificationService.java", "package demo; @Service class NotificationService { NotificationStrategy strategy; NotificationRepository repository; NotificationService(NotificationStrategy strategy, NotificationRepository repository) { this.strategy=strategy; this.repository=repository; } String send(String message) { return strategy.send(message); } }",
                "NotificationController.java", "package demo; @RestController class NotificationController { NotificationService service; NotificationController(NotificationService service) { this.service=service; } }",
                "NotificationBuilder.java", "package demo; class NotificationBuilder { NotificationBuilder message(String message) { return this; } NotificationBuilder id(Long id) { return this; } Notification build() { return new Notification(\"demo\"); } }",
                "NotificationFactory.java", "package demo; class NotificationFactory { NotificationStrategy create(boolean email) { if(email) return new Email(); return new Sms(); } }",
                "Preview.java", "package demo; class Preview { NotificationFactory factory; Preview(NotificationFactory factory) { this.factory=factory; } String view() { return factory.create(true).send(\"demo\"); } }");
        var patterns = new DesignPatternDetectionService().detect(new JavaTypeIndexBuilder().build(sources));
        for (String expected : List.of("STRATEGY", "CONTROLLER_SERVICE_REPOSITORY", "DEPENDENCY_INJECTION", "REPOSITORY_ABSTRACTION", "BUILDER", "FACTORY_METHOD")) {
            var found = patterns.stream().filter(p -> p.patternType().equals(expected) && p.confidence().equals("HIGH")).findFirst();
            assertTrue(found.isPresent(), expected);
            assertTrue(found.get().evidence().stream().allMatch(e -> sources.containsKey(e.sourcePath())));
        }
    }
}
