package com.mainuddin.codereview.design;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class DesignPatternDetectionService {
    private final List<DesignPatternRule> rules;

    public DesignPatternDetectionService() {
        List<DesignPatternRule> configured = new ArrayList<>(GoFPatternRules.all());
        configured.addAll(ArchitecturalPatternRules.all());
        rules = List.copyOf(configured);
    }

    public List<PatternCandidate> detect(JavaTypeIndex index) {
        List<PatternCandidate> candidates = new ArrayList<>();
        for (DesignPatternRule rule : rules) candidates.addAll(rule.detect(index));
        return List.copyOf(candidates);
    }
}
