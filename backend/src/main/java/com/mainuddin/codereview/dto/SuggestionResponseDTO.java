package com.mainuddin.codereview.dto;

import java.time.LocalDateTime;

public record SuggestionResponseDTO(Long findingId, String status, String suggestion,
                                    LocalDateTime generatedAt, String providerModel,
                                    String promptVersion, String reason) {
    public static SuggestionResponseDTO unavailable(Long id, String reason) {
        return new SuggestionResponseDTO(id, "UNAVAILABLE", null, null, null, null, reason);
    }
}
