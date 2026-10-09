package com.mainuddin.codereview.design;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mainuddin.codereview.exception.ResourceNotFoundException;
import com.mainuddin.codereview.repository.PrPatternCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class DesignAdvicePersistence {
    private final PrPatternCheckRepository checks;
    private final ObjectMapper json;
    public DesignAdvicePersistence(PrPatternCheckRepository checks, ObjectMapper json) { this.checks = checks; this.json = json; }

    @Transactional
    public PatternConformanceDTO.Advice saveOrGet(Long prId, Long checkId, int index, PatternConformanceDTO.Advice advice) {
        var check = checks.lockForAdvice(checkId, prId).orElseThrow(() -> new ResourceNotFoundException("Design check not found"));
        try {
            List<PatternConformanceDTO.Finding> items = new ArrayList<>(json.readValue(check.getFindingsJson(), new TypeReference<List<PatternConformanceDTO.Finding>>() {}));
            if (!"COMPLETED".equals(check.getStatus()) || index < 0 || index >= items.size()
                    || !"POTENTIAL_DEVIATION".equals(items.get(index).status())) throw new ResourceNotFoundException("Design finding not found");
            if (items.get(index).aiSuggestion() != null) return items.get(index).aiSuggestion();
            items.set(index, items.get(index).withAdvice(advice));
            check.setFindingsJson(json.writeValueAsString(items)); checks.save(check);
            return advice;
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException("Saved design evidence is invalid", invalid); }
    }
}
