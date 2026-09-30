package com.mainuddin.codereview.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class DebtCategoryAssessmentService {
    private record Rule(String category, Pattern evidence, String reason) {}

    private static Rule rule(String category, String regex, String reason) {
        return new Rule(category, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), reason);
    }

    // Each pattern requires an explicit admission in the comment. Paths and nearby code only corroborate it.
    private static final List<Rule> RULES = List.of(
            rule("DESIGN", "\\b(refactor(?:ing)?(?: required| needed| this)?|tight coupling|coupled design|design flaw|temporary (?:design|architecture)|architectural cleanup|structural workaround)\\b", "Explicit design or refactoring debt"),
            rule("DEFECT", "\\b(known bug|bug fix (?:deferred|needed)|fix (?:the )?bug|broken behavior|incorrect (?:behavior|result|output)|wrong (?:behavior|result|output)|returns? (?:the )?wrong result)\\b", "Explicit defective behavior"),
            rule("TEST", "\\b(?:missing|no|need|needs|add|write|inadequate|disabled|skipped|flaky|incomplete)\\s+(?:(?:unit|integration|regression)\\s+)?tests?\\b|\\b(?:test coverage|tests?)\\s+(?:is |are )?(?:missing|inadequate|low|disabled|skipped|flaky|incomplete)\\b", "Explicit missing or inadequate testing"),
            rule("REQUIREMENT", "\\b(?:missing|unsupported|incomplete|unimplemented|required|not implemented)\\s+(?:required\\s+)?(?:feature|behavior|requirement|capability|functionality)\\b|\\brequired (?:feature|behavior|requirement|capability|functionality) (?:is )?(?:missing|unsupported|incomplete)\\b", "Explicit missing required behavior or capability"),
            rule("DOCUMENTATION", "\\b(?:missing|outdated|incorrect|incomplete|update|write|need|needs)\\s+(?:api\\s+)?(?:documentation|docs|javadoc|readme)\\b|\\b(?:documentation|docs|javadoc|readme)\\s+(?:is |are )?(?:missing|outdated|incorrect|incomplete)\\b", "Explicit documentation debt")
    );

    public CategoryAssessment assess(String commentText, String filename, String precedingCode, String succeedingCode) {
        String comment = commentText == null ? "" : commentText.toLowerCase(Locale.ROOT);
        List<Rule> matches = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.evidence().matcher(comment).find()) matches.add(rule);
        }
        if (matches.isEmpty()) return CategoryAssessment.unclassified("No explicit category evidence in the SATD comment");
        if (matches.size() > 1) return CategoryAssessment.unclassified("Conflicting category evidence in the SATD comment");

        Rule match = matches.get(0);
        String reason = match.reason();
        String path = filename == null ? "" : filename.toLowerCase(Locale.ROOT).replace('\\', '/');
        String context = (precedingCode == null ? "" : precedingCode) + "\n" + (succeedingCode == null ? "" : succeedingCode);
        if (match.category().equals("TEST") && (path.contains("/test/") || path.contains("/tests/") || path.endsWith("test.java") || context.contains("@Test"))) {
            reason += "; test path or nearby test code corroborates it";
        } else if (match.category().equals("DOCUMENTATION") && (path.contains("/docs/") || path.endsWith("readme.md") || context.contains("/**"))) {
            reason += "; documentation path or nearby JavaDoc corroborates it";
        }
        return new CategoryAssessment(match.category(), reason, CategoryAssessment.RULE_VERSION);
    }
}
