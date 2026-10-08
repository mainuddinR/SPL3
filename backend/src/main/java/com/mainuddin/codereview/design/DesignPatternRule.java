package com.mainuddin.codereview.design;

import java.util.List;

public interface DesignPatternRule {
    List<PatternCandidate> detect(JavaTypeIndex index);
}
