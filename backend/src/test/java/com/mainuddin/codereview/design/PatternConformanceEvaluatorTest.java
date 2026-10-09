package com.mainuddin.codereview.design;

import com.mainuddin.codereview.entity.PullRequestFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PatternConformanceEvaluatorTest {
    private final PatternConformanceEvaluator evaluator = new PatternConformanceEvaluator();
    private final JavaTypeIndexBuilder builder = new JavaTypeIndexBuilder();
    private final PatternCandidate strategy = new PatternCandidate("STRATEGY", "GOF", "HIGH", "baseline", "four signals",
            List.of(new PatternCandidate.Evidence("Strategy", "NotificationStrategy", "NotificationStrategy.java", null, 1),
                    new PatternCandidate.Evidence("Context", "NotificationService", "NotificationService.java", null, 1),
                    new PatternCandidate.Evidence("ConcreteStrategy", "PushNotificationStrategy", "PushNotificationStrategy.java", null, 1)));

    private PullRequestFile file(String path, String patch) {
        PullRequestFile file = new PullRequestFile(); file.setFilename(path); file.setPatch(patch); return file;
    }

    @Test void newImplementationConforms() {
        var index = builder.build(Map.of("PushNotificationStrategy.java",
                "class PushNotificationStrategy implements NotificationStrategy { public void send() {} }"));
        var findings = evaluator.evaluate(List.of(strategy), index,
                List.of(file("PushNotificationStrategy.java", "+class PushNotificationStrategy")), true);
        assertEquals("CONFORMS", findings.getFirst().status());
    }

    @Test void providerBranchInExistingContextIsPotentialDeviation() {
        var index = builder.build(Map.of("NotificationService.java",
                "class NotificationService { void send(String provider) { if (provider.equals(\"push\")) { new PushNotificationStrategy().send(); } } }"));
        var findings = evaluator.evaluate(List.of(strategy), index,
                List.of(file("NotificationService.java", "@@ -1 +1 @@\n+ if (provider.equals(\"push\")) {")), true);
        assertEquals("POTENTIAL_DEVIATION", findings.getFirst().status());
    }

    @Test void unrelatedOrMismatchedBaselineIsNeverADeviation() {
        var changed = builder.build(Map.of("ContactController.java", "class ContactController {}"));
        assertEquals("NOT_APPLICABLE", evaluator.evaluate(List.of(strategy), changed,
                List.of(file("README.md", "+hello")), true).getFirst().status());
        assertEquals("INSUFFICIENT_EVIDENCE", evaluator.evaluate(List.of(strategy), changed,
                List.of(file("ContactController.java", "+class ContactController")), false).getFirst().status());
    }

    @Test void controllerRepositoryDependencyIsPotentialLayeringDeviation() {
        var baseline = new PatternCandidate("CONTROLLER_SERVICE_REPOSITORY", "ARCHITECTURAL", "HIGH", "baseline", "chain",
                List.of(new PatternCandidate.Evidence("Service", "UserService", "UserService.java", null, 1),
                        new PatternCandidate.Evidence("Repository", "UserRepository", "UserRepository.java", null, 1)));
        var changed = builder.build(Map.of("UserController.java",
                "@RestController class UserController { UserRepository repository; UserController(UserRepository repository) { this.repository=repository; } }"));
        assertEquals("POTENTIAL_DEVIATION", evaluator.evaluate(List.of(baseline), changed,
                List.of(file("UserController.java", "+UserRepository repository;")), true).getFirst().status());
    }
    @Test void newLayeredChainConformsAndMultipleChecksRemainIndependent() {
        var layering = new PatternCandidate("CONTROLLER_SERVICE_REPOSITORY", "ARCHITECTURAL", "HIGH", "baseline", "chain",
                List.of(new PatternCandidate.Evidence("Service", "OldService", "OldService.java", null, 1),
                        new PatternCandidate.Evidence("Repository", "UserRepository", "UserRepository.java", null, 1)));
        var changed = builder.build(Map.of("NewController.java", "@RestController class NewController { NewService service; }",
                "NewService.java", "@Service class NewService { UserRepository repository; }",
                "Push.java", "class Push implements NotificationStrategy { public void send(){} }"));
        var results = evaluator.evaluate(List.of(strategy, layering), changed, List.of(file("NewController.java", "+NewService service;"),
                file("NewService.java", "+UserRepository repository;"), file("Push.java", "+class Push implements NotificationStrategy")), true);
        assertEquals(2, results.size()); assertTrue(results.stream().allMatch(f -> f.status().equals("CONFORMS")));
    }
    @Test void deviationWinsOverExtensionWithinTheSameStrategyOccurrence() {
        var changed = builder.build(Map.of("Push.java", "class Push implements NotificationStrategy {}",
                "NotificationService.java", "class NotificationService { void send(String provider){ if(provider.equals(\"push\")){ new PushNotificationStrategy().send(); } } }"));
        var results = evaluator.evaluate(List.of(strategy), changed, List.of(file("Push.java", "+class Push implements NotificationStrategy {}"),
                file("NotificationService.java", "+if(provider.equals(\"push\")){ new PushNotificationStrategy().send(); }")), true);
        assertEquals("POTENTIAL_DEVIATION", results.getFirst().status());
    }
    @Test void unrelatedLiteralDoesNotMakeAConditionalProviderDeviation() {
        var changed = builder.build(Map.of("NotificationService.java", "class NotificationService { void send(int x){ if(x>0){} System.out.println(\"push\"); } }"));
        assertEquals("INSUFFICIENT_EVIDENCE", evaluator.evaluate(List.of(strategy), changed,
                List.of(file("NotificationService.java", "+if(x>0){}\n+System.out.println(\"push\");")), true).getFirst().status());
    }
    @Test void summaryPrioritizesReliableDeviationAndRetainsUncertainty() {
        var changed = builder.build(Map.of());
        var irrelevant = evaluator.evaluate(List.of(strategy), changed, List.of(), true);
        assertEquals("NOT_APPLICABLE", PatternConformanceDTO.Summary.from(irrelevant).overallStatus());
        var mismatch = evaluator.evaluate(List.of(), changed, List.of(), false);
        assertEquals("INSUFFICIENT_EVIDENCE", PatternConformanceDTO.Summary.from(mismatch).overallStatus());
        var conforms = new PatternConformanceDTO.Finding("STRATEGY", "GOF", "CONFORMS", "HIGH", null,null,null,"",null,List.of());
        var deviation = new PatternConformanceDTO.Finding("BUILDER", "GOF", "POTENTIAL_DEVIATION", "HIGH", null,null,null,"",null,List.of());
        assertEquals("INSUFFICIENT_EVIDENCE", PatternConformanceDTO.Summary.from(List.of(conforms, mismatch.getFirst())).overallStatus());
        assertEquals("POTENTIAL_DEVIATION", PatternConformanceDTO.Summary.from(List.of(conforms, deviation)).overallStatus());
    }
    @Test void comparesBaseAndHeadAndDoesNotBlameAnExistingProviderBranchOnThePr() {
        String source = "class NotificationService { void send(String provider) {if(provider.equals(\"push\")){ new PushNotificationStrategy().send(); } } }";
        var index = builder.build(Map.of("NotificationService.java", source));
        var files = List.of(file("NotificationService.java", "+if(provider.equals(\"push\")){ new PushNotificationStrategy().send(); }"));
        assertEquals("NOT_APPLICABLE", evaluator.evaluate(List.of(strategy), index, index, files, true).getFirst().status());
        var base = builder.build(Map.of("NotificationService.java", "class NotificationService {}"));
        assertEquals("POTENTIAL_DEVIATION", evaluator.evaluate(List.of(strategy), index, base, files, true).getFirst().status());
    }
    @Test void condensesIrrelevantOccurrencesAndStillChecksSecondaryLocalizedFiles() {
        var builderBaseline = new PatternCandidate("BUILDER", "GOF", "HIGH", "baseline", "fluent",
                List.of(new PatternCandidate.Evidence("Builder", "RequestBuilder", "RequestBuilder.java", null, 1)));
        var results = evaluator.evaluate(List.of(strategy, strategy, builderBaseline), builder.build(Map.of("RequestBuilder.java", "class RequestBuilder {}")),
                List.of(file("RequestBuilder.java", "+class RequestBuilder {}")), true);
        assertEquals(2, results.size()); assertEquals("NOT_APPLICABLE", results.getFirst().status());
        assertEquals("INSUFFICIENT_EVIDENCE", results.getLast().status());
    }

    @Test void ordinaryArithmeticBugFixInPatternFileHasNoApplicableDesignChange() {
        var pattern = new PatternCandidate("BUILDER", "GOF", "HIGH", "Baseline", "Structure",
                List.of(new PatternCandidate.Evidence("Builder", "RequestBuilder", "RequestBuilder.java", null, 1)));
        var base = builder.build(Map.of("RequestBuilder.java", "class RequestBuilder { int size(int n){ return n + 1; } }"));
        var head = builder.build(Map.of("RequestBuilder.java", "class RequestBuilder { int size(int n){ return n + 2; } }"));
        var result = evaluator.evaluate(List.of(pattern), head, base, List.of(file("RequestBuilder.java", "+return n + 2;")), true);
        assertEquals("NOT_APPLICABLE", result.getFirst().status());
    }
    @Test void unrelatedLiteralValidationDoesNotClaimStrategyDeviation() {
        var head = builder.build(Map.of("NotificationService.java", "class NotificationService { void send(String value){ if(value.equals(\"invalid\")) return; } }"));
        var result = evaluator.evaluate(List.of(strategy), head, List.of(file("NotificationService.java", "+if(value.equals(\"invalid\")) return;")), true);
        assertNotEquals("POTENTIAL_DEVIATION", result.getFirst().status());
        assertNull(result.getFirst().reviewPriority());
    }
    @Test void newLayeringDeviationCarriesImpactAndReviewPriorityAndOldDependencyIsSuppressed() {
        var pattern = new PatternCandidate("CONTROLLER_SERVICE_REPOSITORY", "ARCHITECTURAL", "HIGH", "Baseline", "Chain",
                List.of(new PatternCandidate.Evidence("Repository", "Store", "Store.java", null, 1)));
        var head = builder.build(Map.of("Controller.java", "@RestController class Controller { Store repository; }"));
        var base = builder.build(Map.of("Controller.java", "@RestController class Controller {}"));
        var files = List.of(file("Controller.java", "+Store repository;"));
        var result = evaluator.evaluate(List.of(pattern), head, base, files, true).getFirst();
        assertEquals("POTENTIAL_DEVIATION", result.status()); assertEquals("HIGH", result.reviewPriority());
        assertNotNull(result.structuralChange()); assertNotNull(result.designImpact()); assertNotNull(result.recommendation());
        assertEquals("NOT_APPLICABLE", evaluator.evaluate(List.of(pattern), head, head, files, true).getFirst().status());
    }
    @Test void introducingConcreteBehaviorIntoAnExistingConditionIsStillANewDeviation() {
        var base = builder.build(Map.of("NotificationService.java", "class NotificationService { void send(String provider){if(provider.equals(\"push\")){} } }"));
        var head = builder.build(Map.of("NotificationService.java", "class NotificationService { void send(String provider){if(provider.equals(\"push\")){new PushNotificationStrategy().send();} } }"));
        assertEquals("POTENTIAL_DEVIATION", evaluator.evaluate(List.of(strategy), head, base,
                List.of(file("NotificationService.java", "+new PushNotificationStrategy().send();")), true).getFirst().status());
    }
    @Test void loggingInsideExistingStrategyBranchDoesNotCreateANewConcern() {
        var base = builder.build(Map.of("NotificationService.java", "class NotificationService { void send(String provider){if(provider.equals(\"push\")){new PushNotificationStrategy().send();} } }"));
        var head = builder.build(Map.of("NotificationService.java", "class NotificationService { void send(String provider){if(provider.equals(\"push\")){new PushNotificationStrategy().send();log();} } void log(){} }"));
        assertNotEquals("POTENTIAL_DEVIATION", evaluator.evaluate(List.of(strategy), head, base,
                List.of(file("NotificationService.java", "+log();")), true).getFirst().status());
    }
}
